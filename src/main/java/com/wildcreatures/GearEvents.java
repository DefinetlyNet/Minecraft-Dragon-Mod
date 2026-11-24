package com.wildcreatures;

import com.wildcreatures.item.DragonScaleArmorItem;
import com.wildcreatures.item.DragonShieldItem;
import com.wildcreatures.item.DragonSwordItem;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.ShieldBlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Game events for the dragon gear. */
@Mod.EventBusSubscriber(modid = WildCreatures.MOD_ID)
public final class GearEvents {
    private GearEvents() {}

    private static final ResourceKey<DamageType> DRAGON_FIRE =
            ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(WildCreatures.MOD_ID, "dragon_fire"));

    /** A full set of Dragon Scale armor makes you immune to fire, lava and magma blocks. */
    @SubscribeEvent
    public static void onLivingAttack(LivingAttackEvent event) {
        DamageSource source = event.getSource();
        if (!source.is(DamageTypeTags.IS_FIRE) || !DragonScaleArmorItem.givesFireImmunity(event.getEntity())) return;
        if (source.is(DRAGON_FIRE) && !CreatureConfig.ARMOR_BLOCKS_DRAGON_BREATH.get()) return;
        event.setCanceled(true);
    }

    /**
     * ...and you never catch fire. Lava and fire relight a player every tick, so the flames are put out at the end of
     * each player tick (on both sides, so neither you nor other players ever see you burning).
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) putOutFire(event.player);
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof Player)) putOutFire(event.getEntity());   // mobs wearing the armor
    }

    private static void putOutFire(LivingEntity entity) {
        if (entity.getRemainingFireTicks() <= 0 && !entity.isOnFire()) return;
        if (!DragonScaleArmorItem.givesFireImmunity(entity)) return;
        entity.clearFire();
        if (!entity.level().isClientSide) entity.setSharedFlagOnFire(false);
    }

    /** Animals killed with the Dragon Sword drop their meat already cooked (like Fire Aspect, but for every kill). */
    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        if (!CreatureConfig.SWORD_COOKS_DROPS.get()) return;
        DamageSource source = event.getSource();
        if (!(source.getEntity() instanceof Player player) || source.getDirectEntity() != player) return;
        if (!(player.getMainHandItem().getItem() instanceof DragonSwordItem)) return;
        Level level = event.getEntity().level();
        for (ItemEntity drop : event.getDrops()) {
            ItemStack raw = drop.getItem();
            if (!raw.isEdible()) continue;
            level.getRecipeManager().getRecipeFor(RecipeType.SMELTING, new SimpleContainer(raw), level)
                    .map(recipe -> recipe.getResultItem(level.registryAccess()))
                    .filter(cooked -> !cooked.isEmpty() && cooked.isEdible())
                    .ifPresent(cooked -> drop.setItem(cooked.copyWithCount(Math.min(cooked.getMaxStackSize(), cooked.getCount() * raw.getCount()))));
        }
    }

    /** Blocking a hit with the Dragon Shield sets the attacker on fire. */
    @SubscribeEvent
    public static void onShieldBlock(ShieldBlockEvent event) {
        LivingEntity blocker = event.getEntity();
        if (!(blocker.getUseItem().getItem() instanceof DragonShieldItem)) return;
        Entity attacker = event.getDamageSource().getDirectEntity();
        // only creatures hitting you up close (not arrows or fireballs)
        if (!(attacker instanceof LivingEntity living) || attacker != event.getDamageSource().getEntity()) return;
        int seconds = CreatureConfig.SHIELD_BURN_SECONDS.get();
        if (seconds <= 0) return;
        living.setSecondsOnFire(seconds);
        if (blocker.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.FLAME, living.getX(), living.getY() + living.getBbHeight() * 0.6, living.getZ(),
                    18, living.getBbWidth() * 0.4, living.getBbHeight() * 0.3, living.getBbWidth() * 0.4, 0.03);
            level.playSound(null, blocker.getX(), blocker.getY(), blocker.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.7F, 1.2F);
        }
    }
}
