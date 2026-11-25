package com.wildcreatures.entity;

import com.wildcreatures.CreatureConfig;
import com.wildcreatures.DragonStats;
import com.wildcreatures.ModItems;
import com.wildcreatures.ModSounds;
import com.wildcreatures.WildCreatures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * A dark blue and gold dragon from the windswept mountains, with glowing lightning markings. Instead of breathing,
 * it rears up, roars at the sky and calls lightning down on whatever it's hunting. Sparks crackle on the ground where
 * it aims, and half a second later lightning strikes that spot, so you can run out of the way. Lightning can't hurt it,
 * and a lightning rod nearby pulls its strikes to the rod instead (and then they start no fires).
 */
public class LightningDragon extends AbstractDragon {
    public static final ResourceKey<DamageType> LIGHTNING_STRIKE =
            ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(WildCreatures.MOD_ID, "lightning_strike"));

    /** The first spot is marked this many ticks into a call, then a new one every EVERY ticks; each strikes WARNING ticks after it's marked. */
    private static final int FIRST_MARK = 6, EVERY = 12, WARNING = 10;
    /** A lightning rod this close to a strike (and open to the sky) takes the strike instead. */
    private static final int ROD_RANGE = 16;

    @Nullable private BlockPos marked;

    public LightningDragon(EntityType<? extends LightningDragon> type, Level level) {
        super(type, level);
    }

    @Override public DragonStats stats() { return CreatureConfig.LIGHTNING; }

    @Override protected ResourceKey<DamageType> breathDamageType() { return LIGHTNING_STRIKE; }

    @Override protected Item scaleItem() { return ModItems.LIGHTNING_DRAGON_SCALE.get(); }

    @Override protected SoundEvent breathSound() { return ModSounds.LIGHTNING_DRAGON_CALL.get(); }

    /** It roars up at the sky while it calls the lightning. */
    @Override protected float breathPitch(Vec3 mouth, Vec3 aim) { return -38.0F; }

    /** A little deeper than the Red Dragon. */
    @Override protected float voicePitch() { return 0.9F; }

    /** Lightning, its own or a storm's, can't hurt it. */
    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return source.is(DamageTypes.LIGHTNING_BOLT) || source.is(LIGHTNING_STRIKE) || super.isInvulnerableTo(source);
    }

    /** Struck by lightning (a storm, a Channeling trident): it soaks up the charge and heals a little. */
    @Override
    public void thunderHit(ServerLevel level, LightningBolt bolt) {
        heal(10.0F);
    }

    // ------------------------------------------------------------------ the lightning call

    @Override
    protected void breathTick(Vec3 aim) {
        if (!(level() instanceof ServerLevel server)) return;
        int total = stats().breathTicks.get();
        int elapsed = total - breathTicksLeft();
        int phase = elapsed - FIRST_MARK;
        if (phase < 0) {                                    // a new call: forget any spot left from the last one
            marked = null;
            return;
        }
        if (phase % EVERY == 0 && elapsed + WARNING <= total) {
            marked = strikeSpot(server, aim);
            server.playSound(null, marked, ModSounds.LIGHTNING_DRAGON_SPARK.get(), getSoundSource(), 1.5F, 0.9F + random.nextFloat() * 0.2F);
        }
        if (marked == null) return;
        if (phase % EVERY == WARNING) {
            strike(server, marked);
            marked = null;
        } else {
            warn(server, marked, elapsed);
        }
    }

    /** The ground under the aim, or the top of a lightning rod close by. */
    private BlockPos strikeSpot(ServerLevel server, Vec3 aim) {
        BlockPos ground = server.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, BlockPos.containing(aim));
        Optional<BlockPos> rod = server.getPoiManager().findClosest(p -> p.is(PoiTypes.LIGHTNING_ROD),
                p -> p.getY() == server.getHeight(Heightmap.Types.WORLD_SURFACE, p.getX(), p.getZ()) - 1,
                ground, ROD_RANGE, PoiManager.Occupancy.ANY);
        return rod.map(BlockPos::above).orElse(ground);
    }

    /** Sparks crackling in a ring on the ground where the strike will land. */
    private void warn(ServerLevel server, BlockPos spot, int tick) {
        double r = radius();
        double x = spot.getX() + 0.5, y = spot.getY() + 0.1, z = spot.getZ() + 0.5;
        for (int i = 0; i < 10; i++) {
            double a = tick * 0.3 + i * Math.PI * 2 / 10;
            server.sendParticles(ParticleTypes.ELECTRIC_SPARK, x + Math.cos(a) * r, y, z + Math.sin(a) * r, 2, 0.1, 0.05, 0.1, 0.02);
        }
        server.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y + 0.6, z, 8, r * 0.35, 0.5, r * 0.35, 0.1);
        if (tick % 2 == 0) server.sendParticles(ParticleTypes.END_ROD, x, y + 0.2, z, 2, r * 0.3, 0.1, r * 0.3, 0.03);
    }

    /** One lightning strike: the flash, the bolt and the thunder, then damage and fire around it. */
    private void strike(ServerLevel server, BlockPos spot) {
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(server);
        if (bolt == null) return;
        Vec3 c = Vec3.atBottomCenterOf(spot);
        bolt.moveTo(c);
        bolt.setVisualOnly(true);           // only the look and sound: the dragon decides below what gets hit
        server.addFreshEntity(bolt);

        double r = radius();
        float damage = stats().breathDamage.get().floatValue() * sizeDamage();
        AABB box = new AABB(c.x - r - 1, c.y - 3, c.z - r - 1, c.x + r + 1, c.y + 6, c.z + r + 1);
        for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, box, e -> !isBreathImmune(e))) {
            double dx = e.getX() - c.x, dz = e.getZ() - c.z;
            double reach = r + e.getBbWidth() / 2;
            if (dx * dx + dz * dz > reach * reach || ForgeEventFactory.onEntityStruckByLightning(e, bolt)) continue;
            boolean hurt = e.hurt(breathDamage(), damage);
            onBreathHit(e, hurt);
            // everything real lightning does to mobs: creepers get charged, pigs turn into zombified piglins...
            if (!(e instanceof Player)) e.thunderHit(server, bolt);
        }

        // fire, like normal lightning: only on Normal and Hard, and never around a lightning rod that took the strike
        boolean rod = server.getBlockState(spot.below()).is(Blocks.LIGHTNING_ROD);
        boolean fireDifficulty = server.getDifficulty() == Difficulty.NORMAL || server.getDifficulty() == Difficulty.HARD;
        if (!rod && fireDifficulty && stats().breathAffectsBlocks.get() && ForgeEventFactory.getMobGriefingEvent(server, this)
                && server.getGameRules().getBoolean(GameRules.RULE_DOFIRETICK)) {
            affectBlock(spot);
            for (int i = 0; i < 3; i++) affectBlock(spot.offset(random.nextInt(3) - 1, random.nextInt(3) - 1, random.nextInt(3) - 1));
        }
    }

    private double radius() { return stats().effectRadius.get() * Math.max(1.0, getSizeScale()); }

    /** Sets whatever the strike hits on fire for a few seconds. */
    @Override
    protected void onBreathHit(LivingEntity target, boolean hurt) {
        int seconds = stats().effectSeconds.get();
        if (seconds > 0) target.setSecondsOnFire(seconds);
    }

    /** Lightning sets the ground where it strikes on fire, like normal lightning does. */
    @Override
    protected void affectBlock(BlockPos pos) {
        Level level = level();
        BlockState fire = BaseFireBlock.getState(level, pos);
        if (level.getBlockState(pos).isAir() && fire.canSurvive(level, pos)) level.setBlockAndUpdate(pos, fire);
    }

    /** Client side, while calling: sparks crackle around its head and a thin beam of light rises from its jaws. */
    @Override
    protected void breathParticles(Vec3 mouth, Vec3 dir, Vec3 aim) {
        for (int i = 0; i < 6; i++) {
            level().addParticle(ParticleTypes.ELECTRIC_SPARK,
                    mouth.x + random.nextGaussian() * 0.8, mouth.y + random.nextGaussian() * 0.7, mouth.z + random.nextGaussian() * 0.8,
                    random.nextGaussian() * 0.25, random.nextGaussian() * 0.25, random.nextGaussian() * 0.25);
        }
        for (int i = 0; i < 3; i++) {
            level().addParticle(ParticleTypes.END_ROD, mouth.x + random.nextGaussian() * 0.12, mouth.y + random.nextDouble() * 1.5,
                    mouth.z + random.nextGaussian() * 0.12, random.nextGaussian() * 0.02, 0.5 + random.nextDouble() * 0.4, random.nextGaussian() * 0.02);
        }
        // little sparks running over its back
        double s = getSizeScale();
        Vec3 back = position().add(0, 3.2 * s, 0).add(Vec3.directionFromRotation(0, yBodyRot).scale((random.nextDouble() * 6 - 3) * s));
        level().addParticle(ParticleTypes.ELECTRIC_SPARK, back.x + random.nextGaussian() * 0.6, back.y + random.nextGaussian() * 0.3,
                back.z + random.nextGaussian() * 0.6, 0, 0.05, 0);
    }
}
