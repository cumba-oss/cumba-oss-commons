package net.cumba.cdisc.library.api.model.rules;

import java.util.List;
import java.util.Optional;

import net.cumba.web.api.ApiResource;

/**
 * The outcome of a rule violation.
 *
 * <p>
 * Defines the message to display when a conformance rule is violated, along with the list of output
 * variable names whose values should be included in the violation report to help identify the
 * offending records.
 * </p>
 */
public interface RuleOutcome extends ApiResource
{

    /** Returns the violation message to display when this rule is triggered. */
    default Optional<String> message()
    {
        return getString("Message");
    }


    /** Returns the list of variable names to include in the violation output. */
    default List<String> outputVariables()
    {
        return getStringList("Output_Variables");
    }
}
