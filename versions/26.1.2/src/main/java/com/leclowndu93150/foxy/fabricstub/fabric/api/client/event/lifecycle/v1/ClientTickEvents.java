package com.leclowndu93150.foxy.fabricstub.fabric.api.client.event.lifecycle.v1;

import com.leclowndu93150.foxy.fabricstub.fabric.api.event.Event;
import net.minecraft.client.Minecraft;

public final class ClientTickEvents {
    public static final Event<EndTick> END_CLIENT_TICK = new Event<>();

    private ClientTickEvents() {}

    @FunctionalInterface
    public interface EndTick {
        void onEndTick(Minecraft client);
    }
}
