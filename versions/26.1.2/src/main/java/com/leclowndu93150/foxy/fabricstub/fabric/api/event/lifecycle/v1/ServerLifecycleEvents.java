package com.leclowndu93150.foxy.fabricstub.fabric.api.event.lifecycle.v1;

import com.leclowndu93150.foxy.fabricstub.fabric.api.event.Event;
import net.minecraft.server.MinecraftServer;

public final class ServerLifecycleEvents {
    public static final Event<ServerStarted> SERVER_STARTED = new Event<>();
    public static final Event<ServerStopping> SERVER_STOPPING = new Event<>();

    private ServerLifecycleEvents() {}

    @FunctionalInterface
    public interface ServerStarted {
        void onServerStarted(MinecraftServer server);
    }

    @FunctionalInterface
    public interface ServerStopping {
        void onServerStopping(MinecraftServer server);
    }
}
