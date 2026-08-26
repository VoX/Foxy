package com.leclowndu93150.foxy.fabricstub.fabric.api.event;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Fabric's event holder, reduced to what the mods Foxy loads actually use: they only ever
 * register listeners, and {@link com.leclowndu93150.foxy.loader.FoxyFabricApi} is what fires
 * them off the NeoForge event bus.
 */
public class Event<T> {
    private final List<T> listeners = new CopyOnWriteArrayList<>();

    public void register(T listener) {
        listeners.add(listener);
    }

    public void invoke(Consumer<? super T> action) {
        for (T listener : listeners) {
            action.accept(listener);
        }
    }
}
