package net.cumba.cdisc.library.api.model.rules;

import java.util.List;
import java.util.Optional;

import net.cumba.web.api.ApiResource;
import net.cumba.web.api.Link;

/**
 * A rule package for a standard/version (response from {@code /mdr/rules/{standard}/{version}}).
 *
 * <p>
 * Represents the API response containing a collection of conformance rules for a specific CDISC
 * standard and version. The {@code rules} field is a JSON object mapping rule UUIDs to rule
 * definitions, accessible as a {@link RuleMap}.
 * </p>
 */
public interface RulePackage extends ApiResource
{

    /** Returns the rules as a {@link RuleMap} for keyed access by UUID. */
    default Optional<RuleMap> rules()
    {
        return getObject("rules", RuleMap.class);
    }

    // --- Links ---


    /** Returns the links to the standards associated with this package. */
    default List<Link> standardLinks()
    {
        return getLinks("standards");
    }


    /** Returns the link to the prior version of this rule package, if any. */
    default Optional<Link> priorVersionLink()
    {
        return getLink("priorVersion");
    }
}
