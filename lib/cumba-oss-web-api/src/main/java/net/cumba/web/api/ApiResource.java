package net.cumba.web.api;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Read-only typed view over a Jackson {@link JsonNode}.
 *
 * <p>
 * Implementations wrap a {@code JsonNode} (typically an ObjectNode from an API response) and
 * provide type-safe accessors. All accessors return empty optionals on missing fields or type
 * mismatches — no exceptions are thrown for structural problems.
 * </p>
 *
 * <p>
 * Domain-specific model interfaces (e.g. {@code AdamDataStructure}, {@code Codelist}) extend this
 * interface and add named default methods that delegate to the generic accessors.
 * </p>
 */
public interface ApiResource
{

    // --- Type checks ---

    /**
     * Checks whether the field with the given name is a text value.
     *
     * @param fieldName
     *            the JSON field name
     * @return {@code true} if the field exists and is textual, {@code false} otherwise
     */
    boolean isString(String fieldName);


    /**
     * Checks whether the field with the given name is any numeric value.
     *
     * @param fieldName
     *            the JSON field name
     * @return {@code true} if the field exists and is a number, {@code false} otherwise
     */
    boolean isNumber(String fieldName);


    /**
     * Checks whether the field with the given name is a boolean value.
     *
     * @param fieldName
     *            the JSON field name
     * @return {@code true} if the field exists and is a boolean, {@code false} otherwise
     */
    boolean isBoolean(String fieldName);


    /**
     * Checks whether the field with the given name is a JSON object.
     *
     * @param fieldName
     *            the JSON field name
     * @return {@code true} if the field exists and is an object, {@code false} otherwise
     */
    boolean isObject(String fieldName);


    /**
     * Checks whether the field with the given name is a JSON array.
     *
     * @param fieldName
     *            the JSON field name
     * @return {@code true} if the field exists and is an array, {@code false} otherwise
     */
    boolean isArray(String fieldName);


    /**
     * Checks whether the field with the given name is a JSON null value.
     *
     * <p>
     * Note: a missing field returns {@code false}. This only returns {@code true} when the field is
     * explicitly set to {@code null} in the JSON.
     * </p>
     *
     * @param fieldName
     *            the JSON field name
     * @return {@code true} if the field exists and is null, {@code false} otherwise
     */
    boolean isNull(String fieldName);

    // --- Scalar accessors ---


    /**
     * Returns the value of a text field.
     *
     * @param fieldName
     *            the JSON field name
     * @return the string value, or empty if the field is missing or not textual
     */
    Optional<String> getString(String fieldName);


    /**
     * Returns the value of an integer field.
     *
     * <p>
     * Strict (Q12, 2026-09-11): a value that is not an <i>integer</i>, or that does not fit an int,
     * answers empty rather than a narrowed or truncated stand-in. A floating-point {@code 3.0} is
     * not an integer, whatever its fraction.
     * </p>
     *
     * @param fieldName
     *            the JSON field name
     * @return the int value, or empty if the field is missing or is not an integer that fits
     */
    OptionalInt getInt(String fieldName);


    /**
     * Returns the value of a long integer field.
     *
     * <p>
     * Strict (Q12, 2026-09-11), exactly as {@link #getInt(String)} above.
     * </p>
     *
     * @param fieldName
     *            the JSON field name
     * @return the long value, or empty if the field is missing or is not an integer that fits
     */
    OptionalLong getLong(String fieldName);


    /**
     * Returns the value of a floating-point field.
     *
     * @param fieldName
     *            the JSON field name
     * @return the double value, or empty if the field is missing or not a number
     */
    OptionalDouble getDouble(String fieldName);


    /**
     * Returns the value of a boolean field.
     *
     * @param fieldName
     *            the JSON field name
     * @return the boolean value, or empty if the field is missing or not a boolean
     */
    Optional<Boolean> getBoolean(String fieldName);


    /**
     * Returns the value of a numeric field as a {@link Number}.
     *
     * @param fieldName
     *            the JSON field name
     * @return the number value, or empty if the field is missing or not a number
     */
    Optional<Number> getNumber(String fieldName);


    /**
     * Returns the names of all fields in this resource.
     *
     * @return an unmodifiable set of field names
     */
    Set<String> getFieldNames();


    /**
     * Checks whether this resource contains a field with the given name.
     *
     * @param fieldName
     *            the JSON field name
     * @return {@code true} if the field exists
     */
    boolean containsFieldName(String fieldName);


    /**
     * Returns the number of fields in this resource.
     *
     * @return the field count
     */
    int getFieldCount();

    // --- Structural accessors ---


    default Optional<ApiResource> getObject(String fieldName)
    {
        return getObject(fieldName, ApiResource.class);
    }


    /**
     * Returns a nested object field as a new {@link ApiResource} of the given type.
     *
     * @param fieldName
     *            the JSON field name
     * @param type
     *            the target interface (must extend {@code ApiResource})
     * @param <T>
     *            the target type
     * @return the wrapped sub-object, or empty if the field is missing or not an object.
     */
    <T extends ApiResource> Optional<T> getObject(String fieldName, Class<T> type);


    /**
     * Returns a nested array field as a new {@link ApiArrayResource} of the given type.
     *
     * @param fieldName
     *            the JSON field name
     * @param type
     *            the target interface (must extend {@code ApiArrayResource})
     * @param <T>
     *            the target type
     * @return the wrapped sub-object, or empty if the field is missing or not an array.
     */
    <T extends ApiArrayResource> Optional<T> getArray(String fieldName, Class<T> type);


    /**
     * Returns an array field as a list of {@link ApiResource} views.
     *
     * @param fieldName
     *            the JSON field name
     * @param type
     *            the target interface for each array element
     * @param <T>
     *            the target type
     * @return the list (empty if the field is missing or not an array)
     */
    <T extends ApiResource> List<T> getList(String fieldName, Class<T> type);


    /**
     * Returns an array field as a stream of {@link ApiResource} views.
     *
     * @param fieldName
     *            the JSON field name
     * @param type
     *            the target interface for each array element
     * @param <T>
     *            the target type
     * @return the stream (empty if the field is missing or not an array)
     */
    <T extends ApiResource> Stream<T> getStream(String fieldName, Class<T> type);


    /**
     * Returns an array field as a list of strings.
     *
     * <p>
     * An element that is not a string reads as the empty string and keeps its position, so the list
     * is always the same length as the array and never fabricates a value: a JSON {@code null} is
     * not the term {@code "null"} and the number {@code 7} is not the term {@code "7"} (Q13,
     * 2026-09-11). This is the same answer {@link #getString(String)} gives for a field of that
     * type, and every implementation of this interface owes it.
     * </p>
     *
     * @param fieldName
     *            the JSON field name
     * @return the list (empty if the field is missing or not an array)
     */
    List<String> getStringList(String fieldName);


    /**
     * Returns an array field as a stream of strings, element for element as
     * {@link #getStringList(String)} reads them.
     *
     * @param fieldName
     *            the JSON field name
     * @return the stream (empty if the field is missing or not an array)
     */
    Stream<String> getStringStream(String fieldName);

    // --- HATEOAS link navigation ---


    /**
     * Returns a single HAL-style link by relation name from the {@code _links} object.
     *
     * @param rel
     *            the link relation (e.g. "self", "parentProduct")
     * @return the link, or empty if not present
     */
    Optional<Link> getLink(String rel);


    /**
     * Returns a list of HAL-style links by relation name (for array-valued relations).
     *
     * @param rel
     *            the link relation (e.g. "codelists", "datasets")
     * @return the links (empty list if not present)
     */
    List<Link> getLinks(String rel);
}
