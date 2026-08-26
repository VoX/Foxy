package com.leclowndu93150.foxy.loader;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * Decides which of a Fabric mod's mixins can apply here.
 *
 * <p>Fabric mods routinely mix into other mods for optional integrations. Those configs are
 * {@code "required": true}, and a {@code @Mixin} naming a class that does not exist fails the
 * whole config rather than just that entry — so an integration with a mod that has no NeoForge
 * build takes the host mod down with it. Anything targeting the game or the mod's own classes is
 * kept; anything targeting a class no installed jar provides is dropped.
 */
final class FoxyMixinFilter {
    private static final Logger LOGGER = LoggerFactory.getLogger("Foxy");

    private FoxyMixinFilter() {}

    static boolean canApply(ZipFile jar, Path jarPath, String mixinPackage, String mixinName) {
        String entry = (mixinPackage + "." + mixinName).replace('.', '/') + ".class";
        Set<String> targets = readTargets(jar, entry);
        if (targets.isEmpty()) {
            return true;
        }
        for (String target : targets) {
            if (!isAvailable(jar, jarPath, target)) {
                LOGGER.info("Skipping mixin {}: target {} is not installed", mixinName, target.replace('/', '.'));
                return false;
            }
        }
        return true;
    }

    private static boolean isAvailable(ZipFile jar, Path jarPath, String target) {
        if (target.startsWith("net/minecraft/") || target.startsWith("net/neoforged/")
                || target.startsWith("com/mojang/") || target.startsWith("java/")) {
            return true;
        }
        if (jar.getEntry(target + ".class") != null) {
            return true;
        }
        return installedClasses(jarPath).contains(target + ".class");
    }

    private static Set<String> readTargets(ZipFile jar, String entry) {
        Set<String> targets = new HashSet<>();
        var zipEntry = jar.getEntry(entry);
        if (zipEntry == null) {
            return targets;
        }
        try (InputStream is = jar.getInputStream(zipEntry)) {
            new ClassReader(is.readAllBytes()).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                    if (!"Lorg/spongepowered/asm/mixin/Mixin;".equals(descriptor)) {
                        return null;
                    }
                    return new AnnotationVisitor(Opcodes.ASM9) {
                        @Override
                        public AnnotationVisitor visitArray(String name) {
                            return new AnnotationVisitor(Opcodes.ASM9) {
                                @Override
                                public void visit(String unused, Object value) {
                                    if (value instanceof Type type) {
                                        targets.add(type.getInternalName());
                                    } else if (value instanceof String s) {
                                        targets.add(s.replace('.', '/'));
                                    }
                                }
                            };
                        }
                    };
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        } catch (IOException | RuntimeException e) {
            LOGGER.debug("Could not read mixin targets from {}", entry, e);
        }
        return targets;
    }

    private static Set<String> installedClasses;

    /**
     * Every class in every jar sitting alongside this one. Built once; the alternative is asking
     * the class loader, which at discovery time cannot see other mods yet.
     */
    private static synchronized Set<String> installedClasses(Path jarPath) {
        if (installedClasses != null) {
            return installedClasses;
        }
        Set<String> classes = new HashSet<>();
        Path dir = jarPath.getParent();
        if (dir == null) {
            return installedClasses = classes;
        }
        List<Path> jars = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.jar")) {
            stream.forEach(jars::add);
        } catch (IOException e) {
            LOGGER.debug("Could not list {}", dir, e);
        }
        for (Path path : jars) {
            try (ZipFile zip = new ZipFile(path.toFile())) {
                collectClasses(zip, classes);
            } catch (IOException | RuntimeException e) {
                LOGGER.debug("Could not scan {}", path, e);
            }
        }
        return installedClasses = classes;
    }

    /**
     * Collects class names, descending into nested jars. Mods that ship their real content in
     * {@code META-INF/jarjar} — Sodium among them — leave nothing but a bootstrap class at the top
     * level, so a jar's own entries are not a complete picture of what it provides.
     */
    private static void collectClasses(ZipFile zip, Set<String> classes) throws IOException {
        var entries = zip.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            String name = entry.getName();
            if (name.endsWith(".class")) {
                classes.add(name);
            } else if (name.endsWith(".jar")) {
                try (ZipInputStream nested = new ZipInputStream(zip.getInputStream(entry))) {
                    for (ZipEntry inner; (inner = nested.getNextEntry()) != null; ) {
                        if (inner.getName().endsWith(".class")) {
                            classes.add(inner.getName());
                        }
                    }
                }
            }
        }
    }
}
