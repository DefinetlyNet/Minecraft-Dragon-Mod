package com.wildcreatures;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * The config section for one kind of dragon. Every dragon type has the same basic numbers
 * (health, sizes, bite, breath, flying, spawning), plus three that depend on what its breath does:
 * a radius, a number of seconds, and whether the breath changes blocks. Each type names those three
 * itself (the Red Dragon calls them fireRadius, burnSeconds and breathSetsBlocksOnFire).
 */
public final class DragonStats {
    public final ForgeConfigSpec.DoubleValue health, smallHealth, giantHealth, armor, biteDamage,
            breathDamage, breathRange, effectRadius, flySpeed;
    public final ForgeConfigSpec.IntValue smallChance, giantChance, biteCooldown, breathTicks, breathCooldown,
            effectSeconds, spawnRarity;
    public final ForgeConfigSpec.BooleanValue breathAffectsBlocks, canFly;

    private DragonStats(ForgeConfigSpec.Builder b, Defaults d) {
        b.comment(d.title + ". Health and damage are in half-hearts (20 = a full player health bar).").push(d.section);
        health = b.comment("Max health of a normal-size dragon").defineInRange("health", d.health, 1.0, 2048.0);
        smallChance = b.comment("Percent chance a new dragon is a small one (smaller, weaker, less health)").defineInRange("smallChancePercent", d.smallChance, 0, 100);
        smallHealth = b.comment("Max health of a small dragon").defineInRange("smallHealth", d.smallHealth, 1.0, 2048.0);
        giantChance = b.comment("Percent chance a new dragon is a giant (1.5x bigger, hits harder, drops extra scales)").defineInRange("giantChancePercent", d.giantChance, 0, 100);
        giantHealth = b.comment("Max health of a giant dragon").defineInRange("giantHealth", d.giantHealth, 1.0, 2048.0);
        armor = b.comment("Armor points (a full diamond suit is 20)").defineInRange("armor", d.armor, 0.0, 30.0);
        biteDamage = b.comment("Damage of one bite").defineInRange("biteDamage", d.biteDamage, 0.0, 1000.0);
        biteCooldown = b.comment("Ticks between bites (20 ticks = 1 second)").defineInRange("biteCooldownTicks", d.biteCooldown, 5, 400);
        breathDamage = b.comment("Damage of each " + d.breathName + " hit" + d.damageNote)
                .defineInRange("breathDamage", d.breathDamage, 0.0, 1000.0);
        breathTicks = b.comment("How long one " + d.breathName + " lasts, in ticks").defineInRange("breathDurationTicks", d.breathTicks, 10, 400);
        breathCooldown = b.comment("Minimum ticks between " + d.breathName + "s").defineInRange("breathCooldownTicks", d.breathCooldown, 20, 2400);
        breathRange = b.comment("How far the " + d.breathName + " reaches, in blocks").defineInRange("breathRange", d.breathRange, 4.0, 48.0);
        effectRadius = b.comment(d.radiusComment).defineInRange(d.radiusKey, d.radius, 0.0, 8.0);
        effectSeconds = b.comment(d.secondsComment).defineInRange(d.secondsKey, d.seconds, 0, 60);
        breathAffectsBlocks = b.comment(d.blocksComment).define(d.blocksKey, true);
        canFly = b.comment("Whether the dragon can take off and fly").define("canFly", d.canFly);
        flySpeed = b.comment("Top flying speed in blocks per tick (a sprinting player is about 0.28)").defineInRange("flySpeed", d.flySpeed, 0.05, 2.0);
        spawnRarity = b.comment("Natural spawns (in " + d.home + ") only succeed 1 time in this many tries. Higher = rarer. 0 = never spawn naturally.")
                .defineInRange("spawnRarity", d.spawnRarity, 0, 10000);
        b.pop();
    }

    /** Starting values for a dragon type's config section. Anything not set keeps the Red Dragon's numbers. */
    public static final class Defaults {
        private final String section, title, home;
        private double health = 200, smallHealth = 120, giantHealth = 400, armor = 10, biteDamage = 16;
        private int smallChance = 30, giantChance = 3, biteCooldown = 30;
        private String breathName = "breath";
        private double breathDamage = 7, breathRange = 18;
        private int breathTicks = 50, breathCooldown = 120;
        private String damageNote = " (a target in the breath is hit about twice a second)";
        private String radiusKey = "effectRadius", radiusComment = "Radius around the aim point that the breath affects";
        private double radius = 3;
        private String secondsKey = "effectSeconds", secondsComment = "Seconds the breath's effect lasts on a creature";
        private int seconds = 8;
        private String blocksKey = "breathAffectsBlocks", blocksComment = "Whether the breath changes blocks (also needs the mobGriefing game rule on)";
        private boolean canFly = true;
        private double flySpeed = 0.45;
        private int spawnRarity = 4;

        /**
         * @param section config section name, e.g. "red_dragon"
         * @param title   name shown in the config comment, e.g. "Red Dragon"
         * @param home    where it spawns, for the spawnRarity comment, e.g. "the Ashen Wastes"
         */
        public Defaults(String section, String title, String home) {
            this.section = section;
            this.title = title;
            this.home = home;
        }

        public Defaults health(double normal, double small, double giant) { health = normal; smallHealth = small; giantHealth = giant; return this; }
        public Defaults sizeChances(int smallPercent, int giantPercent) { smallChance = smallPercent; giantChance = giantPercent; return this; }
        public Defaults armor(double points) { armor = points; return this; }
        public Defaults bite(double damage, int cooldownTicks) { biteDamage = damage; biteCooldown = cooldownTicks; return this; }

        public Defaults breath(String name, double damage, int durationTicks, int cooldownTicks, double range) {
            breathName = name; breathDamage = damage; breathTicks = durationTicks; breathCooldown = cooldownTicks; breathRange = range;
            return this;
        }

        /** Replaces the note after the breathDamage comment (normally how often the breath hits). */
        public Defaults damageNote(String note) { damageNote = note; return this; }
        public Defaults effectRadius(String key, String comment, double value) { radiusKey = key; radiusComment = comment; radius = value; return this; }
        public Defaults effectSeconds(String key, String comment, int value) { secondsKey = key; secondsComment = comment; seconds = value; return this; }
        public Defaults affectsBlocks(String key, String comment) { blocksKey = key; blocksComment = comment; return this; }
        public Defaults flying(boolean can, double speed) { canFly = can; flySpeed = speed; return this; }
        public Defaults spawnRarity(int oneIn) { spawnRarity = oneIn; return this; }

        public DragonStats build(ForgeConfigSpec.Builder b) { return new DragonStats(b, this); }
    }
}
