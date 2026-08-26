package com.leclowndu93150.foxy.loader;

import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The per-game-directory scratch space for extracted and patched jars.
 *
 * <p>These used to be {@code Files.createTempFile(...)} + {@code deleteOnExit()}, but FML
 * holds the jars open as module-path entries for the whole run, so on Windows the exit-time
 * delete always fails and {@code %TEMP%} grew by roughly 40 MB per launch, without bound.
 * A stable directory swept at the next startup fixes that: files from previous runs are no
 * longer held open and delete cleanly, while a file a still-running second instance holds
 * simply fails to delete and is skipped.
 */
final class FoxyWork {
    private static final Path DIR = FMLPaths.GAMEDIR.get().resolve(".foxy").resolve("work");
    private static final AtomicInteger COUNTER = new AtomicInteger();

    static {
        sweep(DIR);
        sweep(DIR.getParent().resolve("natives"));
    }

    private FoxyWork() {}

    static Path file(String prefix, String name) throws IOException {
        Files.createDirectories(DIR);
        Path out = DIR.resolve(prefix + COUNTER.getAndIncrement() + "-" + name);
        Files.deleteIfExists(out);
        return Files.createFile(out);
    }

    private static void sweep(Path dir) {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (var walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder())
                    .filter(p -> !p.equals(dir))
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignored) {
                            // held open by a running instance — leave it
                        }
                    });
        } catch (IOException ignored) {}
    }
}
