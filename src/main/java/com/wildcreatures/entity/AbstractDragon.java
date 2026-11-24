package com.wildcreatures.entity;

import com.wildcreatures.CreatureConfig;
import com.wildcreatures.DragonStats;
import com.wildcreatures.ModItems;
import com.wildcreatures.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.EnumSet;

/**
 * Everything dragons have in common: walking and flying, biting, breathing at things from range,
 * the three sizes, sounds, saving, and the animation timers the model reads.
 * Each kind of dragon extends this and only says what its breath does, which config section
 * it uses and which scale it drops.
 */
public abstract class AbstractDragon extends Monster {
    /** The model is drawn 1.8x larger than its pixel size. Used by the renderer and for the mouth position. */
    public static final float MODEL_SCALE = 1.8F;

    private static final EntityDataAccessor<Boolean> FLYING = SynchedEntityData.defineId(AbstractDragon.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> BREATHING = SynchedEntityData.defineId(AbstractDragon.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Vector3f> BREATH_TARGET = SynchedEntityData.defineId(AbstractDragon.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Float> SIZE = SynchedEntityData.defineId(AbstractDragon.class, EntityDataSerializers.FLOAT);

    /** Dragon sizes: how big it is drawn, and how its damage is scaled. Health comes from the config. */
    public enum Size {
        SMALL(0.7F, 0.75F), NORMAL(1.0F, 1.0F), GIANT(1.5F, 1.4F);
        public final float scale, damage;
        Size(float scale, float damage) { this.scale = scale; this.damage = damage; }
    }
    private Size size = Size.NORMAL;

    private static final byte BITE_EVENT = 4;
    private static final int BITE_ANIM_TICKS = 10;

    private final MoveControl groundMoveControl;
    private final FlyControl flyMoveControl;

    // server-side timers
    private int biteCooldown, biteTicks, breathCooldown = 60, breathTicks, takeoffCooldown = 40;
    private int flightTicks, flightLimit, stuckTicks;
    private boolean landing;

    // client-side animation state
    private int biteAnim;
    private float flyAnim, flyAnimO, breathAnim, breathAnimO;

    protected AbstractDragon(EntityType<? extends AbstractDragon> type, Level level) {
        super(type, level);
        this.xpReward = 60;
        this.groundMoveControl = this.moveControl;
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
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new BreathGoal());
        goalSelector.addGoal(2, new AttackGoal());
        goalSelector.addGoal(5, new SkyWanderGoal());
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8) {
            @Override public boolean canUse() { return !isFlying() && super.canUse(); }
            @Override public boolean canContinueToUse() { return !isFlying() && super.canContinueToUse(); }
        });
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 16.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));

        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new SpotTargetGoal<>(Player.class));
        targetSelector.addGoal(3, new SpotTargetGoal<>(AbstractVillager.class));
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

    private boolean canBite(LivingEntity target) {
        double dx = target.getX() - getX(), dz = target.getZ() - getZ();
        double reach = getBbWidth() / 2 + 4.3 * getSizeScale() + target.getBbWidth() / 2;
        double dy = target.getY() - getY();
        return dx * dx + dz * dz <= reach * reach && dy > -3.0 && dy < 4.5 && hasLineOfSight(target);
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
        if (biteTicks > 0 && --biteTicks == 4) {
            LivingEntity target = getTarget();
            if (target != null && target.isAlive() && canBite(target)) doHurtTarget(target);
        }
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        updateFlight();
    }

    private void updateFlight() {
        if (!stats().canFly.get()) {
            if (isFlying()) setFlying(false);
            return;
        }
        if (takeoffCooldown > 0) takeoffCooldown--;
        LivingEntity target = getTarget();
        boolean hasTarget = target != null && target.isAlive();

        if (!isFlying()) {
            boolean wantsToFly = false;
            if (hasTarget) {
                double dy = target.getY() - getY();
                double dist = Math.sqrt(target.distanceToSqr(getX(), target.getY(), getZ()));
                stuckTicks = (getNavigation().isDone() && dist > 5 && !canBite(target)) ? stuckTicks + 1 : 0;
                wantsToFly = dy > 4 || dist > 20 || stuckTicks > 40;
            } else if (random.nextInt(1500) == 0) {
                wantsToFly = true;
            }
            if (wantsToFly && takeoffCooldown == 0 && !isBreathing()) takeOff();
            return;
        }

        flightTicks++;
        if (!landing && !isBreathing()) {
            if (!hasTarget) {
                landing = flightTicks > flightLimit;
            } else {
                double dist = Math.sqrt(target.distanceToSqr(getX(), target.getY(), getZ()));
                landing = flightTicks > 240 && target.onGround() && target.getY() - getY() < 1.5 && dist < 12;
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
                Vec3 to = hasTarget ? target.position().subtract(position()).multiply(1, 0, 1) : Vec3.ZERO;
                if (to.length() > 6) to = to.normalize().scale(6);
                flyMoveControl.setWantedPosition(getX() + to.x * 0.5, getY() - 4, getZ() + to.z * 0.5, 0.6);
            }
        }
    }

    private void clientTick() {
        flyAnimO = flyAnim;
        breathAnimO = breathAnim;
        flyAnim = Mth.approach(flyAnim, isFlying() ? 1 : 0, 0.08F);
        breathAnim = Mth.approach(breathAnim, isBreathing() ? 1 : 0, 0.2F);
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
        else super.handleEntityEvent(id);
    }

    @Override
    public void travel(Vec3 input) {
        if (!isFlying()) {
            super.travel(input);
            return;
        }
        if (isControlledByLocalInstance()) {
            move(MoverType.SELF, getDeltaMovement());
            if (horizontalCollision) setDeltaMovement(getDeltaMovement().add(0, 0.12, 0));
            setDeltaMovement(getDeltaMovement().scale(0.96));
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

    protected DamageSource breathDamage() {
        return new DamageSource(level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(breathDamageType()), this);
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
            for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, area,
                    e -> e != this && e.isAlive() && !(e instanceof AbstractDragon) && !e.isSpectator())) {
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

    // ------------------------------------------------------------------ goals

    /** Like the vanilla target finder, but a dragon also spots things far above or below it (vanilla only looks 4 blocks up or down). */
    private class SpotTargetGoal<T extends LivingEntity> extends NearestAttackableTargetGoal<T> {
        SpotTargetGoal(Class<T> type) { super(AbstractDragon.this, type, true); }

        @Override protected AABB getTargetSearchArea(double range) { return mob.getBoundingBox().inflate(range, 24.0, range); }
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

        @Override public void stop() { getNavigation().stop(); }

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
            if (isFlying()) flyMoveControl.hover();
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
            entityData.set(BREATH_TARGET, new Vector3f((float) aim.x, (float) aim.y, (float) aim.z));
            double dx = aim.x - getX(), dz = aim.z - getZ();
            float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
            setYRot(Mth.rotLerp(0.25F, getYRot(), yaw));
            yBodyRot = getYRot();
            yHeadRot = getYRot();
            setXRot(breathPitch(mouthPos(), aim));
            if (isFlying()) flyMoveControl.hover();
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

    /** Normal monsters vanish in daylight once you're 32 blocks away; a dragon only leaves if you're really far off. */
    @Override public boolean removeWhenFarAway(double distanceSqr) { return distanceSqr > 96.0 * 96.0 && getTarget() == null; }

    @Override protected SoundEvent getAmbientSound() { return ModSounds.DRAGON_ROAR.get(); }

    @Override public int getAmbientSoundInterval() { return 240; }

    @Override protected SoundEvent getHurtSound(DamageSource source) { return ModSounds.DRAGON_HURT.get(); }

    @Override protected SoundEvent getDeathSound() { return ModSounds.DRAGON_DEATH.get(); }

    @Override protected float getSoundVolume() { return 2.0F; }

    @Override public float getVoicePitch() { return 1.0F + (random.nextFloat() - 0.5F) * 0.15F; }

    @Override protected void playStepSound(BlockPos pos, BlockState state) { playSound(ModSounds.DRAGON_STEP.get(), 0.8F, 0.9F + random.nextFloat() * 0.2F); }

    @Override public SoundSource getSoundSource() { return SoundSource.HOSTILE; }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Flying", isFlying());
        tag.putString("DragonSize", size.name());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.getBoolean("Flying")) setFlying(true);
        if (tag.contains("DragonSize")) {
            try {
                Size saved = Size.valueOf(tag.getString("DragonSize").toUpperCase(java.util.Locale.ROOT));
                float health = getHealth();
                setSize(saved);
                if (tag.contains("Health")) setHealth(Math.min(health, getMaxHealth()));
            } catch (IllegalArgumentException ignored) {}
        }
    }

    // ------------------------------------------------------------------ for the model

    public float getFlyAnim(float partialTick) { return Mth.lerp(partialTick, flyAnimO, flyAnim); }

    public float getBreathAnim(float partialTick) { return Mth.lerp(partialTick, breathAnimO, breathAnim); }

    /** 0 when not biting, rises to 1 halfway through the bite and back to 0. */
    public float getBiteAnim(float partialTick) {
        if (biteAnim <= 0) return 0.0F;
        float t = Mth.clamp((BITE_ANIM_TICKS - biteAnim + partialTick) / BITE_ANIM_TICKS, 0.0F, 1.0F);
        return Mth.sin(t * Mth.PI);
    }
}
