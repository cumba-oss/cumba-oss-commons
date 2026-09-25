package net.cumba.bootstrap;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;

/**
 * Locates the optional <em>user overlay</em>: a second config file, in the user's own configuration
 * directory, whose {@code [properties]} are applied after the bundled config's and win over them.
 *
 * <p>
 * The bundled config declares the candidate locations with repeatable
 * {@code [bootstrap] user-config} lines; the first that names an existing regular file is used, and
 * none is the normal case. A config that declares no candidate has no overlay at all — not even
 * through {@value #OVERRIDE_PROPERTY}, so a {@code JAVA_TOOL_OPTIONS} exported for one application
 * cannot overlay another that shares the launcher.
 *
 * <p>
 * Candidates are resolved leniently ({@link VariableResolver#resolveCandidate}): one naming another
 * platform's variable is skipped. They see the environment, the system properties as they were
 * before the bundled config was applied, and {@code bootstrap.dir} — never the bundled
 * {@code [properties]}. A relative candidate resolves against {@code bootstrap.dir}.
 */
final class UserConfig
{

    /** System property replacing the candidate list with one explicit file (which must exist). */
    static final String OVERRIDE_PROPERTY = Launcher.USER_CONFIG_OVERRIDE_PROPERTY;

    /** Published: the absolute path of the overlay applied; unset when none was. */
    static final String APPLIED_PROPERTY = Launcher.USER_CONFIG_APPLIED_PROPERTY;

    /**
     * Published: the resolved candidate paths, {@link File#pathSeparator}-joined — meant for a log
     * line (a path that itself contains the separator makes the list ambiguous); empty when every
     * candidate was skipped.
     */
    static final String CANDIDATES_PROPERTY = Launcher.USER_CONFIG_CANDIDATES_PROPERTY;

    /**
     * Keys a user overlay may never set: the launcher's own inputs and outputs. The bundled config
     * adds to them with {@code [bootstrap] reserved}.
     */
    static final Set<String> BUILT_IN_RESERVED_KEYS = Set.of(Launcher.BOOTSTRAP_DIR_PROPERTY,
            ConfigLocator.CONFIG_OVERRIDE_PROPERTY, OVERRIDE_PROPERTY, APPLIED_PROPERTY,
            CANDIDATES_PROPERTY, Bootstrap.ERROR_DIALOG_PROPERTY,
            Launcher.CLASSPATH_ERROR_MODE_PROPERTY);

    /**
     * Where the launcher looked, and what it found.
     *
     * @param candidates
     *            the resolved candidate paths, in order (the override alone when it is in effect)
     * @param applied
     *            the overlay to apply, if any
     */
    record Location(List<Path> candidates, Optional<Path> applied)
    {

        /** Canonicalises the list to an immutable copy. */
        Location
        {
            candidates = List.copyOf(candidates);
        }


        /** The candidates as published in {@value UserConfig#CANDIDATES_PROPERTY}. */
        String joinedCandidates()
        {
            List<String> paths = new ArrayList<>(candidates.size());
            for (Path candidate : candidates)
            {
                paths.add(candidate.toString());
            }
            return String.join(File.pathSeparator, paths);
        }
    }

    private UserConfig()
    {
    }


    /**
     * Locates the user overlay.
     *
     * @param config
     *            the bundled config
     * @param baseDir
     *            the bundled config's directory ({@code bootstrap.dir})
     * @param envLookup
     *            maps an environment variable name to its value (or {@code null})
     * @param snapshot
     *            the system properties as they were before the bundled config was applied
     * @return empty when the config declares no candidate (no overlay, nothing published);
     *         otherwise where the launcher looked and what it found
     * @throws BootstrapException
     *             on a syntax error in a candidate, a candidate or override that is not a valid
     *             path, or an override naming a file that is not there
     */
    static Optional<Location> locate(BootstrapConfig config, Path baseDir,
            UnaryOperator<String> envLookup, UnaryOperator<String> snapshot)
    {
        if (config.userConfigCandidates().isEmpty())
        {
            return Optional.empty();
        }

        String override = snapshot.apply(OVERRIDE_PROPERTY);
        if (override != null && !override.isBlank())
        {
            // Relative to the working directory, like -Dbootstrap.config: the operator typed it.
            Path path = toPath(override.strip(), "-D" + OVERRIDE_PROPERTY).toAbsolutePath()
                    .normalize();
            if (!Files.exists(path))
            {
                throw new BootstrapException(
                        "user config not found: " + path + " (set by -D" + OVERRIDE_PROPERTY + ")");
            }
            if (!Files.isRegularFile(path))
            {
                throw new BootstrapException("user config is not a regular file: " + path
                        + " (set by -D" + OVERRIDE_PROPERTY + ")");
            }
            return Optional.of(new Location(List.of(path), Optional.of(path)));
        }

        VariableResolver resolver = new VariableResolver(envLookup, snapshot,
                Map.of(Launcher.BOOTSTRAP_DIR_PROPERTY, baseDir.toString()));
        List<Path> candidates = new ArrayList<>();
        Path applied = null;
        for (String raw : config.userConfigCandidates())
        {
            Optional<String> resolved = resolver.resolveCandidate(raw, config.source());
            // Blank counts as unresolvable: Path.of("") would resolve to bootstrap.dir itself.
            if (resolved.isEmpty() || resolved.get().isBlank())
            {
                continue;
            }
            Path candidate = baseDir
                    .resolve(toPath(resolved.get(), config.source() + ": user-config")).normalize();
            candidates.add(candidate);
            if (applied == null && Files.isRegularFile(candidate))
            {
                applied = candidate;
            }
        }
        return Optional.of(new Location(candidates, Optional.ofNullable(applied)));
    }


    /**
     * The reserved keys the overlay may not set: the built-in ones and the config's own.
     *
     * @param config
     *            the bundled config
     * @param key
     *            an overlay key
     * @return whether {@code key} is reserved
     */
    static boolean isReserved(BootstrapConfig config, String key)
    {
        return BUILT_IN_RESERVED_KEYS.contains(key) || config.reservedKeys().contains(key);
    }


    private static Path toPath(String value, String origin)
    {
        try
        {
            return Path.of(value);
        }
        catch (InvalidPathException e)
        {
            // Unchecked: left alone it escapes Bootstrap.main as a stack trace and exit 1 instead
            // of the launcher's 'bootstrap: ...' line and exit 2.
            throw new BootstrapException(
                    origin + " is not a valid path: '" + value + "' (" + e.getMessage() + ")", e);
        }
    }
}
