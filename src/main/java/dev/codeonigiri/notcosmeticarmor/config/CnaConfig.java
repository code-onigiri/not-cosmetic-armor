package dev.codeonigiri.notcosmeticarmor.config;

import net.minecraftforge.common.ForgeConfigSpec;

/** Server side configuration. */
public final class CnaConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue ENABLED;
    public static final ForgeConfigSpec.BooleanValue MIRROR_ATTRIBUTES;
    public static final ForgeConfigSpec.BooleanValue OVERRIDE_QUERIES;
    public static final ForgeConfigSpec.BooleanValue INTERLEAVE;
    public static final ForgeConfigSpec.BooleanValue TICK_COSMETIC_ITEMS;
    public static final ForgeConfigSpec.BooleanValue COSMETIC_DURABILITY;
    public static final ForgeConfigSpec.BooleanValue IGNORE_SKIN_FLAG;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.comment("Master switch for the whole mod.").push("general");
        ENABLED = builder.define("enabled", true);
        builder.pop();

        builder.comment(
                "How the cosmetic slots become functional.",
                "mirrorAttributes: apply the cosmetic armor's attribute modifiers (armor, toughness, knockback",
                "  resistance, ...) on top of the real armor. Independent of what getItemBySlot returns.",
                "overrideQueries: report the cosmetic stack from LivingEntity#getItemBySlot so that enchantments",
                "  (protection, thorns, ...) and mod code that asks for the worn item see the cosmetic armor.",
                "interleave: on odd game ticks hold the cosmetic stacks in the real armor slots for the",
                "  duration of Player#tick, so every query inside the tick (elytra, turtle helmet, mod hooks)",
                "  sees the cosmetic armor. Nothing is sent to the client and the real armor is restored",
                "  before the tick ends. Needs mirrorAttributes for the armor value itself.",
                "tickCosmeticItems: tick cosmetic stacks like worn armor (Forge onArmorTick / inventoryTick).",
                "cosmeticDurability: let the cosmetic armor wear out like worn armor. Every hit that damages the",
                "  real armor also damages the cosmetic armor of the same slot (same amount, same unbreaking and",
                "  breaking rules), and the change is written back to Cosmetic Armor Reworked. Only meaningful",
                "  with mirrorAttributes, because that is what makes the cosmetic armor act as worn armor.",
                "ignoreSkinFlag: also mirror slots that Cosmetic Armor Reworked marks as 'skin armor'."
        ).push("behavior");
        MIRROR_ATTRIBUTES = builder.define("mirrorAttributes", true);
        OVERRIDE_QUERIES = builder.define("overrideQueries", true);
        INTERLEAVE = builder.define("interleave", true);
        TICK_COSMETIC_ITEMS = builder.define("tickCosmeticItems", true);
        COSMETIC_DURABILITY = builder.define("cosmeticDurability", true);
        IGNORE_SKIN_FLAG = builder.define("ignoreSkinArmorFlag", true);
        builder.pop();

        SPEC = builder.build();
    }

    private CnaConfig() {
    }

    public static boolean enabled() {
        return !SPEC.isLoaded() || ENABLED.get();
    }

    public static boolean mirrorAttributes() {
        return !SPEC.isLoaded() || MIRROR_ATTRIBUTES.get();
    }

    public static boolean overrideQueries() {
        return !SPEC.isLoaded() || OVERRIDE_QUERIES.get();
    }

    public static boolean interleave() {
        return !SPEC.isLoaded() || INTERLEAVE.get();
    }

    public static boolean tickCosmeticItems() {
        return !SPEC.isLoaded() || TICK_COSMETIC_ITEMS.get();
    }

    public static boolean cosmeticDurability() {
        return !SPEC.isLoaded() || COSMETIC_DURABILITY.get();
    }

    public static boolean ignoreSkinFlag() {
        return !SPEC.isLoaded() || IGNORE_SKIN_FLAG.get();
    }
}
