package com.leclowndu93150.foxy.fabricstub.fabric.api.client.networking.v1;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.HashMap;
import java.util.Map;

public final class ClientPlayNetworking {
    private static final Map<CustomPacketPayload.Type<?>, PlayPayloadHandler<?>> HANDLERS = new HashMap<>();

    private ClientPlayNetworking() {}

    public interface Context {
        Minecraft client();
    }

    @FunctionalInterface
    public interface PlayPayloadHandler<T extends CustomPacketPayload> {
        void receive(T payload, Context context);
    }

    public static <T extends CustomPacketPayload> boolean registerGlobalReceiver(
            CustomPacketPayload.Type<T> type, PlayPayloadHandler<T> handler) {
        return HANDLERS.putIfAbsent(type, handler) == null;
    }

    public static void send(CustomPacketPayload payload) {
        ClientPacketDistributor.sendToServer(payload);
    }

    @SuppressWarnings("unchecked")
    public static <T extends CustomPacketPayload> PlayPayloadHandler<T> handlerFor(CustomPacketPayload.Type<T> type) {
        return (PlayPayloadHandler<T>) HANDLERS.get(type);
    }
}
