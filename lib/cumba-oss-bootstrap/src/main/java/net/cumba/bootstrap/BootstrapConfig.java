package net.cumba.bootstrap;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
 * @param userConfigCandidates
 *            ordered {@code [bootstrap] user-config} candidates for the user overlay
 *            (pre-interpolation); empty when the config declares none, which disables the overlay
 * @param reservedKeys
 *            keys declared by {@code [bootstrap] reserved} that the user overlay may not set, in
 *            addition to the launcher's own
 */
public record BootstrapConfig(Path source, Optional<String> mainClass,
        ClassLoaderMode classLoaderMode, List<Map.Entry<String, String>> properties,
        List<String> classpathEntries, List<String> userConfigCandidates, Set<String> reservedKeys)
{

    /**
     * Canonicalises the collections to immutable copies so the record cannot be mutated through a
     * retained reference to the collections passed in.
     */
    public BootstrapConfig
    {
        properties = List.copyOf(properties);
        classpathEntries = List.copyOf(classpathEntries);
        userConfigCandidates = List.copyOf(userConfigCandidates);
        reservedKeys = Set.copyOf(reservedKeys);
    }


    /**
     * A config without a user overlay: no {@code user-config} candidates, no extra reserved keys.
     *
     * @param source
     *            the file this config was read from
     * @param mainClass
     *            the target {@code main} class, if declared
     * @param classLoaderMode
     *            delegation strategy for the assembled classpath
     * @param properties
     *            ordered {@code [properties]} key/value pairs
     * @param classpathEntries
     *            ordered {@code [classpath]} entries
     */
    public BootstrapConfig(Path source, Optional<String> mainClass, ClassLoaderMode classLoaderMode,
            List<Map.Entry<String, String>> properties, List<String> classpathEntries)
    {
        this(source, mainClass, classLoaderMode, properties, classpathEntries, List.of(), Set.of());
    }
}
