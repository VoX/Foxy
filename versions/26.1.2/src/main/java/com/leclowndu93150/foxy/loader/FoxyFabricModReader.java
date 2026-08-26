package com.leclowndu93150.foxy.loader;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.neoforged.fml.jarcontents.JarContents;
import net.neoforged.fml.loading.moddiscovery.readers.JarModsDotTomlModFileReader;
import net.neoforged.neoforgespi.locating.IModFile;
import net.neoforged.neoforgespi.locating.IModFileReader;
import net.neoforged.neoforgespi.locating.ModFileDiscoveryAttributes;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class FoxyFabricModReader implements IModFileReader {
    private static final String MODS_TOML = "META-INF/neoforge.mods.toml";

    /**
     * The mods Foxy knows how to bridge. This reader sees every jar in discovery, including
     * Fabric-only libraries other mods nest inside themselves; converting those into javafml
     * mods hijacks them from the loader that was already handling them correctly, so the set is
     * an allowlist rather than "anything with a fabric.mod.json".
     */
    private static final Set<String> SUPPORTED_MOD_IDS = Set.of("voxy", "voxy-extra", "voxyworldgenv2");

    /**
     * Fabric mod ids that have no NeoForge counterpart to depend on: the loader itself, and
     * Fabric API, whose surface Foxy supplies from {@code fabricstub} instead.
     */
    private static final Set<String> IGNORED_DEPENDENCIES = Set.of("fabricloader", "fabric-api", "fabric", "java");

    /** Fabric mod ids whose NeoForge builds use a different id. */
    private static final Map<String, String> DEPENDENCY_ID_MAP = Map.of("cloth-config", "cloth_config");

    /** Sodium takes a single class name here on NeoForge, against a list on Fabric. */
    private static final String SODIUM_CONFIG_ENTRYPOINT = "sodium:config_api_user";

    /**
     * Mod property carrying the Fabric {@code main} and {@code client} entrypoint classes.
     * This reader runs as an FML service, on a different class loader than the mod itself, so
     * the two cannot share a static field; the mod properties are read back from {@code ModList}.
     */
    static final String ENTRYPOINTS_PROPERTY = "foxy:entrypoints";

    private static final String FABRIC_PREFIX = "net/fabricmc/";
    private static final String STUB_PREFIX = "com/leclowndu93150/foxy/fabricstub/";

    /**
     * Jars this reader converted from Fabric metadata. Only these carry Fabric-style nested
     * jars that NeoForge does not already handle, so only these may be JiJ-extracted.
     */
    private static final Set<Path> PATCHED = ConcurrentHashMap.newKeySet();

    static boolean isFoxyPatched(@Nullable Path path) {
        return path != null && PATCHED.contains(path);
    }

    private static List<String> readEntrypoints(JsonObject fmj) {
        List<String> result = new ArrayList<>();
        for (String kind : List.of("main", "client")) {
            result.addAll(readEntrypoints(fmj, kind));
        }
        return result;
    }

    private static List<String> readEntrypoints(JsonObject fmj, String kind) {
        List<String> result = new ArrayList<>();
        JsonElement entrypoints = fmj.get("entrypoints");
        if (entrypoints == null || !entrypoints.isJsonObject()) {
            return result;
        }
        JsonElement list = entrypoints.getAsJsonObject().get(kind);
        if (list == null || !list.isJsonArray()) {
            return result;
        }
        for (JsonElement el : list.getAsJsonArray()) {
            if (el.isJsonPrimitive()) {
                result.add(el.getAsString());
            } else if (el.isJsonObject() && el.getAsJsonObject().has("value")) {
                result.add(el.getAsJsonObject().get("value").getAsString());
            }
        }
        return result;
    }

    @Override
    public @Nullable IModFile read(JarContents jar, ModFileDiscoveryAttributes attributes) {
        if (!jar.containsFile("fabric.mod.json") || jar.containsFile(MODS_TOML)) {
            return null;
        }

        JsonObject fmj = readFabricModJson(jar);
        String modId = fmj == null ? null : string(fmj, "id", null);
        if (modId == null || !SUPPORTED_MOD_IDS.contains(modId)) {
            return null;
        }

        try {
            Path patched = patchJar(jar.getPrimaryPath(), fmj);
            PATCHED.add(patched);
            return JarModsDotTomlModFileReader.createModFile(JarContents.ofPath(patched), attributes);
        } catch (IOException e) {
            throw new RuntimeException("Foxy: failed to patch " + modId + " jar", e);
        }
    }

    private static Path patchJar(Path original, JsonObject fmj) throws IOException {
        String modId = string(fmj, "id", "voxy");
        Path patched = Files.createTempFile("foxy-patched-" + modId + "-", ".jar");
        patched.toFile().deleteOnExit();

        Set<String> mixinConfigs = new HashSet<>(readMixins(fmj));
        try (ZipFile in = new ZipFile(original.toFile());
             ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(patched))) {
            var entries = in.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.getName().equals(MODS_TOML)) continue;
                out.putNextEntry(new ZipEntry(entry.getName()));
                if (!entry.isDirectory()) {
                    try (InputStream is = in.getInputStream(entry)) {
                        if (entry.getName().endsWith(".class")) {
                            out.write(remapFabricRefs(is.readAllBytes()));
                        } else if (mixinConfigs.contains(entry.getName())) {
                            out.write(filterMixinConfig(in, original, is.readAllBytes()));
                        } else {
                            is.transferTo(out);
                        }
                    }
                }
                out.closeEntry();
            }
            out.putNextEntry(new ZipEntry(MODS_TOML));
            out.write(buildModsToml(fmj).getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        return patched;
    }

    /**
     * Points the mod's {@code net/fabricmc} references at Foxy's stubs, which live under Foxy's
     * own package.
     *
     * <p>The stubs used to sit in {@code net.fabricmc} so this rewrite was unnecessary, but every
     * mod shares one class loader: any multiloader mod that detects Fabric by looking up
     * {@code net.fabricmc.loader.api.FabricLoader} found Foxy's copy, concluded it was on Fabric,
     * and then failed on a stub Foxy does not ship. Keeping the name out of the game entirely is
     * what makes those probes go back to answering "no".
     */
    private static byte[] remapFabricRefs(byte[] clazz) {
        ClassReader reader = new ClassReader(clazz);
        ClassWriter writer = new ClassWriter(0);
        reader.accept(new ClassRemapper(writer, new Remapper() {
            @Override
            public String map(String internalName) {
                return internalName.startsWith(FABRIC_PREFIX)
                        ? STUB_PREFIX + internalName.substring(FABRIC_PREFIX.length())
                        : internalName;
            }
        }), 0);
        return writer.toByteArray();
    }

    /**
     * Drops the mixins whose targets are not installed. Fabric mods use mixins for optional
     * integrations with other mods, but a {@code @Mixin} on a class that does not exist fails the
     * whole config, and these configs are {@code "required": true} — so one absent integration
     * would take the mod down. On Fabric this never comes up, because a mod with no NeoForge build
     * is not something a Fabric user can have half-installed.
     */
    private static byte[] filterMixinConfig(ZipFile jar, Path jarPath, byte[] json) {
        JsonObject config = JsonParser.parseString(new String(json, StandardCharsets.UTF_8)).getAsJsonObject();
        String mixinPackage = string(config, "package", "");
        if (mixinPackage.isEmpty()) {
            return json;
        }
        for (String side : List.of("mixins", "client", "server")) {
            JsonElement list = config.get(side);
            if (list == null || !list.isJsonArray()) continue;
            JsonArray kept = new JsonArray();
            for (JsonElement el : list.getAsJsonArray()) {
                if (!el.isJsonPrimitive()
                        || FoxyMixinFilter.canApply(jar, jarPath, mixinPackage, el.getAsString())) {
                    kept.add(el);
                }
            }
            config.add(side, kept);
        }
        return config.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String buildModsToml(JsonObject fmj) {
        String modId = neoforgeId(string(fmj, "id", "voxy"));
        String version = string(fmj, "version", "0.0.0");
        String name = string(fmj, "name", modId);
        String description = string(fmj, "description", "");
        String icon = string(fmj, "icon", null);
        String authors = joinAuthors(fmj);
        String commit = readCommit(fmj);

        StringBuilder sb = new StringBuilder();
        sb.append("modLoader = \"javafml\"\n");
        sb.append("loaderVersion = \"[4,)\"\n");
        sb.append("license = \"").append(esc(string(fmj, "license", "All-Rights-Reserved"))).append("\"\n");
        if (!authors.isEmpty()) {
            sb.append("authors = \"").append(esc(authors)).append("\"\n");
        }
        sb.append("[[mods]]\n");
        sb.append("modId = \"").append(esc(modId)).append("\"\n");
        sb.append("version = \"").append(esc(version)).append("\"\n");
        sb.append("displayName = \"").append(esc(name)).append("\"\n");
        if (!description.isEmpty()) {
            sb.append("description = \"").append(esc(description)).append("\"\n");
        }
        if (icon != null) {
            sb.append("logoFile = \"").append(esc(icon)).append("\"\n");
        }
        sb.append("[modproperties.").append(modId).append("]\n");
        sb.append("commit = \"").append(esc(commit)).append("\"\n");
        List<String> entrypoints = readEntrypoints(fmj);
        if (!entrypoints.isEmpty()) {
            sb.append('"').append(ENTRYPOINTS_PROPERTY).append("\" = \"")
                    .append(esc(String.join(",", entrypoints))).append("\"\n");
        }
        // Sodium's NeoForge build looks for its config API users in mod properties, where its
        // Fabric build reads an entrypoint, so this one carries over rather than being lost.
        List<String> sodiumConfig = readEntrypoints(fmj, SODIUM_CONFIG_ENTRYPOINT);
        if (!sodiumConfig.isEmpty()) {
            sb.append('"').append(SODIUM_CONFIG_ENTRYPOINT).append("\" = \"")
                    .append(esc(sodiumConfig.getFirst())).append("\"\n");
        }
        sb.append("[[dependencies.").append(modId).append("]]\n");
        sb.append("modId = \"neoforge\"\ntype = \"required\"\nversionRange = \"[4,)\"\nordering = \"NONE\"\nside = \"BOTH\"\n");
        for (String dependency : readDependencies(fmj)) {
            sb.append("[[dependencies.").append(modId).append("]]\n");
            sb.append("modId = \"").append(esc(dependency)).append("\"\n");
            sb.append("type = \"required\"\nversionRange = \"*\"\nordering = \"BEFORE\"\nside = \"BOTH\"\n");
        }
        for (String mixin : readMixins(fmj)) {
            sb.append("[[mixins]]\nconfig = \"").append(esc(mixin)).append("\"\n");
        }
        return sb.toString();
    }

    /**
     * The mod's Fabric dependencies, translated to the ids their NeoForge builds use. Minecraft
     * and the loader entries are dropped: FML already checks those, and Fabric API has no
     * NeoForge build to point at.
     */
    private static List<String> readDependencies(JsonObject fmj) {
        List<String> result = new ArrayList<>();
        JsonElement depends = fmj.get("depends");
        if (depends == null || !depends.isJsonObject()) {
            return result;
        }
        for (String id : depends.getAsJsonObject().keySet()) {
            if (IGNORED_DEPENDENCIES.contains(id) || "minecraft".equals(id)) continue;
            result.add(neoforgeId(DEPENDENCY_ID_MAP.getOrDefault(id, id)));
        }
        return result;
    }

    private static List<String> readMixins(JsonObject fmj) {
        List<String> result = new ArrayList<>();
        JsonElement mixins = fmj.get("mixins");
        if (mixins != null && mixins.isJsonArray()) {
            for (JsonElement el : mixins.getAsJsonArray()) {
                if (el.isJsonPrimitive()) {
                    result.add(el.getAsString());
                } else if (el.isJsonObject() && el.getAsJsonObject().has("config")) {
                    result.add(el.getAsJsonObject().get("config").getAsString());
                }
            }
        }
        return result;
    }

    private static String readCommit(JsonObject fmj) {
        JsonElement custom = fmj.get("custom");
        if (custom != null && custom.isJsonObject()) {
            JsonElement commit = custom.getAsJsonObject().get("commit");
            if (commit != null && commit.isJsonPrimitive()) {
                String c = commit.getAsString();
                if (!c.startsWith("$")) return c;
            }
        }
        return "foxycompat0000000000000000000000000000000";
    }

    private static String joinAuthors(JsonObject fmj) {
        JsonElement authors = fmj.get("authors");
        if (authors == null || !authors.isJsonArray()) return "";
        StringJoiner sj = new StringJoiner(", ");
        for (JsonElement el : authors.getAsJsonArray()) {
            if (el.isJsonPrimitive()) {
                sj.add(el.getAsString());
            } else if (el.isJsonObject() && el.getAsJsonObject().has("name")) {
                sj.add(el.getAsJsonObject().get("name").getAsString());
            }
        }
        return sj.toString();
    }

    @Nullable
    private static JsonObject readFabricModJson(JarContents jar) {
        try (InputStream is = jar.openFile("fabric.mod.json")) {
            if (is == null) return null;
            return JsonParser.parseReader(new InputStreamReader(is, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException | IllegalStateException e) {
            return null;
        }
    }

    private static String string(JsonObject obj, String key, String fallback) {
        JsonElement el = obj.get(key);
        if (el == null || !el.isJsonPrimitive()) return fallback;
        String s = el.getAsString();
        return s.startsWith("$") ? fallback : s;
    }

    /**
     * Bends a Fabric mod id into the shape NeoForge accepts. Fabric allows hyphens and NeoForge
     * does not, so {@code voxy-extra} would otherwise be rejected outright as an invalid mod file.
     */
    private static String neoforgeId(String fabricId) {
        StringBuilder sb = new StringBuilder(fabricId.length());
        for (char c : fabricId.toCharArray()) {
            sb.append((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '.' ? c
                    : (c >= 'A' && c <= 'Z') ? Character.toLowerCase(c) : '_');
        }
        return sb.toString();
    }

    private static String esc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    @Override
    public int getPriority() {
        return HIGHEST_SYSTEM_PRIORITY;
    }
}
