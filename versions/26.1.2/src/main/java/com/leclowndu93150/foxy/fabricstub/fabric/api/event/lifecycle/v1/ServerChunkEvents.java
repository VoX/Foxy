package com.leclowndu93150.foxy.fabricstub.fabric.api.event.lifecycle.v1;

import com.leclowndu93150.foxy.fabricstub.fabric.api.event.Event;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;

public final class ServerChunkEvents {
    public static final Event<Load> CHUNK_LOAD = new Event<>();

    private ServerChunkEvents() {}

    @FunctionalInterface
    public interface Load {
        void onChunkLoad(ServerLevel level, LevelChunk chunk, boolean newChunk);
    }
}
