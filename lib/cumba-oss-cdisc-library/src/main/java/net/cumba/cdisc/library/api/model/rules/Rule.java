package net.cumba.cdisc.library.api.model.rules;

import java.util.List;
import java.util.Optional;

import net.cumba.web.api.ApiResource;
import net.cumba.web.api.Link;

/**
 * A conformance rule as returned by the CDISC Library API.
 *
 * <p>
 * A top-level conformance rule definition containing its unique identifier, human-readable
 * description, classification metadata (Rule_Type, Sensitivity, Executability), CORE identifier,
 * publishing authorities, applicability scope, check conditions, violation outcome, match datasets
 * for cross-dataset checks, pre-processing operations, and grouping variables.
 * </p>
 *
 * <p>
 * Note: The actual API response structure differs significantly from the OpenAPI specification.
 * This model is based on real API responses.
 * </p>
 */
public interface Rule extends ApiResource
{

    /** Returns the unique rule identifier. */
    default Optional<String> id()
    {
        return getString("id");
    }


    /** Returns the human-readable description of this rule. */
    default Optional<String> description()
    {
        return getString("Description");
    }


    /** Returns the rule type as an API string value (e.g. "Record Data"). */
    default Optional<String> ruleType()
    {
        return getString("Rule_Type");
    }


    /** Returns the sensitivity level as an API string value (e.g. "Record"). */
    default Optional<String> sensitivity()
    {
        return getString("Sensitivity");
    }


    /** Returns the executability level as an API string value (e.g. "Fully Executable"). */
    default Optional<String> executability()
    {
        return getString("Executability");
    }


    /** Returns the rule type as a typed enum. */
    default RuleType ruleTypeEnum()
    {
        return ruleType().map(RuleType::fromValue).orElse(RuleType.UNKNOWN);
    }


    /** Returns the sensitivity as a typed enum. */
    default RuleSensitivity sensitivityEnum()
    {
        return sensitivity().map(RuleSensitivity::fromValue).orElse(RuleSensitivity.UNKNOWN);
    }


    /** Returns the executability as a typed enum. */
    default RuleExecutability executabilityEnum()
    {
        return executability().map(RuleExecutability::fromValue).orElse(RuleExecutability.UNKNOWN);
    }


    /** CDISC CORE identifier (Id, Version, Status). */
    default Optional<RuleCore> core()
    {
        return getObject("Core", RuleCore.class);
    }


    /** Authorities and their standards/references. */
    default List<RuleAuthority> authorities()
    {
        return getList("Authorities", RuleAuthority.class);
    }


    /** Scope: classes, domains, and optional use case. */
    default Optional<RuleScope> scope()
    {
        return getObject("Scope", RuleScope.class);
    }


    /**
     * The check conditions (recursive structure with all/any combinators). The top-level Check
     * object is itself a {@link RuleCondition} containing {@code all} and/or {@code any} lists.
     */
    default Optional<RuleCondition> check()
    {
        return getObject("Check", RuleCondition.class);
    }


    /** Outcome when the rule is violated (message and output variables). */
    default Optional<RuleOutcome> outcome()
    {
        return getObject("Outcome", RuleOutcome.class);
    }


    /** Datasets to match for cross-dataset checks. */
    default List<RuleMatchDataset> matchDatasets()
    {
        return getList("Match_Datasets", RuleMatchDataset.class);
    }


    /** Operations to compute derived values. */
    default List<RuleOperation> operations()
    {
        return getList("Operations", RuleOperation.class);
    }


    /** Variables to group by when evaluating the rule. */
    default List<String> groupingVariables()
    {
        return getStringList("Grouping_Variables");
    }

    // --- Links ---


    /** Returns the links to the standards associated with this rule. */
    default List<Link> standardLinks()
    {
        return getLinks("standards");
    }


    /** Returns the link to the rule package containing this rule. */
    default Optional<Link> packageLink()
    {
        return getLink("package");
    }
}
