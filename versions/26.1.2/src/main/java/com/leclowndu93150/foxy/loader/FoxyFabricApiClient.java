package com.leclowndu93150.foxy.loader;

import com.leclowndu93150.foxy.fabricstub.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import com.leclowndu93150.foxy.fabricstub.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import com.leclowndu93150.foxy.fabricstub.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * The client half of {@link FoxyFabricApi}, kept separate so a dedicated server never loads a
 * class that touches {@link Minecraft}.
 */
final class FoxyFabricApiClient {
    private FoxyFabricApiClient() {}

    static void init() {
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) ->
                ClientTickEvents.END_CLIENT_TICK.invoke(l -> l.onEndTick(Minecraft.getInstance())));

        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> {
            if (e.getPlayer() != null) {
                ClientPlayConnectionEvents.DISCONNECT.invoke(
                        l -> l.onPlayDisconnect(e.getPlayer().connection, Minecraft.getInstance()));
            }
        });
    }

    static <T extends CustomPacketPayload> void receive(CustomPacketPayload.Type<T> type, T payload) {
        ClientPlayNetworking.PlayPayloadHandler<T> handler = ClientPlayNetworking.handlerFor(type);
        if (handler != null) {
            handler.receive(payload, Minecraft::getInstance);
        }
    }
}
