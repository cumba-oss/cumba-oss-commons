package net.cumba.cdisc.library.api.model.qrs;

import java.util.Optional;
import java.util.OptionalInt;

import net.cumba.web.api.ApiResource;
import net.cumba.web.api.Link;

/**
 * A single response option within a response group.
 *
 * <p>
 * Represents one permissible answer to an instrument item, with links to the corresponding ORRES,
 * STRESC, and STRESN controlled terminology terms.
 * </p>
 */
public interface QrsResponse extends ApiResource
{

    /**
     * Returns the ordinal position of this response within the group.
     *
     * @return the ordinal position of this response within the group.
     */
    default OptionalInt ordinal()
    {
        return getInt("ordinal");
    }


    /**
     * Returns whether the standard result for this response is numeric.
     *
     * @return whether the standard result for this response is numeric.
     */
    default Optional<Boolean> isStandardResultNumeric()
    {
        return getBoolean("isStandardResultNumeric");
    }


    /**
     * Returns link to the ORRES (Original Result) term.
     *
     * @return link to the ORRES (Original Result) term.
     */
    default Optional<Link> responseOrresLink()
    {
        return getLink("responseORRES");
    }


    /**
     * Returns link to the STRESC (Standard Result in Character) term.
     *
     * @return link to the STRESC (Standard Result in Character) term.
     */
    default Optional<Link> responseStrescLink()
    {
        return getLink("responseSTRESC");
    }


    /**
     * Returns link to the STRESN (Standard Result in Numeric) term.
     *
     * @return link to the STRESN (Standard Result in Numeric) term.
     */
    default Optional<Link> responseStresnLink()
    {
        return getLink("responseSTRESN");
    }
}
