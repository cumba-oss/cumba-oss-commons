package net.cumba.cdisc.library.api.model.sdtm;

import java.util.List;
import java.util.Optional;

import net.cumba.web.api.ApiResource;
import net.cumba.web.api.Link;

/**
 * An SDTM/SDTM-IG observation class.
 *
 * <p>
 * Represents a general observation class (e.g., Events, Findings, Interventions) that groups
 * related datasets and defines class-level variables shared across those datasets.
 * </p>
 */
public interface SdtmClass extends ApiResource
{

    /** Returns the ordinal position of this class within the product. */
    default Optional<String> ordinal()
    {
        return getString("ordinal");
    }


    /** Returns the class name (e.g., "Events", "Findings", "Interventions"). */
    default Optional<String> name()
    {
        return getString("name");
    }


    /** Returns the class label. */
    default Optional<String> label()
    {
        return getString("label");
    }


    /** Returns the class description. */
    default Optional<String> description()
    {
        return getString("description");
    }


    /** Returns embedded class-level variables shared by all datasets in this class. */
    default List<SdtmVariable> classVariables()
    {
        return getList("classVariables", SdtmVariable.class);
    }


    /** Returns embedded datasets within this class. */
    default List<SdtmDataset> datasets()
    {
        return getList("datasets", SdtmDataset.class);
    }


    /** Returns links to subclasses of this class. */
    default List<Link> subclassLinks()
    {
        return getLinks("subclasses");
    }


    /** Returns link to the parent product. */
    default Optional<Link> parentProductLink()
    {
        return getLink("parentProduct");
    }


    /** Returns link to the parent class (for subclasses). */
    default Optional<Link> parentClassLink()
    {
        return getLink("parentClass");
    }


    /** Returns link to the corresponding model class. */
    default Optional<Link> modelClassLink()
    {
        return getLink("modelClass");
    }


    /** Returns link to the prior version of this class. */
    default Optional<Link> priorVersionLink()
    {
        return getLink("priorVersion");
    }
}
