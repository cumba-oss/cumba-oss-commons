package net.cumba.cdisc.library.api.model.adam;

import java.util.List;
import java.util.Optional;

import net.cumba.web.api.ApiResource;
import net.cumba.web.api.Link;

/**
 * An ADaM data structure (response from {@code /mdr/adam/{product}/datastructures/{ds}}).
 *
 * <p>
 * Represents a standard analysis dataset structure (e.g., BDS, ADSL) that groups related analysis
 * variable sets.
 * </p>
 */
public interface AdamDataStructure extends ApiResource
{

    /**
     * Returns the ordinal position of this data structure within the product.
     *
     * @return the ordinal position of this data structure within the product.
     */
    default Optional<String> ordinal()
    {
        return getString("ordinal");
    }


    /**
     * Returns the data structure name (e.g., "BDS", "ADSL").
     *
     * @return the data structure name (e.g., "BDS", "ADSL").
     */
    default Optional<String> name()
    {
        return getString("name");
    }


    /**
     * Returns the data structure label.
     *
     * @return the data structure label.
     */
    default Optional<String> label()
    {
        return getString("label");
    }


    /**
     * Returns the data structure description.
     *
     * @return the data structure description.
     */
    default Optional<String> description()
    {
        return getString("description");
    }


    /**
     * Returns the data structure class (e.g., "BASIC DATA STRUCTURE").
     *
     * @return the data structure class (e.g., "BASIC DATA STRUCTURE").
     */
    default Optional<String> className()
    {
        return getString("class");
    }


    /**
     * Returns the data structure sub class.
     *
     * @return the data structure sub class.
     */
    default Optional<String> subClass()
    {
        return getString("subClass");
    }


    /**
     * Returns the data structure status.
     *
     * @return the data structure status.
     */
    default Optional<String> status()
    {
        return getString("status");
    }


    /**
     * Returns embedded analysis variable sets within this data structure.
     *
     * @return embedded analysis variable sets within this data structure.
     */
    default List<AdamVariableSet> analysisVariableSets()
    {
        return getList("analysisVariableSets", AdamVariableSet.class);
    }


    /**
     * Returns link to the parent product.
     *
     * @return link to the parent product.
     */
    default Optional<Link> parentProductLink()
    {
        return getLink("parentProduct");
    }


    /**
     * Returns link to the prior version of this data structure.
     *
     * @return link to the prior version of this data structure.
     */
    default Optional<Link> priorVersionLink()
    {
        return getLink("priorVersion");
    }


    /**
     * Returns link to the parent class data structure (for subclassed structures).
     *
     * @return link to the parent class data structure (for subclassed structures).
     */
    default Optional<Link> parentClassDatastructureLink()
    {
        return getLink("parentClassDatastructure");
    }
}
