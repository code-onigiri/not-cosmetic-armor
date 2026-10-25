package dev.codeonigiri.notcosmeticarmor.command;

import dev.codeonigiri.notcosmeticarmor.car.CarBridge;
import dev.codeonigiri.notcosmeticarmor.compat.endinglib.EndingLibCompat;
import dev.codeonigiri.notcosmeticarmor.compat.projecte.ProjectECompat;
import dev.codeonigiri.notcosmeticarmor.config.CnaConfig;
import dev.codeonigiri.notcosmeticarmor.core.CosmeticSlots;
import dev.codeonigiri.notcosmeticarmor.core.CosmeticState;
import dev.codeonigiri.notcosmeticarmor.core.CosmeticStacks;
import dev.codeonigiri.notcosmeticarmor.core.RealPriority;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.RegisterCommandsEvent;

/**
 * {@code /notcosmeticarmor status} for debugging, plus {@code /notcosmeticarmor cosmetic} to place a
 * test item in a cosmetic slot. Writing through CAR's inventory means the change is synced and saved
 * like a normal cosmetic edit, which makes it possible to test several armor combinations without
 * leaving the world (editing {@code .cosarmor} by hand only works while the world is closed).
 */
public final class CnaCommand {
    private CnaCommand() {
    }

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("notcosmeticarmor")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("status").executes(ctx -> status(ctx.getSource())))
                .then(Commands.literal("probe").executes(ctx -> probe(ctx.getSource())))
                .then(Commands.literal("cosmetic")
                        .then(Commands.argument("slot", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                        new String[] {"feet", "legs", "chest", "head"}, builder))
                                .then(Commands.argument("item", StringArgumentType.greedyString())
                                        .executes(ctx -> cosmetic(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "slot"),
                                                StringArgumentType.getString(ctx, "item"))))))
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
            out.append("  fantasyEnding: ").append(EndingLibCompat.describe(player)).append('\n');
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
                        .append(" query=").append(RealPriority.prefersReal(real, cosmetic) ? "real" : "cosmetic")
                        .append('\n');
            }
        }
        String message = out.toString();
        source.sendSuccess(() -> Component.literal(message), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int cosmetic(CommandSourceStack source, String slotName, String itemName) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("notcosmeticarmor cosmetic needs a player"));
            return 0;
        }
        int slot = switch (slotName) {
            case "feet" -> 0;
            case "legs" -> 1;
            case "chest" -> 2;
            case "head" -> 3;
            default -> -1;
        };
        if (slot < 0) {
            source.sendFailure(Component.literal("slot must be feet, legs, chest or head"));
            return 0;
        }
        ItemStack stack = ItemStack.EMPTY;
        String trimmed = itemName.trim();
        if (!trimmed.equals("none") && !trimmed.equals("air")) {
            ResourceLocation id = ResourceLocation.tryParse(trimmed);
            if (id == null) {
                source.sendFailure(Component.literal("not an item id: " + trimmed));
                return 0;
            }
            Item item = BuiltInRegistries.ITEM.get(id);
            if (item == Items.AIR) {
                source.sendFailure(Component.literal("unknown item: " + id));
                return 0;
            }
            stack = new ItemStack(item);
        }
        if (!CarBridge.set(player.getUUID(), slot, stack)) {
            source.sendFailure(Component.literal("CAR did not accept the write (not detected?)"));
            return 0;
        }
        ItemStack result = stack;
        source.sendSuccess(() -> Component.literal("cosmetic " + slotName + " = "
                + (result.isEmpty() ? "empty" : result.getHoverName().getString())), false);
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
