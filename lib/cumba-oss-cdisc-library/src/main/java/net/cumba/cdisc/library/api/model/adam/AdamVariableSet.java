package net.cumba.cdisc.library.api.model.adam;

import java.util.List;
import java.util.Optional;

import net.cumba.web.api.ApiResource;
import net.cumba.web.api.Link;

/**
 * An ADaM analysis variable set within a data structure.
 *
 * <p>
 * Groups related analysis variables together within a data structure (e.g., the set of timing
 * variables or the set of analysis criterion variables).
 * </p>
 */
public interface AdamVariableSet extends ApiResource
{

    /** Returns the ordinal position of this variable set within the data structure. */
    default Optional<String> ordinal()
    {
        return getString("ordinal");
    }


    /** Returns the analysis variable set name. */
    default Optional<String> name()
    {
        return getString("name");
    }


    /** Returns the analysis variable set label. */
    default Optional<String> label()
    {
        return getString("label");
    }


    /** Returns embedded analysis variables within this variable set. */
    default List<AdamVariable> analysisVariables()
    {
        return getList("analysisVariables", AdamVariable.class);
    }


    /** Returns the link to the parent product. */
    default Optional<Link> parentProductLink()
    {
        return getLink("parentProduct");
    }


    /** Returns the link to the parent data structure. */
    default Optional<Link> parentDatastructureLink()
    {
        return getLink("parentDatastructure");
    }


    /** Returns the link to the prior version of this variable set. */
    default Optional<Link> priorVersionLink()
    {
        return getLink("priorVersion");
    }


    /** Returns the link to the parent class variable set (for subclassed structures). */
    default Optional<Link> parentClassVariableSetLink()
    {
        return getLink("parentClassVariableSet");
    }
}
