package net.cumba.cdisc.library.api.model.rules;

import org.jspecify.annotations.Nullable;

/**
 * Known condition operator values used in rule check expressions.
 *
 * <p>
 * Enumerates the operators that can appear in {@link RuleCondition#operator()} fields, covering
 * presence checks, equality and comparison operators, date-specific operators, string matching
 * (contains, starts/ends with, regex), set containment, and cross-record uniqueness checks.
 * Unrecognized API values resolve to {@link #UNKNOWN}.
 * </p>
 */
public enum ConditionOperator
{

    // --- Presence ---
    EMPTY("empty"),
    NON_EMPTY("non_empty"),
    EXISTS("exists"),
    NOT_EXISTS("not_exists"),

    // --- Equality ---
    EQUAL_TO("equal_to"),
    NOT_EQUAL_TO("not_equal_to"),
    EQUAL_TO_CASE_INSENSITIVE("equal_to_case_insensitive"),
    NOT_EQUAL_TO_CASE_INSENSITIVE("not_equal_to_case_insensitive"),
    PREFIX_EQUAL_TO("prefix_equal_to"),
    PREFIX_NOT_EQUAL_TO("prefix_not_equal_to"),
    DOES_NOT_EQUAL_STRING_PART("does_not_equal_string_part"),

    // --- Comparison ---
    GREATER_THAN("greater_than"),
    GREATER_THAN_OR_EQUAL_TO("greater_than_or_equal_to"),
    LESS_THAN("less_than"),
    LESS_THAN_OR_EQUAL_TO("less_than_or_equal_to"),
    LONGER_THAN("longer_than"),
    SHORTER_THAN("shorter_than"),
    HAS_NOT_EQUAL_LENGTH("has_not_equal_length"),

    // --- Date operators ---
    DATE_EQUAL_TO("date_equal_to"),
    DATE_NOT_EQUAL_TO("date_not_equal_to"),
    DATE_GREATER_THAN("date_greater_than"),
    DATE_GREATER_THAN_OR_EQUAL_TO("date_greater_than_or_equal_to"),
    DATE_LESS_THAN("date_less_than"),
    DATE_LESS_THAN_OR_EQUAL_TO("date_less_than_or_equal_to"),
    IS_COMPLETE_DATE("is_complete_date"),
    IS_INCOMPLETE_DATE("is_incomplete_date"),
    INVALID_DATE("invalid_date"),
    INVALID_DURATION("invalid_duration"),

    // --- String matching ---
    CONTAINS("contains"),
    DOES_NOT_CONTAIN("does_not_contain"),
    STARTS_WITH("starts_with"),
    ENDS_WITH("ends_with"),
    MATCHES_REGEX("matches_regex"),
    NOT_MATCHES_REGEX("not_matches_regex"),
    PREFIX_MATCHES_REGEX("prefix_matches_regex"),
    NOT_PREFIX_MATCHES_REGEX("not_prefix_matches_regex"),
    SUFFIX_MATCHES_REGEX("suffix_matches_regex"),
    NOT_SUFFIX_MATCHES_REGEX("not_suffix_matches_regex"),

    // --- Set / containment ---
    IS_CONTAINED_BY("is_contained_by"),
    IS_CONTAINED_BY_CASE_INSENSITIVE("is_contained_by_case_insensitive"),
    IS_NOT_CONTAINED_BY("is_not_contained_by"),
    PREFIX_IS_NOT_CONTAINED_BY("prefix_is_not_contained_by"),
    SUFFIX_IS_NOT_CONTAINED_BY("suffix_is_not_contained_by"),
    HAS_SAME_VALUES("has_same_values"),
    SHARES_NO_ELEMENTS_WITH("shares_no_elements_with"),
    NOT_CONTAINS_ALL("not_contains_all"),
    IS_NOT_ORDERED_SUBSET_OF("is_not_ordered_subset_of"),

    // --- Uniqueness / cross-record ---
    IS_UNIQUE_SET("is_unique_set"),
    IS_NOT_UNIQUE_SET("is_not_unique_set"),
    IS_NOT_UNIQUE_RELATIONSHIP("is_not_unique_relationship"),
    IS_INCONSISTENT_ACROSS_DATASET("is_inconsistent_across_dataset"),
    INCONSISTENT_ENUMERATED_COLUMNS("inconsistent_enumerated_columns"),
    PRESENT_ON_MULTIPLE_ROWS_WITHIN("present_on_multiple_rows_within"),
    NOT_PRESENT_ON_MULTIPLE_ROWS_WITHIN("not_present_on_multiple_rows_within"),
    DOES_NOT_HAVE_NEXT_CORRESPONDING_RECORD("does_not_have_next_corresponding_record"),
    EMPTY_WITHIN_EXCEPT_LAST_ROW("empty_within_except_last_row"),
    TARGET_IS_NOT_SORTED_BY("target_is_not_sorted_by"),

    UNKNOWN(null);

    // UNKNOWN carries a null value.
    private final @Nullable String value;

    ConditionOperator(@Nullable String value)
    {
        this.value = value;
    }


    /** The original API string value, or {@code null} for {@link #UNKNOWN}. */
    public @Nullable String value()
    {
        return value;
    }


    /**
     * Resolves the enum from the API string value, returning {@link #UNKNOWN} for unrecognized
     * values.
     */
    public static ConditionOperator fromValue(@Nullable String value)
    {
        if (value == null)
        {
            return UNKNOWN;
        }
        for (ConditionOperator op : values())
        {
            if (value.equals(op.value))
            {
                return op;
            }
        }
        return UNKNOWN;
    }
}
