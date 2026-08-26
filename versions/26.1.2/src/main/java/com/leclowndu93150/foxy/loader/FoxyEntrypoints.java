package com.leclowndu93150.foxy.loader;

import com.leclowndu93150.foxy.fabricstub.api.ClientModInitializer;
import com.leclowndu93150.foxy.fabricstub.api.ModInitializer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforgespi.language.IModInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs the Fabric entrypoints of the mods Foxy bridged. Fabric calls these at mod init, before
 * anything else touches the mod; the NeoForge equivalent is Foxy's own constructor, which also
 * puts them ahead of {@code RegisterPayloadHandlersEvent}.
 *
 * <p>The class names come from the mod properties {@link FoxyFabricModReader} wrote into the
 * synthetic {@code neoforge.mods.toml}. That reader runs as an FML service, on a different class
 * loader, so a static field would be a different field by the time this read it.
 */
public final class FoxyEntrypoints {
    private static final Logger LOGGER = LoggerFactory.getLogger("Foxy");

    private FoxyEntrypoints() {}

    public static void invokeAll() {
        for (IModInfo mod : ModList.get().getMods()) {
            Object property = mod.getModProperties().get(FoxyFabricModReader.ENTRYPOINTS_PROPERTY);
            if (!(property instanceof String classNames) || classNames.isBlank()) {
                continue;
            }
            for (String className : classNames.split(",")) {
                invoke(mod.getModId(), className.trim());
            }
        }
    }

    private static void invoke(String modId, String className) {
        try {
            Object instance = Class.forName(className).getDeclaredConstructor().newInstance();
            if (instance instanceof ModInitializer main) {
                main.onInitialize();
            }
            if (FMLEnvironment.getDist() == Dist.CLIENT && instance instanceof ClientModInitializer client) {
                client.onInitializeClient();
            }
        } catch (ClassNotFoundException e) {
            // Fabric mods commonly list entrypoints for mods that are not installed, such as
            // ModMenu; those classes are simply absent and the integration is meant to be skipped.
            LOGGER.debug("Skipping absent entrypoint {} of {}", className, modId);
        } catch (ReflectiveOperationException | LinkageError e) {
            LOGGER.error("Failed to run entrypoint {} of {}", className, modId, e);
        }
    }
}
