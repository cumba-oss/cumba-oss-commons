package net.cumba.cdisc.library.api.model.sdtm;

import java.util.List;
import java.util.Optional;

import net.cumba.web.api.ApiResource;
import net.cumba.web.api.Link;

/**
 * An SDTM/SDTM-IG variable within a dataset or class.
 *
 * <p>
 * Represents an individual variable definition including its name, label, data type, role, core
 * classification, and usage restrictions. Variables may belong to a dataset or to a class (shared
 * across all datasets in that class).
 * </p>
 */
public interface SdtmVariable extends ApiResource
{

    /** Returns the ordinal position of this variable within the dataset or class. */
    default Optional<String> ordinal()
    {
        return getString("ordinal");
    }


    /** Returns the variable name (e.g., "USUBJID", "AETERM"). */
    default Optional<String> name()
    {
        return getString("name");
    }


    /** Returns the variable label (e.g., "Unique Subject Identifier"). */
    default Optional<String> label()
    {
        return getString("label");
    }


    /** Returns the variable description. */
    default Optional<String> description()
    {
        return getString("description");
    }


    /** Returns the variable role (e.g., "Identifier", "Topic", "Qualifier"). */
    default Optional<String> role()
    {
        return getString("role");
    }


    /** Returns the variable role description. */
    default Optional<String> roleDescription()
    {
        return getString("roleDescription");
    }


    /** Returns the simple data type (e.g., "Char", "Num"). */
    default Optional<String> simpleDatatype()
    {
        return getString("simpleDatatype");
    }


    /** Returns the core classifier ("Perm", "Req", or "Cond"). */
    default Optional<String> core()
    {
        return getString("core");
    }


    /** Returns the usage restrictions for this variable. */
    default Optional<String> usageRestrictions()
    {
        return getString("usageRestrictions");
    }


    /** Returns additional notes about this variable. */
    default Optional<String> notes()
    {
        return getString("notes");
    }


    /** Returns example values for this variable. */
    default Optional<String> examples()
    {
        return getString("examples");
    }


    /** Returns the described value domain for this variable. */
    default Optional<String> describedValueDomain()
    {
        return getString("describedValueDomain");
    }


    /** Returns the permitted value list for this variable. */
    default List<String> valueList()
    {
        return getStringList("valueList");
    }


    /** Returns a link to the associated codelist. */
    default Optional<Link> codelistLink()
    {
        return getLink("codelist");
    }


    /** Returns a link to the parent product. */
    default Optional<Link> parentProductLink()
    {
        return getLink("parentProduct");
    }


    /** Returns a link to the parent class. */
    default Optional<Link> parentClassLink()
    {
        return getLink("parentClass");
    }


    /** Returns a link to the parent dataset. */
    default Optional<Link> parentDatasetLink()
    {
        return getLink("parentDataset");
    }


    /** Returns a link to the corresponding model dataset variable. */
    default Optional<Link> modelDatasetVariableLink()
    {
        return getLink("modelDatasetVariable");
    }


    /** Returns a link to the root item in the terminology hierarchy. */
    default Optional<Link> rootItemLink()
    {
        return getLink("rootItem");
    }


    /** Returns a link to the prior version of this variable. */
    default Optional<Link> priorVersionLink()
    {
        return getLink("priorVersion");
    }


    /** Returns links to variables that this variable qualifies (for qualifier variables). */
    default List<Link> qualifiesVariableLinks()
    {
        return getLinks("qualifiesVariables");
    }
}
