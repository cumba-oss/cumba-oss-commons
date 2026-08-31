package net.cumba.cdisc.library.api.model.cdash;

import java.util.List;
import java.util.Optional;

import net.cumba.web.api.ApiResource;
import net.cumba.web.api.Link;

/**
 * A CDASH product version (response from {@code /mdr/cdash/{version}}).
 *
 * <p>
 * Represents a specific version of the Clinical Data Acquisition Standards Harmonization (CDASH)
 * standard, containing observation classes and domains that define standard data collection fields.
 * </p>
 */
public interface CdashProduct extends ApiResource
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


    /** Returns the source organization (e.g., {@code "CDISC"}). */
    default Optional<String> source()
    {
        return getString("source");
    }


    /** Returns the effective date of this product version. */
    default Optional<String> effectiveDate()
    {
        return getString("effectiveDate");
    }


    /** Returns the registration status (e.g., {@code "Final"}). */
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
    default List<CdashClass> classes()
    {
        return getList("classes", CdashClass.class);
    }


    /** Returns the embedded domains in this product version. */
    default List<CdashDomain> domains()
    {
        return getList("domains", CdashDomain.class);
    }


    /** Returns the link to the prior version of this product. */
    default Optional<Link> priorVersionLink()
    {
        return getLink("priorVersion");
    }
}
