package com.leclowndu93150.foxy.fabricstub.fabric.api.client.command.v2;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;

/**
 * Voxy builds its {@code /voxy} tree with these, but Foxy registers an equivalent tree against
 * NeoForge's command system instead (see {@code FoxyCommands}), so nothing here is ever invoked.
 * It exists so Voxy's command classes link.
 */
public final class ClientCommands {
    private ClientCommands() {}

    public static LiteralArgumentBuilder<FabricClientCommandSource> literal(String name) {
        return LiteralArgumentBuilder.literal(name);
    }

    public static <T> RequiredArgumentBuilder<FabricClientCommandSource, T> argument(String name, ArgumentType<T> type) {
        return RequiredArgumentBuilder.argument(name, type);
    }
}
