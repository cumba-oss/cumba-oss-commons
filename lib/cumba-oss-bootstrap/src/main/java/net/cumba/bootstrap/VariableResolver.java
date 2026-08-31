package net.cumba.bootstrap;

import java.nio.file.Path;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * Resolves {@code ${...}} references in config values. The lookups are injected so the resolver can
 * be unit-tested without mutating real environment variables or system properties.
 *
 * <p>
 * Supported tokens:
 *
 * <ul>
 * <li>{@code ${name}} — an earlier {@code [properties]} key (the {@code earlierProperties} map),
 * falling back to a system property.
 * <li>{@code ${env:NAME}} — the environment variable {@code NAME} only.
 * <li>{@code ${sys:name}} — the system property {@code name} only.
 * <li>{@code ${...:-default}} — any of the above, using the literal {@code default} when the
 * reference is unset or empty (shell {@code :-} semantics).
 * <li>{@code $$} — a literal {@code $}.
 * </ul>
 *
 * <p>
 * References may nest: both the variable name and the {@code :-default} may themselves contain
 * {@code ${...}} (e.g. {@code ${env:X:-${bootstrap.dir}}}). A looked-up <em>value</em> is not
 * re-resolved, so a value that happens to contain {@code ${...}} cannot trigger recursion. A
 * reference that resolves to nothing and has no default is a hard error.
 */
public final class VariableResolver
{

    private static final String ENV_PREFIX = "env:";

    private static final String SYS_PREFIX = "sys:";

    private static final String DEFAULT_SEPARATOR = ":-";

    private final UnaryOperator<String> envLookup;

    private final UnaryOperator<String> sysLookup;

    private final Map<String, String> earlierProperties;

    /**
     * Creates a resolver.
     *
     * @param envLookup
     *            maps an environment variable name to its value (or {@code null})
     * @param sysLookup
     *            maps a system property name to its value (or {@code null})
     * @param earlierProperties
     *            already-resolved {@code [properties]} keys, consulted before {@code sysLookup} for
     *            bare {@code ${name}} references; the resolver keeps a live reference so entries
     *            added after construction are seen
     */
    public VariableResolver(UnaryOperator<String> envLookup, UnaryOperator<String> sysLookup,
            Map<String, String> earlierProperties)
    {
        this.envLookup = envLookup;
        this.sysLookup = sysLookup;
        this.earlierProperties = earlierProperties;
    }


    /**
     * Resolves every {@code ${...}} reference in {@code value}.
     *
     * @param value
     *            the raw config value
     * @param source
     *            the config file, for error messages
     * @return the fully resolved value
     * @throws BootstrapException
     *             on an unterminated variable reference or an unresolved reference with no default
     */
    public String resolve(String value, Path source)
    {
        StringBuilder out = new StringBuilder(value.length());
        int i = 0;
        int n = value.length();
        while (i < n)
        {
            char c = value.charAt(i);
            if (c == '$' && i + 1 < n && value.charAt(i + 1) == '$')
            {
                out.append('$');
                i += 2;
            }
            else if (c == '$' && i + 1 < n && value.charAt(i + 1) == '{')
            {
                int close = matchingBrace(value, i + 2);
                if (close < 0)
                {
                    throw new BootstrapException(
                            source + ": unterminated ${...} in value: " + value);
                }
                out.append(resolveToken(value.substring(i + 2, close), source, value));
                i = close + 1;
            }
            else
            {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }


    /**
     * Finds the closing brace that matches the opening dollar-brace just before {@code start},
     * counting nested groups so a reference whose default itself contains a reference matches the
     * outer brace.
     *
     * @param value
     *            the whole value being scanned
     * @param start
     *            index of the first character after the opening dollar-brace
     * @return index of the matching closing brace, or {@code -1} if unbalanced
     */
    private static int matchingBrace(String value, int start)
    {
        int depth = 1;
        for (int i = start; i < value.length(); i++)
        {
            char c = value.charAt(i);
            if (c == '$' && i + 1 < value.length() && value.charAt(i + 1) == '{')
            {
                depth++;
                i++;
            }
            else if (c == '}')
            {
                depth--;
                if (depth == 0)
                {
                    return i;
                }
            }
        }
        return -1;
    }


    private String resolveToken(String token, Path source, String wholeValue)
    {
        // Resolve nested references inside the token first, so both the variable name and the
        // ':-default' may themselves contain ${...} (e.g. ${env:X:-${bootstrap.dir}}). Looked-up
        // values are NOT re-resolved, so a value that happens to contain ${...} cannot recurse.
        String resolvedToken = resolve(token, source);

        String spec = resolvedToken;
        String defaultValue = null;
        int dd = resolvedToken.indexOf(DEFAULT_SEPARATOR);
        if (dd >= 0)
        {
            spec = resolvedToken.substring(0, dd);
            defaultValue = resolvedToken.substring(dd + DEFAULT_SEPARATOR.length());
        }

        String resolved;
        if (spec.startsWith(ENV_PREFIX))
        {
            resolved = envLookup
                    .apply(requireName(spec.substring(ENV_PREFIX.length()), source, wholeValue));
        }
        else if (spec.startsWith(SYS_PREFIX))
        {
            resolved = sysLookup
                    .apply(requireName(spec.substring(SYS_PREFIX.length()), source, wholeValue));
        }
        else
        {
            resolved = bareLookup(requireName(spec, source, wholeValue));
        }

        if (resolved == null || resolved.isEmpty())
        {
            if (defaultValue != null)
            {
                return defaultValue;
            }
            if (resolved == null)
            {
                throw new BootstrapException(source + ": unresolved variable '${" + token
                        + "}' (and no ':-default') in value: " + wholeValue);
            }
        }
        return resolved;
    }


    private static String requireName(String name, Path source, String wholeValue)
    {
        if (name.isBlank())
        {
            throw new BootstrapException(
                    source + ": empty variable name in a ${...} reference in value: " + wholeValue);
        }
        return name;
    }


    private String bareLookup(String name)
    {
        if (earlierProperties.containsKey(name))
        {
            return earlierProperties.get(name);
        }
        return sysLookup.apply(name);
    }
}
