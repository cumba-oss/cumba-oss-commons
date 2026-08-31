package net.cumba.cdisc.library.api.model.adam;

import java.util.List;
import java.util.Optional;

import net.cumba.web.api.ApiResource;
import net.cumba.web.api.Link;

/**
 * An ADaM analysis variable.
 *
 * <p>
 * Represents an individual variable definition within an analysis variable set, including its name,
 * label, data type, and core classification.
 * </p>
 */
public interface AdamVariable extends ApiResource
{

    /**
     * Returns the ordinal position of this variable within the variable set.
     *
     * @return the ordinal position of this variable within the variable set.
     */
    default Optional<String> ordinal()
    {
        return getString("ordinal");
    }


    /**
     * Returns the analysis variable name.
     *
     * @return the analysis variable name.
     */
    default Optional<String> name()
    {
        return getString("name");
    }


    /**
     * Returns the analysis variable label.
     *
     * @return the analysis variable label.
     */
    default Optional<String> label()
    {
        return getString("label");
    }


    /**
     * Returns the analysis variable description.
     *
     * @return the analysis variable description.
     */
    default Optional<String> description()
    {
        return getString("description");
    }


    /**
     * Returns the core classifier ("Perm", "Req", or "Cond").
     *
     * @return the core classifier ("Perm", "Req", or "Cond").
     */
    default Optional<String> core()
    {
        return getString("core");
    }


    /**
     * Returns the simple data type (e.g., "Char", "Num").
     *
     * @return the simple data type (e.g., "Char", "Num").
     */
    default Optional<String> simpleDatatype()
    {
        return getString("simpleDatatype");
    }


    /**
     * Returns the permitted value list for this variable.
     *
     * @return the permitted value list for this variable.
     */
    default List<String> valueList()
    {
        return getStringList("valueList");
    }


    /**
     * Returns link to the associated codelist.
     *
     * @return link to the associated codelist.
     */
    default Optional<Link> codelistLink()
    {
        return getLink("codelist");
    }


    /**
     * Returns link to the associated value list.
     *
     * @return link to the associated value list.
     */
    default Optional<Link> valuelistLink()
    {
        return getLink("valuelist");
    }


    /**
     * Returns link to the parent product.
     *
     * @return link to the parent product.
     */
    default Optional<Link> parentProductLink()
    {
        return getLink("parentProduct");
    }


    /**
     * Returns link to the parent data structure.
     *
     * @return link to the parent data structure.
     */
    default Optional<Link> parentDatastructureLink()
    {
        return getLink("parentDatastructure");
    }


    /**
     * Returns link to the parent variable set.
     *
     * @return link to the parent variable set.
     */
    default Optional<Link> parentVariableSetLink()
    {
        return getLink("parentVariableSet");
    }


    /**
     * Returns link to the parent class variable (for subclassed structures).
     *
     * @return link to the parent class variable (for subclassed structures).
     */
    default Optional<Link> parentClassVariableLink()
    {
        return getLink("parentClassVariable");
    }


    /**
     * Returns link to the prior version of this variable.
     *
     * @return link to the prior version of this variable.
     */
    default Optional<Link> priorVersionLink()
    {
        return getLink("priorVersion");
    }
}
