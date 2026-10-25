package dev.codeonigiri.notcosmeticarmor;

import dev.codeonigiri.notcosmeticarmor.car.CarBridge;
import dev.codeonigiri.notcosmeticarmor.command.CnaCommand;
import dev.codeonigiri.notcosmeticarmor.config.CnaConfig;
import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * Not Cosmetic Armor.
 *
 * <p>Makes the Cosmetic Armor Reworked cosmetic slots actually functional instead of purely visual.
 * The real armor slots stay untouched and keep working as the "real" equipment; the cosmetic slots
 * contribute their attributes (armor, toughness, knockback resistance, ...) and their enchantments.
 */
@Mod(NotCosmeticArmor.MOD_ID)
public final class NotCosmeticArmor {
    public static final String MOD_ID = "notcosmeticarmor";
    public static final Logger LOGGER = LogUtils.getLogger();

    public NotCosmeticArmor() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, CnaConfig.SPEC);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onCommonSetup);
        MinecraftForge.EVENT_BUS.addListener(CnaCommand::register);
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            if (CarBridge.available()) {
                LOGGER.info("[Not Cosmetic Armor] Cosmetic Armor Reworked detected; cosmetic slots are functional.");
            } else {
                LOGGER.warn("[Not Cosmetic Armor] Cosmetic Armor Reworked was not found; the mod will stay passive.");
            }
        });
    }
}
