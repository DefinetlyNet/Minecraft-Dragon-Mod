package com.wildcreatures.world;

import com.wildcreatures.CreatureConfig;
import com.wildcreatures.ModEntities;
import com.wildcreatures.WildCreatures;
import com.wildcreatures.entity.SeaDragon;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Natural spawning for the Sea Dragon. The normal mob spawning can't do it: the Sea Dragon would have to share the
 * monster limit with every zombie and creeper in the caves under the ocean, and those always use it up first.
 * So every 30 seconds each player gets a 1 in spawnRarity chance of one turning up on the floor of a deep ocean
 * 32 to 64 blocks away (with the same rules as {@link SeaDragon#canSpawnAt}), as long as there isn't one around already.
 */
@Mod.EventBusSubscriber(modid = WildCreatures.MOD_ID)
public final class SeaDragonSpawner {
    private SeaDragonSpawner() {}

    /** The biomes it lives in (deep oceans of every temperature). */
    public static final TagKey<Biome> HABITAT = TagKey.create(Registries.BIOME, new ResourceLocation(WildCreatures.MOD_ID, "sea_dragon_habitat"));

    private static final int INTERVAL = 600;                    // ticks between tries (30 seconds)
    private static final int MIN_DISTANCE = 32, MAX_DISTANCE = 64;
    private static final int SPOTS_TO_TRY = 8;
    /** No new one while another is this close to the player (it's also how far you go before one can leave). */
    private static final int ONE_AT_A_TIME = 96;

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) return;
        if (level.getGameTime() % INTERVAL != 0) return;
        if (level.getDifficulty() == Difficulty.PEACEFUL || !level.getServer().isSpawningMonsters()
                || !level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)) return;
        int rarity = CreatureConfig.SEA.spawnRarity.get();
        if (rarity <= 0) return;
        for (ServerPlayer player : level.players()) {
            if (!player.isSpectator() && level.random.nextInt(rarity) == 0) spawnNear(level, player);
        }
    }

    /** Looks for a good spot on the sea floor around the player and puts a Sea Dragon there. */
    private static void spawnNear(ServerLevel level, ServerPlayer player) {
        // you only ever meet one at a time
        if (!level.getEntitiesOfClass(SeaDragon.class, player.getBoundingBox().inflate(ONE_AT_A_TIME, 64, ONE_AT_A_TIME)).isEmpty()) return;
        RandomSource random = level.random;
        for (int i = 0; i < SPOTS_TO_TRY; i++) {
            float angle = random.nextFloat() * Mth.TWO_PI;
            double distance = Mth.nextDouble(random, MIN_DISTANCE, MAX_DISTANCE);
            int x = Mth.floor(player.getX() + Mth.cos(angle) * distance);
            int z = Mth.floor(player.getZ() + Mth.sin(angle) * distance);
            if (!level.isPositionEntityTicking(new BlockPos(x, level.getSeaLevel(), z))) continue;  // only where the world is running
            BlockPos pos = new BlockPos(x, level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z), z);
            if (!level.getBiome(pos).is(HABITAT) || !SeaDragon.canSpawnAt(level, pos)) continue;

            SeaDragon dragon = ModEntities.SEA_DRAGON.get().create(level);
            if (dragon == null) return;
            dragon.moveTo(x + 0.5, pos.getY(), z + 0.5, random.nextFloat() * 360.0F, 0.0F);
            if (!ForgeEventFactory.checkSpawnPosition(dragon, level, MobSpawnType.NATURAL)) continue;
            ForgeEventFactory.onFinalizeSpawn(dragon, level, level.getCurrentDifficultyAt(pos), MobSpawnType.NATURAL, null, null);
            if (dragon.isSpawnCancelled() || !liftUntilItFits(level, dragon)) continue;
            level.addFreshEntityWithPassengers(dragon);
            return;
        }
    }

    /** A lumpy sea floor can poke into a dragon this big (giants most of all), so lift it a few blocks until it's clear. */
    private static boolean liftUntilItFits(ServerLevel level, SeaDragon dragon) {
        for (int up = 0; up < 4; up++) {
            if (level.noCollision(dragon)) return true;
            dragon.setPos(dragon.getX(), dragon.getY() + 1.0, dragon.getZ());
        }
        return false;
    }
}
