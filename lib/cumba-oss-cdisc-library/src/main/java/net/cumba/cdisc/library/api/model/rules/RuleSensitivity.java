package net.cumba.cdisc.library.api.model.rules;

import org.jspecify.annotations.Nullable;

/**
 * Known rule sensitivity values, indicating the granularity at which a rule is evaluated.
 *
 * <p>
 * Defines whether a rule operates at the individual record level, the entire dataset level, or a
 * grouped subset of records. Unrecognized API values resolve to {@link #UNKNOWN}.
 * </p>
 */
public enum RuleSensitivity
{

    RECORD("Record"), DATASET("Dataset"), GROUP("Group"), UNKNOWN(null);

    // UNKNOWN carries a null value.
    private final @Nullable String value;

    RuleSensitivity(@Nullable String value)
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
    public static RuleSensitivity fromValue(@Nullable String value)
    {
        if (value == null)
        {
            return UNKNOWN;
        }
        for (RuleSensitivity s : values())
        {
            if (value.equals(s.value))
            {
                return s;
            }
        }
        return UNKNOWN;
    }
}
