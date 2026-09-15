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
     * Returns link to the associated codelist, or the <em>first</em> of them when the variable is
     * associated with several.
     *
     * <p>
     * ⚠ The live API serves {@code _links.codelist} as an <b>array</b>, and a variable may carry
     * more than one — {@code --SEV} in ADaM OCCDS 1.1 is associated with both
     * {@code /mdr/root/ct/sdtmct/codelists/C66769} and
     * {@code /mdr/root/ct/sdtmct/codelists/C165643}. This accessor keeps its historical single-link
     * signature and reports only the first; use {@link #codelistLinks()} for all.
     * </p>
     *
     * @return link to the first associated codelist.
     */
    default Optional<Link> codelistLink()
    {
        return getLink("codelist");
    }


    /**
     * Returns every codelist link associated with this variable.
     *
     * <p>
     * Measured over the captured response cache: {@code _links.codelist} is an array in all 6 988
     * occurrences, 169 of them with more than one entry. A caller resolving controlled terminology
     * through {@link #codelistLink()} alone therefore validates against an incomplete vocabulary,
     * with no error to say so.
     * </p>
     *
     * @return every codelist link associated with this variable.
     */
    default List<Link> codelistLinks()
    {
        return getLinks("codelist");
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
