package com.wildcreatures.entity;

import com.wildcreatures.CreatureConfig;
import com.wildcreatures.DragonStats;
import com.wildcreatures.ModEffects;
import com.wildcreatures.ModItems;
import com.wildcreatures.ModSounds;
import com.wildcreatures.WildCreatures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.EnumSet;

/**
 * A deep-sea dragon: dark teal and turquoise with glowing spots, a fin along its back and a forked tail fin.
 * It lives in deep oceans and swims fast, rowing with its wings like a sea turtle. Instead of fire it fires a
 * blast of water that shoves you back, knocks you out of your boat and drags you under for a few seconds.
 * It can crawl out onto land after you, but it's slow there and heads back to the sea when it can.
 */
public class SeaDragon extends AbstractDragon {
    public static final ResourceKey<DamageType> WATER_BLAST =
            ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(WildCreatures.MOD_ID, "water_blast"));

    /** How hard each blast hit shoves (on top of the normal knockback from being hurt). */
    private static final float BLAST_KNOCKBACK = 0.8F;
    /** On land it moves at this fraction of its normal walking speed. */
    private static final double LAND_SPEED = 0.55;
    /** No new Sea Dragon spawns naturally within this many blocks of another one. */
    private static final int TERRITORY = 64;

    private static final DustParticleOptions SPRAY = new DustParticleOptions(new Vector3f(0.78F, 0.95F, 1.0F), 1.6F);
    private static final DustParticleOptions JET = new DustParticleOptions(new Vector3f(0.35F, 0.75F, 0.95F), 1.3F);

    public SeaDragon(EntityType<? extends SeaDragon> type, Level level) {
        super(type, level);
        setPathfindingMalus(BlockPathTypes.WATER, 0.0F);
        setPathfindingMalus(BlockPathTypes.WATER_BORDER, 0.0F);
    }

    @Override public DragonStats stats() { return CreatureConfig.SEA; }

    @Override protected ResourceKey<DamageType> breathDamageType() { return WATER_BLAST; }

    @Override protected Item scaleItem() { return ModItems.SEA_DRAGON_SCALE.get(); }

    @Override protected SoundEvent breathSound() { return ModSounds.SEA_DRAGON_BLAST.get(); }

    /** A deep, whale-like voice. */
    @Override public float getVoicePitch() { return 0.78F + (random.nextFloat() - 0.5F) * 0.12F; }

    // ------------------------------------------------------------------ a sea creature

    @Override public boolean canBreatheUnderwater() { return true; }

    @Override public boolean isPushedByFluid() { return false; }

    /** Counts as a water creature, so tridents with Impaling hit it harder. */
    @Override public MobType getMobType() { return MobType.WATER; }

    @Override protected boolean floatsInWater() { return false; }

    /** Dragons never take fall damage, so a stranded one happily drops off a cliff into the sea below. */
    @Override public int getMaxFallDistance() { return 32; }

    @Override protected boolean swimPose() { return isInWater(); }

    /** It spawns in water, so water inside its spawn box is fine. */
    @Override public boolean checkSpawnObstruction(LevelReader level) { return level.isUnobstructed(this); }

    @Override protected MoveControl createGroundMoveControl() { return new SwimControl(this); }

    @Override
    protected void registerWanderGoals() {
        goalSelector.addGoal(4, new BackToWaterGoal());
        goalSelector.addGoal(5, new SwimAroundGoal());
    }

    /**
     * Spawn check for mob spawners and the like: never on Peaceful. If the normal mob spawning ever places one
     * (another mod adding it to a biome, say), the same rules as {@link #canSpawnAt} apply, 1 in spawnRarity tries.
     * Its own natural spawns come from {@link com.wildcreatures.world.SeaDragonSpawner}.
     */
    public static SpawnPlacements.SpawnPredicate<SeaDragon> spawnRules() {
        return (type, level, reason, pos, random) -> {
            if (level.getDifficulty() == Difficulty.PEACEFUL) return false;
            if (reason != MobSpawnType.NATURAL && reason != MobSpawnType.CHUNK_GENERATION) return true;
            int rarity = CreatureConfig.SEA.spawnRarity.get();
            return rarity > 0 && random.nextInt(rarity) == 0 && canSpawnAt(level, pos);
        };
    }

    /**
     * Whether a Sea Dragon may turn up here naturally: resting on the sea floor under at least 8 blocks of water,
     * and never within {@link #TERRITORY} blocks of another Sea Dragon, so they never pile up in one place.
     */
    public static boolean canSpawnAt(LevelAccessor level, BlockPos pos) {
        if (!level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)) return false;
        for (int i = 0; i < 8; i++) {
            if (!level.getFluidState(pos.above(i)).is(FluidTags.WATER)) return false;
        }
        return level.getEntitiesOfClass(SeaDragon.class, new AABB(pos).inflate(TERRITORY, 48, TERRITORY)).isEmpty();
    }

    /** Swimming counts up-and-down movement too, so it strokes its tail and wings when it dives or rises. */
    @Override
    public void calculateEntityAnimation(boolean includeHeight) {
        super.calculateEntityAnimation(includeHeight || isInWater());
    }

    // ------------------------------------------------------------------ swimming

    @Override
    public void travel(Vec3 input) {
        if (isInWater() && isControlledByLocalInstance() && !isFlying()) {
            move(MoverType.SELF, getDeltaMovement());
            Vec3 v = getDeltaMovement();
            if (horizontalCollision) v = v.add(0, 0.06, 0);                  // slide up over rocks and the sea floor
            setDeltaMovement(v.scale(getMoveControl().hasWanted() ? 0.98 : 0.9)); // water slows it when it stops rowing
            calculateEntityAnimation(false);
            return;
        }
        super.travel(input);
    }

    /** In water it swims straight at its prey, staying a bite's length away and a little below it. */
    @Override
    protected boolean swimToward(LivingEntity target) {
        if (!isInWater()) return false;
        double s = getSizeScale();
        Vec3 away = position().subtract(target.position()).multiply(1, 0, 1);
        away = away.lengthSqr() < 1.0E-3 ? Vec3.ZERO : away.normalize().scale(3.5 * s);
        getMoveControl().setWantedPosition(target.getX() + away.x, target.getY() - 1.2 * s, target.getZ() + away.z, 1.25);
        return true;
    }

    @Override
    protected void holdPosition() {
        super.holdPosition();
        if (moveControl instanceof SwimControl swim) swim.stop();
    }

    /**
     * In water: rows straight toward the wanted point in any direction, turning its whole body to face where it's going.
     * On land: walks like the other dragons, but slowly.
     */
    private static class SwimControl extends MoveControl {
        private final SeaDragon dragon;

        SwimControl(SeaDragon dragon) {
            super(dragon);
            this.dragon = dragon;
        }

        void stop() { operation = Operation.WAIT; }

        @Override
        public void tick() {
            if (!dragon.isInWater()) {
                double full = speedModifier;
                speedModifier = full * LAND_SPEED;
                super.tick();
                speedModifier = full;
                return;
            }
            dragon.setSpeed(0.0F);
            dragon.setZza(0.0F);
            dragon.setXxa(0.0F);
            dragon.setYya(0.0F);
            if (operation != Operation.MOVE_TO) return;
            Vec3 to = new Vec3(wantedX - dragon.getX(), wantedY - dragon.getY(), wantedZ - dragon.getZ());
            double dist = to.length();
            if (dist < 0.8) {
                operation = Operation.WAIT;
                return;
            }
            // swimSpeed is its chasing speed (speed modifier 1.25); wandering is slower
            double top = dragon.stats().swimSpeed.get() * speedModifier / 1.25;
            Vec3 desired = to.scale(Math.min(top, dist * 0.15) / dist);
            Vec3 v = dragon.getDeltaMovement();
            dragon.setDeltaMovement(v.add(desired.subtract(v).scale(0.15)));
            if (to.horizontalDistanceSqr() > 0.04 && !dragon.isBreathing()) {
                float yaw = (float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90.0F;
                dragon.setYRot(rotlerp(dragon.getYRot(), yaw, 7.0F));
                dragon.yBodyRot = dragon.getYRot();
            }
        }
    }

    /** With nothing to hunt, cruise between random spots in the open water, pausing now and then. */
    private class SwimAroundGoal extends Goal {
        private int pause;

        SwimAroundGoal() { setFlags(EnumSet.of(Flag.MOVE)); }

        @Override public boolean canUse() { return isInWater() && getTarget() == null; }

        @Override public boolean canContinueToUse() { return canUse(); }

        @Override public void start() { pause = 0; }

        @Override
        public void tick() {
            if (getMoveControl().hasWanted()) return;
            if (pause == 0) pause = 10 + random.nextInt(30);   // reached the spot: drift a moment (1 to 4 seconds), then pick another
            if (--pause <= 0) {
                pause = 0;
                pick();
            }
        }

        private void pick() {
            double s = getSizeScale();
            for (int tries = 0; tries < 12; tries++) {
                int x = Mth.floor(getX() + (random.nextDouble() * 2 - 1) * 24);
                int z = Mth.floor(getZ() + (random.nextDouble() * 2 - 1) * 24);
                int floor = level().getHeight(Heightmap.Types.OCEAN_FLOOR, x, z);
                int surface = level().getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                if (!level().getFluidState(new BlockPos(x, surface - 1, z)).is(FluidTags.WATER)) continue;
                int low = floor + 1, high = surface - Mth.ceil(3.6 * s);
                if (high < low) continue;
                int y = low + random.nextInt(high - low + 1);
                getMoveControl().setWantedPosition(x + 0.5, y, z + 0.5, 0.6);
                return;
            }
        }
    }

    /** Stranded on land with nothing to chase nearby: crawl back to the nearest water. */
    private class BackToWaterGoal extends Goal {
        @Nullable private BlockPos water;
        @Nullable private Vec3 plunge;
        private int searchDelay, timeLeft;

        BackToWaterGoal() { setFlags(EnumSet.of(Flag.MOVE)); }

        @Override
        public boolean canUse() {
            if (isInWater() || --searchDelay > 0) return false;
            searchDelay = 5;                                    // canUse runs every other tick: look about twice a second
            LivingEntity t = getTarget();
            if (t != null && t.isAlive() && distanceToSqr(t) < 20 * 20) return false;
            water = findWater();
            return water != null;
        }

        @Override public boolean canContinueToUse() { return !isInWater() && timeLeft > 0; }

        @Override public boolean requiresUpdateEveryTick() { return true; }

        @Override
        public void start() {
            timeLeft = 300;
            plunge = null;
            getNavigation().moveTo(water.getX() + 0.5, water.getY(), water.getZ() + 0.5, 1.0);
        }

        @Override public void stop() { getNavigation().stop(); }

        @Override
        public void tick() {
            timeLeft--;
            // the path finder can't always fit a dragon down a steep bank: when the path runs out short of the water,
            // it heads straight out over the water nearby (it never minds a drop)
            if (getNavigation().isDone()) {
                if (plunge == null) plunge = plungeSpot();
                getMoveControl().setWantedPosition(plunge.x, plunge.y, plunge.z, 1.0);
            }
        }

        /** A spot a few blocks out toward the middle of the water around it, so it doesn't just walk along the bank. */
        private Vec3 plungeSpot() {
            BlockPos here = blockPosition();
            double sx = 0, sz = 0;
            for (int dx = -5; dx <= 5; dx++) {
                for (int dz = -5; dz <= 5; dz++) {
                    int x = here.getX() + dx, z = here.getZ() + dz;
                    if (level().getFluidState(new BlockPos(x, level().getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1, z)).is(FluidTags.WATER)) {
                        sx += dx;
                        sz += dz;
                    }
                }
            }
            Vec3 way = new Vec3(sx, 0, sz);
            if (way.lengthSqr() < 1.0E-4) return Vec3.atBottomCenterOf(water);
            Vec3 spot = position().add(way.normalize().scale(4.0));
            return new Vec3(spot.x, water.getY(), spot.z);
        }

        @Nullable
        private BlockPos findWater() {
            BlockPos here = blockPosition();
            for (int r = 1; r <= 24; r++) {
                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;   // just the ring at distance r
                        int x = here.getX() + dx, z = here.getZ() + dz;
                        BlockPos top = new BlockPos(x, level().getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1, z);
                        // at least 2 deep, so a puddle or a farm's water doesn't count
                        if (level().getFluidState(top).is(FluidTags.WATER) && level().getFluidState(top.below()).is(FluidTags.WATER)) return top;
                    }
                }
            }
            return null;
        }
    }

    // ------------------------------------------------------------------ the water blast

    /**
     * Shoves the target back, knocks it off whatever it's riding (boats!), puts out fire and drags it under.
     * A shield that blocks the blast stops all of that. The drag starts with the first hit of each blast and isn't
     * topped up by the rest of it, so you always get a few seconds to swim back up before the next one.
     */
    @Override
    protected void onBreathHit(LivingEntity target, boolean hurt) {
        if (target.isOnFire()) target.clearFire();
        if (!hurt) return;
        if (target.isPassenger()) target.stopRiding();
        Vec3 push = target.position().subtract(position()).multiply(1, 0, 1);
        if (push.lengthSqr() > 1.0E-4) {
            push = push.normalize();
            target.knockback(BLAST_KNOCKBACK, -push.x, -push.z);
            target.hurtMarked = true;                                   // players need to be told about the shove
        }
        int seconds = stats().effectSeconds.get();
        if (seconds > 0 && !target.hasEffect(ModEffects.UNDERTOW.get()))
            target.addEffect(new MobEffectInstance(ModEffects.UNDERTOW.get(), seconds * 20, 0, false, false, true), this);
    }

    /** Where the blast lands: fires and campfires go out, and lava cools to obsidian (or cobblestone if it was flowing). */
    @Override
    protected void affectBlock(BlockPos pos) {
        Level level = level();
        BlockState state = level.getBlockState(pos);
        if (state.is(BlockTags.FIRE)) {
            level.removeBlock(pos, false);
            level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.5F, 1.6F);
        } else if (state.getBlock() instanceof CampfireBlock && state.getValue(CampfireBlock.LIT)) {
            CampfireBlock.dowse(this, level, pos, state);
            level.setBlockAndUpdate(pos, state.setValue(CampfireBlock.LIT, false));
            level.playSound(null, pos, SoundEvents.GENERIC_EXTINGUISH_FIRE, SoundSource.BLOCKS, 0.5F, 1.4F);
        } else if (state.getFluidState().is(FluidTags.LAVA)) {
            level.setBlockAndUpdate(pos, (state.getFluidState().isSource() ? Blocks.OBSIDIAN : Blocks.COBBLESTONE).defaultBlockState());
            level.playSound(null, pos, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 0.5F, 2.2F + random.nextFloat() * 0.6F);
            if (level instanceof ServerLevel server)                    // a puff of steam
                server.sendParticles(ParticleTypes.CLOUD, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 3, 0.3, 0.1, 0.3, 0.02);
        }
    }

    /** Client side: a jet of water from its jaws, bubbles underwater and spray in the air, bursting where it hits. */
    @Override
    protected void breathParticles(Vec3 mouth, Vec3 dir, Vec3 aim) {
        Level level = level();
        double length = Math.max(1.0, aim.distanceTo(mouth));
        for (int i = 0; i < 26; i++) {
            double t = random.nextDouble();
            Vec3 p = mouth.add(dir.scale(t * length)).add(random.nextGaussian() * 0.15 * (0.4 + t), random.nextGaussian() * 0.15 * (0.4 + t),
                    random.nextGaussian() * 0.15 * (0.4 + t));
            Vec3 v = dir.scale(0.6 + random.nextDouble() * 0.5);
            boolean wet = level.getFluidState(BlockPos.containing(p)).is(FluidTags.WATER);
            if (wet) {
                level.addParticle(i % 3 == 0 ? ParticleTypes.DOLPHIN : ParticleTypes.BUBBLE, p.x, p.y, p.z, v.x, v.y, v.z);
            } else {
                level.addParticle(i % 4 == 0 ? SPRAY : i % 4 == 1 ? JET : ParticleTypes.SPLASH, p.x, p.y, p.z, v.x * 0.3, v.y * 0.3, v.z * 0.3);
            }
        }
        // a burst of spray and foam where it hits
        boolean wetEnd = level.getFluidState(BlockPos.containing(aim)).is(FluidTags.WATER);
        for (int i = 0; i < 8; i++) {
            double vx = random.nextGaussian() * 0.15, vy = 0.1 + random.nextDouble() * 0.25, vz = random.nextGaussian() * 0.15;
            level.addParticle(wetEnd ? ParticleTypes.BUBBLE : i % 2 == 0 ? ParticleTypes.SPLASH : SPRAY,
                    aim.x + random.nextGaussian() * 0.6, aim.y + random.nextDouble() * 0.8, aim.z + random.nextGaussian() * 0.6, vx, vy, vz);
        }
    }
}
