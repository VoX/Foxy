package com.leclowndu93150.foxy.fabricstub.fabric.api.event.lifecycle.v1;

import com.leclowndu93150.foxy.fabricstub.fabric.api.event.Event;
import net.minecraft.server.MinecraftServer;

public final class ServerTickEvents {
    public static final Event<EndTick> END_SERVER_TICK = new Event<>();

    private ServerTickEvents() {}

    @FunctionalInterface
    public interface EndTick {
        void onEndTick(MinecraftServer server);
    }
}
