package net.fabricmc.api;

/**
 * Compile-time shadow, excluded from the built jar (see the root build script).
 *
 * <p>Foxy compiles against the real Voxy jar, whose entrypoint classes implement Fabric
 * interfaces, so javac has to be able to resolve those supertypes. Nothing calls through this
 * type: at runtime Voxy's references are rewritten to {@code com.leclowndu93150.foxy.fabricstub}
 * by {@code FoxyFabricModReader}, and shipping a real {@code net.fabricmc} package is exactly
 * what broke other mods' Fabric detection.
 */
public interface ModInitializer {
}
