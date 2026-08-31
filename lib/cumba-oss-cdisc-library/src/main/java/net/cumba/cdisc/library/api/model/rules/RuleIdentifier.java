package net.cumba.cdisc.library.api.model.rules;

import java.util.Optional;

import net.cumba.web.api.ApiResource;

/**
 * A rule identifier (e.g. CG0151 v1).
 *
 * <p>
 * Provides a secondary, human-friendly identifier for a rule within the context of a specific
 * standard reference. This is distinct from the CORE identifier in {@link RuleCore} and is used for
 * cross-referencing with conformance rule documents.
 * </p>
 */
public interface RuleIdentifier extends ApiResource
{

    /** Returns the identifier string (e.g. "CG0151"). */
    default Optional<String> id()
    {
        return getString("Id");
    }


    /** Returns the version of this identifier. */
    default Optional<String> version()
    {
        return getString("Version");
    }
}
