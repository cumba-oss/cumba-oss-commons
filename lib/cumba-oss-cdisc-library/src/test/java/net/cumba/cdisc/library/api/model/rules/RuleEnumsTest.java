package net.cumba.cdisc.library.api.model.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RuleEnumsTest
{

    // --- ConditionOperator ---

    @Test
    void conditionOperatorFromValueResolvesKnownValues()
    {
        assertEquals(ConditionOperator.EMPTY, ConditionOperator.fromValue("empty"));
        assertEquals(ConditionOperator.EQUAL_TO, ConditionOperator.fromValue("equal_to"));
        assertEquals(ConditionOperator.GREATER_THAN, ConditionOperator.fromValue("greater_than"));
        assertEquals(ConditionOperator.CONTAINS, ConditionOperator.fromValue("contains"));
        assertEquals(ConditionOperator.MATCHES_REGEX, ConditionOperator.fromValue("matches_regex"));
        assertEquals(ConditionOperator.IS_UNIQUE_SET, ConditionOperator.fromValue("is_unique_set"));
        assertEquals(ConditionOperator.IS_COMPLETE_DATE,
                ConditionOperator.fromValue("is_complete_date"));
        assertEquals(ConditionOperator.IS_CONTAINED_BY,
                ConditionOperator.fromValue("is_contained_by"));
    }


    @Test
    void conditionOperatorFromValueReturnsUnknownForNull()
    {
        assertEquals(ConditionOperator.UNKNOWN, ConditionOperator.fromValue(null));
    }


    @Test
    void conditionOperatorFromValueReturnsUnknownForUnrecognized()
    {
        assertEquals(ConditionOperator.UNKNOWN, ConditionOperator.fromValue("made_up_operator"));
    }


    @Test
    void conditionOperatorValueRoundTrips()
    {
        for (ConditionOperator op : ConditionOperator.values())
        {
            if (op != ConditionOperator.UNKNOWN)
            {
                assertNotNull(op.value());
                assertEquals(op, ConditionOperator.fromValue(op.value()));
            }
        }
    }


    @Test
    void conditionOperatorUnknownHasNullValue()
    {
        assertNull(ConditionOperator.UNKNOWN.value());
    }

    // --- OperationOperator ---


    @Test
    void operationOperatorFromValueResolvesKnownValues()
    {
        assertEquals(OperationOperator.DISTINCT, OperationOperator.fromValue("distinct"));
        assertEquals(OperationOperator.MAX, OperationOperator.fromValue("max"));
        assertEquals(OperationOperator.RECORD_COUNT, OperationOperator.fromValue("record_count"));
        assertEquals(OperationOperator.VARIABLE_EXISTS,
                OperationOperator.fromValue("variable_exists"));
        assertEquals(OperationOperator.CODELIST_TERMS,
                OperationOperator.fromValue("codelist_terms"));
        assertEquals(OperationOperator.DY, OperationOperator.fromValue("dy"));
    }


    @Test
    void operationOperatorFromValueReturnsUnknownForNull()
    {
        assertEquals(OperationOperator.UNKNOWN, OperationOperator.fromValue(null));
    }


    @Test
    void operationOperatorFromValueReturnsUnknownForUnrecognized()
    {
        assertEquals(OperationOperator.UNKNOWN, OperationOperator.fromValue("no_such_op"));
    }


    @Test
    void operationOperatorValueRoundTrips()
    {
        for (OperationOperator op : OperationOperator.values())
        {
            if (op != OperationOperator.UNKNOWN)
            {
                assertNotNull(op.value());
                assertEquals(op, OperationOperator.fromValue(op.value()));
            }
        }
    }

    // --- RuleExecutability ---


    @Test
    void ruleExecutabilityFromValueResolvesKnownValues()
    {
        assertEquals(RuleExecutability.FULLY_EXECUTABLE,
                RuleExecutability.fromValue("Fully Executable"));
        assertEquals(RuleExecutability.PARTIALLY_EXECUTABLE,
                RuleExecutability.fromValue("Partially Executable"));
        assertEquals(RuleExecutability.PARTIALLY_EXECUTABLE_OVERREPORTING,
                RuleExecutability.fromValue("Partially Executable - Possible Overreporting"));
        assertEquals(RuleExecutability.PARTIALLY_EXECUTABLE_UNDERREPORTING,
                RuleExecutability.fromValue("Partially Executable - Possible Underreporting"));
    }


    @Test
    void ruleExecutabilityFromValueReturnsUnknownForNull()
    {
        assertEquals(RuleExecutability.UNKNOWN, RuleExecutability.fromValue(null));
    }


    @Test
    void ruleExecutabilityFromValueReturnsUnknownForUnrecognized()
    {
        assertEquals(RuleExecutability.UNKNOWN, RuleExecutability.fromValue("Not Executable"));
    }


    @Test
    void ruleExecutabilityValueRoundTrips()
    {
        for (RuleExecutability e : RuleExecutability.values())
        {
            if (e != RuleExecutability.UNKNOWN)
            {
                assertNotNull(e.value());
                assertEquals(e, RuleExecutability.fromValue(e.value()));
            }
        }
    }

    // --- RuleSensitivity ---


    @Test
    void ruleSensitivityFromValueResolvesKnownValues()
    {
        assertEquals(RuleSensitivity.RECORD, RuleSensitivity.fromValue("Record"));
        assertEquals(RuleSensitivity.DATASET, RuleSensitivity.fromValue("Dataset"));
        assertEquals(RuleSensitivity.GROUP, RuleSensitivity.fromValue("Group"));
    }


    @Test
    void ruleSensitivityFromValueReturnsUnknownForNull()
    {
        assertEquals(RuleSensitivity.UNKNOWN, RuleSensitivity.fromValue(null));
    }


    @Test
    void ruleSensitivityFromValueReturnsUnknownForUnrecognized()
    {
        assertEquals(RuleSensitivity.UNKNOWN, RuleSensitivity.fromValue("Study"));
    }


    @Test
    void ruleSensitivityValueRoundTrips()
    {
        for (RuleSensitivity s : RuleSensitivity.values())
        {
            if (s != RuleSensitivity.UNKNOWN)
            {
                assertNotNull(s.value());
                assertEquals(s, RuleSensitivity.fromValue(s.value()));
            }
        }
    }

    // --- RuleType ---


    @Test
    void ruleTypeFromValueResolvesKnownValues()
    {
        assertEquals(RuleType.RECORD_DATA, RuleType.fromValue("Record Data"));
        assertEquals(RuleType.DATASET_METADATA_CHECK, RuleType.fromValue("Dataset Metadata Check"));
        assertEquals(RuleType.VARIABLE_METADATA_CHECK,
                RuleType.fromValue("Variable Metadata Check"));
        assertEquals(RuleType.DOMAIN_PRESENCE_CHECK, RuleType.fromValue("Domain Presence Check"));
        assertEquals(RuleType.VALUE_CHECK_WITH_DATASET_METADATA,
                RuleType.fromValue("Value Check with Dataset Metadata"));
    }


    @Test
    void ruleTypeFromValueReturnsUnknownForNull()
    {
        assertEquals(RuleType.UNKNOWN, RuleType.fromValue(null));
    }


    @Test
    void ruleTypeFromValueReturnsUnknownForUnrecognized()
    {
        assertEquals(RuleType.UNKNOWN, RuleType.fromValue("Custom Check"));
    }


    @Test
    void ruleTypeValueRoundTrips()
    {
        for (RuleType t : RuleType.values())
        {
            if (t != RuleType.UNKNOWN)
            {
                assertNotNull(t.getValue());
                assertEquals(t, RuleType.fromValue(t.getValue()));
            }
        }
    }


    @Test
    void ruleTypeValueBasedIsCorrect()
    {
        assertTrue(RuleType.RECORD_DATA.isValueBased());
        assertTrue(RuleType.VALUE_CHECK_WITH_DATASET_METADATA.isValueBased());
        assertTrue(RuleType.VALUE_CHECK_WITH_VARIABLE_METADATA.isValueBased());
        assertFalse(RuleType.DATASET_METADATA_CHECK.isValueBased());
        assertFalse(RuleType.VARIABLE_METADATA_CHECK.isValueBased());
        assertFalse(RuleType.DOMAIN_PRESENCE_CHECK.isValueBased());
        assertFalse(RuleType.UNKNOWN.isValueBased());
    }
}
