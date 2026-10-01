package net.sievert.item_blacklist.layout;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The project's root for the tests that read sources rather than compiled output: Gradle runs
 * the unit tests in a module's folder, an IDE may run them elsewhere inside the project, so
 * the root is found by walking up, not assumed.
 */
public final class ProjectFiles {
    private ProjectFiles() {
    }

    /**
     * The first folder, from the working directory up, that holds settings.gradle.kts.
     *
     * @throws IllegalStateException when no folder up to the file system's root holds it
     */
    public static Path root() {
        Path start = Path.of("").toAbsolutePath();
        for (Path dir = start; dir != null; dir = dir.getParent()) {
            if (Files.isRegularFile(dir.resolve("settings.gradle.kts"))) {
                return dir;
            }
        }
        throw new IllegalStateException("no settings.gradle.kts in " + start + " or above");
    }
}
