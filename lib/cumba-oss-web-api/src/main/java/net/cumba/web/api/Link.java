package net.cumba.web.api;

import java.util.Optional;

/**
 * A HAL-style link as a read-only {@link ApiResource}.
 *
 * <p>
 * Represents a single link object from a {@code _links} section in a HAL/JSON response. Rather than
 * eagerly extracting fields into a fixed structure, this interface provides lazy, typed access to
 * the standard link properties ({@code href}, {@code title}, {@code type}, {@code templated},
 * {@code hreflang}, {@code name}, {@code deprecation}, {@code profile}) and any additional
 * properties that may be present via the inherited {@link ApiResource} accessors.
 * </p>
 *
 * <p>
 * The {@link #id()} and {@link #id(int)} convenience methods extract path segments from the
 * {@code href}, which is a common pattern when navigating HATEOAS APIs where the last path segment
 * serves as the resource identifier.
 * </p>
 *
 * @see <a href="https://datatracker.ietf.org/doc/html/draft-kelly-json-hal">JSON HAL</a>
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc8288">RFC 8288 — Web Linking</a>
 */
public interface Link extends ApiResource
{

    /**
     * Returns the link target URI.
     *
     * @return the href value, or empty if not present
     */
    default Optional<String> href()
    {
        return getString("href");
    }


    /**
     * Returns the human-readable link title.
     *
     * @return the title value, or empty if not present
     */
    default Optional<String> title()
    {
        return getString("title");
    }


    /**
     * Returns the media type hint for the link target.
     *
     * @return the type value, or empty if not present
     */
    default Optional<String> type()
    {
        return getString("type");
    }


    /**
     * Returns whether the href is a URI Template (RFC 6570).
     *
     * <p>
     * When {@code true}, the {@code href} contains template variables (e.g.
     * {@code /items{?page,size}}) that must be expanded before use. Defaults to {@code false} if
     * not present.
     * </p>
     *
     * @return {@code true} if the href is templated, {@code false} otherwise
     */
    default boolean templated()
    {
        return getBoolean("templated").orElse(false);
    }


    /**
     * Returns the language of the link target as a BCP 47 language tag.
     *
     * @return the hreflang value, or empty if not present
     */
    default Optional<String> hreflang()
    {
        return getString("hreflang");
    }


    /**
     * Returns the secondary key for selecting among links with the same relation.
     *
     * @return the name value, or empty if not present
     */
    default Optional<String> name()
    {
        return getString("name");
    }


    /**
     * Returns a URI indicating that the link is deprecated.
     *
     * <p>
     * When present, the URI typically points to documentation about the deprecation.
     * </p>
     *
     * @return the deprecation URI, or empty if the link is not deprecated
     */
    default Optional<String> deprecation()
    {
        return getString("deprecation");
    }


    /**
     * Returns a URI that hints about the profile of the link target.
     *
     * @return the profile URI, or empty if not present
     */
    default Optional<String> profile()
    {
        return getString("profile");
    }


    /**
     * Extracts the last path segment from the href, which typically serves as the resource
     * identifier (e.g. {@code "adam-2-1"} from {@code "/mdr/adam/adam-2-1"}).
     *
     * @return the last path segment, or empty if the href is missing, empty, or ends with a slash
     */
    default Optional<String> id()
    {
        return href().flatMap(h ->
        {
            if (h.isEmpty())
            {
                return Optional.empty();
            }
            int idx = h.lastIndexOf('/');
            return idx >= 0 && idx < h.length() - 1 ? Optional.of(h.substring(idx + 1))
                    : Optional.empty();
        });
    }


    /**
     * Extracts a path segment from the href at the given position.
     *
     * <p>
     * A positive index selects the segment at that absolute position (1-based since the leading
     * slash produces an empty first element). An index of {@code 0} is equivalent to {@link #id()}
     * and returns the last segment. A negative index counts backwards from the end (e.g. {@code -1}
     * returns the second-to-last segment).
     * </p>
     *
     * @param aIndex
     *            the segment position (0 = last, positive = absolute, negative = from end)
     * @return the segment, or empty if the href is missing, empty, or the index is out of range
     */
    default Optional<String> id(int aIndex)
    {
        return href().flatMap(h ->
        {
            if (h.isEmpty())
            {
                return Optional.empty();
            }
            // Use -1 limit to preserve trailing empty strings (e.g. "/path/" -> ["", "path", ""])
            String[] parts = h.split("/", -1);

            if (parts.length == 0)
            {
                return Optional.empty();
            }

            if (aIndex == 0)
            {
                String last = parts[parts.length - 1];
                return last.isEmpty() ? Optional.empty() : Optional.of(last);
            }
            if (aIndex > 0)
            {
                if (aIndex >= parts.length)
                {
                    return Optional.empty();
                }
                String segment = parts[aIndex];
                return segment.isEmpty() ? Optional.empty() : Optional.of(segment);
            }
            else
            {
                int idx = parts.length - 1 - Math.abs(aIndex);
                if (idx < 0)
                {
                    return Optional.empty();
                }
                String segment = parts[idx];
                return segment.isEmpty() ? Optional.empty() : Optional.of(segment);
            }
        });
    }
}
