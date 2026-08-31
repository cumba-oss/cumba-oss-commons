package net.cumba.cdisc.library.api.model.qrs;

import java.util.Optional;
import java.util.OptionalInt;

import net.cumba.web.api.ApiResource;
import net.cumba.web.api.Link;

/**
 * An item (question) within a QRS instrument.
 *
 * <p>
 * Represents a single question or assessment item, including its text and the response group that
 * defines the permitted answers.
 * </p>
 */
public interface QrsItem extends ApiResource
{

    /**
     * Returns the ordinal position of this item within the instrument.
     *
     * @return the ordinal position of this item within the instrument.
     */
    default OptionalInt ordinal()
    {
        return getInt("ordinal");
    }


    /**
     * Returns the item label.
     *
     * @return the item label.
     */
    default Optional<String> label()
    {
        return getString("label");
    }


    /**
     * Returns the question text.
     *
     * @return the question text.
     */
    default Optional<String> questionText()
    {
        return getString("questionText");
    }


    /**
     * Returns the item code.
     *
     * @return the item code.
     */
    default Optional<String> itemCode()
    {
        return getString("itemCode");
    }


    /**
     * Returns the response group this item belongs to (embedded object).
     *
     * @return the response group this item belongs to (embedded object).
     */
    default Optional<QrsResponseGroup> responseGroup()
    {
        return getObject("responseGroup", QrsResponseGroup.class);
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


    /**
     * Returns link to the TESTCD term for this item.
     *
     * @return link to the TESTCD term for this item.
     */
    default Optional<Link> instrumentItemTestcdLink()
    {
        return getLink("instrumentItemTESTCD");
    }


    /**
     * Returns link to the TEST term for this item.
     *
     * @return link to the TEST term for this item.
     */
    default Optional<Link> instrumentItemTestLink()
    {
        return getLink("instrumentItemTEST");
    }
}
