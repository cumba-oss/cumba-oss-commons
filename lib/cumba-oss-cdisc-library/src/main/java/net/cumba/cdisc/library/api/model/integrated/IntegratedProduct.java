package net.cumba.cdisc.library.api.model.integrated;

import java.util.List;
import java.util.Optional;

import net.cumba.web.api.ApiResource;
import net.cumba.web.api.Link;

/**
 * An integrated standard product version (response from
 * {@code /mdr/integrated/{standard}/{version}}).
 *
 * <p>
 * Represents a version of an integrated standard that combines multiple CDISC standards (e.g.,
 * CDASH + SDTM + ADaM) into a single harmonized specification.
 * </p>
 */
public interface IntegratedProduct extends ApiResource
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


    /** Returns links to the component standards included in this integrated product. */
    default List<Link> standardLinks()
    {
        return getLinks("standards");
    }


    /** Returns links to the underlying models. */
    default List<Link> modelLinks()
    {
        return getLinks("models");
    }


    /** Returns a link to the prior version of this product. */
    default Optional<Link> priorVersionLink()
    {
        return getLink("priorVersion");
    }
}
