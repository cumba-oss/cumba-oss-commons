package net.cumba.cdisc.library.api.model.adam;

import java.util.List;
import java.util.Optional;

import net.cumba.web.api.ApiResource;
import net.cumba.web.api.Link;

/**
 * An ADaM product version (response from {@code /mdr/adam/{product}}).
 *
 * <p>
 * Represents a specific version of the Analysis Data Model (ADaM), containing data structures that
 * define the standard analysis datasets and their variables.
 * </p>
 */
public interface AdamProduct extends ApiResource
{

    /**
     * Returns the product name.
     *
     * @return the product name.
     */
    default Optional<String> name()
    {
        return getString("name");
    }


    /**
     * Returns the product label.
     *
     * @return the product label.
     */
    default Optional<String> label()
    {
        return getString("label");
    }


    /**
     * Returns the product description.
     *
     * @return the product description.
     */
    default Optional<String> description()
    {
        return getString("description");
    }


    /**
     * Returns the source organization (e.g., "CDISC").
     *
     * @return the source organization (e.g., "CDISC").
     */
    default Optional<String> source()
    {
        return getString("source");
    }


    /**
     * Returns the effective date of this product version.
     *
     * @return the effective date of this product version.
     */
    default Optional<String> effectiveDate()
    {
        return getString("effectiveDate");
    }


    /**
     * Returns the registration status (e.g., "Final").
     *
     * @return the registration status (e.g., "Final").
     */
    default Optional<String> registrationStatus()
    {
        return getString("registrationStatus");
    }


    /**
     * Returns the product version identifier.
     *
     * @return the product version identifier.
     */
    default Optional<String> version()
    {
        return getString("version");
    }


    /**
     * Returns embedded data structures in this product version.
     *
     * @return embedded data structures in this product version.
     */
    default List<AdamDataStructure> dataStructures()
    {
        return getList("dataStructures", AdamDataStructure.class);
    }


    /**
     * Returns link to the prior version of this product.
     *
     * @return link to the prior version of this product.
     */
    default Optional<Link> priorVersionLink()
    {
        return getLink("priorVersion");
    }
}
