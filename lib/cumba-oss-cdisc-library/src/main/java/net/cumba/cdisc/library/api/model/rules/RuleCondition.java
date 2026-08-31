package net.cumba.cdisc.library.api.model.rules;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import net.cumba.web.api.ApiResource;

/**
 * A rule condition within a Check block. Conditions can be nested recursively via {@link #any()},
 * {@link #all()}, and {@link #not()} combinators.
 *
 * <p>
 * Represents a single node in the recursive condition tree that defines a rule's check logic. A
 * leaf condition specifies a variable name, an operator, and a comparison value, with optional
 * modifiers such as prefix/suffix extraction, regex matching, ordering constraints, and flags
 * controlling value interpretation (literal, reference, type-insensitive). Branch conditions use
 * the {@code any}, {@code all}, and {@code not} combinators to compose sub-conditions with OR, AND,
 * and NOT logic respectively.
 * </p>
 *
 * <p>
 * Condition fields from real data include: {@code name}, {@code operator}, {@code value},
 * {@code value_is_literal}, {@code value_is_reference}, {@code prefix}, {@code suffix},
 * {@code regex}, {@code negative}, {@code ordering}, {@code type_insensitive}, {@code within}.
 * </p>
 */
public interface RuleCondition extends ApiResource
{

    /** Returns the variable name this condition applies to. */
    default Optional<String> name()
    {
        return getString("name");
    }


    /** Returns the operator as an API string value (e.g. "equal_to"). */
    default Optional<String> operator()
    {
        return getString("operator");
    }


    /** Returns the operator as a typed enum. */
    default ConditionOperator operatorEnum()
    {
        return operator().map(ConditionOperator::fromValue).orElse(ConditionOperator.UNKNOWN);
    }


    /** Returns the prefix string modifier for value extraction. */
    default Optional<String> prefix()
    {
        return getString("prefix");
    }


    /** Returns the suffix string modifier for value extraction. */
    default Optional<String> suffix()
    {
        return getString("suffix");
    }


    /** Returns the prefix as an integer (prefix character count). */
    default OptionalInt prefixInt()
    {
        return getInt("prefix");
    }


    /** Returns the suffix as an integer (suffix character count). */
    default OptionalInt suffixInt()
    {
        return getInt("suffix");
    }


    /** Returns the regex pattern for value matching. */
    default Optional<String> regex()
    {
        return getString("regex");
    }


    /** Returns the within-group scope for cross-record checks. */
    default Optional<String> within()
    {
        return getString("within");
    }


    /** Returns the ordering constraint for sorted checks. */
    default Optional<String> ordering()
    {
        return getString("ordering");
    }


    /** Returns whether the condition result should be negated. */
    default Optional<Boolean> negative()
    {
        return getBoolean("negative");
    }


    /** Returns whether the value should be treated as a literal. */
    default Optional<Boolean> valueIsLiteral()
    {
        return getBoolean("value_is_literal");
    }


    /** Returns whether the value should be resolved as a column reference. */
    default Optional<Boolean> valueIsReference()
    {
        return getBoolean("value_is_reference");
    }


    /** Returns whether type comparison should be case-insensitive. */
    default Optional<Boolean> typeInsensitive()
    {
        return getBoolean("type_insensitive");
    }


    /** Returns the comparison value as a string (if textual). */
    default Optional<String> valueString()
    {
        return getString("value");
    }


    /** Returns the comparison value as a number (if numeric). */
    default Optional<Number> valueNumber()
    {
        return getNumber("value");
    }


    /** Returns the comparison value as an {@link ApiResource} (if it is an object). */
    default Optional<ApiResource> valueObject()
    {
        return getObject("value", ApiResource.class);
    }


    /** Returns the comparison value as a list of strings (if the value is an array). */
    default List<String> valueList()
    {
        return getStringList("value");
    }


    /** Returns the params as an {@link ApiResource} for structured access. */
    default Optional<ApiResource> params()
    {
        return getObject("params", ApiResource.class);
    }


    /** Sub-conditions where at least one must be true (OR logic). */
    default List<RuleCondition> any()
    {
        return getList("any", RuleCondition.class);
    }


    /** Sub-conditions where all must be true (AND logic). */
    default List<RuleCondition> all()
    {
        return getList("all", RuleCondition.class);
    }


    /** Negated sub-condition (NOT logic). */
    default Optional<RuleCondition> not()
    {
        return getObject("not", RuleCondition.class);
    }
}
