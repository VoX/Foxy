package com.leclowndu93150.foxy.fabricstub.fabric.api.networking.v1;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;

public final class ServerPlayNetworking {
    private static final Map<CustomPacketPayload.Type<?>, PlayPayloadHandler<?>> HANDLERS = new HashMap<>();

    private ServerPlayNetworking() {}

    public interface Context {
        MinecraftServer server();

        ServerPlayer player();
    }

    @FunctionalInterface
    public interface PlayPayloadHandler<T extends CustomPacketPayload> {
        void receive(T payload, Context context);
    }

    public static <T extends CustomPacketPayload> boolean registerGlobalReceiver(
            CustomPacketPayload.Type<T> type, PlayPayloadHandler<T> handler) {
        return HANDLERS.putIfAbsent(type, handler) == null;
    }

    public static void send(ServerPlayer player, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    @SuppressWarnings("unchecked")
    public static <T extends CustomPacketPayload> PlayPayloadHandler<T> handlerFor(CustomPacketPayload.Type<T> type) {
        return (PlayPayloadHandler<T>) HANDLERS.get(type);
    }
}
