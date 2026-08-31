package net.cumba.cdisc.library.api.model.rules;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

import net.cumba.web.api.ApiResource;

/**
 * A typed map view over the {@code rules} object in a {@link RulePackage}. The underlying JSON is
 * an object mapping rule UUIDs to rule definitions.
 *
 * <p>
 * Provides map-like access methods (get, size, keys, values, containsKey) for navigating the rules
 * within a package. All access is lazy -- no copying or upfront wrapping occurs.
 * </p>
 */
public interface RuleMap extends ApiResource
{

    /** Returns a rule by its UUID, or empty if not found. */
    default Optional<Rule> get(String ruleId)
    {
        return getObject(ruleId, Rule.class);
    }


    /** Returns the number of rules. */
    default int size()
    {
        return getFieldCount();
    }


    /** Returns whether this map is empty. */
    default boolean isEmpty()
    {
        return getFieldCount() == 0;
    }


    /** Returns whether a rule with the given UUID exists. */
    default boolean containsKey(String ruleId)
    {
        return containsFieldName(ruleId);
    }


    /** Returns the set of rule UUIDs. */
    default Set<String> keys()
    {
        return getFieldNames();
    }


    /** Returns all rules as an unmodifiable collection. */
    default Collection<Rule> values()
    {
        return getFieldNames().stream().map(n -> getObject(n, Rule.class))
                .filter(Optional::isPresent).map(Optional::get).toList();
    }
}
