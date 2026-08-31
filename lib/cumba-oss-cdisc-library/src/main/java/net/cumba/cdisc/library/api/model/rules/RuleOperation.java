package net.cumba.cdisc.library.api.model.rules;

import java.util.List;
import java.util.Optional;

import net.cumba.web.api.ApiResource;

/**
 * An operation to compute a derived value for use in rule conditions.
 *
 * <p>
 * Operations are pre-processing steps executed before rule evaluation to populate context variables
 * (e.g., distinct values, min/max dates, record counts). Each operation specifies an operator, an
 * optional domain and grouping, filters, codelist references, and various metadata fields for
 * library lookups and controlled terminology resolution.
 * </p>
 */
public interface RuleOperation extends ApiResource
{

    /** Returns the unique operation identifier. */
    default Optional<String> id()
    {
        return getString("id");
    }


    /** Returns the variable name to store the operation result. */
    default Optional<String> name()
    {
        return getString("name");
    }


    /** Returns the operator as an API string value (e.g. "distinct"). */
    default Optional<String> operator()
    {
        return getString("operator");
    }


    /** Returns the operator as a typed enum. */
    default OperationOperator operatorEnum()
    {
        return operator().map(OperationOperator::fromValue).orElse(OperationOperator.UNKNOWN);
    }


    /** Domain name (may contain "--" wildcard replaced with actual table name at runtime). */
    default Optional<String> domain()
    {
        return getString("domain");
    }


    /** Group columns for grouped aggregation (e.g., min_date by USUBJID). */
    default List<String> group()
    {
        return getStringList("group");
    }


    /**
     * Column/value filter as a JSON object (e.g., {@code {"VARIABLE": "value"}}). Use
     * {@link ApiResource#getFieldNames()} and {@link ApiResource#getString(String)} to iterate the
     * filter entries.
     */
    default Optional<ApiResource> filter()
    {
        return getObject("filter");
    }


    /** Codelist identifiers used by this operation. */
    default List<String> codelists()
    {
        return getStringList("codelists");
    }


    /** Level qualifier for the operation. */
    default Optional<String> level()
    {
        return getString("level");
    }


    /** Expected return type hint. */
    default Optional<String> returntype()
    {
        return getString("returntype");
    }


    /** Key name for key-based lookups. */
    default Optional<String> keyName()
    {
        return getString("key_name");
    }


    /** Key value for key-based lookups. */
    default Optional<String> keyValue()
    {
        return getString("key_value");
    }


    /** Controlled terminology attribute name. */
    default Optional<String> ctAttribute()
    {
        return getString("ct_attribute");
    }


    /** Version qualifier for library lookups. */
    default Optional<String> version()
    {
        return getString("version");
    }


    /** Controlled terminology package types. */
    default List<String> ctPackageTypes()
    {
        return getStringList("ct_package_types");
    }


    /** Regular expression used by the operation. */
    default Optional<String> regex()
    {
        return getString("regex");
    }


    /** Whether the value should be resolved as a column reference. */
    default Optional<Boolean> valueIsReference()
    {
        return getBoolean("value_is_reference");
    }
}
