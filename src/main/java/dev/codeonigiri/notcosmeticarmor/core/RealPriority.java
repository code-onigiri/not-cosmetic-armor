package dev.codeonigiri.notcosmeticarmor.core;

import dev.codeonigiri.notcosmeticarmor.config.CnaConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Decides which stack a "what is this player wearing" query should report when the real slot and the
 * cosmetic slot are both occupied.
 *
 * <p>Until 1.4.0 the cosmetic stack always won. That is the right default (it is the whole point of a
 * cosmetic armor slot), but it silently hides armor whose owner only ever looks at the real slot -
 * Mekanism's MekaSuit is the canonical example: every module check goes through
 * {@code Player#getItemBySlot} ({@code CommonPlayerTickHandler#getStepBoost}, {@code getSwimBoost},
 * {@code isGravitationalModulationReady}, ...), so a full MekaSuit under a fantasy_ending skin looked
 * like the player was wearing nothing at all.
 *
 * <p>The fix is a namespace allow list: when the gear of a listed mod sits in a slot, that gear wins
 * the query, whether it is the real stack or the cosmetic one. The other layers (attribute mirror,
 * enchantment union, item tick, set-bonus union) are untouched, so the cosmetic armor keeps its
 * effects.
 */
public final class RealPriority {
    private RealPriority() {
    }

    /**
     * @return true when {@code getItemBySlot} should keep reporting the real stack for this pair.
     */
    public static boolean prefersReal(ItemStack real, ItemStack cosmetic) {
        if (real.isEmpty()) {
            return false;
        }
        if (!isPriorityGear(real)) {
            return false;
        }
        // The cosmetic side wins when it is the listed gear itself, so the rule is symmetric: the
        // allow listed mod always sees its own armor, no matter which slot it is in.
        return cosmetic.isEmpty() || !isPriorityGear(cosmetic);
    }

    private static boolean isPriorityGear(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        for (String namespace : CnaConfig.realPriorityNamespaces()) {
            if (namespace.equals(id.getNamespace())) {
                return true;
            }
        }
        return false;
    }
}
