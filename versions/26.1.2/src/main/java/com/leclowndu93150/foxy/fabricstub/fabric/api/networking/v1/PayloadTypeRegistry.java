package com.leclowndu93150.foxy.fabricstub.fabric.api.networking.v1;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * Fabric registers a payload's codec here and its handler separately via
 * {@code registerGlobalReceiver}; NeoForge wants both at once, inside its own registration
 * event. So nothing is registered eagerly — entries are collected and
 * {@link com.leclowndu93150.foxy.loader.FoxyFabricApi} pairs them up with their handlers when
 * NeoForge asks.
 *
 * <p>An interface, because Fabric declares it as one: callers compile their calls to
 * {@code InterfaceMethodref}, which will not resolve against a class.
 */
public interface PayloadTypeRegistry {
    record Entry(PacketFlow flow, CustomPacketPayload.Type<?> type, StreamCodec<?, ?> codec) {}

    static PayloadTypeRegistry clientboundPlay() {
        return Impl.CLIENTBOUND_PLAY;
    }

    static PayloadTypeRegistry serverboundPlay() {
        return Impl.SERVERBOUND_PLAY;
    }

    static List<Entry> entries() {
        return Impl.ENTRIES;
    }

    <T extends CustomPacketPayload> CustomPacketPayload.TypeAndCodec<RegistryFriendlyByteBuf, T> register(
            CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec);

    final class Impl implements PayloadTypeRegistry {
        private static final List<Entry> ENTRIES = new ArrayList<>();

        private static final PayloadTypeRegistry CLIENTBOUND_PLAY = new Impl(PacketFlow.CLIENTBOUND);
        private static final PayloadTypeRegistry SERVERBOUND_PLAY = new Impl(PacketFlow.SERVERBOUND);

        private final PacketFlow flow;

        private Impl(PacketFlow flow) {
            this.flow = flow;
        }

        @Override
        public <T extends CustomPacketPayload> CustomPacketPayload.TypeAndCodec<RegistryFriendlyByteBuf, T> register(
                CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
            ENTRIES.add(new Entry(flow, type, codec));
            @SuppressWarnings("unchecked")
            StreamCodec<RegistryFriendlyByteBuf, T> narrowed = (StreamCodec<RegistryFriendlyByteBuf, T>) codec;
            return new CustomPacketPayload.TypeAndCodec<>(type, narrowed);
        }
    }
}
