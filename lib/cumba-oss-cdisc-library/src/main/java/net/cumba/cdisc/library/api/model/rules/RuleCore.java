package net.cumba.cdisc.library.api.model.rules;

import java.util.Optional;

import net.cumba.web.api.ApiResource;

/**
 * CDISC CORE identifier for a rule (e.g. CORE-000351 v1, Published).
 *
 * <p>
 * Contains the core metadata fields that uniquely identify a conformance rule within the CDISC CORE
 * rule catalog: a human-readable identifier (e.g. "CORE-000351"), a version string, and a
 * publication status.
 * </p>
 */
public interface RuleCore extends ApiResource
{

    /** Returns the CORE identifier (e.g. "CORE-000351"). */
    default Optional<String> id()
    {
        return getString("Id");
    }


    /** Returns the version of this rule definition. */
    default Optional<String> version()
    {
        return getString("Version");
    }


    /** Returns the publication status (e.g. "Published"). */
    default Optional<String> status()
    {
        return getString("Status");
    }
}
