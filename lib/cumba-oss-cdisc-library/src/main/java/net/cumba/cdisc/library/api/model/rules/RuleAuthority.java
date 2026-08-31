package net.cumba.cdisc.library.api.model.rules;

import java.util.List;
import java.util.Optional;

import net.cumba.web.api.ApiResource;

/**
 * An authority (organization) that published a rule, containing the standards and their references.
 *
 * <p>
 * Each rule may be published by one or more authorities (e.g. "CDISC"). An authority entry contains
 * the organization name and a list of {@link RuleStandard} entries that reference the applicable
 * standards and their specific citations.
 * </p>
 */
public interface RuleAuthority extends ApiResource
{

    /** Returns the name of the publishing organization (e.g. "CDISC"). */
    default Optional<String> organization()
    {
        return getString("Organization");
    }


    /** Returns the list of standards associated with this authority. */
    default List<RuleStandard> standards()
    {
        return getList("Standards", RuleStandard.class);
    }
}
