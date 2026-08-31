package net.cumba.bootstrap;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Immutable, parsed representation of a bootstrap {@code .conf} file.
 *
 * <p>
 * Order is significant and preserved: {@code properties} are applied in declaration order so a
 * later value may reference an earlier one, and {@code classpathEntries} keep their declared order
 * (the launcher de-duplicates them by canonical path after expansion, first occurrence winning).
 *
 * @param source
 *            the file this config was read from (used in error messages)
 * @param mainClass
 *            the target {@code main} class, if declared in {@code [bootstrap]}
 * @param classLoaderMode
 *            delegation strategy for the assembled classpath
 * @param properties
 *            ordered {@code [properties]} key/value pairs (pre-interpolation)
 * @param classpathEntries
 *            ordered {@code [classpath]} entries (pre-interpolation, pre-glob)
 */
public record BootstrapConfig(Path source, Optional<String> mainClass,
        ClassLoaderMode classLoaderMode, List<Map.Entry<String, String>> properties,
        List<String> classpathEntries)
{

    /**
     * Canonicalises the collections to immutable copies so the record cannot be mutated through a
     * retained reference to the lists passed in.
     */
    public BootstrapConfig
    {
        properties = List.copyOf(properties);
        classpathEntries = List.copyOf(classpathEntries);
    }
}
