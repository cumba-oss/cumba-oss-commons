package net.cumba.cdisc.library.api.model.rules;

import org.jspecify.annotations.Nullable;

/**
 * Known operation operator values used in rule pre-processing steps.
 *
 * <p>
 * Enumerates the operators that can appear in {@link RuleOperation#operator()} fields, covering
 * aggregation functions (distinct, max, min_date, record_count), variable and dataset
 * introspection, column ordering queries, metadata extraction, codelist lookups, and date
 * computation. Unrecognized API values resolve to {@link #UNKNOWN}.
 * </p>
 */
public enum OperationOperator
{

    // --- Aggregation ---
    DISTINCT("distinct"),
    MAX("max"),
    MAX_DATE("max_date"),
    MIN_DATE("min_date"),
    RECORD_COUNT("record_count"),
    VARIABLE_COUNT("variable_count"),

    // --- Variable / dataset introspection ---
    VARIABLE_EXISTS("variable_exists"),
    DATASET_NAMES("dataset_names"),
    STUDY_DOMAINS("study_domains"),
    EXPECTED_VARIABLES("expected_variables"),
    REQUIRED_VARIABLES("required_variables"),
    DOMAIN_IS_CUSTOM("domain_is_custom"),
    DOMAIN_LABEL("domain_label"),

    // --- Column order ---
    GET_COLUMN_ORDER_FROM_DATASET("get_column_order_from_dataset"),
    GET_COLUMN_ORDER_FROM_LIBRARY("get_column_order_from_library"),
    GET_MODEL_COLUMN_ORDER("get_model_column_order"),
    GET_PARENT_MODEL_COLUMN_ORDER("get_parent_model_column_order"),

    // --- Metadata / filtering ---
    EXTRACT_METADATA("extract_metadata"),
    GET_DATASET_FILTERED_VARIABLES("get_dataset_filtered_variables"),
    GET_MODEL_FILTERED_VARIABLES("get_model_filtered_variables"),

    // --- Codelist ---
    CODELIST_TERMS("codelist_terms"),
    GET_CODELIST_ATTRIBUTES("get_codelist_attributes"),
    VALID_CODELIST_DATES("valid_codelist_dates"),

    // --- Date computation ---
    DY("dy"),

    UNKNOWN(null);

    // UNKNOWN carries a null value.
    private final @Nullable String value;

    OperationOperator(@Nullable String value)
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
    public static OperationOperator fromValue(@Nullable String value)
    {
        if (value == null)
        {
            return UNKNOWN;
        }
        for (OperationOperator op : values())
        {
            if (value.equals(op.value))
            {
                return op;
            }
        }
        return UNKNOWN;
    }
}
