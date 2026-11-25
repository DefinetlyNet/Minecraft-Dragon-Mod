package com.wildcreatures.world;

import com.wildcreatures.CreatureConfig;
import com.wildcreatures.ModEntities;
import com.wildcreatures.WildCreatures;
import com.wildcreatures.entity.BlackDragon;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Natural spawning for the Black Dragon, which is meant to be a rare find. Once a minute each player gets a 1 in
 * spawnRarity chance of one landing on a mountain peak 24 to 48 blocks away, under open sky, as long as there isn't
 * one around already. It's friendly, so it can turn up on Peaceful too.
 */
@Mod.EventBusSubscriber(modid = WildCreatures.MOD_ID)
public final class BlackDragonSpawner {
    private BlackDragonSpawner() {}

    /** The biomes it lives in: the highest peaks. */
    public static final TagKey<Biome> HABITAT = TagKey.create(Registries.BIOME, new ResourceLocation(WildCreatures.MOD_ID, "black_dragon_habitat"));

    private static final int INTERVAL = 1200;                   // ticks between tries (a minute)
    private static final int MIN_DISTANCE = 24, MAX_DISTANCE = 48;
    private static final int SPOTS_TO_TRY = 10;
    /** No new one while another (wild or tamed) is this close to the player. */
    private static final int ONE_AT_A_TIME = 128;

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) return;
        if (level.getGameTime() % INTERVAL != 0) return;
        if (!level.getServer().isSpawningAnimals() || !level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)) return;
        int rarity = CreatureConfig.BLACK.spawnRarity.get();
        if (rarity <= 0) return;
        for (ServerPlayer player : level.players()) {
            if (!player.isSpectator() && level.random.nextInt(rarity) == 0) spawnNear(level, player);
        }
    }

    /** Looks for an open spot on a peak around the player and puts a Black Dragon there. */
    public static boolean spawnNear(ServerLevel level, ServerPlayer player) {
        if (!level.getEntitiesOfClass(BlackDragon.class, player.getBoundingBox().inflate(ONE_AT_A_TIME, 96, ONE_AT_A_TIME)).isEmpty()) return false;
        RandomSource random = level.random;
        for (int i = 0; i < SPOTS_TO_TRY; i++) {
            float angle = random.nextFloat() * Mth.TWO_PI;
            double distance = Mth.nextDouble(random, MIN_DISTANCE, MAX_DISTANCE);
            int x = Mth.floor(player.getX() + Mth.cos(angle) * distance);
            int z = Mth.floor(player.getZ() + Mth.sin(angle) * distance);
            if (!level.isPositionEntityTicking(new BlockPos(x, Mth.floor(player.getY()), z))) continue;   // only where the world is running
            BlockPos pos = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            if (!level.getBiome(pos).is(HABITAT) || !level.canSeeSky(pos)) continue;
            BlockState ground = level.getBlockState(pos.below());
            if (!ground.isFaceSturdy(level, pos.below(), Direction.UP) || ground.is(Blocks.POWDER_SNOW)) continue;

            BlackDragon dragon = ModEntities.BLACK_DRAGON.get().create(level);
            if (dragon == null) return false;
            dragon.moveTo(x + 0.5, pos.getY(), z + 0.5, random.nextFloat() * 360.0F, 0.0F);
            if (!ForgeEventFactory.checkSpawnPosition(dragon, level, MobSpawnType.NATURAL)) continue;
            ForgeEventFactory.onFinalizeSpawn(dragon, level, level.getCurrentDifficultyAt(pos), MobSpawnType.NATURAL, null, null);
            if (dragon.isSpawnCancelled() || !standOnTop(level, dragon)) continue;
            level.addFreshEntityWithPassengers(dragon);
            return true;
        }
        return false;
    }

    /**
     * Peaks are jagged and the dragon is three blocks or more across, so it stands on the highest ground under it (perched on
     * the rock, its edges can hang over lower ground). Snow layers can still poke into it, so it gets lifted a little until it's clear.
     */
    private static boolean standOnTop(ServerLevel level, BlackDragon dragon) {
        AABB box = dragon.getBoundingBox();
        int top = Mth.floor(dragon.getY());
        for (int x = Mth.floor(box.minX); x <= Mth.floor(box.maxX); x++) {
            for (int z = Mth.floor(box.minZ); z <= Mth.floor(box.maxZ); z++) {
                top = Math.max(top, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z));
            }
        }
        if (top - dragon.getY() > 6) return false;          // the middle of a cliff face, not a peak
        dragon.setPos(dragon.getX(), top, dragon.getZ());
        for (int up = 0; up < 2; up++) {
            if (level.noCollision(dragon)) return true;
            dragon.setPos(dragon.getX(), dragon.getY() + 1.0, dragon.getZ());
        }
        return false;
    }
}
