package com.wildcreatures;

import net.minecraftforge.common.ForgeConfigSpec;

/** All tweakable numbers. Saved to config/wildcreatures-common.toml; no rebuild needed to change them. */
public final class CreatureConfig {
    private CreatureConfig() {}

    public static final ForgeConfigSpec SPEC;

    /** One section per dragon type. */
    public static final DragonStats RED, FROST;

    public static final ForgeConfigSpec.IntValue ASHEN_WASTES_WEIGHT;
    public static final ForgeConfigSpec.DoubleValue ESSENCE_CHANCE, ESSENCE_CHANCE_SMALL, ESSENCE_CHANCE_GIANT;
    public static final ForgeConfigSpec.IntValue SWORD_BURN_SECONDS, SHIELD_BURN_SECONDS, ARMOR_FIRE_PIECES;
    public static final ForgeConfigSpec.BooleanValue SWORD_COOKS_DROPS, ARMOR_BLOCKS_DRAGON_BREATH;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        RED = new DragonStats.Defaults("red_dragon", "Red Dragon", "the Ashen Wastes")
                .breath("fire breath", 7.0, 50, 120, 18.0)
                .effectRadius("fireRadius", "Radius around the aim point where blocks and creatures catch fire", 3.0)
                .effectSeconds("burnSeconds", "Seconds a creature hit by the breath keeps burning", 8)
                .affectsBlocks("breathSetsBlocksOnFire", "Whether the breath sets blocks on fire (also needs the mobGriefing game rule on)")
                .build(b);
        FROST = new DragonStats.Defaults("frost_dragon", "Frost Dragon", "snowy peaks, snowy slopes and ice spikes")
                .health(180.0, 110.0, 360.0)
                .armor(8.0)
                .bite(14.0, 30)
                .breath("frost breath", 5.0, 50, 120, 18.0)
                .effectRadius("frostRadius", "Radius around the aim point that gets frozen and snowed on", 3.0)
                .effectSeconds("slowSeconds", "Seconds a creature hit by the breath stays slowed. It also freezes up like in powder snow (leather armor stops that part)", 5)
                .affectsBlocks("breathFreezesBlocks", "Whether the breath turns water to ice and lava to obsidian, puts out fires and lays snow (also needs the mobGriefing game rule on)")
                .build(b);
        b.comment("Dragon Essence drops (percent chance when a player kills any kind of dragon; each level of Looting adds half of the normal chance)").push("dragon_essence");
        ESSENCE_CHANCE = b.comment("Normal-size dragon").defineInRange("dropChancePercent", 2.0, 0.0, 100.0);
        ESSENCE_CHANCE_SMALL = b.comment("Small dragon").defineInRange("smallDropChancePercent", 1.0, 0.0, 100.0);
        ESSENCE_CHANCE_GIANT = b.comment("Giant dragon").defineInRange("giantDropChancePercent", 10.0, 0.0, 100.0);
        b.pop();
        b.comment("Dragon gear").push("dragon_gear");
        SWORD_BURN_SECONDS = b.comment("Seconds a creature burns after being hit by the Dragon Sword (0 = no fire)").defineInRange("swordBurnSeconds", 4, 0, 60);
        SHIELD_BURN_SECONDS = b.comment("Seconds a creature burns after hitting a player who is blocking with the Dragon Shield (0 = no fire)").defineInRange("shieldBurnSeconds", 5, 0, 60);
        SWORD_COOKS_DROPS = b.comment("Animals killed with the Dragon Sword drop cooked meat").define("swordCooksDrops", true);
        b.pop();
        b.comment("Dragon Scale armor").push("dragon_armor");
        ARMOR_FIRE_PIECES = b.comment("How many Dragon Scale armor pieces you need on to be immune to fire and lava (4 = full set, 0 = never)")
                .defineInRange("fireImmunityPieces", 4, 0, 4);
        ARMOR_BLOCKS_DRAGON_BREATH = b.comment("Whether that fire immunity also blocks the Red Dragon's fire breath damage").define("blocksDragonBreath", true);
        b.pop();
        b.comment("World generation. Changes only affect newly generated land.").push("world");
        ASHEN_WASTES_WEIGHT = b.comment("How common the Ashen Wastes biome is. Higher = more common, 0 = turned off. Needs a game restart.")
                .defineInRange("ashenWastesWeight", 10, 0, 50);
        b.pop();
        SPEC = b.build();
    }
}
