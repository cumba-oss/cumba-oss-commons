package net.cumba.cdisc.library.api.model.sdtm;

import java.util.List;
import java.util.Optional;

import net.cumba.web.api.ApiResource;
import net.cumba.web.api.Link;

/**
 * An SDTM or SDTM-IG product version (response from {@code /mdr/sdtm/{version}} or
 * {@code /mdr/sdtmig/{version}}).
 *
 * <p>
 * Represents a specific version of the Study Data Tabulation Model (SDTM), containing observation
 * classes that define the standard tabulation datasets and their variables.
 * </p>
 */
public interface SdtmProduct extends ApiResource
{

    /** Returns the product name. */
    default Optional<String> name()
    {
        return getString("name");
    }


    /** Returns the product label. */
    default Optional<String> label()
    {
        return getString("label");
    }


    /** Returns the product description. */
    default Optional<String> description()
    {
        return getString("description");
    }


    /** Returns the source organization (e.g., "CDISC"). */
    default Optional<String> source()
    {
        return getString("source");
    }


    /** Returns the effective date of this product version. */
    default Optional<String> effectiveDate()
    {
        return getString("effectiveDate");
    }


    /** Returns the registration status (e.g., "Final"). */
    default Optional<String> registrationStatus()
    {
        return getString("registrationStatus");
    }


    /** Returns the product version identifier. */
    default Optional<String> version()
    {
        return getString("version");
    }


    /** Returns the embedded observation classes in this product version. */
    default List<SdtmClass> classes()
    {
        return getList("classes", SdtmClass.class);
    }


    /**
     * Returns the product-level datasets, when present. SDTM <em>Model</em> responses carry a
     * top-level {@code datasets} array (the class-less datasets such as {@code DM}, {@code CO},
     * {@code SE}, trial-design and relationship datasets); SDTM-IG responses generally nest their
     * datasets inside {@link #classes()} instead and leave this list empty. Mirrors Python's
     * {@code get_model_domain_metadata}, which searches {@code model_details["datasets"]}.
     */
    default List<SdtmDataset> datasets()
    {
        return getList("datasets", SdtmDataset.class);
    }


    /** Returns a link to the underlying SDTM model. */
    default Optional<Link> modelLink()
    {
        return getLink("model");
    }


    /** Returns a link to the prior version of this product. */
    default Optional<Link> priorVersionLink()
    {
        return getLink("priorVersion");
    }
}
