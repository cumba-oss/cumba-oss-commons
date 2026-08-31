package net.cumba.cdisc.library.api.model.qrs;

import java.util.List;
import java.util.Optional;

import net.cumba.web.api.ApiResource;
import net.cumba.web.api.Link;

/**
 * A response group within a QRS instrument, defining a set of possible responses.
 *
 * <p>
 * Groups related response options together (e.g., a Likert scale from "Not at all" to "Extremely").
 * </p>
 */
public interface QrsResponseGroup extends ApiResource
{

    /**
     * Returns the response group name.
     *
     * @return the response group name.
     */
    default Optional<String> name()
    {
        return getString("name");
    }


    /**
     * Returns the response group label.
     *
     * @return the response group label.
     */
    default Optional<String> label()
    {
        return getString("label");
    }


    /**
     * Returns the response type (e.g., "Likert", "VAS").
     *
     * @return the response type (e.g., "Likert", "VAS").
     */
    default Optional<String> responseType()
    {
        return getString("responseType");
    }


    /**
     * Returns embedded responses within this group.
     *
     * @return embedded responses within this group.
     */
    default List<QrsResponse> responses()
    {
        return getList("responses", QrsResponse.class);
    }


    /**
     * Returns link to the parent instrument.
     *
     * @return link to the parent instrument.
     */
    default Optional<Link> parentInstrumentLink()
    {
        return getLink("parentInstrument");
    }
}
