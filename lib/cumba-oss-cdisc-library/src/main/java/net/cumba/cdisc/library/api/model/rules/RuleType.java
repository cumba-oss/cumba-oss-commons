package net.cumba.cdisc.library.api.model.rules;

import lombok.Getter;
import org.jspecify.annotations.Nullable;

/**
 * Known rule type values.
 *
 * <p>
 * Enumerates the types of conformance rules supported by the CDISC Rules Engine, classifying rules
 * by what they check (record data, dataset metadata, variable metadata, domain presence) and
 * whether they produce per-record or per-domain outcomes. The {@code isValueBased()} flag indicates
 * whether a rule type evaluates individual data values (true) or dataset/variable-level metadata
 * (false). Unrecognized API values resolve to {@link #UNKNOWN}.
 * </p>
 */
public enum RuleType
{

    /**
     * Rules matching on the single data values.<br/>
     * These rules have separate boolean outcomes per record.<br/>
     * For example CORE-000351 - USUBJID must be unique across all studies.
     */
    RECORD_DATA("Record Data", true),

    /**
     * Rules matching on the metadata of the data set.<br/>
     * These rules have a single boolean outcome per domain.<br/>
     * For example CORE-000765 - The submitted dataset is larger than 5 GB.
     */
    DATASET_METADATA_CHECK("Dataset Metadata Check", false),

    /**
     * Rules matching on the variable metadata.<br/>
     * These rules have a single boolean outcome per domain.<br/>
     * For example CORE-000355 - Part A: Raise an error when a Required variable is not present in
     * the dataset.
     */
    VARIABLE_METADATA_CHECK("Variable Metadata Check", false),

    /**
     * Rules matching on the variable metadata compared to the library metadata.<br/>
     * These rules have a single boolean outcome per domain.<br/>
     * For example CORE-001082 - Raise an error when the variable type does not match IG Type (for
     * domains in IG) or Model Type (custom domains)
     */
    VARIABLE_METADATA_CHECK_AGAINST_LIBRARY("Variable Metadata Check against Library Metadata", false),

    /**
     * Rules matching on the item (column) metadata against the library metadata.<br/>
     * These rules have a single boolean outcome per domain.<br/>
     * For example CORE-001081 - Raise an error when the metadata attribute of variable role does
     * not match the IG role for domain in IG, or model role (for custom domains)
     */
    DEFINE_ITEM_METADATA_CHECK_AGAINST_LIBRARY("Define Item Metadata Check against Library Metadata", false),

    /**
     * Rules matching on the presence of domains.<br/>
     * These rules have a single boolean outcome per domain.<br/>
     * For example CORE-000188 - Trigger error if MS dataset is present and MB dataset is not
     * present.
     */
    DOMAIN_PRESENCE_CHECK("Domain Presence Check", false),

    /**
     * Rules matching on single values compared to data set metadata.<br/>
     * These rules have separate boolean outcomes per record.<br/>
     * For example CORE-000356 Part B: Raise an error when a Required variable is null.
     */
    VALUE_CHECK_WITH_DATASET_METADATA("Value Check with Dataset Metadata", true),

    /**
     * Rules matching on single values compared to variable metadata.<br/>
     * These rules have separate boolean outcomes per record.<br/>
     * For example CORE-000890 - Text variable in submitted dataset should not contain '.' as an
     * entire value.
     */
    VALUE_CHECK_WITH_VARIABLE_METADATA("Value Check with Variable Metadata", true),

    /**
     * All rule types that do not match one of the above. This is only used for new rule types that
     * are unknown to the engine.
     */
    UNKNOWN(null, false);

    // UNKNOWN carries a null value; the getter is mirrored @Nullable via lombok.addNullAnnotations.
    @Getter
    private final @Nullable String value;

    @Getter
    private final boolean valueBased;

    RuleType(@Nullable String aValue, boolean aValueBased)
    {
        value = aValue;
        valueBased = aValueBased;
    }


    /**
     * Resolves the enum from the API string value, returning {@link #UNKNOWN} for unrecognized
     * values.
     */
    public static RuleType fromValue(@Nullable String value)
    {
        if (value == null)
        {
            return UNKNOWN;
        }
        for (RuleType type : values())
        {
            if (value.equals(type.value))
            {
                return type;
            }
        }
        return UNKNOWN;
    }
}
