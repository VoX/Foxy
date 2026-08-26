package com.leclowndu93150.foxy.loader;

import com.leclowndu93150.foxy.fabricstub.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import com.leclowndu93150.foxy.fabricstub.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import com.leclowndu93150.foxy.fabricstub.fabric.api.client.networking.v1.ClientPlayNetworking;
import com.leclowndu93150.foxy.fabricstub.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import com.leclowndu93150.foxy.fabricstub.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import com.leclowndu93150.foxy.fabricstub.fabric.api.event.lifecycle.v1.ServerTickEvents;
import com.leclowndu93150.foxy.fabricstub.fabric.api.networking.v1.PacketSender;
import com.leclowndu93150.foxy.fabricstub.fabric.api.networking.v1.PayloadTypeRegistry;
import com.leclowndu93150.foxy.fabricstub.fabric.api.networking.v1.ServerPlayConnectionEvents;
import com.leclowndu93150.foxy.fabricstub.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadHandler;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fires the Fabric events in {@code fabricstub} from their NeoForge equivalents, and completes
 * the deferred payload registrations {@link PayloadTypeRegistry} collected.
 */
public final class FoxyFabricApi {
    private FoxyFabricApi() {}

    public static void init(IEventBus modBus) {
        modBus.addListener(FoxyFabricApi::onRegisterPayloads);

        NeoForge.EVENT_BUS.addListener((ServerStartedEvent e) ->
                ServerLifecycleEvents.SERVER_STARTED.invoke(l -> l.onServerStarted(e.getServer())));
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent e) ->
                ServerLifecycleEvents.SERVER_STOPPING.invoke(l -> l.onServerStopping(e.getServer())));
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) ->
                ServerTickEvents.END_SERVER_TICK.invoke(l -> l.onEndTick(e.getServer())));

        NeoForge.EVENT_BUS.addListener((ChunkEvent.Load e) -> {
            if (e.getLevel() instanceof ServerLevel level && e.getChunk() instanceof LevelChunk chunk) {
                ServerChunkEvents.CHUNK_LOAD.invoke(l -> l.onChunkLoad(level, chunk, e.isNewChunk()));
            }
        });

        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer player) {
                MinecraftServer server = player.level().getServer();
                PacketSender sender = payload -> ServerPlayNetworking.send(player, payload);
                ServerPlayConnectionEvents.JOIN.invoke(l -> l.onPlayReady(player.connection, sender, server));
            }
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer player) {
                MinecraftServer server = player.level().getServer();
                ServerPlayConnectionEvents.DISCONNECT.invoke(l -> l.onPlayDisconnect(player.connection, server));
            }
        });

        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            FoxyFabricApiClient.init();
        }
    }

    /**
     * NeoForge takes a payload's codec and its handler together, so both halves of the Fabric
     * registration have to be in hand before this runs. Foxy invokes the Fabric entrypoints from
     * common setup, which is early enough.
     */
    private static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1").optional();

        Map<Identifier, List<PayloadTypeRegistry.Entry>> byId = new LinkedHashMap<>();
        for (PayloadTypeRegistry.Entry entry : PayloadTypeRegistry.entries()) {
            byId.computeIfAbsent(entry.type().id(), id -> new ArrayList<>()).add(entry);
        }
        for (List<PayloadTypeRegistry.Entry> flows : byId.values()) {
            register(registrar, flows);
        }
    }

    /**
     * Fabric keeps a registry per direction, so a bidirectional payload is registered once in each
     * and both registrations are entries here. NeoForge keys a single registry by payload id and
     * refuses a second registration of the same id, so the two have to be folded into one
     * {@code playBidirectional} call.
     */
    @SuppressWarnings("unchecked")
    private static <T extends CustomPacketPayload> void register(PayloadRegistrar registrar, List<PayloadTypeRegistry.Entry> flows) {
        CustomPacketPayload.Type<T> type = (CustomPacketPayload.Type<T>) flows.getFirst().type();
        StreamCodec<? super RegistryFriendlyByteBuf, T> codec =
                (StreamCodec<? super RegistryFriendlyByteBuf, T>) flows.getFirst().codec();

        boolean clientbound = flows.stream().anyMatch(e -> e.flow() == PacketFlow.CLIENTBOUND);
        boolean serverbound = flows.stream().anyMatch(e -> e.flow() == PacketFlow.SERVERBOUND);

        if (clientbound && serverbound) {
            registrar.playBidirectional(type, codec, serverHandler(type), clientHandler(type));
        } else if (clientbound) {
            registrar.playToClient(type, codec, clientHandler(type));
        } else {
            registrar.playToServer(type, codec, serverHandler(type));
        }
    }

    private static <T extends CustomPacketPayload> IPayloadHandler<T> clientHandler(CustomPacketPayload.Type<T> type) {
        return (payload, context) -> FoxyFabricApiClient.receive(type, payload);
    }

    private static <T extends CustomPacketPayload> IPayloadHandler<T> serverHandler(CustomPacketPayload.Type<T> type) {
        return (payload, context) -> {
            ServerPlayNetworking.PlayPayloadHandler<T> handler = ServerPlayNetworking.handlerFor(type);
            if (handler == null || !(context.player() instanceof ServerPlayer player)) {
                return;
            }
            handler.receive(payload, new ServerPlayNetworking.Context() {
                @Override
                public MinecraftServer server() {
                    return player.level().getServer();
                }

                @Override
                public ServerPlayer player() {
                    return player;
                }
            });
        };
    }
}
