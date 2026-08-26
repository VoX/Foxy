package com.leclowndu93150.foxy.fabricstub.fabric.api.client.command.v2;

import com.leclowndu93150.foxy.fabricstub.fabric.api.event.Event;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandBuildContext;

@FunctionalInterface
public interface ClientCommandRegistrationCallback {
    Event<ClientCommandRegistrationCallback> EVENT = new Event<>();

    void register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandBuildContext context);
}
