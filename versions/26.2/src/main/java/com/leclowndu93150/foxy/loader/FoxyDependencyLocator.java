package com.leclowndu93150.foxy.loader;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.neoforged.fml.jarcontents.JarContents;
import net.neoforged.fml.loading.moddiscovery.readers.JarModsDotTomlModFileReader;
import net.neoforged.neoforgespi.locating.IDependencyLocator;
import net.neoforged.neoforgespi.locating.IDiscoveryPipeline;
import net.neoforged.neoforgespi.locating.IModFile;
import net.neoforged.neoforgespi.locating.ModFileDiscoveryAttributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FoxyDependencyLocator implements IDependencyLocator {
    private static final Logger LOGGER = LoggerFactory.getLogger("Foxy");

    /**
     * A nested jar pulled out of a Fabric mod, along with the packages it would contribute.
     */
    private record Candidate(String name, Path path, Set<String> packages) {}

    @Override
    public void scanMods(List<IModFile> loadedMods, IDiscoveryPipeline pipeline) {
        List<IModFile> fabricMods = new ArrayList<>();
        List<IModFile> otherMods = new ArrayList<>();
        for (IModFile mod : loadedMods) {
            // Multiloader mods ship a fabric.mod.json next to their neoforge.mods.toml; NeoForge
            // already loads those and their nested jars. Only jars Foxy itself converted from
            // Fabric carry nested jars nobody else has handled.
            if (FoxyFabricModReader.isFoxyPatched(mod.getContents().getPrimaryPath())) {
                fabricMods.add(mod);
            } else {
                otherMods.add(mod);
            }
        }
        if (fabricMods.isEmpty()) {
            return;
        }

        List<Candidate> candidates = new ArrayList<>();
        for (IModFile mod : fabricMods) {
            candidates.addAll(extractNestedJars(mod.getContents()));
        }
        if (candidates.isEmpty()) {
            return;
        }

        Set<String> wanted = new HashSet<>();
        for (Candidate candidate : candidates) {
            wanted.addAll(candidate.packages());
        }
        Map<String, String> providers = findProviders(otherMods, wanted);
        LOGGER.info("Extracted {} bundled jar(s) contributing {} package(s); {} already provided by {} other loaded mod(s)",
                candidates.size(), wanted.size(), providers.size(), otherMods.size());

        exposeNatives(candidates);

        for (Candidate candidate : candidates) {
            String clash = firstProvided(candidate, providers);
            if (clash != null) {
                // Adding it anyway would put the same package in two modules, which the module
                // system rejects outright. NeoForge's own JarJar dedup misses this when the two
                // copies resolve to different module names.
                LOGGER.info("Skipping bundled {}: package {} is already provided by {}",
                        candidate.name(), clash.replace('/', '.'), providers.get(clash));
                deleteQuietly(candidate.path());
                continue;
            }
            try {
                pipeline.addModFile(IModFile.create(
                        JarContents.ofPath(candidate.path()),
                        JarModsDotTomlModFileReader::manifestParser,
                        IModFile.Type.GAMELIBRARY,
                        ModFileDiscoveryAttributes.DEFAULT));
            } catch (IOException e) {
                LOGGER.error("Failed to add bundled {}", candidate.name(), e);
            }
        }
    }

    /**
     * Makes the native libraries inside {@code *-natives-<os>} nested jars loadable.
     *
     * <p>On Fabric every jar shares one class loader, so LWJGL's classpath extraction
     * finds a bundled {@code lwjgl_zstd.dll} on its own. Under FML each nested jar is its
     * own module, and {@code Library.loadSystem}'s resource lookup cannot see another
     * module's files — the bindings class then dies with {@code UnsatisfiedLinkError},
     * which on a voxy worker thread is a SILENT death (the no-LODs / hung-disconnect
     * bug; upstream issue #4). The shared libraries are copied to a stable directory and
     * that directory is appended to {@code org.lwjgl.librarypath}, which LWJGL checks
     * FIRST — set here, in the locator phase, long before any mod code touches LWJGL.
     */
    private static void exposeNatives(List<Candidate> candidates) {
        String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        String ext = os.contains("win") ? ".dll" : os.contains("mac") ? ".dylib" : ".so";
        Path dir = net.neoforged.fml.loading.FMLPaths.GAMEDIR.get().resolve(".foxy").resolve("natives");
        List<String> extracted = new ArrayList<>();
        for (Candidate candidate : candidates) {
            if (!candidate.name().contains("-natives-")) {
                continue;
            }
            try (JarContents jar = JarContents.ofPath(candidate.path())) {
                List<String> libs = new ArrayList<>();
                jar.visitContent((name, resource) -> {
                    if (name.endsWith(ext)) libs.add(name);
                });
                for (String lib : libs) {
                    String base = lib.substring(lib.lastIndexOf('/') + 1);
                    Files.createDirectories(dir);
                    try (InputStream is = jar.openFile(lib)) {
                        Files.copy(is, dir.resolve(base), StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException locked) {
                        // A previous instance may still hold the dll mapped (Windows lock);
                        // the existing copy is the same version — keep it.
                        if (!Files.exists(dir.resolve(base))) throw locked;
                    }
                    extracted.add(base);
                }
            } catch (IOException e) {
                LOGGER.error("Failed to expose natives from bundled {}", candidate.name(), e);
            }
        }
        if (!extracted.isEmpty()) {
            String prop = System.getProperty("org.lwjgl.librarypath", "");
            String path = dir.toAbsolutePath().toString();
            if (!prop.contains(path)) {
                System.setProperty("org.lwjgl.librarypath",
                        prop.isEmpty() ? path : prop + java.io.File.pathSeparator + path);
            }
            // LWJGL's Configuration snapshots org.lwjgl.librarypath at class init, which the
            // loading-window bootstrap triggers BEFORE this locator runs — so the property
            // alone is not enough. FoxyNeoForge picks this up and calls the runtime
            // Configuration.LIBRARY_PATH.set(...), which loadSystem consults at load time.
            System.setProperty("foxy.natives.dir", path);
            LOGGER.info("Exposed {} bundled native librar{} for LWJGL at {}: {}",
                    extracted.size(), extracted.size() == 1 ? "y" : "ies", path, extracted);
        }
    }

    private static String firstProvided(Candidate candidate, Map<String, String> providers) {
        for (String pkg : candidate.packages()) {
            if (providers.containsKey(pkg)) {
                return pkg;
            }
        }
        return null;
    }

    /**
     * Maps each of {@code wanted} that some already-loaded mod contains to that mod's file name.
     */
    private static Map<String, String> findProviders(List<IModFile> mods, Set<String> wanted) {
        Map<String, String> providers = new HashMap<>();
        if (wanted.isEmpty()) {
            return providers;
        }
        for (IModFile mod : mods) {
            if (providers.size() == wanted.size()) {
                break;
            }
            try {
                mod.getContents().visitContent((name, resource) -> {
                    String pkg = packageOf(name);
                    if (pkg != null && wanted.contains(pkg)) {
                        providers.putIfAbsent(pkg, mod.getFileName());
                    }
                });
            } catch (RuntimeException e) {
                LOGGER.debug("Could not scan {} for duplicate packages", mod.getFileName(), e);
            }
        }
        return providers;
    }

    private static List<Candidate> extractNestedJars(JarContents contents) {
        List<Candidate> candidates = new ArrayList<>();
        JsonArray jars = readJarsArray(contents);
        if (jars == null) {
            return candidates;
        }
        for (JsonElement entry : jars) {
            if (!entry.isJsonObject()) continue;
            JsonElement file = entry.getAsJsonObject().get("file");
            if (file == null || !file.isJsonPrimitive()) continue;
            String innerPath = file.getAsString();
            try {
                Path extracted = extract(contents, innerPath);
                if (extracted != null) {
                    String name = innerPath.substring(innerPath.lastIndexOf('/') + 1);
                    candidates.add(new Candidate(name, extracted, packagesOf(extracted)));
                }
            } catch (IOException ignored) {}
        }
        return candidates;
    }

    private static Set<String> packagesOf(Path jar) {
        Set<String> packages = new HashSet<>();
        try (JarContents contents = JarContents.ofPath(jar)) {
            contents.visitContent((name, resource) -> {
                String pkg = packageOf(name);
                if (pkg != null) {
                    packages.add(pkg);
                }
            });
        } catch (IOException | RuntimeException ignored) {}
        return packages;
    }

    /**
     * Directory of a class file, or null for anything that does not contribute a package:
     * resources, metadata, and the module descriptor itself.
     */
    private static String packageOf(String name) {
        if (!name.endsWith(".class") || name.startsWith("META-INF/")) {
            return null;
        }
        // A root module-info.class has no slash and is filtered out here too.
        int slash = name.lastIndexOf('/');
        return slash < 0 ? null : name.substring(0, slash);
    }

    private static JsonArray readJarsArray(JarContents contents) {
        try (InputStream is = contents.openFile("fabric.mod.json")) {
            if (is == null) return null;
            JsonObject obj = JsonParser.parseReader(new InputStreamReader(is, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonElement jars = obj.get("jars");
            return jars != null && jars.isJsonArray() ? jars.getAsJsonArray() : null;
        } catch (IOException | IllegalStateException e) {
            return null;
        }
    }

    private static Path extract(JarContents contents, String innerPath) throws IOException {
        if (!contents.containsFile(innerPath)) {
            return null;
        }
        String name = innerPath.substring(innerPath.lastIndexOf('/') + 1);
        Path out = Files.createTempFile("foxy-jij-", "-" + name);
        out.toFile().deleteOnExit();
        try (InputStream is = contents.openFile(innerPath)) {
            Files.copy(is, out, StandardCopyOption.REPLACE_EXISTING);
        }
        return out;
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {}
    }

    @Override
    public int getPriority() {
        return LOWEST_SYSTEM_PRIORITY;
    }
}
