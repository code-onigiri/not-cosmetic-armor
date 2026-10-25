package dev.codeonigiri.notcosmeticarmor.command;

import dev.codeonigiri.notcosmeticarmor.car.CarBridge;
import dev.codeonigiri.notcosmeticarmor.compat.projecte.ProjectECompat;
import dev.codeonigiri.notcosmeticarmor.config.CnaConfig;
import dev.codeonigiri.notcosmeticarmor.core.CosmeticSlots;
import dev.codeonigiri.notcosmeticarmor.core.CosmeticState;
import dev.codeonigiri.notcosmeticarmor.core.CosmeticStacks;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.RegisterCommandsEvent;

/** {@code /notcosmeticarmor status} for debugging. */
public final class CnaCommand {
    private CnaCommand() {
    }

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("notcosmeticarmor")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("status").executes(ctx -> status(ctx.getSource())))
                .then(Commands.literal("probe").executes(ctx -> probe(ctx.getSource())))
        );
    }

    private static int status(CommandSourceStack source) {
        StringBuilder out = new StringBuilder();
        out.append("Not Cosmetic Armor").append('\n');
        out.append("  car: ").append(CarBridge.available() ? "detected" : "missing").append('\n');
        out.append("  mode=").append(mode()).append('\n');
        out.append("  enabled=").append(CnaConfig.enabled())
                .append(" mirrorAttributes=").append(CnaConfig.mirrorAttributes())
                .append(" overrideQueries=").append(CnaConfig.overrideQueries())
                .append(" interleave=").append(CnaConfig.interleave())
                .append(" tickCosmeticItems=").append(CnaConfig.tickCosmeticItems())
                .append(" cosmeticDurability=").append(CnaConfig.cosmeticDurability())
                .append('\n');

        ServerPlayer player = source.getPlayer();
        if (player != null) {
            CosmeticState state = CosmeticState.peek(player);
            out.append("  armor=").append(player.getArmorValue())
                    .append(" bookkeeping=").append(state != null && state.bookkeeping)
                    .append(" window=").append(state != null && state.windowHeld)
                    .append(" probe=").append(state != null && state.probe)
                    .append('\n');
            out.append("  carInv: ").append(CarBridge.describe(player.getUUID())).append('\n');
            out.append("  projectE: patch=").append(CnaConfig.projectECompat())
                    .append(" calls=").append(ProjectECompat.CALLS.get())
                    .append(" hits=").append(ProjectECompat.HITS.get())
                    .append('\n');
            AttributeInstance step = player.getAttribute(ForgeMod.STEP_HEIGHT_ADDITION.get());
            out.append("  stepHeight=").append(step == null ? "n/a" : step.getValue());
            if (step != null) {
                for (AttributeModifier modifier : step.getModifiers()) {
                    out.append(" [").append(modifier.getName()).append(' ')
                            .append(modifier.getAmount()).append(' ')
                            .append(modifier.getOperation()).append(']');
                }
            }
            out.append('\n');
            for (int i = 0; i < CosmeticSlots.COUNT; i++) {
                ItemStack real = player.getInventory().armor.get(i);
                ItemStack cosmetic = CosmeticStacks.get(player, i);
                int applied = state == null ? 0 : state.applied[i].size();
                out.append("  ").append(CosmeticSlots.byIndex(i).getName())
                        .append(": real=").append(name(real))
                        .append(" cosmetic=").append(name(cosmetic))
                        .append(" modifiers=").append(applied)
                        .append('\n');
            }
        }
        String message = out.toString();
        source.sendSuccess(() -> Component.literal(message), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int probe(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("notcosmeticarmor probe needs a player"));
            return 0;
        }
        CosmeticState state = CosmeticState.of(player);
        state.probe = !state.probe;
        boolean on = state.probe;
        source.sendSuccess(() -> Component.literal("notcosmeticarmor probe: " + (on ? "on" : "off")), false);
        return Command.SINGLE_SUCCESS;
    }

    /** The plan's mode name derived from the individual switches. */
    private static String mode() {
        if (!CnaConfig.enabled()) {
            return "OFF";
        }
        if (CnaConfig.interleave() && CnaConfig.overrideQueries() && CnaConfig.mirrorAttributes()) {
            return "INTERLEAVE";
        }
        if (CnaConfig.overrideQueries() && CnaConfig.mirrorAttributes()) {
            return "PRIORITY";
        }
        if (CnaConfig.mirrorAttributes()) {
            return "ATTRIBUTE";
        }
        return "CUSTOM";
    }

    private static String name(ItemStack stack) {
        if (stack.isEmpty()) {
            return "-";
        }
        String base = stack.getHoverName().getString() + " x" + stack.getCount();
        if (stack.isDamageableItem()) {
            base += " dmg=" + stack.getDamageValue() + "/" + stack.getMaxDamage();
        }
        return base;
    }
}
