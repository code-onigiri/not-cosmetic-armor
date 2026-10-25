package dev.codeonigiri.notcosmeticarmor.compat.projecte;

import dev.codeonigiri.notcosmeticarmor.config.CnaConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import java.util.function.Predicate;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Shared helpers for the optional ProjectE patch. Nothing here references a ProjectE class, so this
 * file is safe to load even when the mod is missing.
 */
public final class ProjectECompat {
    /** Namespace of the armour pieces whose abilities ProjectE only checks against the real slots. */
    private static final String PROJECTE_NAMESPACE = "projecte";
    private static final String GEM_ARMOR_PREFIX = "gem_";

    /** Diagnostic counters exposed by {@code /notcosmeticarmor status}. */
    public static final AtomicLong CALLS = new AtomicLong();
    public static final AtomicLong HITS = new AtomicLong();

    private ProjectECompat() {
    }

    /** True when the patch should be applied at all. */
    public static boolean active() {
        return CnaConfig.enabled() && CnaConfig.overrideQueries() && CnaConfig.projectECompat();
    }

    /**
     * Runs {@code checker} over the stacks that {@code Player#getItemBySlot} reports for the armor
     * slots. Going through {@code getItemBySlot} (instead of reading our own cosmetic stacks) keeps
     * the identity comparisons ProjectE does - {@code GemFeet#canProvideFlight} compares the stack it
     * was handed with {@code player.getItemBySlot(FEET)} by reference - working.
     */
    public static boolean anyWorn(Player player, Predicate<ItemStack> checker) {
        CALLS.incrementAndGet();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() != EquipmentSlot.Type.ARMOR) {
                continue;
            }
            ItemStack worn = player.getItemBySlot(slot);
            if (!worn.isEmpty() && checker.test(worn)) {
                HITS.incrementAndGet();
                return true;
            }
        }
        return false;
    }

    public static boolean isGemArmor(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return PROJECTE_NAMESPACE.equals(id.getNamespace()) && id.getPath().startsWith(GEM_ARMOR_PREFIX);
    }
}
