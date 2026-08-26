package com.leclowndu93150.foxy.fabricstub.fabric.api.client.command.v2;

import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

public interface FabricClientCommandSource extends SharedSuggestionProvider {
    void sendError(Component message);
}
