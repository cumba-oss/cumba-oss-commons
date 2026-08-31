package net.cumba.cdisc.library.api.model.rules;

import java.util.Optional;

import net.cumba.web.api.ApiResource;

/**
 * Scope of a rule: which classes and domains it applies to.
 *
 * <p>
 * Defines the applicability of a conformance rule by specifying class-level and domain-level
 * include/exclude filters via {@link RuleScopeFilter}, and an optional use case restriction string
 * (e.g. "NONCLIN, INDH") that further narrows when the rule should be evaluated.
 * </p>
 */
public interface RuleScope extends ApiResource
{

    /** Class scope (Include/Exclude lists). */
    default Optional<RuleScopeFilter> classes()
    {
        return getObject("Classes", RuleScopeFilter.class);
    }


    /** Domain scope (Include/Exclude lists). */
    default Optional<RuleScopeFilter> domains()
    {
        return getObject("Domains", RuleScopeFilter.class);
    }


    /** Use case restriction (e.g. "NONCLIN, INDH"). */
    default Optional<String> useCase()
    {
        return getString("Use_Case");
    }


    /**
     * Fix #117 (review finding 6): ADaM data-structure scope (Include/Exclude lists). Upstream CORE
     * rules author the key with a space ({@code "Data Structures"}); the underscore spelling is
     * accepted for symmetry with the engine's canonical form.
     */
    default Optional<RuleScopeFilter> dataStructures()
    {
        Optional<RuleScopeFilter> spaced = getObject("Data Structures", RuleScopeFilter.class);
        return spaced.isPresent() ? spaced : getObject("Data_Structures", RuleScopeFilter.class);
    }


    /** Fix #118 (review finding 6): ADaM subclass scope (Include/Exclude lists). */
    default Optional<RuleScopeFilter> subclasses()
    {
        return getObject("Subclasses", RuleScopeFilter.class);
    }
}
