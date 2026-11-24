package com.wildcreatures.entity;

import com.wildcreatures.CreatureConfig;
import com.wildcreatures.ModSounds;
import com.wildcreatures.WildCreatures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
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
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;
import org.joml.Vector3f;

import java.util.EnumSet;

/**
 * A big, hostile red dragon. Walks on the ground, takes off when its target is far away,
 * high up or out of reach, bites hard up close and breathes a stream of fire from range.
 */
public class RedDragon extends Monster {
    /** Model is drawn 1.8x larger than its pixel size. Used by the renderer and for the mouth position. */
    public static final float MODEL_SCALE = 1.8F;

    public static final ResourceKey<DamageType> DRAGON_FIRE =
            ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(WildCreatures.MOD_ID, "dragon_fire"));

    private static final EntityDataAccessor<Boolean> FLYING = SynchedEntityData.defineId(RedDragon.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> BREATHING = SynchedEntityData.defineId(RedDragon.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Vector3f> BREATH_TARGET = SynchedEntityData.defineId(RedDragon.class, EntityDataSerializers.VECTOR3);

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

    public RedDragon(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 60;
        this.groundMoveControl = this.moveControl;
        this.flyMoveControl = new FlyControl(this);
        this.setMaxUpStep(1.5F);
        this.setPathfindingMalus(BlockPathTypes.DANGER_FIRE, 0.0F);
        this.setPathfindingMalus(BlockPathTypes.DAMAGE_FIRE, 0.0F);
        this.setPathfindingMalus(BlockPathTypes.LAVA, 8.0F);
        if (CreatureConfig.SPEC.isLoaded()) {
            getAttribute(Attributes.MAX_HEALTH).setBaseValue(CreatureConfig.DRAGON_HEALTH.get());
            getAttribute(Attributes.ARMOR).setBaseValue(CreatureConfig.DRAGON_ARMOR.get());
            getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(CreatureConfig.DRAGON_BITE_DAMAGE.get());
            setHealth(getMaxHealth());
        }
    }

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

    public static boolean checkDragonSpawnRules(EntityType<RedDragon> type, ServerLevelAccessor level, MobSpawnType reason,
                                                BlockPos pos, RandomSource random) {
        if (level.getDifficulty() == Difficulty.PEACEFUL || !checkMobSpawnRules(type, level, reason, pos, random)) return false;
        if (reason != MobSpawnType.NATURAL && reason != MobSpawnType.CHUNK_GENERATION) return true;
        int rarity = CreatureConfig.DRAGON_SPAWN_CHANCE.get();
        return rarity > 0 && level.canSeeSky(pos.above()) && random.nextInt(rarity) == 0;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(FLYING, false);
        entityData.define(BREATHING, false);
        entityData.define(BREATH_TARGET, new Vector3f());
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

    /** Where the fire comes out: in front of the body, up at head height, following the head's aim. */
    public Vec3 mouthPos() {
        double s = MODEL_SCALE / 16.0;
        Vec3 bodyFwd = Vec3.directionFromRotation(0, yBodyRot);
        Vec3 headFwd = Vec3.directionFromRotation(getXRot(), yHeadRot);
        return position().add(bodyFwd.scale(32 * s)).add(0, 31 * s, 0).add(headFwd.scale(19 * s));
    }

    private Vec3 breathTarget() {
        Vector3f t = entityData.get(BREATH_TARGET);
        return new Vec3(t.x(), t.y(), t.z());
    }

    private boolean canBite(LivingEntity target) {
        double dx = target.getX() - getX(), dz = target.getZ() - getZ();
        double reach = getBbWidth() / 2 + 4.3 + target.getBbWidth() / 2;
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
        if (!CreatureConfig.DRAGON_CAN_FLY.get()) {
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
            Vec3 dir = breathTarget().subtract(mouth);
            dir = dir.lengthSqr() < 1.0E-4 ? getLookAngle() : dir.normalize();
            for (int i = 0; i < 22; i++) {
                double speed = 0.7 + random.nextDouble() * 0.6;
                double spread = 0.1;
                Vec3 v = dir.add(random.nextGaussian() * spread, random.nextGaussian() * spread, random.nextGaussian() * spread).normalize().scale(speed);
                level().addParticle(i % 11 == 0 ? ParticleTypes.SMOKE : (i % 3 == 0 ? ParticleTypes.SMALL_FLAME : ParticleTypes.FLAME),
                        mouth.x, mouth.y, mouth.z, v.x, v.y, v.z);
            }
            // flames bursting out where the breath lands
            Vec3 aim = breathTarget();
            for (int i = 0; i < 4; i++) {
                level().addParticle(ParticleTypes.FLAME, aim.x + random.nextGaussian() * 0.8, aim.y + random.nextDouble(), aim.z + random.nextGaussian() * 0.8,
                        random.nextGaussian() * 0.06, 0.05 + random.nextDouble() * 0.1, random.nextGaussian() * 0.06);
            }
            if (random.nextInt(3) == 0) level().addParticle(ParticleTypes.LAVA, mouth.x, mouth.y, mouth.z, 0, 0, 0);
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
        biteCooldown = CreatureConfig.DRAGON_BITE_COOLDOWN.get();
        level().broadcastEntityEvent(this, BITE_EVENT);
        playSound(ModSounds.DRAGON_BITE.get(), 2.0F, 0.9F + random.nextFloat() * 0.2F);
    }

    private DamageSource breathDamage() {
        return new DamageSource(level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DRAGON_FIRE), this);
    }

    /** One server tick of fire breath aimed at {@code aim}. */
    private void breathTick(Vec3 aim) {
        Vec3 mouth = mouthPos();
        Vec3 dir = aim.subtract(mouth).normalize();
        double range = CreatureConfig.DRAGON_BREATH_RANGE.get();
        Vec3 end = mouth.add(dir.scale(range));
        BlockHitResult hit = level().clip(new ClipContext(mouth, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        boolean hitBlock = hit.getType() != HitResult.Type.MISS;
        // stop at the aimed creature if it's closer than the block behind it
        Vec3 impact = hitBlock ? hit.getLocation() : end;
        if (aim.distanceTo(mouth) < impact.distanceTo(mouth)) impact = aim;
        double length = impact.distanceTo(mouth);
        double fireRadius = CreatureConfig.DRAGON_FIRE_RADIUS.get();

        if (breathTicks % 4 == 0) {
            float damage = CreatureConfig.DRAGON_BREATH_DAMAGE.get().floatValue();
            int burn = CreatureConfig.DRAGON_BURN_SECONDS.get();
            AABB area = new AABB(mouth, impact).inflate(fireRadius + 2);
            final Vec3 imp = impact;
            for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, area,
                    e -> e != this && e.isAlive() && !(e instanceof RedDragon) && !e.isSpectator())) {
                Vec3 c = e.position().add(0, e.getBbHeight() / 2, 0);
                Vec3 rel = c.subtract(mouth);
                double along = rel.dot(dir);
                double off = rel.subtract(dir.scale(along)).length();
                boolean inCone = along > -0.5 && along < length + 1.0 && off < 1.0 + along * 0.15 + e.getBbWidth() / 2;
                boolean nearImpact = c.distanceTo(imp) < fireRadius + e.getBbWidth() / 2;
                if (inCone || nearImpact) {
                    if (e.hurt(breathDamage(), damage) || burn > 0) e.setSecondsOnFire(burn);
                }
            }
        }

        if (CreatureConfig.DRAGON_BURNS_BLOCKS.get() && ForgeEventFactory.getMobGriefingEvent(level(), this) && (hitBlock || length < range - 0.5)) {
            if (hitBlock) igniteAt(hit.getBlockPos().relative(hit.getDirection()));
            for (int i = 0; i < 3; i++) {
                Vec3 p = impact.add((random.nextDouble() * 2 - 1) * fireRadius, (random.nextDouble() * 2 - 1) * 1.5, (random.nextDouble() * 2 - 1) * fireRadius);
                igniteAt(BlockPos.containing(p));
            }
        }
    }

    private void igniteAt(BlockPos pos) {
        // drop down to the first spot with ground under it
        for (int i = 0; i < 3 && level().isEmptyBlock(pos.below()); i++) pos = pos.below();
        if (!level().isEmptyBlock(pos)) pos = pos.above();
        if (level().isEmptyBlock(pos) && BaseFireBlock.canBePlacedAt(level(), pos, Direction.UP)) {
            BlockState fire = BaseFireBlock.getState(level(), pos);
            level().setBlock(pos, fire, 11);
        }
    }

    // ------------------------------------------------------------------ goals

    /** Like the vanilla target finder, but a dragon also spots things far above or below it (vanilla only looks 4 blocks up or down). */
    private class SpotTargetGoal<T extends LivingEntity> extends NearestAttackableTargetGoal<T> {
        SpotTargetGoal(Class<T> type) { super(RedDragon.this, type, true); }

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

    /** Stop, face the target and pour fire at it, sweeping a little behind if it dodges. */
    private class BreathGoal extends Goal {
        private Vec3 aim = Vec3.ZERO;

        BreathGoal() { setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP)); }

        @Override public boolean canUse() {
            LivingEntity t = getTarget();
            if (t == null || !t.isAlive() || breathCooldown > 0 || biteTicks > 0) return false;
            double dist = distanceTo(t);
            if (dist > CreatureConfig.DRAGON_BREATH_RANGE.get() * 0.85 || !hasLineOfSight(t)) return false;
            return dist > 6.0 || random.nextInt(4) == 0;
        }

        @Override public boolean canContinueToUse() { return breathTicks > 0 && isAlive(); }

        @Override public boolean requiresUpdateEveryTick() { return true; }

        @Override public void start() {
            breathTicks = CreatureConfig.DRAGON_BREATH_TICKS.get();
            LivingEntity t = getTarget();
            aim = t != null ? t.position().add(0, t.getBbHeight() * 0.4, 0) : position().add(getLookAngle().scale(8));
            getNavigation().stop();
            if (isFlying()) flyMoveControl.hover();
            entityData.set(BREATHING, true);
            playSound(ModSounds.DRAGON_BREATH.get(), 3.0F, 0.95F + random.nextFloat() * 0.1F);
        }

        @Override public void stop() {
            breathTicks = 0;
            entityData.set(BREATHING, false);
            breathCooldown = CreatureConfig.DRAGON_BREATH_COOLDOWN.get() + random.nextInt(60);
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
            Vec3 m = mouthPos();
            double h = Math.sqrt((aim.x - m.x) * (aim.x - m.x) + (aim.z - m.z) * (aim.z - m.z));
            setXRot(Mth.clamp((float) (-Mth.atan2(aim.y - m.y, h) * Mth.RAD_TO_DEG), -40.0F, 50.0F));
            if (isFlying()) flyMoveControl.hover();
            if (breathTicks < CreatureConfig.DRAGON_BREATH_TICKS.get() - 4) breathTick(aim);   // short wind-up first
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
        private final RedDragon dragon;

        FlyControl(RedDragon dragon) {
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
            double top = CreatureConfig.DRAGON_FLY_SPEED.get() * speedModifier;
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

    @Override public float getStandingEyeHeight(Pose pose, EntityDimensions size) { return 2.9F; }

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
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.getBoolean("Flying")) setFlying(true);
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
