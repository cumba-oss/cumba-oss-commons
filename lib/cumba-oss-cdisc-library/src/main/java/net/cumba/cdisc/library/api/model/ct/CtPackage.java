package net.cumba.cdisc.library.api.model.ct;

import java.util.List;
import java.util.Optional;

import net.cumba.web.api.ApiResource;
import net.cumba.web.api.Link;

/**
 * A Controlled Terminology (CT) package (response from {@code /mdr/ct/packages/{package}}).
 *
 * <p>
 * Represents a versioned collection of codelists published by CDISC for a specific standard and
 * effective date.
 * </p>
 */
public interface CtPackage extends ApiResource
{

    /**
     * Returns the package name.
     *
     * @return the package name.
     */
    default Optional<String> name()
    {
        return getString("name");
    }


    /**
     * Returns the package label.
     *
     * @return the package label.
     */
    default Optional<String> label()
    {
        return getString("label");
    }


    /**
     * Returns the package description.
     *
     * @return the package description.
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
     * Returns the effective date of this package.
     *
     * @return the effective date of this package.
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
     * Returns the package version identifier.
     *
     * @return the package version identifier.
     */
    default Optional<String> version()
    {
        return getString("version");
    }


    /**
     * Returns embedded codelists in this package.
     *
     * @return embedded codelists in this package.
     */
    default List<CtCodelist> codelists()
    {
        return getList("codelists", CtCodelist.class);
    }


    /**
     * Returns a link to the prior version of this package.
     *
     * @return link to the prior version of this package.
     */
    default Optional<Link> priorVersionLink()
    {
        return getLink("priorVersion");
    }
}
