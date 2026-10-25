package dev.codeonigiri.notcosmeticarmor.core;

import dev.codeonigiri.notcosmeticarmor.car.CarBridge;
import dev.codeonigiri.notcosmeticarmor.config.CnaConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;

/**
 * Lets the cosmetic armor wear out like worn armor.
 *
 * <p>Vanilla damages the stacks in {@code Inventory#armor} from {@code Player#hurtArmor}, so the
 * cosmetic slots - which live in Cosmetic Armor Reworked's own inventory - would never lose
 * durability. Since {@link AttributeMirror} makes the cosmetic armor grant its attributes, that
 * would be free protection. For every slot the cosmetic armor covers we apply the vanilla amount
 * (damage / 4, at least 1) to the cosmetic stack and write it back through CAR's inventory, which
 * syncs the change to the client and saves it. Vanilla's exceptions are kept: only
 * {@link ArmorItem}s are damaged and fire damage does not hurt fire resistant armor. Unbreaking and
 * the breaking rules come from {@link ItemStack#hurtAndBreak}, exactly as for real armor.
 */
public final class CosmeticDurability {
    private CosmeticDurability() {
    }

    /**
     * Called from {@code Player#hurtArmor}. Vanilla has already been given the real armor (the tick
     * window is closed before this runs), so the two wear down in lockstep.
     */
    public static void hurtArmor(ServerPlayer player, DamageSource source, float amount) {
        if (!CnaConfig.enabled() || !CnaConfig.cosmeticDurability() || !CnaConfig.mirrorAttributes()) {
            return;
        }
        if (amount <= 0.0F || !CarBridge.available()) {
            return;
        }
        float perSlot = amount / 4.0F;
        if (perSlot < 1.0F) {
            perSlot = 1.0F;
        }
        for (int i = 0; i < CosmeticSlots.COUNT; i++) {
            ItemStack cosmetic = CosmeticStacks.get(player, i);
            if (cosmetic.isEmpty() || !(cosmetic.getItem() instanceof ArmorItem)) {
                continue;
            }
            if (source.is(DamageTypeTags.IS_FIRE) && cosmetic.getItem().isFireResistant()) {
                continue;
            }
            EquipmentSlot slot = CosmeticSlots.byIndex(i);
            ItemStack damaged = cosmetic.copy();
            damaged.hurtAndBreak((int) perSlot, player, entity -> entity.broadcastBreakEvent(slot));
            CarBridge.set(player.getUUID(), i, damaged.isEmpty() ? ItemStack.EMPTY : damaged);
        }
    }
}
