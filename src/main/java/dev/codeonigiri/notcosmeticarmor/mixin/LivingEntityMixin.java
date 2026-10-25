package dev.codeonigiri.notcosmeticarmor.mixin;

import dev.codeonigiri.notcosmeticarmor.core.CosmeticState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Differential guard.
 *
 * <p>Every tick {@code LivingEntity#tick} calls {@code detectEquipmentUpdates}, which compares the
 * last known armor stacks with {@code getItemBySlot}, removes and re-adds attribute modifiers and
 * broadcasts {@code ClientboundSetEquipmentPacket} when they differ.
 *
 * <p>Two things must not happen: the query override must not feed the cosmetic stack into that
 * comparison (it would remove the modifiers of the wrong stack), and while the tick window holds the
 * cosmetic stacks in the real slots the diff must be skipped entirely - otherwise the cosmetic armor's
 * own modifiers would be applied on top of the mirrored ones and the client would be told about armor
 * it cannot see.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Inject(method = "detectEquipmentUpdates", at = @At("HEAD"), cancellable = true)
    private void notcosmeticarmor$beforeDetectEquipmentUpdates(CallbackInfo ci) {
        if (!((Object) this instanceof ServerPlayer player)) {
            return;
        }
        CosmeticState state = CosmeticState.peek(player);
        if (state == null) {
            return;
        }
        if (state.windowHeld) {
            ci.cancel();
            return;
        }
        state.bookkeeping = true;
    }

    @Inject(method = "detectEquipmentUpdates", at = @At("RETURN"))
    private void notcosmeticarmor$afterDetectEquipmentUpdates(CallbackInfo ci) {
        if (!((Object) this instanceof ServerPlayer player)) {
            return;
        }
        CosmeticState state = CosmeticState.peek(player);
        if (state != null && !state.windowHeld) {
            state.bookkeeping = false;
        }
    }
}
