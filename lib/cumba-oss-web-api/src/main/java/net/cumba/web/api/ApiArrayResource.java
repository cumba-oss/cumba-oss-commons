package net.cumba.web.api;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.stream.Stream;

/**
 * Read-only typed view over a JSON array.
 *
 * <p>
 * This is the array counterpart to {@link ApiResource}. Implementations wrap a JSON array and
 * provide index-based, type-safe accessors. All accessors return empty optionals on out-of-bounds
 * indices or type mismatches — no exceptions are thrown for structural problems.
 * </p>
 *
 * <p>
 * Domain-specific array interfaces can extend this interface and add named default methods that
 * delegate to the generic accessors.
 * </p>
 */
public interface ApiArrayResource
{

    /**
     * Returns the number of elements in this array.
     *
     * @return the array length
     */
    int getLength();


    /**
     * Checks whether the element at the given index is a text value.
     *
     * @param aIndex
     *            the zero-based element index
     * @return {@code true} if the element exists and is textual, {@code false} otherwise
     */
    boolean isString(int aIndex);


    /**
     * Returns the element at the given index as a string.
     *
     * @param aIndex
     *            the zero-based element index
     * @return the string value, or empty if the element is missing or not textual
     */
    Optional<String> getString(int aIndex);


    /**
     * Checks whether the element at the given index is an integer value.
     *
     * @param aIndex
     *            the zero-based element index
     * @return {@code true} if the element exists and is an integer, {@code false} otherwise
     */
    boolean isInt(int aIndex);


    /**
     * Returns the element at the given index as an int.
     *
     * @param aIndex
     *            the zero-based element index
     * @return the int value, or empty if the element is missing or not an integer
     */
    OptionalInt getInt(int aIndex);


    /**
     * Checks whether the element at the given index is a long integer value.
     *
     * @param aIndex
     *            the zero-based element index
     * @return {@code true} if the element exists and is a long, {@code false} otherwise
     */
    boolean isLong(int aIndex);


    /**
     * Returns the element at the given index as a long.
     *
     * @param aIndex
     *            the zero-based element index
     * @return the long value, or empty if the element is missing or not a long
     */
    OptionalLong getLong(int aIndex);


    /**
     * Checks whether the element at the given index is a floating-point value.
     *
     * @param aIndex
     *            the zero-based element index
     * @return {@code true} if the element exists and is a double, {@code false} otherwise
     */
    boolean isDouble(int aIndex);


    /**
     * Returns the element at the given index as a double.
     *
     * @param aIndex
     *            the zero-based element index
     * @return the double value, or empty if the element is missing or not a double
     */
    OptionalDouble getDouble(int aIndex);


    /**
     * Checks whether the element at the given index is a boolean value.
     *
     * @param aIndex
     *            the zero-based element index
     * @return {@code true} if the element exists and is a boolean, {@code false} otherwise
     */
    boolean isBoolean(int aIndex);


    /**
     * Returns the element at the given index as a boolean.
     *
     * @param aIndex
     *            the zero-based element index
     * @return the boolean value, or empty if the element is missing or not a boolean
     */
    Optional<Boolean> getBoolean(int aIndex);


    /**
     * Checks whether the element at the given index is any numeric value.
     *
     * @param aIndex
     *            the zero-based element index
     * @return {@code true} if the element exists and is a number, {@code false} otherwise
     */
    boolean isNumber(int aIndex);


    /**
     * Returns the element at the given index as a {@link Number}.
     *
     * @param aIndex
     *            the zero-based element index
     * @return the number value, or empty if the element is missing or not a number
     */
    Optional<Number> getNumber(int aIndex);


    /**
     * Checks whether the element at the given index is a JSON object.
     *
     * @param aIndex
     *            the zero-based element index
     * @return {@code true} if the element exists and is an object, {@code false} otherwise
     */
    boolean isObject(int aIndex);


    /**
     * Returns the element at the given index as an {@link ApiResource} of the given type.
     *
     * @param aIndex
     *            the zero-based element index
     * @param type
     *            the target interface (must extend {@code ApiResource})
     * @param <T>
     *            the target type
     * @return the wrapped sub-object, or empty if the element is missing or not an object
     */
    <T extends ApiResource> Optional<T> getObject(int aIndex, Class<T> type);


    /**
     * Checks whether the element at the given index is a JSON array.
     *
     * @param aIndex
     *            the zero-based element index
     * @return {@code true} if the element exists and is an array, {@code false} otherwise
     */
    boolean isArray(int aIndex);


    /**
     * Returns the element at the given index as an {@link ApiArrayResource} of the given type.
     *
     * @param aIndex
     *            the zero-based element index
     * @param type
     *            the target interface (must extend {@code ApiArrayResource})
     * @param <T>
     *            the target type
     * @return the wrapped sub-array, or empty if the element is missing or not an array
     */
    <T extends ApiArrayResource> Optional<T> getArray(int aIndex, Class<T> type);


    /**
     * Returns a nested array element as a list of strings.
     *
     * @param aIndex
     *            the zero-based element index of the nested array
     * @return the string list (empty if the element is missing or not an array)
     */
    List<String> getStringList(int aIndex);


    /**
     * Returns a nested array element as a stream of strings.
     *
     * @param aIndex
     *            the zero-based element index of the nested array
     * @return the string stream (empty if the element is missing or not an array)
     */
    Stream<String> getStringStream(int aIndex);
}
