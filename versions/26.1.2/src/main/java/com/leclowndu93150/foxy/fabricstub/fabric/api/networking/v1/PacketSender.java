package com.leclowndu93150.foxy.fabricstub.fabric.api.networking.v1;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public interface PacketSender {
    void sendPacket(CustomPacketPayload payload);
}
