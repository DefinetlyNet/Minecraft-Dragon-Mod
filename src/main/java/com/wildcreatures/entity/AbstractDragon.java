package com.wildcreatures.entity;

import com.wildcreatures.CreatureConfig;
import com.wildcreatures.DragonStats;
import com.wildcreatures.ModItems;
import com.wildcreatures.ModSounds;
import com.wildcreatures.WildCreatures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.SnowGolem;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Everything dragons have in common: walking and flying, biting, breathing at things from range,
 * the three sizes, sounds, saving, and the animation timers the model reads.
 * Each kind of dragon extends this and only says what its breath does, which config section
 * it uses and which scale it drops.
 * <p>
 * Dragons can also be tamed (the Black Dragon by feeding it; {@link #tamedByFeeding}). A tamed dragon follows its owner,
 * stays put when told to, fights for its owner, and once it wears a saddle the owner can ride it: it walks and flies
 * where the rider looks, and the rider's attack key fires its breath ({@link #riderBreath}).
 */
public abstract class AbstractDragon extends Monster implements OwnableEntity {
    /** The model is drawn 1.8x larger than its pixel size. Used by the renderer and for the mouth position. */
    public static final float MODEL_SCALE = 1.8F;

    private static final EntityDataAccessor<Boolean> FLYING = SynchedEntityData.defineId(AbstractDragon.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> BREATHING = SynchedEntityData.defineId(AbstractDragon.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Vector3f> BREATH_TARGET = SynchedEntityData.defineId(AbstractDragon.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Float> SIZE = SynchedEntityData.defineId(AbstractDragon.class, EntityDataSerializers.FLOAT);
    /** Tame, told to stay and saddled, as bits. */
    private static final EntityDataAccessor<Byte> TAME_FLAGS = SynchedEntityData.defineId(AbstractDragon.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(AbstractDragon.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final int TAME = 1, STAYING = 2, SADDLED = 4;

    /** Dragon sizes: how big it is drawn, and how its damage is scaled. Health comes from the config. */
    public enum Size {
        SMALL(0.7F, 0.75F), NORMAL(1.0F, 1.0F), GIANT(1.5F, 1.4F);
        public final float scale, damage;
        Size(float scale, float damage) { this.scale = scale; this.damage = damage; }
    }
    private Size size = Size.NORMAL;

    private static final byte BITE_EVENT = 4, TAME_FAIL_EVENT = 6, TAME_EVENT = 7;
    private static final int BITE_ANIM_TICKS = 10;

    /** Where the rider sits, in model pixels: how far forward of the middle of the body, and how high the top of the saddle is. */
    private static final float SEAT_FORWARD = 4.5F, SEAT_HEIGHT = 26.75F;
    /** A rider's jump key, read from LivingEntity.jumping (protected; f_20899_ is its name outside the dev environment). */
    @Nullable private static final Field JUMPING = findJumpingField();

    private final MoveControl groundMoveControl;
    private final FlyControl flyMoveControl;

    // server-side timers
    private int biteCooldown, biteTicks, breathCooldown = 60, breathTicks, takeoffCooldown = 40;
    private int flightTicks, flightLimit, stuckTicks;
    private boolean landing;
    // server side, while ridden
    private boolean riderBreathing;
    private int riderTakeoffDelay;

    // client-side animation state
    private int biteAnim;
    private float flyAnim, flyAnimO, breathAnim, breathAnimO, swimAnim, swimAnimO, swimPitch, swimPitchO, climb, climbO, sitAnim, sitAnimO;

    protected AbstractDragon(EntityType<? extends AbstractDragon> type, Level level) {
        super(type, level);
        this.xpReward = 60;
        this.groundMoveControl = createGroundMoveControl();
        this.moveControl = groundMoveControl;
        this.flyMoveControl = new FlyControl(this);
        this.setMaxUpStep(1.5F);
        if (CreatureConfig.SPEC.isLoaded()) {
            DragonStats stats = stats();
            getAttribute(Attributes.MAX_HEALTH).setBaseValue(stats.health.get());
            getAttribute(Attributes.ARMOR).setBaseValue(stats.armor.get());
            getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(stats.biteDamage.get());
            setHealth(getMaxHealth());
        }
    }

    // ------------------------------------------------------------------ what each kind of dragon fills in

    /** This kind of dragon's section of the config file. */
    public abstract DragonStats stats();

    /** The damage type of the breath. It picks the death message and which armor, potions and mobs resist it. */
    protected abstract ResourceKey<DamageType> breathDamageType();

    /** Called for every creature caught in the breath, about five times a second. {@code hurt} says whether the damage got through. */
    protected abstract void onBreathHit(LivingEntity target, boolean hurt);

    /** Changes one block where the breath lands. Only called when the config and the mobGriefing rule allow it. */
    protected abstract void affectBlock(BlockPos pos);

    /** Client side, every tick while breathing: the particles from the mouth toward where the breath lands. */
    protected abstract void breathParticles(Vec3 mouth, Vec3 dir, Vec3 aim);

    /** The scale this dragon drops. Giants drop a handful extra on top of the loot table. */
    protected abstract Item scaleItem();

    /** The sound when it starts breathing. */
    protected SoundEvent breathSound() { return ModSounds.DRAGON_BREATH.get(); }

    /** Which liquids stop the breath. Fire goes straight through water to the bottom; a cold breath could stop at the surface. */
    protected ClipContext.Fluid breathBlockedBy() { return ClipContext.Fluid.NONE; }

    /** Head pitch in degrees while using the breath (negative looks up). By default it points the mouth at the aim. */
    protected float breathPitch(Vec3 mouth, Vec3 aim) {
        double h = Math.sqrt((aim.x - mouth.x) * (aim.x - mouth.x) + (aim.z - mouth.z) * (aim.z - mouth.z));
        return Mth.clamp((float) (-Mth.atan2(aim.y - mouth.y, h) * Mth.RAD_TO_DEG), -40.0F, 50.0F);
    }

    /** Ticks left in the current breath (counts down to 0). */
    protected int breathTicksLeft() { return breathTicks; }

    /** How much harder (or softer) this dragon hits for its size: 1 for a normal dragon. */
    protected float sizeDamage() { return size.damage; }

    /** Whether a wild one can be tamed by feeding it meat or fish. */
    protected boolean tamedByFeeding() { return false; }

    /** Whether a wild one hunts players and villagers it sees. If not, it only fights back when something hurts it. */
    protected boolean huntsOnSight() { return true; }

    // ------------------------------------------------------------------ hooks for dragons that don't walk and fly (the Sea Dragon)

    /** How it moves when it isn't flying. Called once, from the constructor. */
    protected MoveControl createGroundMoveControl() { return this.moveControl; }

    /** Whether it paddles to the surface in water, like most land animals. */
    protected boolean floatsInWater() { return true; }

    /** The goals for moving around with nothing to hunt. */
    protected void registerWanderGoals() {
        goalSelector.addGoal(5, new SkyWanderGoal());
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8) {
            @Override public boolean canUse() { return !isFlying() && super.canUse(); }
            @Override public boolean canContinueToUse() { return !isFlying() && super.canContinueToUse(); }
        });
    }

    /** Steers toward the target by itself (for swimming); return false to walk there with the path finder. */
    protected boolean swimToward(LivingEntity target) { return false; }

    /** Stays put while it uses its breath. */
    protected void holdPosition() {
        if (isFlying()) flyMoveControl.hover();
    }

    /** Whether the model should use the swimming pose. */
    protected boolean swimPose() { return false; }

    // ------------------------------------------------------------------ setup

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 200.0)
                .add(Attributes.ARMOR, 10.0)
                .add(Attributes.ATTACK_DAMAGE, 16.0)
                .add(Attributes.ATTACK_KNOCKBACK, 1.5)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FOLLOW_RANGE, 48.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.9);
    }

    /** Spawn check for one kind of dragon: never on Peaceful, natural spawns only under open sky and only 1 in spawnRarity tries. */
    public static <T extends AbstractDragon> SpawnPlacements.SpawnPredicate<T> spawnRules(DragonStats stats) {
        return spawnRules(stats, 1);
    }

    /** Like {@link #spawnRules(DragonStats)}, but natural spawns are {@code stormBoost} times more likely during a thunderstorm. */
    public static <T extends AbstractDragon> SpawnPlacements.SpawnPredicate<T> spawnRules(DragonStats stats, int stormBoost) {
        return (type, level, reason, pos, random) -> {
            if (level.getDifficulty() == Difficulty.PEACEFUL || !checkMobSpawnRules(type, level, reason, pos, random)) return false;
            if (reason != MobSpawnType.NATURAL && reason != MobSpawnType.CHUNK_GENERATION) return true;
            int rarity = stats.spawnRarity.get();
            if (rarity > 0 && stormBoost > 1 && level.getLevel().isThundering()) rarity = Math.max(1, rarity / stormBoost);
            return rarity > 0 && level.canSeeSky(pos.above()) && random.nextInt(rarity) == 0;
        };
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(FLYING, false);
        entityData.define(BREATHING, false);
        entityData.define(BREATH_TARGET, new Vector3f());
        entityData.define(SIZE, 1.0F);
        entityData.define(TAME_FLAGS, (byte) 0);
        entityData.define(OWNER, Optional.empty());
    }

    @Override
    protected void registerGoals() {
        if (floatsInWater()) goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new StayGoal());
        goalSelector.addGoal(1, new BreathGoal());
        goalSelector.addGoal(2, new AttackGoal());
        goalSelector.addGoal(3, new FollowOwnerGoal());
        registerWanderGoals();
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 16.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));

        targetSelector.addGoal(1, new DefendOwnerGoal());
        targetSelector.addGoal(2, new HelpOwnerGoal());
        targetSelector.addGoal(3, new HurtByTargetGoal(this));
        targetSelector.addGoal(4, new SpotTargetGoal<>(Player.class));
        targetSelector.addGoal(5, new SpotTargetGoal<>(AbstractVillager.class));
    }

    // ------------------------------------------------------------------ size

    /** How big this dragon is compared to a normal one (0.7 small, 1 normal, 1.5 giant). */
    public float getSizeScale() { return entityData.get(SIZE); }

    public Size getSize() { return size; }

    public void setSize(Size newSize) {
        DragonStats stats = stats();
        size = newSize;
        entityData.set(SIZE, newSize.scale);
        double health = switch (newSize) {
            case SMALL -> stats.smallHealth.get();
            case GIANT -> stats.giantHealth.get();
            default -> stats.health.get();
        };
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
        getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(stats.biteDamage.get() * newSize.damage);
        setHealth(getMaxHealth());
        xpReward = (int) (60 * health / 200);
        refreshDimensions();
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason,
                                        @Nullable SpawnGroupData data, @Nullable CompoundTag tag) {
        int roll = random.nextInt(100);
        int giant = stats().giantChance.get(), small = stats().smallChance.get();
        setSize(roll < giant ? Size.GIANT : roll < giant + small ? Size.SMALL : Size.NORMAL);
        return super.finalizeSpawn(level, difficulty, reason, data, tag);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (SIZE.equals(key)) refreshDimensions();
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return super.getDimensions(pose).scale(getSizeScale());
    }

    @Override
    protected void dropCustomDeathLoot(DamageSource source, int looting, boolean hitByPlayer) {
        super.dropCustomDeathLoot(source, looting, hitByPlayer);
        if (isSaddled()) spawnAtLocation(Items.SADDLE);
        if (size == Size.GIANT) spawnAtLocation(new ItemStack(scaleItem(), 4 + random.nextInt(4 + looting)));
        if (hitByPlayer) {
            double chance = switch (size) {
                case SMALL -> CreatureConfig.ESSENCE_CHANCE_SMALL.get();
                case GIANT -> CreatureConfig.ESSENCE_CHANCE_GIANT.get();
                default -> CreatureConfig.ESSENCE_CHANCE.get();
            } * (1 + 0.5 * looting);
            if (random.nextDouble() * 100 < chance) spawnAtLocation(new ItemStack(ModItems.DRAGON_ESSENCE.get()));
        }
    }

    // ------------------------------------------------------------------ state

    public boolean isFlying() { return entityData.get(FLYING); }
    public boolean isBreathing() { return entityData.get(BREATHING); }

    private void setFlying(boolean flying) {
        entityData.set(FLYING, flying);
        setNoGravity(flying);
        moveControl = flying ? flyMoveControl : groundMoveControl;
        getNavigation().stop();
        if (flying) {
            flightTicks = 0;
            landing = false;
        } else {
            takeoffCooldown = 100 + random.nextInt(100);
        }
    }

    private void takeOff() {
        setFlying(true);
        flightLimit = 300 + random.nextInt(400);
        setDeltaMovement(getDeltaMovement().add(0, 0.7, 0));
        playSound(ModSounds.DRAGON_FLAP.get(), 3.0F, 0.8F);
    }

    /** Where the breath comes out: in front of the body, up at head height, following the head's aim. */
    public Vec3 mouthPos() {
        double s = MODEL_SCALE * getSizeScale() / 16.0;
        Vec3 bodyFwd = Vec3.directionFromRotation(0, yBodyRot);
        Vec3 headFwd = Vec3.directionFromRotation(getXRot(), yHeadRot);
        return position().add(bodyFwd.scale(36 * s)).add(0, 33 * s, 0).add(headFwd.scale(16.5 * s));
    }

    private Vec3 breathTarget() {
        Vector3f t = entityData.get(BREATH_TARGET);
        return new Vec3(t.x(), t.y(), t.z());
    }

    private void setBreathTarget(Vec3 aim) {
        entityData.set(BREATH_TARGET, new Vector3f((float) aim.x, (float) aim.y, (float) aim.z));
    }

    private boolean canBite(LivingEntity target) {
        double dx = target.getX() - getX(), dz = target.getZ() - getZ();
        double reach = getBbWidth() / 2 + 4.3 * getSizeScale() + target.getBbWidth() / 2;
        double dy = target.getY() - getY();
        return dx * dx + dz * dz <= reach * reach && dy > -3.0 && dy < 4.5 && hasLineOfSight(target);
    }

    // ------------------------------------------------------------------ taming

    private boolean flag(int bit) { return (entityData.get(TAME_FLAGS) & bit) != 0; }

    private void setFlag(int bit, boolean on) {
        byte flags = entityData.get(TAME_FLAGS);
        entityData.set(TAME_FLAGS, (byte) (on ? flags | bit : flags & ~bit));
    }

    public boolean isTame() { return flag(TAME); }

    /** Told to stay where it is (it still fights back if something attacks it). */
    public boolean isOrderedToSit() { return flag(STAYING); }

    public void setOrderedToSit(boolean stay) { setFlag(STAYING, stay); }

    public boolean isSaddled() { return flag(SADDLED); }

    public void setSaddled(boolean saddled) { setFlag(SADDLED, saddled); }

    /** Tame, or a kind that leaves you alone unless you hurt it. Friendly dragons don't stop you sleeping, despawn on Peaceful or get attacked by golems. */
    public boolean isFriendly() { return isTame() || !huntsOnSight(); }

    @Nullable @Override
    public UUID getOwnerUUID() { return entityData.get(OWNER).orElse(null); }

    public void setOwnerUUID(@Nullable UUID owner) { entityData.set(OWNER, Optional.ofNullable(owner)); }

    public boolean isOwnedBy(@Nullable Entity entity) {
        UUID owner = getOwnerUUID();
        return entity != null && owner != null && owner.equals(entity.getUUID());
    }

    /** Makes this dragon the player's: it stops whatever it was doing, follows them, and never despawns. */
    public void tame(Player player) {
        setFlag(TAME, true);
        setOwnerUUID(player.getUUID());
        setPersistenceRequired();
        setTarget(null);
        setLastHurtByMob(null);
        getNavigation().stop();
        level().broadcastEntityEvent(this, TAME_EVENT);
        playSound(ModSounds.DRAGON_ROAR.get(), 2.0F, getVoicePitch() * 1.1F);
        player.displayClientMessage(Component.translatable("message.wildcreatures.dragon_tamed", getDisplayName()), true);
    }

    /** Meat and fish. */
    public boolean isDragonFood(ItemStack stack) {
        if (stack.is(ItemTags.FISHES)) return true;
        FoodProperties food = stack.getFoodProperties(this);
        return food != null && food.isMeat();
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        boolean client = level().isClientSide;
        if (isTame()) {
            if (!isOwnedBy(player)) return InteractionResult.PASS;
            if (isDragonFood(stack) && getHealth() < getMaxHealth()) {
                if (!client) {
                    FoodProperties food = stack.getFoodProperties(this);
                    heal(food == null ? 4.0F : food.getNutrition() * 3.0F);
                    playSound(SoundEvents.GENERIC_EAT, 1.5F, 0.6F);
                    level().broadcastEntityEvent(this, TAME_EVENT);
                    if (!player.getAbilities().instabuild) stack.shrink(1);
                }
                return InteractionResult.sidedSuccess(client);
            }
            if (stack.is(Items.SADDLE) && !isSaddled()) {
                if (!client) {
                    setSaddled(true);
                    playSound(SoundEvents.HORSE_SADDLE, 1.0F, 0.8F);
                    if (!player.getAbilities().instabuild) stack.shrink(1);
                }
                return InteractionResult.sidedSuccess(client);
            }
            if (player.isSecondaryUseActive() || !isSaddled()) {
                if (!client) {
                    boolean stay = !isOrderedToSit();
                    setOrderedToSit(stay);
                    setTarget(null);
                    getNavigation().stop();
                    player.displayClientMessage(Component.translatable(stay ? "message.wildcreatures.dragon_stays" : "message.wildcreatures.dragon_follows",
                            getDisplayName()), true);
                }
                return InteractionResult.sidedSuccess(client);
            }
            if (!client && !isVehicle()) player.startRiding(this);
            return InteractionResult.sidedSuccess(client);
        }
        if (tamedByFeeding() && isDragonFood(stack) && getTarget() != player) {
            if (!client) {
                if (!player.getAbilities().instabuild) stack.shrink(1);
                playSound(SoundEvents.GENERIC_EAT, 1.5F, 0.6F);
                int chance = stats().tameChance != null ? stats().tameChance.get() : 33;
                if (random.nextInt(100) < chance) tame(player);
                else level().broadcastEntityEvent(this, TAME_FAIL_EVENT);
            }
            return InteractionResult.sidedSuccess(client);
        }
        return super.mobInteract(player, hand);
    }

    /** Client side: hearts when it's tamed (or healed), smoke when it isn't won over yet. */
    private void tameParticles(boolean success) {
        Vec3 head = mouthPos();
        ParticleOptions particle = success ? ParticleTypes.HEART : ParticleTypes.SMOKE;
        for (int i = 0; i < (success ? 9 : 14); i++) {
            level().addParticle(particle, head.x + random.nextGaussian() * 0.9, head.y + 0.4 + random.nextGaussian() * 0.6,
                    head.z + random.nextGaussian() * 0.9, random.nextGaussian() * 0.02, 0.04, random.nextGaussian() * 0.02);
        }
    }

    /** The owner, if this dragon should be following them right now. */
    @Nullable
    private LivingEntity followedOwner() {
        if (!isTame() || isOrderedToSit() || isVehicle()) return null;
        LivingEntity owner = getOwner();
        return owner != null && owner.isAlive() && !owner.isSpectator() ? owner : null;
    }

    /** Whether the owner is up in the air (elytra, creative flight, or riding a flying dragon). */
    private static boolean airborne(LivingEntity entity) {
        return entity.isFallFlying() || entity instanceof Player p && p.getAbilities().flying
                || entity.getVehicle() instanceof AbstractDragon mount && mount.isFlying();
    }

    /** When its owner gets far away (an ender pearl, a fast horse, ...) it catches up by turning up next to them. */
    private boolean teleportNear(LivingEntity owner) {
        boolean air = airborne(owner);
        for (int i = 0; i < 16; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double r = 5 + random.nextDouble() * 6;
            double x = owner.getX() + Math.cos(angle) * r, z = owner.getZ() + Math.sin(angle) * r;
            for (int dy : air ? new int[]{3, 5} : new int[]{0, 1, -1, 2}) {
                double y = Math.floor(owner.getY()) + dy;
                AABB box = getBoundingBox().move(x - getX(), y - getY(), z - getZ());
                if (!level().noCollision(this, box) || level().containsAnyLiquid(box)) continue;
                if (!air && level().noCollision(this, box.move(0, -0.5, 0))) continue;      // needs ground to stand on
                moveTo(x, y, z, getYRot(), getXRot());
                getNavigation().stop();
                if (air && stats().canFly() && !isFlying()) setFlying(true);
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ riding

    @Nullable @Override
    public LivingEntity getControllingPassenger() {
        return isSaddled() && getFirstPassenger() instanceof Player rider ? rider : super.getControllingPassenger();
    }

    @Nullable
    private static Field findJumpingField() {
        try {
            return ObfuscationReflectionHelper.findField(LivingEntity.class, "f_20899_");
        } catch (RuntimeException e) {
            WildCreatures.LOGGER.error("Can't read the jump key, so ridden dragons won't take off", e);
            return null;
        }
    }

    /**
     * Whether the rider is holding Shift. A riding player's own game clears its Shift flag at the end of every tick (so the
     * server can make them get off), which means the dragon never sees it there; the client swaps in a check of the key itself.
     */
    public static Predicate<Player> holdingShift = Player::isShiftKeyDown;

    private static boolean isJumping(LivingEntity entity) {
        try {
            return JUMPING != null && JUMPING.getBoolean(entity);
        } catch (IllegalAccessException e) {
            return false;
        }
    }

    /** Top flying speed while ridden, in blocks per tick. */
    private double riddenFlySpeed() {
        DragonStats stats = stats();
        return stats.riddenFlySpeed != null ? stats.riddenFlySpeed.get() : stats.flySpeed.get() * 1.5;
    }

    @Override
    protected void addPassenger(Entity passenger) {
        super.addPassenger(passenger);
        if (!level().isClientSide && passenger instanceof Player) {
            setOrderedToSit(false);
            setTarget(null);
            getNavigation().stop();
            riderTakeoffDelay = 10;
            updateControlFlags();
        }
    }

    /** A rider who gets off high up floats down slowly instead of falling. */
    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        if (level().isClientSide) return;
        if (riderBreathing) stopRiderBreath();
        updateControlFlags();
        if (passenger instanceof LivingEntity rider) {
            double height = getY() - level().getHeight(Heightmap.Types.MOTION_BLOCKING, getBlockX(), getBlockZ());
            if (height > 3) rider.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 20 * (int) Math.min(90, 8 + height / 2), 0, false, false, true));
        }
    }

    /** The rider sits in the saddle, tipping with the body when it climbs or dives. */
    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction move) {
        if (!hasPassenger(passenger)) return;
        double s = MODEL_SCALE * getSizeScale() / 16.0;
        double forward = SEAT_FORWARD * s, up = SEAT_HEIGHT * s;
        float tilt = level().isClientSide ? getClimb(1.0F) * getFlyAnim(1.0F) * Mth.DEG_TO_RAD : 0.0F;
        if (tilt != 0) {
            double pivot = 1.2 * getSizeScale(), a = forward, b = up - pivot;
            forward = a * Mth.cos(tilt) - b * Mth.sin(tilt);
            up = pivot + a * Mth.sin(tilt) + b * Mth.cos(tilt);
        }
        Vec3 dir = Vec3.directionFromRotation(0, yBodyRot);
        move.accept(passenger, getX() + dir.x * forward, getY() + up - 0.6, getZ() + dir.z * forward);
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        Vec3 side = Vec3.directionFromRotation(0, yBodyRot + 90.0F);
        double out = getBbWidth() / 2 + passenger.getBbWidth() / 2 + 0.4;
        for (int dir : new int[]{1, -1}) {
            Vec3 spot = position().add(side.scale(out * dir));
            BlockPos base = BlockPos.containing(spot);
            for (int dy : new int[]{0, 1, -1, 2, -2}) {
                Vec3 v = DismountHelper.findSafeDismountLocation(passenger.getType(), level(), base.above(dy), true);
                if (v != null) return v;
            }
        }
        if (!onGround()) return position().add(side.scale(out)).add(0, 1.0, 0);     // up in the air: just step off the side
        return super.getDismountLocationForPassenger(passenger);
    }

    /** While a player rides it, its own goals (wandering, hunting, breathing) are switched off. */
    @Override
    protected void updateControlFlags() {
        super.updateControlFlags();
        boolean ridden = getControllingPassenger() instanceof Player;
        if (ridden) {
            goalSelector.setControlFlag(Goal.Flag.MOVE, false);
            goalSelector.setControlFlag(Goal.Flag.JUMP, false);
            goalSelector.setControlFlag(Goal.Flag.LOOK, false);
        }
        targetSelector.setControlFlag(Goal.Flag.TARGET, !ridden);
    }

    /** Both sides, every tick while ridden: turns toward where the rider looks; the server also handles take-off and landing. */
    @Override
    protected void tickRidden(Player rider, Vec3 input) {
        super.tickRidden(rider, input);
        if (level().isClientSide && !isControlledByLocalInstance()) return;     // someone else's dragon: the server says where it faces
        float turn = Mth.clamp(Mth.wrapDegrees(rider.getYRot() - getYRot()) * 0.35F, -12.0F, 12.0F);
        setYRot(getYRot() + turn);
        yBodyRot = getYRot();
        yHeadRot = getYRot();
        setXRot(isBreathing() ? breathPitch(mouthPos(), breathTarget()) : rider.getXRot() * 0.5F);
        if (!level().isClientSide) riderFlightControls(rider);
    }

    /** Server side: Jump takes off; it lands once it touches the ground. Shift (normally "get off") makes it dive while it flies. */
    private void riderFlightControls(Player rider) {
        if (riderTakeoffDelay > 0) riderTakeoffDelay--;
        boolean jump = isJumping(rider);
        if (!isFlying()) {
            if (jump && riderTakeoffDelay == 0 && stats().canFly()) {
                setFlying(true);
                flightLimit = 0;                    // if the rider gets off up there, it comes back down
                playSound(ModSounds.DRAGON_FLAP.get(), 3.0F, 0.8F);
            }
            return;
        }
        flightTicks++;
        // the vehicle ticks before its rider: clearing Shift here keeps the rider in the saddle (the rider's client steers down with it)
        if (rider.isShiftKeyDown()) rider.setShiftKeyDown(false);
        if (flightTicks > 15 && !jump && !level().noCollision(this, getBoundingBox().deflate(0.25, 0.0, 0.25).move(0, -0.3, 0))) {
            setFlying(false);
            riderTakeoffDelay = 10;
        }
    }

    @Override
    protected Vec3 getRiddenInput(Player rider, Vec3 input) {
        float forward = rider.zza;
        if (forward < 0) forward *= 0.3F;               // backs up slowly
        return new Vec3(rider.xxa * 0.5F, 0.0, forward);
    }

    @Override
    protected float getRiddenSpeed(Player rider) {
        return (float) getAttributeValue(Attributes.MOVEMENT_SPEED) * 0.6F;
    }

    /** The rider's client: flies where the rider looks, with Jump to climb and Shift to dive. */
    private void flyRidden(Player rider, Vec3 input) {
        double top = riddenFlySpeed();
        float pitch = rider.getXRot();
        pitch = Math.abs(pitch) < 12.0F ? 0.0F : pitch - Math.copySign(12.0F, pitch);      // cruises level unless you look well up or down
        Vec3 ahead = Vec3.directionFromRotation(pitch, getYRot());
        Vec3 flat = Vec3.directionFromRotation(0, getYRot());
        Vec3 left = new Vec3(flat.z, 0, -flat.x);
        Vec3 want = ahead.scale(top * input.z).add(left.scale(top * input.x));
        boolean up = isJumping(rider), down = holdingShift.test(rider);
        if (up) want = want.add(0, top * 0.55, 0);
        if (down) want = want.add(0, -top * 0.65, 0);
        if (!up && !down && Math.abs(input.z) < 1.0E-3 && !isBreathing()) want = want.add(0, -0.04, 0);   // glides slowly down with no keys held
        if (want.y < 0 && overFluid()) want = new Vec3(want.x, isInWater() || isInLava() ? 0.15 : 0.0, want.z);  // won't dive into water or lava
        Vec3 v = getDeltaMovement();
        v = v.add(want.subtract(v).scale(0.1));
        setDeltaMovement(v);
        move(MoverType.SELF, v);
        if (horizontalCollision) setDeltaMovement(getDeltaMovement().multiply(0.6, 1.0, 0.6).add(0, 0.04, 0));
    }

    private boolean overFluid() {
        return isInWater() || isInLava() || !level().getFluidState(BlockPos.containing(getX(), getY() - 0.7, getZ())).isEmpty();
    }

    /** Server side, from the rider's attack key: breathes where the rider is looking, if the breath is ready. */
    public void riderBreath(Player rider) {
        if (rider != getControllingPassenger() || isBreathing() || breathCooldown > 0 || !isAlive()) return;
        breathTicks = stats().breathTicks.get();
        riderBreathing = true;
        setBreathTarget(riderAim(rider));
        entityData.set(BREATHING, true);
        playSound(breathSound(), 3.0F, 0.95F + random.nextFloat() * 0.1F);
    }

    private void tickRiderBreath() {
        if (!(getControllingPassenger() instanceof Player rider) || breathTicks <= 0) {
            stopRiderBreath();
            return;
        }
        breathTicks--;
        Vec3 aim = riderAim(rider);
        setBreathTarget(aim);
        if (breathTicks < stats().breathTicks.get() - 4) breathTick(aim);   // short wind-up first
        if (breathTicks <= 0) stopRiderBreath();
    }

    private void stopRiderBreath() {
        riderBreathing = false;
        breathTicks = 0;
        entityData.set(BREATHING, false);
        breathCooldown = Math.max(10, stats().breathCooldown.get() / 2);
    }

    /** What the rider is looking at: the first creature or block along their view, or a point at the breath's full reach. */
    private Vec3 riderAim(Player rider) {
        double reach = stats().breathRange.get() * Math.max(1.0, getSizeScale()) + 6.0;
        Vec3 eye = rider.getEyePosition();
        Vec3 end = eye.add(rider.getViewVector(1.0F).scale(reach));
        HitResult block = level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, rider));
        if (block.getType() != HitResult.Type.MISS) end = block.getLocation();
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(level(), rider, eye, end, new AABB(eye, end).inflate(1.0),
                e -> e instanceof LivingEntity living && !isBreathImmune(living));
        return hit != null ? hit.getEntity().position().add(0, hit.getEntity().getBbHeight() * 0.5, 0) : end;
    }

    // ------------------------------------------------------------------ ticking

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) {
            clientTick();
            return;
        }
        if (biteCooldown > 0) biteCooldown--;
        if (breathCooldown > 0 && !isBreathing()) breathCooldown--;
        if (riderBreathing) tickRiderBreath();
        if (biteTicks > 0 && --biteTicks == 4) {
            LivingEntity target = getTarget();
            if (target != null && target.isAlive() && canBite(target)) doHurtTarget(target);
        }
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (!(getControllingPassenger() instanceof Player)) updateFlight();
    }

    private void updateFlight() {
        if (!stats().canFly()) {
            if (isFlying()) setFlying(false);
            return;
        }
        if (takeoffCooldown > 0) takeoffCooldown--;
        LivingEntity target = getTarget();
        boolean hasTarget = target != null && target.isAlive();
        LivingEntity owner = hasTarget ? null : followedOwner();

        if (!isFlying()) {
            boolean wantsToFly = false;
            if (hasTarget) {
                double dy = target.getY() - getY();
                double dist = Math.sqrt(target.distanceToSqr(getX(), target.getY(), getZ()));
                stuckTicks = (getNavigation().isDone() && dist > 5 && !canBite(target)) ? stuckTicks + 1 : 0;
                wantsToFly = dy > 4 || dist > 20 || stuckTicks > 40;
            } else if (!isOrderedToSit() && random.nextInt(isTame() ? 4000 : 1500) == 0) {
                wantsToFly = true;
            }
            if (wantsToFly && takeoffCooldown == 0 && !isBreathing()) takeOff();
            return;
        }

        flightTicks++;
        if (!landing && !isBreathing()) {
            if (isOrderedToSit()) {
                landing = true;
            } else if (hasTarget) {
                double dist = Math.sqrt(target.distanceToSqr(getX(), target.getY(), getZ()));
                landing = flightTicks > 240 && target.onGround() && target.getY() - getY() < 1.5 && dist < 12;
            } else if (owner != null) {
                // a pet comes down next to its owner once it has caught up, unless the owner is up in the air
                double dist = Math.sqrt(owner.distanceToSqr(getX(), owner.getY(), getZ()));
                landing = !airborne(owner) && owner.getY() - getY() < 2 && (dist < 14 ? flightTicks > 60 : flightTicks > flightLimit && dist < 24);
            } else {
                landing = flightTicks > flightLimit;
            }
        }
        if (landing) {
            if (isInWater() || isInLava()) {
                landing = false;
                flightTicks = flightLimit / 2;
                setDeltaMovement(getDeltaMovement().add(0, 0.3, 0));
            } else if (onGround()) {
                setFlying(false);
            } else {
                LivingEntity toward = hasTarget ? target : owner;
                Vec3 to = toward != null ? toward.position().subtract(position()).multiply(1, 0, 1) : Vec3.ZERO;
                if (owner != null && toward == owner && to.length() > 5) to = to.scale((to.length() - 5) / to.length());   // not right on top of the owner
                if (to.length() > 6) to = to.normalize().scale(6);
                flyMoveControl.setWantedPosition(getX() + to.x * 0.5, getY() - 4, getZ() + to.z * 0.5, 0.6);
            }
        }
    }

    private void clientTick() {
        flyAnimO = flyAnim;
        breathAnimO = breathAnim;
        swimAnimO = swimAnim;
        sitAnimO = sitAnim;
        flyAnim = Mth.approach(flyAnim, isFlying() ? 1 : 0, 0.08F);
        swimAnim = Mth.approach(swimAnim, swimPose() ? 1 : 0, 0.08F);
        breathAnim = Mth.approach(breathAnim, isBreathing() ? 1 : 0, 0.2F);
        sitAnim = Mth.approach(sitAnim, isOrderedToSit() && !isFlying() && !isBreathing() ? 1 : 0, 0.06F);
        // while swimming the body tips nose-up when rising and nose-down when diving, smoothed so it doesn't twitch
        swimPitchO = swimPitch;
        float wantPitch = 0.0F;
        if (swimPose()) {
            double dx = getX() - xo, dy = getY() - yo, dz = getZ() - zo;
            double h = Math.sqrt(dx * dx + dz * dz), speed = Math.sqrt(h * h + dy * dy);
            if (speed > 0.01) wantPitch = (float) (Mth.atan2(dy, h) * Mth.RAD_TO_DEG * Math.min(1.0, speed / 0.15));
        }
        swimPitch += (Mth.clamp(wantPitch, -40.0F, 40.0F) - swimPitch) * 0.15F;
        // and in flight it tips nose-up to climb and nose-down to dive
        climbO = climb;
        climb += ((float) Mth.clamp((getY() - yo) * 40.0, -25.0, 25.0) - climb) * 0.3F;
        if (biteAnim > 0) biteAnim--;

        if (isFlying() && tickCount % 21 == 0) {
            level().playLocalSound(getX(), getY(), getZ(), ModSounds.DRAGON_FLAP.get(), getSoundSource(), 2.0F, 0.9F + random.nextFloat() * 0.2F, false);
        }
        if (isBreathing()) {
            Vec3 mouth = mouthPos();
            Vec3 aim = breathTarget();
            Vec3 dir = aim.subtract(mouth);
            dir = dir.lengthSqr() < 1.0E-4 ? getLookAngle() : dir.normalize();
            breathParticles(mouth, dir, aim);
        }
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == BITE_EVENT) biteAnim = BITE_ANIM_TICKS;
        else if (id == TAME_EVENT || id == TAME_FAIL_EVENT) tameParticles(id == TAME_EVENT);
        else super.handleEntityEvent(id);
    }

    @Override
    public void travel(Vec3 input) {
        if (!isFlying()) {
            // a ridden dragon swims at the surface instead of sinking with its rider
            if (isVehicle() && isControlledByLocalInstance() && getFluidTypeHeight(ForgeMod.WATER_TYPE.get()) > getBbHeight() * 0.4)
                setDeltaMovement(getDeltaMovement().add(0, 0.06, 0));
            super.travel(input);
            return;
        }
        if (isControlledByLocalInstance()) {
            if (getControllingPassenger() instanceof Player rider) {
                flyRidden(rider, input);
            } else {
                move(MoverType.SELF, getDeltaMovement());
                if (horizontalCollision) setDeltaMovement(getDeltaMovement().add(0, 0.12, 0));
                setDeltaMovement(getDeltaMovement().scale(0.96));
            }
        }
        calculateEntityAnimation(false);
    }

    // ------------------------------------------------------------------ attacks

    private void startBite() {
        biteTicks = BITE_ANIM_TICKS;
        biteCooldown = stats().biteCooldown.get();
        level().broadcastEntityEvent(this, BITE_EVENT);
        playSound(ModSounds.DRAGON_BITE.get(), 2.0F, 0.9F + random.nextFloat() * 0.2F);
    }

    /** The breath's damage. When someone rides the dragon, the rider gets the credit (and the blame). */
    protected DamageSource breathDamage() {
        var type = level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(breathDamageType());
        return getControllingPassenger() instanceof Player rider ? new DamageSource(type, this, rider) : new DamageSource(type, this);
    }

    /**
     * Who the breath passes through harmlessly: the dragon itself and its rider, its owner and the owner's pets,
     * and other dragons (a tamed dragon can still fight wild ones).
     */
    protected boolean isBreathImmune(LivingEntity e) {
        if (e == this || !e.isAlive() || e.isSpectator() || hasPassenger(e)) return true;
        UUID owner = getOwnerUUID();
        if (e instanceof AbstractDragon other) return !isTame() || other.isTame() && owner != null && owner.equals(other.getOwnerUUID());
        if (!isTame()) return false;
        return isOwnedBy(e) || e instanceof OwnableEntity pet && owner != null && owner.equals(pet.getOwnerUUID());
    }

    /** Server side, every tick of the breath after a short wind-up: hurts what's in the stream and changes blocks where it lands.
     *  A dragon whose attack isn't a stream (like the Lightning Dragon's strikes) overrides this. */
    protected void breathTick(Vec3 aim) {
        DragonStats stats = stats();
        Vec3 mouth = mouthPos();
        Vec3 dir = aim.subtract(mouth).normalize();
        double range = stats.breathRange.get() * Math.max(1.0, getSizeScale());
        Vec3 end = mouth.add(dir.scale(range));
        BlockHitResult hit = level().clip(new ClipContext(mouth, end, ClipContext.Block.COLLIDER, breathBlockedBy(), this));
        boolean hitBlock = hit.getType() != HitResult.Type.MISS;
        // stop at the aimed creature if it's closer than the block behind it
        Vec3 impact = hitBlock ? hit.getLocation() : end;
        if (aim.distanceTo(mouth) < impact.distanceTo(mouth)) impact = aim;
        double length = impact.distanceTo(mouth);
        double radius = stats.effectRadius.get() * Math.max(1.0, getSizeScale());

        if (breathTicks % 4 == 0) {
            float damage = stats.breathDamage.get().floatValue() * size.damage;
            AABB area = new AABB(mouth, impact).inflate(radius + 2);
            final Vec3 imp = impact;
            for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, area, e -> !isBreathImmune(e))) {
                Vec3 c = e.position().add(0, e.getBbHeight() / 2, 0);
                Vec3 rel = c.subtract(mouth);
                double along = rel.dot(dir);
                double off = rel.subtract(dir.scale(along)).length();
                boolean inCone = along > -0.5 && along < length + 1.0 && off < 1.0 + along * 0.15 + e.getBbWidth() / 2;
                boolean nearImpact = c.distanceTo(imp) < radius + e.getBbWidth() / 2;
                if (inCone || nearImpact) onBreathHit(e, e.hurt(breathDamage(), damage));
            }
        }

        if (stats.breathAffectsBlocks.get() && ForgeEventFactory.getMobGriefingEvent(level(), this) && (hitBlock || length < range - 0.5)) {
            if (hitBlock) affectBlock(hit.getBlockPos().relative(hit.getDirection()));
            for (int i = 0; i < 3; i++) {
                Vec3 p = impact.add((random.nextDouble() * 2 - 1) * radius, (random.nextDouble() * 2 - 1) * 1.5, (random.nextDouble() * 2 - 1) * radius);
                affectBlock(BlockPos.containing(p));
            }
        }
    }

    // ------------------------------------------------------------------ who it fights

    /** Never its owner. */
    @Override
    public boolean canAttack(LivingEntity target) {
        return !isOwnedBy(target) && super.canAttack(target);
    }

    /** On its owner's side (so it won't turn on them, even if they hit it by accident). */
    @Override
    public boolean isAlliedTo(Entity other) {
        if (isTame()) {
            LivingEntity owner = getOwner();
            if (other == owner) return true;
            if (owner != null) return owner.isAlliedTo(other);
        }
        return super.isAlliedTo(other);
    }

    /** Whether a pet dragon should go after something that fought its owner (not villagers, golems, the owner's pets, or other players when PvP is off). */
    private boolean wantsToAttack(LivingEntity target, LivingEntity owner) {
        if (target == this || target == owner || !target.attackable() || target instanceof AbstractVillager
                || target instanceof IronGolem || target instanceof SnowGolem) return false;
        if (target instanceof OwnableEntity pet && owner.getUUID().equals(pet.getOwnerUUID())) return false;
        return !(target instanceof Player p && owner instanceof Player o && !o.canHarmPlayer(p));
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isInvulnerableTo(source)) return false;
        // something other than the owner attacked it: it stops staying put and fights back
        if (!level().isClientSide && source.getEntity() instanceof LivingEntity attacker && !isOwnedBy(attacker)) setOrderedToSit(false);
        return super.hurt(source, amount);
    }

    @Override
    public void die(DamageSource source) {
        if (!level().isClientSide) {
            if (isTame() && level().getGameRules().getBoolean(GameRules.RULE_SHOWDEATHMESSAGES) && getOwner() instanceof ServerPlayer owner)
                owner.sendSystemMessage(getCombatTracker().getDeathMessage());
            ejectPassengers();
        }
        super.die(source);
    }

    // ------------------------------------------------------------------ goals

    /** Like the vanilla target finder, but a dragon also spots things far above or below it (vanilla only looks 4 blocks up or down).
     *  Only wild dragons of a hunting kind look for prey. */
    private class SpotTargetGoal<T extends LivingEntity> extends NearestAttackableTargetGoal<T> {
        SpotTargetGoal(Class<T> type) { super(AbstractDragon.this, type, true); }

        @Override public boolean canUse() { return !isTame() && huntsOnSight() && super.canUse(); }

        @Override protected AABB getTargetSearchArea(double range) { return mob.getBoundingBox().inflate(range, 24.0, range); }
    }

    /** A tamed dragon goes for whatever just hurt its owner. */
    private class DefendOwnerGoal extends TargetGoal {
        private LivingEntity attacker;
        private int timestamp;

        DefendOwnerGoal() {
            super(AbstractDragon.this, false);
            setFlags(EnumSet.of(Flag.TARGET));
        }

        @Override public boolean canUse() {
            LivingEntity owner = isTame() && !isOrderedToSit() ? getOwner() : null;
            if (owner == null) return false;
            attacker = owner.getLastHurtByMob();
            return attacker != null && owner.getLastHurtByMobTimestamp() != timestamp
                    && canAttack(attacker, TargetingConditions.DEFAULT) && wantsToAttack(attacker, owner);
        }

        @Override public void start() {
            mob.setTarget(attacker);
            LivingEntity owner = getOwner();
            if (owner != null) timestamp = owner.getLastHurtByMobTimestamp();
            super.start();
        }
    }

    /** ... and whatever its owner attacks. */
    private class HelpOwnerGoal extends TargetGoal {
        private LivingEntity victim;
        private int timestamp;

        HelpOwnerGoal() {
            super(AbstractDragon.this, false);
            setFlags(EnumSet.of(Flag.TARGET));
        }

        @Override public boolean canUse() {
            LivingEntity owner = isTame() && !isOrderedToSit() ? getOwner() : null;
            if (owner == null) return false;
            victim = owner.getLastHurtMob();
            return victim != null && owner.getLastHurtMobTimestamp() != timestamp
                    && canAttack(victim, TargetingConditions.DEFAULT) && wantsToAttack(victim, owner);
        }

        @Override public void start() {
            mob.setTarget(victim);
            LivingEntity owner = getOwner();
            if (owner != null) timestamp = owner.getLastHurtMobTimestamp();
            super.start();
        }
    }

    /** Told to stay: it stays where it is (and comes down to land first if it was flying). */
    private class StayGoal extends Goal {
        StayGoal() { setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP)); }

        @Override public boolean canUse() { return isTame() && isOrderedToSit() && !isVehicle(); }

        @Override public boolean canContinueToUse() { return canUse(); }

        @Override public void start() { getNavigation().stop(); }

        @Override public void tick() {
            if (!isFlying()) getNavigation().stop();
        }
    }

    /** A tamed dragon keeps up with its owner: it walks when they're close, and flies when they're far off, up high or in the air. */
    private class FollowOwnerGoal extends Goal {
        private LivingEntity owner;
        private int repath, stuck;

        FollowOwnerGoal() { setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }

        @Override public boolean canUse() {
            LivingEntity o = getTarget() == null ? followedOwner() : null;
            if (o == null || distanceToSqr(o) < 14 * 14 && !(airborne(o) && !isFlying())) return false;
            owner = o;
            return true;
        }

        @Override public boolean canContinueToUse() {
            if (owner == null || owner != followedOwner() || getTarget() != null) return false;
            double near = isFlying() ? 9.0 : 6.0;
            return distanceToSqr(owner) > near * near || airborne(owner) && !isFlying();
        }

        @Override public boolean requiresUpdateEveryTick() { return true; }

        @Override public void start() {
            repath = 0;
            stuck = 0;
        }

        @Override public void stop() {
            owner = null;
            getNavigation().stop();
            if (isFlying() && !landing) flyMoveControl.hover();
        }

        @Override public void tick() {
            getLookControl().setLookAt(owner, 10.0F, getMaxHeadXRot());
            double dist = distanceTo(owner);
            if (dist > 64 && teleportNear(owner)) return;
            boolean ownerUp = owner.getY() - getY() > 5 || airborne(owner);
            if (!isFlying()) {
                stuck = getNavigation().isDone() && dist > 10 ? stuck + 1 : 0;
                if (stats().canFly() && !isBreathing() && (dist > 24 || ownerUp || stuck > 40)) {
                    takeOff();
                    return;
                }
                if (--repath <= 0) {
                    repath = 10;
                    getNavigation().moveTo(owner, 1.2);
                }
            } else if (!landing) {
                // circle in a few blocks off to the side of the owner and a little above them
                Vec3 away = position().subtract(owner.position()).multiply(1, 0, 1);
                away = away.lengthSqr() < 1.0E-3 ? Vec3.ZERO : away.normalize().scale(6);
                flyMoveControl.setWantedPosition(owner.getX() + away.x, owner.getY() + 4, owner.getZ() + away.z, dist > 24 ? 1.5 : 1.0);
            }
        }
    }

    /** Chase the target (walking or flying) and bite when close. */
    private class AttackGoal extends Goal {
        private int repath;

        AttackGoal() { setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }

        @Override public boolean canUse() {
            LivingEntity t = getTarget();
            return t != null && t.isAlive() && !isBreathing();
        }

        @Override public boolean requiresUpdateEveryTick() { return true; }

        @Override public void start() { repath = 0; }

        @Override public void stop() {
            getNavigation().stop();
            if (!isFlying()) holdPosition();
        }

        @Override public void tick() {
            LivingEntity t = getTarget();
            if (t == null) return;
            getLookControl().setLookAt(t, 30.0F, 30.0F);
            boolean inReach = canBite(t);
            if (isFlying()) {
                if (!landing) {
                    Vec3 away = position().subtract(t.position()).multiply(1, 0, 1);
                    away = away.lengthSqr() < 1.0E-3 ? Vec3.ZERO : away.normalize().scale(4.2);
                    flyMoveControl.setWantedPosition(t.getX() + away.x, t.getY() + 0.8, t.getZ() + away.z, 1.0);
                }
            } else if (swimToward(t)) {
                repath = 0;
            } else if (--repath <= 0) {
                repath = 8 + random.nextInt(5);
                if (inReach) getNavigation().stop();
                else getNavigation().moveTo(t, 1.25);
            }
            if (inReach) {
                // turn the whole body to face the target for the bite
                double dx = t.getX() - getX(), dz = t.getZ() - getZ();
                setYRot(Mth.rotLerp(0.3F, getYRot(), (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F));
                yBodyRot = getYRot();
                if (biteCooldown == 0 && biteTicks == 0) startBite();
            }
        }
    }

    /** Stop, face the target and pour breath at it, sweeping a little behind if it dodges. */
    private class BreathGoal extends Goal {
        private Vec3 aim = Vec3.ZERO;

        BreathGoal() { setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP)); }

        @Override public boolean canUse() {
            LivingEntity t = getTarget();
            if (t == null || !t.isAlive() || breathCooldown > 0 || biteTicks > 0) return false;
            double dist = distanceTo(t);
            if (dist > stats().breathRange.get() * 0.85 || !hasLineOfSight(t)) return false;
            return dist > 6.0 || random.nextInt(4) == 0;
        }

        @Override public boolean canContinueToUse() { return breathTicks > 0 && isAlive(); }

        @Override public boolean requiresUpdateEveryTick() { return true; }

        @Override public void start() {
            breathTicks = stats().breathTicks.get();
            LivingEntity t = getTarget();
            aim = t != null ? t.position().add(0, t.getBbHeight() * 0.4, 0) : position().add(getLookAngle().scale(8));
            getNavigation().stop();
            holdPosition();
            entityData.set(BREATHING, true);
            playSound(breathSound(), 3.0F, 0.95F + random.nextFloat() * 0.1F);
        }

        @Override public void stop() {
            breathTicks = 0;
            entityData.set(BREATHING, false);
            breathCooldown = stats().breathCooldown.get() + random.nextInt(60);
        }

        @Override public void tick() {
            breathTicks--;
            LivingEntity t = getTarget();
            if (t != null && t.isAlive()) {
                Vec3 want = t.position().add(0, t.getBbHeight() * 0.4, 0);
                aim = aim.add(want.subtract(aim).scale(0.18));   // lags behind, so you can run out of it
            }
            setBreathTarget(aim);
            double dx = aim.x - getX(), dz = aim.z - getZ();
            float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
            setYRot(Mth.rotLerp(0.25F, getYRot(), yaw));
            yBodyRot = getYRot();
            yHeadRot = getYRot();
            setXRot(breathPitch(mouthPos(), aim));
            holdPosition();
            if (breathTicks < stats().breathTicks.get() - 4) breathTick(aim);   // short wind-up first
        }
    }

    /** With nothing to hunt, glide between random points high above the ground. */
    private class SkyWanderGoal extends Goal {
        private int timer;

        SkyWanderGoal() { setFlags(EnumSet.of(Flag.MOVE)); }

        @Override public boolean canUse() { return isFlying() && !landing && getTarget() == null; }

        @Override public boolean canContinueToUse() { return canUse(); }

        @Override public void start() { pick(); }

        @Override public void tick() {
            if (--timer <= 0 || !flyMoveControl.hasWanted()) pick();
        }

        private void pick() {
            timer = 120 + random.nextInt(80);
            double x = getX() + (random.nextDouble() * 2 - 1) * 24;
            double z = getZ() + (random.nextDouble() * 2 - 1) * 24;
            int ground = level().getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z));
            flyMoveControl.setWantedPosition(x, ground + 8 + random.nextInt(10), z, 0.7);
        }
    }

    /** Flies straight toward the wanted point, banking the body toward where it's going. */
    private static class FlyControl extends MoveControl {
        private final AbstractDragon dragon;

        FlyControl(AbstractDragon dragon) {
            super(dragon);
            this.dragon = dragon;
        }

        void hover() { operation = Operation.WAIT; }

        @Override
        public void tick() {
            Vec3 v = dragon.getDeltaMovement();
            if (operation != Operation.MOVE_TO) {
                // hover in place with a slow bob
                double bob = Math.sin(dragon.tickCount * 0.15) * 0.02;
                dragon.setDeltaMovement(v.x * 0.8, v.y * 0.8 + bob, v.z * 0.8);
                return;
            }
            Vec3 to = new Vec3(wantedX - dragon.getX(), wantedY - dragon.getY(), wantedZ - dragon.getZ());
            double dist = to.length();
            if (dist < 1.0 || (dragon.verticalCollision && to.y < 0 && to.horizontalDistanceSqr() < 9.0)) {
                operation = Operation.WAIT;
                return;
            }
            double top = dragon.stats().flySpeed.get() * speedModifier;
            Vec3 desired = to.scale(Math.min(top, dist * 0.2) / dist);
            dragon.setDeltaMovement(v.add(desired.subtract(v).scale(0.12)));
            if (to.horizontalDistanceSqr() > 0.25 && !dragon.isBreathing()) {
                float yaw = (float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90.0F;
                dragon.setYRot(rotlerp(dragon.getYRot(), yaw, 6.0F));
                dragon.yBodyRot = dragon.getYRot();
            }
        }
    }

    // ------------------------------------------------------------------ misc

    @Override public float getStandingEyeHeight(Pose pose, EntityDimensions dims) { return dims.height * (2.9F / 3.1F); }

    @Override public int getMaxHeadYRot() { return 60; }

    @Override public int getMaxHeadXRot() { return 50; }

    @Override public boolean causeFallDamage(float distance, float multiplier, DamageSource source) { return false; }

    @Override protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {}

    @Override public boolean canBeLeashed(Player player) { return false; }

    /** Normal monsters vanish in daylight once you're 32 blocks away; a dragon only leaves if you're really far off, and a tamed one never does. */
    @Override public boolean removeWhenFarAway(double distanceSqr) { return !isTame() && distanceSqr > 96.0 * 96.0 && getTarget() == null; }

    @Override public boolean requiresCustomPersistence() { return super.requiresCustomPersistence() || isTame() || isVehicle(); }

    @Override protected boolean shouldDespawnInPeaceful() { return !isFriendly(); }

    @Override public boolean isPreventingPlayerRest(Player player) { return !isFriendly() && super.isPreventingPlayerRest(player); }

    /** Monsters prefer the dark (and so only spawn in it); a friendly dragon doesn't care. */
    @Override public float getWalkTargetValue(BlockPos pos, LevelReader level) { return isFriendly() ? 0.0F : super.getWalkTargetValue(pos, level); }

    @Override protected SoundEvent getAmbientSound() { return ModSounds.DRAGON_ROAR.get(); }

    @Override public int getAmbientSoundInterval() { return isTame() ? 600 : 240; }

    @Override protected SoundEvent getHurtSound(DamageSource source) { return ModSounds.DRAGON_HURT.get(); }

    @Override protected SoundEvent getDeathSound() { return ModSounds.DRAGON_DEATH.get(); }

    @Override protected float getSoundVolume() { return 2.0F; }

    @Override public float getVoicePitch() { return 1.0F + (random.nextFloat() - 0.5F) * 0.15F; }

    @Override protected void playStepSound(BlockPos pos, BlockState state) { playSound(ModSounds.DRAGON_STEP.get(), 0.8F, 0.9F + random.nextFloat() * 0.2F); }

    @Override public SoundSource getSoundSource() { return isFriendly() ? SoundSource.NEUTRAL : SoundSource.HOSTILE; }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Flying", isFlying());
        tag.putString("DragonSize", size.name());
        tag.putBoolean("Tame", isTame());
        if (getOwnerUUID() != null) tag.putUUID("Owner", getOwnerUUID());
        tag.putBoolean("Staying", isOrderedToSit());
        tag.putBoolean("Saddled", isSaddled());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.getBoolean("Flying") && stats().canFly()) setFlying(true);
        if (tag.contains("DragonSize")) {
            try {
                Size saved = Size.valueOf(tag.getString("DragonSize").toUpperCase(java.util.Locale.ROOT));
                float health = getHealth();
                setSize(saved);
                if (tag.contains("Health")) setHealth(Math.min(health, getMaxHealth()));
            } catch (IllegalArgumentException ignored) {}
        }
        setOwnerUUID(tag.hasUUID("Owner") ? tag.getUUID("Owner") : null);
        setFlag(TAME, tag.getBoolean("Tame") || tag.hasUUID("Owner"));
        setOrderedToSit(tag.getBoolean("Staying"));
        setSaddled(tag.getBoolean("Saddled"));
    }

    // ------------------------------------------------------------------ for the model

    public float getFlyAnim(float partialTick) { return Mth.lerp(partialTick, flyAnimO, flyAnim); }

    public float getBreathAnim(float partialTick) { return Mth.lerp(partialTick, breathAnimO, breathAnim); }

    /** 0 on land, 1 swimming (only sea dragons swim). */
    public float getSwimAnim(float partialTick) { return Mth.lerp(partialTick, swimAnimO, swimAnim); }

    /** Client side: how far the body is tipped while swimming, in degrees (positive = nose up). */
    public float getSwimPitch(float partialTick) { return Mth.lerp(partialTick, swimPitchO, swimPitch); }

    /** Client side: how far the body is tipped in flight, in degrees (positive = nose up while climbing). */
    public float getClimb(float partialTick) { return Mth.lerp(partialTick, climbO, climb); }

    /** 0 standing, 1 lying down (a tamed dragon told to stay). */
    public float getSitAnim(float partialTick) { return Mth.lerp(partialTick, sitAnimO, sitAnim); }

    /** 0 when not biting, rises to 1 halfway through the bite and back to 0. */
    public float getBiteAnim(float partialTick) {
        if (biteAnim <= 0) return 0.0F;
        float t = Mth.clamp((BITE_ANIM_TICKS - biteAnim + partialTick) / BITE_ANIM_TICKS, 0.0F, 1.0F);
        return Mth.sin(t * Mth.PI);
    }
}
