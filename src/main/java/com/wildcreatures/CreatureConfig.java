package com.wildcreatures;

import net.minecraftforge.common.ForgeConfigSpec;

/** All tweakable numbers. Saved to config/wildcreatures-common.toml; no rebuild needed to change them. */
public final class CreatureConfig {
    private CreatureConfig() {}

    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.DoubleValue DRAGON_HEALTH, DRAGON_ARMOR, DRAGON_BITE_DAMAGE, DRAGON_BREATH_DAMAGE,
            DRAGON_BREATH_RANGE, DRAGON_FIRE_RADIUS, DRAGON_FLY_SPEED;
    public static final ForgeConfigSpec.IntValue DRAGON_BITE_COOLDOWN, DRAGON_BREATH_TICKS, DRAGON_BREATH_COOLDOWN,
            DRAGON_BURN_SECONDS, DRAGON_SPAWN_CHANCE;
    public static final ForgeConfigSpec.BooleanValue DRAGON_CAN_FLY, DRAGON_BURNS_BLOCKS;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.comment("Red Dragon. Health and damage are in half-hearts (20 = a full player health bar).").push("red_dragon");
        DRAGON_HEALTH = b.comment("Max health").defineInRange("health", 200.0, 1.0, 2048.0);
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
        DRAGON_SPAWN_CHANCE = b.comment("Natural spawns in mountains and badlands only succeed 1 time in this many tries. Higher = rarer. 0 = never spawn naturally.")
                .defineInRange("spawnRarity", 4, 0, 10000);
        b.pop();
        SPEC = b.build();
    }
}
