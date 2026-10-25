package dev.codeonigiri.notcosmeticarmor.compat.projecte.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * ProjectE's flight bookkeeping latches onto a stale state when the game mode changes.
 *
 * <p>{@code InternalAbilities#tick} only grants or revokes flight when its cached flags say the
 * situation changed. Switching to creative and back leaves {@code hadFlightItem} true while
 * {@code mayfly} has already been reset to false by the game mode change, so no branch of the
 * tick ever runs again and the player silently loses the flight that the gem armor should grant.
 * Resetting the cached flag whenever it disagrees with the live ability value makes ProjectE
 * re-evaluate the predicate on the next tick.
 *
 * <p>The second half of the fix is the resync: the client only learns about abilities through a
 * packet, and a game mode change sends one that carries {@code mayfly = false}. Re-sending the
 * (already correct) server state on the tick the game mode changes keeps the client able to
 * double-tap into flight.
 */
@Pseudo
@Mixin(targets = "moze_intel.projecte.handlers.InternalAbilities", remap = false)
public abstract class InternalAbilitiesMixin {
    @Shadow
    @Final
    private ServerPlayer player;
    @Shadow
    private boolean hadFlightItem;

    private GameType ncaLastGameMode;
    private boolean ncaResyncAbilities;

    @Inject(method = "tick", at = @At("HEAD"), require = 0, remap = false)
    private void ncaResetStaleFlightFlag(CallbackInfo ci) {
        GameType gameType = player.gameMode.getGameModeForPlayer();
        if (gameType != ncaLastGameMode) {
            ncaLastGameMode = gameType;
            ncaResyncAbilities = true;
            hadFlightItem = false;
        }
        if (!player.getAbilities().mayfly && !player.isCreative() && !player.isSpectator()) {
            hadFlightItem = false;
        }
    }

    @Inject(method = "tick", at = @At("RETURN"), require = 0, remap = false)
    private void ncaResyncFlightAfterGameModeChange(CallbackInfo ci) {
        if (ncaResyncAbilities) {
            ncaResyncAbilities = false;
            player.onUpdateAbilities();
        }
    }
}
