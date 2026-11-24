package com.wildcreatures;

import net.minecraftforge.common.ForgeConfigSpec;

/** All tweakable numbers. Saved to config/wildcreatures-common.toml; no rebuild needed to change them. */
public final class CreatureConfig {
    private CreatureConfig() {}

    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.DoubleValue DRAGON_HEALTH, DRAGON_ARMOR, DRAGON_BITE_DAMAGE, DRAGON_BREATH_DAMAGE,
            DRAGON_BREATH_RANGE, DRAGON_FIRE_RADIUS, DRAGON_FLY_SPEED;
    public static final ForgeConfigSpec.IntValue DRAGON_BITE_COOLDOWN, DRAGON_BREATH_TICKS, DRAGON_BREATH_COOLDOWN,
            DRAGON_BURN_SECONDS, DRAGON_SPAWN_CHANCE, ASHEN_WASTES_WEIGHT,
            DRAGON_SMALL_CHANCE, DRAGON_GIANT_CHANCE;
    public static final ForgeConfigSpec.DoubleValue DRAGON_SMALL_HEALTH, DRAGON_GIANT_HEALTH,
            ESSENCE_CHANCE, ESSENCE_CHANCE_SMALL, ESSENCE_CHANCE_GIANT;
    public static final ForgeConfigSpec.IntValue SWORD_BURN_SECONDS, SHIELD_BURN_SECONDS;
    public static final ForgeConfigSpec.BooleanValue DRAGON_CAN_FLY, DRAGON_BURNS_BLOCKS;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.comment("Red Dragon. Health and damage are in half-hearts (20 = a full player health bar).").push("red_dragon");
        DRAGON_HEALTH = b.comment("Max health of a normal-size dragon").defineInRange("health", 200.0, 1.0, 2048.0);
        DRAGON_SMALL_CHANCE = b.comment("Percent chance a new dragon is a small one (smaller, weaker, less health)").defineInRange("smallChancePercent", 30, 0, 100);
        DRAGON_SMALL_HEALTH = b.comment("Max health of a small dragon").defineInRange("smallHealth", 120.0, 1.0, 2048.0);
        DRAGON_GIANT_CHANCE = b.comment("Percent chance a new dragon is a giant (1.5x bigger, hits harder, drops extra scales)").defineInRange("giantChancePercent", 3, 0, 100);
        DRAGON_GIANT_HEALTH = b.comment("Max health of a giant dragon").defineInRange("giantHealth", 400.0, 1.0, 2048.0);
        DRAGON_ARMOR = b.comment("Armor points (a full diamond suit is 20)").defineInRange("armor", 10.0, 0.0, 30.0);
        DRAGON_BITE_DAMAGE = b.comment("Damage of one bite").defineInRange("biteDamage", 16.0, 0.0, 1000.0);
        DRAGON_BITE_COOLDOWN = b.comment("Ticks between bites (20 ticks = 1 second)").defineInRange("biteCooldownTicks", 30, 5, 400);
        DRAGON_BREATH_DAMAGE = b.comment("Damage of each fire breath hit (a target in the flames is hit about twice a second)")
                .defineInRange("breathDamage", 7.0, 0.0, 1000.0);
        DRAGON_BREATH_TICKS = b.comment("How long one fire breath lasts, in ticks").defineInRange("breathDurationTicks", 50, 10, 400);
        DRAGON_BREATH_COOLDOWN = b.comment("Minimum ticks between fire breaths").defineInRange("breathCooldownTicks", 120, 20, 2400);
        DRAGON_BREATH_RANGE = b.comment("How far the fire breath reaches, in blocks").defineInRange("breathRange", 18.0, 4.0, 48.0);
        DRAGON_FIRE_RADIUS = b.comment("Radius around the aim point where blocks and creatures catch fire").defineInRange("fireRadius", 3.0, 0.0, 8.0);
        DRAGON_BURN_SECONDS = b.comment("Seconds a creature hit by the breath keeps burning").defineInRange("burnSeconds", 8, 0, 60);
        DRAGON_BURNS_BLOCKS = b.comment("Whether the breath sets blocks on fire (also needs the mobGriefing game rule on)").define("breathSetsBlocksOnFire", true);
        DRAGON_CAN_FLY = b.comment("Whether the dragon can take off and fly").define("canFly", true);
        DRAGON_FLY_SPEED = b.comment("Top flying speed in blocks per tick (a sprinting player is about 0.28)").defineInRange("flySpeed", 0.45, 0.05, 2.0);
        DRAGON_SPAWN_CHANCE = b.comment("Natural spawns (in the Ashen Wastes) only succeed 1 time in this many tries. Higher = rarer. 0 = never spawn naturally.")
                .defineInRange("spawnRarity", 4, 0, 10000);
        b.pop();
        b.comment("Dragon Essence drops (percent chance when a player kills a dragon; each level of Looting adds half of the normal chance)").push("dragon_essence");
        ESSENCE_CHANCE = b.comment("Normal-size dragon").defineInRange("dropChancePercent", 2.0, 0.0, 100.0);
        ESSENCE_CHANCE_SMALL = b.comment("Small dragon").defineInRange("smallDropChancePercent", 1.0, 0.0, 100.0);
        ESSENCE_CHANCE_GIANT = b.comment("Giant dragon").defineInRange("giantDropChancePercent", 10.0, 0.0, 100.0);
        b.pop();
        b.comment("Dragon gear").push("dragon_gear");
        SWORD_BURN_SECONDS = b.comment("Seconds a creature burns after being hit by the Dragon Sword (0 = no fire)").defineInRange("swordBurnSeconds", 4, 0, 60);
        SHIELD_BURN_SECONDS = b.comment("Seconds a creature burns after hitting a player who is blocking with the Dragon Shield (0 = no fire)").defineInRange("shieldBurnSeconds", 5, 0, 60);
        b.pop();
        b.comment("World generation. Changes only affect newly generated land.").push("world");
        ASHEN_WASTES_WEIGHT = b.comment("How common the Ashen Wastes biome is. Higher = more common, 0 = turned off. Needs a game restart.")
                .defineInRange("ashenWastesWeight", 10, 0, 50);
        b.pop();
        SPEC = b.build();
    }
}
