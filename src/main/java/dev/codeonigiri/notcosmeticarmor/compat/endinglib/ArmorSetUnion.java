package dev.codeonigiri.notcosmeticarmor.compat.endinglib;

import dev.codeonigiri.notcosmeticarmor.core.CosmeticState;
import java.util.LinkedHashSet;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;

/**
 * Set detection for worn armor when a cosmetic layer is present.
 *
 * <p>EndingLib decides "which armor set is this entity wearing" by asking
 * {@code LivingEntity#getItemBySlot} for the four armor slots. Our query override answers with the
 * cosmetic stack, so a cosmetic piece hides the real one in that slot: a full fantasy_ending set on
 * the body stops counting as a set as soon as anything cosmetic sits above it, and every set effect
 * (flight, death protection, damage immunity) silently disappears even though the armor is still
 * equipped.
 *
 * <p>The mod is additive everywhere else - attributes are summed and {@code getArmorSlots} appends the
 * cosmetic stacks to the real list - so set detection follows the same rule here: a slot counts for a
 * material when <em>either</em> layer matches. The returned material keeps the old preference for the
 * visible (cosmetic) layer, except that a material with set effects wins over a plain one, which is
 * what keeps a real fantasy_ending set alive under cosmetic diamond armor.
 *
 * <p>The tick window needs a third layer: on the ticks it is open the cosmetic copies sit in
 * {@code Inventory#armor} and the real stacks are parked in {@link CosmeticState#windowReal}, so both
 * of the two ordinary layers report the cosmetics. Without looking at the parked stacks a real set
 * would blink out of existence for half of the ticks, which is enough for
 * {@code FeRunicShieldCapability#tick} to reset the shield every other tick.
 */
public final class ArmorSetUnion {
    private static final EquipmentSlot[] SLOTS = {
        EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD
    };

    private static boolean optionLookupDone;
    private static Class<?> optionArmorMaterial;

    private ArmorSetUnion() {
    }

    /**
     * @return a material that covers all four armor slots across the real and the cosmetic layer, or
     *     null when no layer combination forms a set.
     */
    public static ArmorMaterial find(LivingEntity living) {
        ItemStack[] visible = stacks(living::getItemBySlot);
        ItemStack[] real = visible;
        ItemStack[] parked = null;
        if (living instanceof Player player) {
            List<ItemStack> armor = player.getInventory().armor;
            real = new ItemStack[4];
            for (EquipmentSlot slot : SLOTS) {
                int index = slot.getIndex();
                real[index] = index < armor.size() ? armor.get(index) : ItemStack.EMPTY;
            }
            if (player instanceof ServerPlayer serverPlayer) {
                CosmeticState state = CosmeticState.peek(serverPlayer);
                if (state != null && state.windowHeld) {
                    parked = state.windowReal;
                }
            }
        }

        LinkedHashSet<ArmorMaterial> candidates = new LinkedHashSet<>();
        collect(candidates, visible);
        collect(candidates, real);
        collect(candidates, parked);

        ArmorMaterial plain = null;
        for (ArmorMaterial material : candidates) {
            if (count(visible, real, parked, material) < 4) {
                continue;
            }
            if (isOptionMaterial(material)) {
                return material;
            }
            if (plain == null) {
                plain = material;
            }
        }
        return plain;
    }

    private interface SlotLookup {
        ItemStack get(EquipmentSlot slot);
    }

    private static ItemStack[] stacks(SlotLookup lookup) {
        ItemStack[] out = new ItemStack[4];
        for (EquipmentSlot slot : SLOTS) {
            out[slot.getIndex()] = lookup.get(slot);
        }
        return out;
    }

    private static void collect(LinkedHashSet<ArmorMaterial> out, ItemStack[] stacks) {
        if (stacks == null) {
            return;
        }
        for (ItemStack stack : stacks) {
            if (stack != null && stack.getItem() instanceof ArmorItem armor) {
                out.add(armor.getMaterial());
            }
        }
    }

    private static int count(ItemStack[] first, ItemStack[] second, ItemStack[] third, ArmorMaterial material) {
        int count = 0;
        for (EquipmentSlot slot : SLOTS) {
            int index = slot.getIndex();
            if (matches(first, index, material) || matches(second, index, material) || matches(third, index, material)) {
                count++;
            }
        }
        return count;
    }

    private static boolean matches(ItemStack[] stacks, int index, ArmorMaterial material) {
        if (stacks == null) {
            return false;
        }
        ItemStack stack = stacks[index];
        return stack != null && stack.getItem() instanceof ArmorItem armor && armor.getMaterial() == material;
    }

    /** EndingLib's {@code OptionArmorMaterial} marks the materials that carry set effects. */
    private static boolean isOptionMaterial(ArmorMaterial material) {
        if (!optionLookupDone) {
            optionLookupDone = true;
            try {
                optionArmorMaterial = Class.forName(
                        "com.mega.endinglib.api.item.armor.OptionArmorMaterial",
                        false,
                        ArmorSetUnion.class.getClassLoader());
            } catch (Throwable ignored) {
                optionArmorMaterial = null;
            }
        }
        return optionArmorMaterial != null && optionArmorMaterial.isInstance(material);
    }
}
