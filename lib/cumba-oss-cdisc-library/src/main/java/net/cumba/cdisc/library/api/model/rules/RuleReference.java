package net.cumba.cdisc.library.api.model.rules;

import java.util.List;
import java.util.Optional;

import net.cumba.web.api.ApiResource;

/**
 * A reference within a standard, containing citations and a rule identifier.
 *
 * <p>
 * Links a rule to a specific origin document and version, along with a {@link RuleIdentifier} for
 * cross-referencing and a list of {@link RuleCitation} entries pointing to specific sections within
 * the referenced document.
 * </p>
 */
public interface RuleReference extends ApiResource
{

    /** Returns the origin of this reference (e.g. "SDTM and SDTMIG Conformance Rules"). */
    default Optional<String> origin()
    {
        return getString("Origin");
    }


    /** Returns the version of the referenced document. */
    default Optional<String> version()
    {
        return getString("Version");
    }


    /** Returns the rule identifier for cross-referencing (e.g. "CG0151"). */
    default Optional<RuleIdentifier> ruleIdentifier()
    {
        return getObject("Rule_Identifier", RuleIdentifier.class);
    }


    /** Returns the list of citations within the referenced document. */
    default List<RuleCitation> citations()
    {
        return getList("Citations", RuleCitation.class);
    }
}
