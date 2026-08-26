package com.leclowndu93150.foxy;

import com.leclowndu93150.foxy.loader.FoxyEntrypoints;
import com.leclowndu93150.foxy.loader.FoxyFabricApi;
import me.cortex.voxy.client.DebugEntries;
import me.cortex.voxy.commonImpl.VoxyCommon;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod("foxy")
public class FoxyNeoForge {

    public FoxyNeoForge(IEventBus modBus) {
        installThreadDeathReporter();
        exposeBundledNatives();
        FoxyFabricApi.init(modBus);
        modBus.addListener(this::onCommonSetup);
        modBus.addListener(this::onClientSetup);
        NeoForge.EVENT_BUS.addListener(this::onRegisterClientCommands);
    }

    /**
     * Fabric runs a mod's initializers from inside the {@code Minecraft} constructor, so they may
     * touch {@code Minecraft.getInstance()}. NeoForge constructs mods before that instance exists,
     * so the entrypoints wait for common setup — the first mod bus event of the loading phase that
     * runs once the game is up, and still ahead of {@code RegisterPayloadHandlersEvent}, which
     * needs the payload types the entrypoints register. Enqueued so they run on the main thread,
     * as they do on Fabric.
     */
    /**
     * A daemon worker dying by uncaught throwable is INVISIBLE in this launcher setup
     * (neither stdout nor stderr is captured), which is how the natives bug shipped as
     * a silent no-LODs/hung-disconnect failure. Chain a default handler that logs.
     */
    private static void installThreadDeathReporter() {
        Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            org.slf4j.LoggerFactory.getLogger("Foxy")
                    .error("Uncaught exception killed thread [{}]", t.getName(), e);
            if (prev != null) prev.uncaughtException(t, e);
        });
    }

    /**
     * Completes the bundled-natives fix (see FoxyDependencyLocator#exposeNatives): the
     * locator extracted the {@code *-natives-<os>} libraries, but LWJGL's Configuration
     * snapshotted {@code org.lwjgl.librarypath} before the locator ran, so the override
     * must go through the runtime API. Reflection keeps this tolerant of LWJGL being
     * absent (dedicated server) or module-restricted.
     */
    private static void exposeBundledNatives() {
        String dir = System.getProperty("foxy.natives.dir");
        if (dir == null || dir.isBlank()) {
            return;
        }
        try {
            Class<?> config = Class.forName("org.lwjgl.system.Configuration");
            Object libraryPath = config.getField("LIBRARY_PATH").get(null);
            Object current = config.getMethod("get").invoke(libraryPath);
            String merged = current == null || current.toString().isBlank()
                    ? dir : current + java.io.File.pathSeparator + dir;
            if (current == null || !current.toString().contains(dir)) {
                config.getMethod("set", Object.class).invoke(libraryPath, merged);
            }
            org.slf4j.LoggerFactory.getLogger("Foxy")
                    .info("LWJGL library path now includes bundled natives: {}", merged);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            org.slf4j.LoggerFactory.getLogger("Foxy")
                    .error("Could not apply bundled-natives library path", e);
        }
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(FoxyEntrypoints::invokeAll);
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        DebugEntries.init();
    }

    private void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        if (VoxyCommon.isAvailable()) {
            FoxyCommands.register(event.getDispatcher());
        }
    }
}
