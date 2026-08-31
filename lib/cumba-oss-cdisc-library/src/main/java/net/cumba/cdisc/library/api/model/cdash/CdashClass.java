package net.cumba.cdisc.library.api.model.cdash;

import java.util.List;
import java.util.Optional;

import net.cumba.web.api.ApiResource;
import net.cumba.web.api.Link;

/**
 * A CDASH observation class (response from {@code /mdr/cdash/{version}/classes/{className}}).
 *
 * <p>
 * Represents a general observation class (e.g., Events, Findings, Interventions) that groups
 * related domains and defines class-level model fields.
 * </p>
 */
public interface CdashClass extends ApiResource
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


    /** Returns the embedded model fields for this class. */
    default List<CdashField> cdashModelFields()
    {
        return getList("cdashModelFields", CdashField.class);
    }


    /** Returns the embedded domains within this class. */
    default List<CdashDomain> domains()
    {
        return getList("domains", CdashDomain.class);
    }


    /** Returns the link to the parent product. */
    default Optional<Link> parentProductLink()
    {
        return getLink("parentProduct");
    }


    /** Returns the link to the parent class (for subclasses). */
    default Optional<Link> parentClassLink()
    {
        return getLink("parentClass");
    }


    /** Returns the link to the prior version of this class. */
    default Optional<Link> priorVersionLink()
    {
        return getLink("priorVersion");
    }
}
