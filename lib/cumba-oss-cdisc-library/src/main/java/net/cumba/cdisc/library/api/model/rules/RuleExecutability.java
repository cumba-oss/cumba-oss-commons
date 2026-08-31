package net.cumba.cdisc.library.api.model.rules;

import org.jspecify.annotations.Nullable;

/**
 * Known rule executability values, indicating how fully a rule can be automatically evaluated.
 *
 * <p>
 * Classifies whether a conformance rule can be fully executed by an automated engine, or only
 * partially executed with potential for over- or under-reporting of violations. Unrecognized API
 * values resolve to {@link #UNKNOWN}.
 * </p>
 */
public enum RuleExecutability
{

    FULLY_EXECUTABLE("Fully Executable"),
    PARTIALLY_EXECUTABLE("Partially Executable"),
    PARTIALLY_EXECUTABLE_OVERREPORTING("Partially Executable - Possible Overreporting"),
    PARTIALLY_EXECUTABLE_UNDERREPORTING("Partially Executable - Possible Underreporting"),
    UNKNOWN(null);

    // UNKNOWN carries a null value.
    private final @Nullable String value;

    RuleExecutability(@Nullable String value)
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
    public static RuleExecutability fromValue(@Nullable String value)
    {
        if (value == null)
        {
            return UNKNOWN;
        }
        for (RuleExecutability e : values())
        {
            if (value.equals(e.value))
            {
                return e;
            }
        }
        return UNKNOWN;
    }
}
