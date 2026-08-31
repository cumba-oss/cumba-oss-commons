package net.cumba.cdisc.library.api.model.sdtm;

import java.util.List;
import java.util.Optional;

import net.cumba.web.api.ApiResource;
import net.cumba.web.api.Link;

/**
 * An SDTM/SDTM-IG dataset (response from {@code /mdr/sdtm/{version}/datasets/{dataset}} or
 * {@code /mdr/sdtmig/{version}/datasets/{dataset}}).
 *
 * <p>
 * Represents a standard tabulation dataset (e.g., DM, AE, LB) with its variables, structure, and
 * status.
 * </p>
 */
public interface SdtmDataset extends ApiResource
{

    /** Returns the ordinal position of this dataset within its class. */
    default Optional<String> ordinal()
    {
        return getString("ordinal");
    }


    /** Returns the dataset name (e.g., "DM", "AE", "LB"). */
    default Optional<String> name()
    {
        return getString("name");
    }


    /** Returns the dataset label. */
    default Optional<String> label()
    {
        return getString("label");
    }


    /** Returns the dataset description. */
    default Optional<String> description()
    {
        return getString("description");
    }


    /** Returns the dataset structure (e.g., "One record per subject"). */
    default Optional<String> datasetStructure()
    {
        return getString("datasetStructure");
    }


    /** Returns the dataset status. */
    default Optional<String> status()
    {
        return getString("status");
    }


    /** Returns embedded dataset variables. */
    default List<SdtmVariable> datasetVariables()
    {
        return getList("datasetVariables", SdtmVariable.class);
    }


    /** Returns link to the parent class. */
    default Optional<Link> parentClassLink()
    {
        return getLink("parentClass");
    }


    /** Returns link to the parent product. */
    default Optional<Link> parentProductLink()
    {
        return getLink("parentProduct");
    }


    /** Returns link to the corresponding model dataset. */
    default Optional<Link> modelDatasetLink()
    {
        return getLink("modelDataset");
    }


    /** Returns link to the prior version of this dataset. */
    default Optional<Link> priorVersionLink()
    {
        return getLink("priorVersion");
    }
}
