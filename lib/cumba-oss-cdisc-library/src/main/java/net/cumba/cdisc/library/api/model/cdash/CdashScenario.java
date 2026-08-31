package net.cumba.cdisc.library.api.model.cdash;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import net.cumba.web.api.ApiResource;
import net.cumba.web.api.Link;

/**
 * A CDASH scenario (used in CDASHIG and integrated standards).
 *
 * <p>
 * Represents a specific data collection scenario within a domain, providing context-specific field
 * definitions (e.g., different scenarios for the same domain may collect different fields depending
 * on the study type).
 * </p>
 */
public interface CdashScenario extends ApiResource
{

    /**
     * Returns the ordinal position of this scenario.
     *
     * @return the ordinal position of this scenario.
     */
    default OptionalInt ordinal()
    {
        return getInt("ordinal");
    }


    /**
     * Returns the domain code (e.g., "AE", "CM").
     *
     * @return the domain code (e.g., "AE", "CM").
     */
    default Optional<String> domain()
    {
        return getString("domain");
    }


    /**
     * Returns the domain name.
     *
     * @return the domain name.
     */
    default Optional<String> domainName()
    {
        return getString("domainName");
    }


    /**
     * Returns the scenario identifier.
     *
     * @return the scenario identifier.
     */
    default Optional<String> scenario()
    {
        return getString("scenario");
    }


    /**
     * Returns embedded fields within this scenario.
     *
     * @return embedded fields within this scenario.
     */
    default List<CdashField> fields()
    {
        return getList("fields", CdashField.class);
    }


    /**
     * Returns a link to the parent product.
     *
     * @return link to the parent product.
     */
    default Optional<Link> parentProductLink()
    {
        return getLink("parentProduct");
    }


    /**
     * Returns a link to the parent class.
     *
     * @return link to the parent class.
     */
    default Optional<Link> parentClassLink()
    {
        return getLink("parentClass");
    }


    /**
     * Returns a link to the parent domain.
     *
     * @return link to the parent domain.
     */
    default Optional<Link> parentDomainLink()
    {
        return getLink("parentDomain");
    }


    /**
     * Returns a link to the prior version of this scenario.
     *
     * @return link to the prior version of this scenario.
     */
    default Optional<Link> priorVersionLink()
    {
        return getLink("priorVersion");
    }
}
