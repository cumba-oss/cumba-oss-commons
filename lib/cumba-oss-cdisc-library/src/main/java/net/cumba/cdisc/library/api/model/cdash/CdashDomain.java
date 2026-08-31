package net.cumba.cdisc.library.api.model.cdash;

import java.util.List;
import java.util.Optional;

import net.cumba.web.api.ApiResource;
import net.cumba.web.api.Link;

/**
 * A CDASH domain (response from {@code /mdr/cdash/{version}/domains/{domain}}).
 *
 * <p>
 * Represents a data collection domain (e.g., AE, CM, DM) containing the fields to be collected on a
 * case report form.
 * </p>
 */
public interface CdashDomain extends ApiResource
{

    /** Returns the ordinal position of this domain within the product. */
    default Optional<String> ordinal()
    {
        return getString("ordinal");
    }


    /** Returns the domain name (e.g., "AE", "CM", "DM"). */
    default Optional<String> name()
    {
        return getString("name");
    }


    /** Returns the domain label. */
    default Optional<String> label()
    {
        return getString("label");
    }


    /** Returns the domain status. */
    default Optional<String> status()
    {
        return getString("status");
    }


    /** Returns the embedded fields within this domain. */
    default List<CdashField> fields()
    {
        return getList("fields", CdashField.class);
    }


    /** Returns the link to the parent product. */
    default Optional<Link> parentProductLink()
    {
        return getLink("parentProduct");
    }


    /** Returns the link to the parent class. */
    default Optional<Link> parentClassLink()
    {
        return getLink("parentClass");
    }


    /** Returns the link to the prior version of this domain. */
    default Optional<Link> priorVersionLink()
    {
        return getLink("priorVersion");
    }
}
