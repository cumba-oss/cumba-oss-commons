package net.cumba.cdisc.library.api.model.rules;

import java.util.List;
import java.util.Optional;

import net.cumba.web.api.ApiResource;

/**
 * A dataset to match against when evaluating a rule (cross-dataset checks).
 *
 * <p>
 * Defines a dataset that participates in a cross-dataset rule evaluation, specifying the dataset
 * name, join keys, an optional wildcard pattern for dynamic name matching, a child flag for
 * parent-child relationships, and a join type controlling how records are combined.
 * </p>
 */
public interface RuleMatchDataset extends ApiResource
{

    /** Returns the dataset name to match. */
    default Optional<String> name()
    {
        return getString("Name");
    }


    /** Returns the list of key variable names used for joining. */
    default List<String> keys()
    {
        return getStringList("Keys");
    }


    /** Wildcard pattern for dataset name matching (e.g., "--" replaced with domain). */
    default Optional<String> wildcard()
    {
        return getString("Wildcard");
    }


    /** Whether this is a child dataset in a parent-child relationship. */
    default Optional<Boolean> child()
    {
        return getBoolean("Child");
    }


    /** Join type for matching (e.g., inner, outer). */
    default Optional<String> joinType()
    {
        return getString("Join_Type");
    }
}
