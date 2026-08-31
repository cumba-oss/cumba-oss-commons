package net.cumba.cdisc.library.api.model.rules;

import java.util.List;
import java.util.Optional;

import net.cumba.web.api.ApiResource;

/**
 * Include/Exclude filter used within {@link RuleScope} for both classes and domains. "ALL" in the
 * include list means the rule applies to all classes/domains.
 *
 * <p>
 * Provides two lists -- an include list and an exclude list -- that together define which classes
 * or domains are in scope for a rule. An optional {@code include_split_datasets} flag controls
 * whether split datasets should be considered during domain matching.
 * </p>
 */
public interface RuleScopeFilter extends ApiResource
{

    /** Returns the list of class or domain names to include. */
    default List<String> include()
    {
        return getStringList("Include");
    }


    /** Returns the list of class or domain names to exclude. */
    default List<String> exclude()
    {
        return getStringList("Exclude");
    }


    /**
     * Whether split datasets should be included in domain matching. Only applicable when used as a
     * domain scope filter.
     */
    default Optional<Boolean> includeSplitDatasets()
    {
        return getBoolean("include_split_datasets");
    }
}
