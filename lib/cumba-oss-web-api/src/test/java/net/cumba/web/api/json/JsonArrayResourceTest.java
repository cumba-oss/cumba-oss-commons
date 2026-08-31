package net.cumba.web.api.json;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Optional;
import net.cumba.web.api.ApiArrayResource;
import net.cumba.web.api.ApiResource;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class JsonArrayResourceTest
{

    private final ObjectMapper mapper = new ObjectMapper();

    // --- Static factory tests ---

    @Test
    void ofArrayNodeReturnsJsonArrayResource()
    {
        ArrayNode arr = mapper.createArrayNode();
        ApiArrayResource resource = JsonArrayResource.of(arr);
        assertInstanceOf(JsonArrayResource.class, resource);
    }


    @Test
    void ofWithApiArrayResourceClassReturnsJsonArrayResource()
    {
        ArrayNode arr = mapper.createArrayNode();
        ApiArrayResource resource = JsonArrayResource.of(arr, ApiArrayResource.class);
        assertInstanceOf(JsonArrayResource.class, resource);
    }


    @Test
    void ofWithDomainInterfaceReturnsProxy()
    {
        ArrayNode arr = mapper.createArrayNode();
        arr.add("hello");
        SampleDomainArray proxy = JsonArrayResource.of(arr, SampleDomainArray.class);
        assertNotNull(proxy);
        assertEquals(Optional.of("hello"), proxy.first());
    }


    @Test
    void constructorRejectsNull()
    {
        assertThrows(NullPointerException.class, () -> new JsonArrayResource(null));
    }

    // --- Length ---


    @Test
    void getLengthReturnsSize()
    {
        ArrayNode arr = mapper.createArrayNode();
        arr.add(1);
        arr.add(2);
        arr.add(3);
        assertEquals(3, JsonArrayResource.of(arr).getLength());
    }


    @Test
    void getLengthReturnsZeroForEmptyArray()
    {
        ArrayNode arr = mapper.createArrayNode();
        assertEquals(0, JsonArrayResource.of(arr).getLength());
    }

    // --- String accessors ---

    @Nested
    class StringAccessors
    {

        @Test
        void isStringReturnsTrueForTextElement()
        {
            ArrayNode arr = mapper.createArrayNode();
            arr.add("hello");
            assertTrue(JsonArrayResource.of(arr).isString(0));
        }


        @Test
        void isStringReturnsFalseForNumber()
        {
            ArrayNode arr = mapper.createArrayNode();
            arr.add(42);
            assertFalse(JsonArrayResource.of(arr).isString(0));
        }


        @Test
        void isStringReturnsFalseForOutOfBounds()
        {
            ArrayNode arr = mapper.createArrayNode();
            assertFalse(JsonArrayResource.of(arr).isString(0));
        }


        @Test
        void getStringReturnsValue()
        {
            ArrayNode arr = mapper.createArrayNode();
            arr.add("world");
            assertEquals(Optional.of("world"), JsonArrayResource.of(arr).getString(0));
        }


        @Test
        void getStringReturnsEmptyForNonText()
        {
            ArrayNode arr = mapper.createArrayNode();
            arr.add(42);
            assertTrue(JsonArrayResource.of(arr).getString(0).isEmpty());
        }


        @Test
        void getStringReturnsEmptyForOutOfBounds()
        {
            ArrayNode arr = mapper.createArrayNode();
            assertTrue(JsonArrayResource.of(arr).getString(5).isEmpty());
        }
    }

    // --- Numeric accessors ---


    @Nested
    class NumericAccessors
    {

        @Test
        void isIntReturnsTrueForIntElement()
        {
            ArrayNode arr = mapper.createArrayNode();
            arr.add(42);
            assertTrue(JsonArrayResource.of(arr).isInt(0));
        }


        @Test
        void isIntReturnsTrueForDoubleElement()
        {
            ArrayNode arr = mapper.createArrayNode();
            arr.add(3.14);
            assertTrue(JsonArrayResource.of(arr).isInt(0));
        }


        @Test
        void isIntReturnsTrueForLongElement()
        {
            ArrayNode arr = mapper.createArrayNode();
            arr.add(9_999_999_999L);
            assertTrue(JsonArrayResource.of(arr).isInt(0));
        }


        @Test
        void isIntReturnsFalseForString()
        {
            ArrayNode arr = mapper.createArrayNode();
            arr.add("text");
            assertFalse(JsonArrayResource.of(arr).isInt(0));
        }


        @Test
        void isIntReturnsFalseForOutOfBounds()
        {
            ArrayNode arr = mapper.createArrayNode();
            assertFalse(JsonArrayResource.of(arr).isInt(0));
        }


        @Test
        void getIntReturnsValueForInt()
        {
            ArrayNode arr = mapper.createArrayNode();
            arr.add(42);
            assertEquals(42, JsonArrayResource.of(arr).getInt(0).orElse(-1));
        }


        @Test
        void getIntReturnsValueForDouble()
        {
            ArrayNode arr = mapper.createArrayNode();
            arr.add(3.7);
            assertEquals(3, JsonArrayResource.of(arr).getInt(0).orElse(-1));
        }


        @Test
        void getIntReturnsEmptyForString()
        {
            ArrayNode arr = mapper.createArrayNode();
            arr.add("text");
            assertTrue(JsonArrayResource.of(arr).getInt(0).isEmpty());
        }


        @Test
        void getIntReturnsEmptyForOutOfBounds()
        {
            ArrayNode arr = mapper.createArrayNode();
            assertTrue(JsonArrayResource.of(arr).getInt(0).isEmpty());
        }


        @Test
        void isLongReturnsTrueForLong()
        {
            ArrayNode arr = mapper.createArrayNode();
            arr.add(9_999_999_999L);
            assertTrue(JsonArrayResource.of(arr).isLong(0));
        }


        @Test
        void isLongReturnsTrueForInt()
        {
            ArrayNode arr = mapper.createArrayNode();
            arr.add(42);
            assertTrue(JsonArrayResource.of(arr).isLong(0));
        }


        @Test
        void isLongReturnsFalseForString()
        {
            ArrayNode arr = mapper.createArrayNode();
            arr.add("text");
            assertFalse(JsonArrayResource.of(arr).isLong(0));
        }


        @Test
        void isLongReturnsFalseForOutOfBounds()
        {
            ArrayNode arr = mapper.createArrayNode();
            assertFalse(JsonArrayResource.of(arr).isLong(0));
        }


        @Test
        void getLongReturnsValueForLong()
        {
            ArrayNode arr = mapper.createArrayNode();
            arr.add(9_999_999_999L);
            assertEquals(9_999_999_999L, JsonArrayResource.of(arr).getLong(0).orElse(-1));
        }


        @Test
        void getLongReturnsValueForInt()
        {
            ArrayNode arr = mapper.createArrayNode();
            arr.add(42);
            assertEquals(42L, JsonArrayResource.of(arr).getLong(0).orElse(-1));
        }


        @Test
        void getLongReturnsEmptyForString()
        {
            ArrayNode arr = mapper.createArrayNode();
            arr.add("text");
            assertTrue(JsonArrayResource.of(arr).getLong(0).isEmpty());
        }


        @Test
        void getLongReturnsEmptyForOutOfBounds()
        {
            ArrayNode arr = mapper.createArrayNode();
            assertTrue(JsonArrayResource.of(arr).getLong(0).isEmpty());
        }


        @Test
        void isDoubleReturnsTrueForDouble()
        {
            ArrayNode arr = mapper.createArrayNode();
            arr.add(3.14);
            assertTrue(JsonArrayResource.of(arr).isDouble(0));
        }


        @Test
        void isDoubleReturnsFalseForInt()
        {
            // isDouble uses Jackson's isFloatingPointNumber(), which is strict —
            // integral values return false even though they could be widened to double.
            ArrayNode arr = mapper.createArrayNode();
            arr.add(42);
            assertFalse(JsonArrayResource.of(arr).isDouble(0));
        }


        @Test
        void isDoubleReturnsFalseForString()
        {
            ArrayNode arr = mapper.createArrayNode();
            arr.add("text");
            assertFalse(JsonArrayResource.of(arr).isDouble(0));
        }


        @Test
        void isDoubleReturnsFalseForOutOfBounds()
        {
            ArrayNode arr = mapper.createArrayNode();
            assertFalse(JsonArrayResource.of(arr).isDouble(0));
        }


        @Test
        void getDoubleReturnsValueForDouble()
        {
            ArrayNode arr = mapper.createArrayNode();
            arr.add(3.14);
            assertEquals(3.14, JsonArrayResource.of(arr).getDouble(0).orElse(0), 0.001);
        }


        @Test
        void getDoubleReturnsValueForInt()
        {
            ArrayNode arr = mapper.createArrayNode();
            arr.add(42);
            assertEquals(42.0, JsonArrayResource.of(arr).getDouble(0).orElse(0), 0.001);
        }


        @Test
        void getDoubleReturnsEmptyForString()
        {
            ArrayNode arr = mapper.createArrayNode();
            arr.add("text");
            assertTrue(JsonArrayResource.of(arr).getDouble(0).isEmpty());
        }


        @Test
        void getDoubleReturnsEmptyForOutOfBounds()
        {
            ArrayNode arr = mapper.createArrayNode();
            assertTrue(JsonArrayResource.of(arr).getDouble(0).isEmpty());
        }


        @Test
        void isNumberReturnsTrueForInt()
        {
            ArrayNode arr = mapper.createArrayNode();
            arr.add(42);
            assertTrue(JsonArrayResource.of(arr).isNumber(0));
        }


        @Test
        void isNumberReturnsTrueForDouble()
        {
            ArrayNode arr = mapper.createArrayNode();
            arr.add(3.14);
            assertTrue(JsonArrayResource.of(arr).isNumber(0));
        }


        @Test
        void isNumberReturnsFalseForString()
        {
            ArrayNode arr = mapper.createArrayNode();
            arr.add("text");
            assertFalse(JsonArrayResource.of(arr).isNumber(0));
        }


        @Test
        void isNumberReturnsFalseForOutOfBounds()
        {
            ArrayNode arr = mapper.createArrayNode();
            assertFalse(JsonArrayResource.of(arr).isNumber(0));
        }


        @Test
        void getNumberReturnsValueForInt()
        {
            ArrayNode arr = mapper.createArrayNode();
            arr.add(99);
            assertEquals(99, JsonArrayResource.of(arr).getNumber(0).orElseThrow().intValue());
        }


        @Test
        void getNumberReturnsEmptyForString()
        {
            ArrayNode arr = mapper.createArrayNode();
            arr.add("text");
            assertTrue(JsonArrayResource.of(arr).getNumber(0).isEmpty());
        }


        @Test
        void getNumberReturnsEmptyForOutOfBounds()
        {
            ArrayNode arr = mapper.createArrayNode();
            assertTrue(JsonArrayResource.of(arr).getNumber(0).isEmpty());
        }
    }

    // --- Boolean accessors ---


    @Nested
    class BooleanAccessors
    {

        @Test
        void isBooleanReturnsTrueForBoolean()
        {
            ArrayNode arr = mapper.createArrayNode();
            arr.add(true);
            assertTrue(JsonArrayResource.of(arr).isBoolean(0));
        }


        @Test
        void isBooleanReturnsFalseForString()
        {
            ArrayNode arr = mapper.createArrayNode();
            arr.add("true");
            assertFalse(JsonArrayResource.of(arr).isBoolean(0));
        }


        @Test
        void isBooleanReturnsFalseForOutOfBounds()
        {
            ArrayNode arr = mapper.createArrayNode();
            assertFalse(JsonArrayResource.of(arr).isBoolean(0));
        }


        @Test
        void getBooleanReturnsTrue()
        {
            ArrayNode arr = mapper.createArrayNode();
            arr.add(true);
            assertEquals(Optional.of(true), JsonArrayResource.of(arr).getBoolean(0));
        }


        @Test
        void getBooleanReturnsFalse()
        {
            ArrayNode arr = mapper.createArrayNode();
            arr.add(false);
            assertEquals(Optional.of(false), JsonArrayResource.of(arr).getBoolean(0));
        }


        @Test
        void getBooleanReturnsEmptyForNonBoolean()
        {
            ArrayNode arr = mapper.createArrayNode();
            arr.add(42);
            assertTrue(JsonArrayResource.of(arr).getBoolean(0).isEmpty());
        }


        @Test
        void getBooleanReturnsEmptyForOutOfBounds()
        {
            ArrayNode arr = mapper.createArrayNode();
            assertTrue(JsonArrayResource.of(arr).getBoolean(5).isEmpty());
        }
    }

    // --- Object accessors ---


    @Nested
    class ObjectAccessors
    {

        @Test
        void isObjectReturnsTrueForObject()
        {
            ObjectNode obj = mapper.createObjectNode();
            obj.put("id", "abc");
            ArrayNode arr = mapper.createArrayNode();
            arr.add(obj);
            assertTrue(JsonArrayResource.of(arr).isObject(0));
        }


        @Test
        void isObjectReturnsFalseForString()
        {
            ArrayNode arr = mapper.createArrayNode();
            arr.add("text");
            assertFalse(JsonArrayResource.of(arr).isObject(0));
        }


        @Test
        void isObjectReturnsFalseForOutOfBounds()
        {
            ArrayNode arr = mapper.createArrayNode();
            assertFalse(JsonArrayResource.of(arr).isObject(0));
        }


        @Test
        void getObjectReturnsWrappedResource()
        {
            ObjectNode obj = mapper.createObjectNode();
            obj.put("name", "item");
            ArrayNode arr = mapper.createArrayNode();
            arr.add(obj);

            Optional<ApiResource> result = JsonArrayResource.of(arr).getObject(0,
                    ApiResource.class);
            assertTrue(result.isPresent());
            assertEquals(Optional.of("item"), result.get().getString("name"));
        }


        @Test
        void getObjectReturnsEmptyForNonObject()
        {
            ArrayNode arr = mapper.createArrayNode();
            arr.add(42);
            assertTrue(JsonArrayResource.of(arr).getObject(0, ApiResource.class).isEmpty());
        }


        @Test
        void getObjectReturnsEmptyForOutOfBounds()
        {
            ArrayNode arr = mapper.createArrayNode();
            assertTrue(JsonArrayResource.of(arr).getObject(0, ApiResource.class).isEmpty());
        }
    }

    // --- Array accessors ---


    @Nested
    class ArrayAccessors
    {

        @Test
        void isArrayReturnsTrueForNestedArray()
        {
            ArrayNode inner = mapper.createArrayNode();
            inner.add(1);
            ArrayNode outer = mapper.createArrayNode();
            outer.add(inner);
            assertTrue(JsonArrayResource.of(outer).isArray(0));
        }


        @Test
        void isArrayReturnsFalseForString()
        {
            ArrayNode arr = mapper.createArrayNode();
            arr.add("text");
            assertFalse(JsonArrayResource.of(arr).isArray(0));
        }


        @Test
        void isArrayReturnsFalseForOutOfBounds()
        {
            ArrayNode arr = mapper.createArrayNode();
            assertFalse(JsonArrayResource.of(arr).isArray(0));
        }


        @Test
        void getArrayReturnsWrappedArray()
        {
            ArrayNode inner = mapper.createArrayNode();
            inner.add("a");
            inner.add("b");
            ArrayNode outer = mapper.createArrayNode();
            outer.add(inner);

            Optional<ApiArrayResource> result = JsonArrayResource.of(outer).getArray(0,
                    ApiArrayResource.class);
            assertTrue(result.isPresent());
            assertEquals(2, result.get().getLength());
            assertEquals(Optional.of("a"), result.get().getString(0));
        }


        @Test
        void getArrayReturnsEmptyForNonArray()
        {
            ArrayNode arr = mapper.createArrayNode();
            arr.add(42);
            assertTrue(JsonArrayResource.of(arr).getArray(0, ApiArrayResource.class).isEmpty());
        }


        @Test
        void getArrayReturnsEmptyForOutOfBounds()
        {
            ArrayNode arr = mapper.createArrayNode();
            assertTrue(JsonArrayResource.of(arr).getArray(0, ApiArrayResource.class).isEmpty());
        }
    }

    // --- String list/stream accessors ---


    @Nested
    class StringListAccessors
    {

        @Test
        void getStringListReturnsValues()
        {
            ArrayNode inner = mapper.createArrayNode();
            inner.add("x");
            inner.add("y");
            ArrayNode outer = mapper.createArrayNode();
            outer.add(inner);

            List<String> result = JsonArrayResource.of(outer).getStringList(0);
            assertEquals(List.of("x", "y"), result);
        }


        @Test
        void getStringListReturnsEmptyForNonArray()
        {
            ArrayNode arr = mapper.createArrayNode();
            arr.add("text");
            assertTrue(JsonArrayResource.of(arr).getStringList(0).isEmpty());
        }


        @Test
        void getStringListReturnsEmptyForOutOfBounds()
        {
            ArrayNode arr = mapper.createArrayNode();
            assertTrue(JsonArrayResource.of(arr).getStringList(5).isEmpty());
        }


        @Test
        void getStringStreamReturnsValues()
        {
            ArrayNode inner = mapper.createArrayNode();
            inner.add("a");
            inner.add("b");
            ArrayNode outer = mapper.createArrayNode();
            outer.add(inner);

            assertEquals(List.of("a", "b"),
                    JsonArrayResource.of(outer).getStringStream(0).toList());
        }


        @Test
        void getStringStreamReturnsEmptyForOutOfBounds()
        {
            ArrayNode arr = mapper.createArrayNode();
            assertEquals(0, JsonArrayResource.of(arr).getStringStream(5).count());
        }
    }

    // --- Mixed-type array ---

    @Test
    void mixedTypeArrayHandlesAllTypes()
    {
        ArrayNode arr = mapper.createArrayNode();
        arr.add("text"); // 0: string
        arr.add(42); // 1: int
        arr.add(3.14); // 2: double
        arr.add(true); // 3: boolean
        arr.add(mapper.createObjectNode()); // 4: object
        arr.add(mapper.createArrayNode()); // 5: array

        ApiArrayResource r = JsonArrayResource.of(arr);
        assertEquals(6, r.getLength());

        assertTrue(r.isString(0));
        assertFalse(r.isString(1));

        assertTrue(r.isInt(1));
        assertTrue(r.isInt(2)); // isNumber, so double also matches

        assertTrue(r.isBoolean(3));
        assertFalse(r.isBoolean(0));

        assertTrue(r.isObject(4));
        assertFalse(r.isObject(0));

        assertTrue(r.isArray(5));
        assertFalse(r.isArray(0));
    }

    // --- Object method tests ---


    @Test
    void equalsReturnsTrueForSameNode()
    {
        ArrayNode arr = mapper.createArrayNode();
        arr.add(1);
        JsonArrayResource r1 = new JsonArrayResource(arr);
        JsonArrayResource r2 = new JsonArrayResource(arr);
        assertEquals(r1, r2);
    }


    @Test
    void equalsReturnsTrueForEqualNodes()
    {
        ArrayNode arr1 = mapper.createArrayNode();
        arr1.add(1);
        arr1.add("a");
        ArrayNode arr2 = mapper.createArrayNode();
        arr2.add(1);
        arr2.add("a");
        assertEquals(new JsonArrayResource(arr1), new JsonArrayResource(arr2));
    }


    @Test
    void equalsReturnsFalseForDifferentNodes()
    {
        ArrayNode arr1 = mapper.createArrayNode();
        arr1.add(1);
        ArrayNode arr2 = mapper.createArrayNode();
        arr2.add(2);
        assertNotEquals(new JsonArrayResource(arr1), new JsonArrayResource(arr2));
    }


    @Test
    void equalsReturnsFalseForNull()
    {
        ArrayNode arr = mapper.createArrayNode();
        assertNotEquals(null, new JsonArrayResource(arr));
    }


    @Test
    void equalsReturnsFalseForDifferentType()
    {
        ArrayNode arr = mapper.createArrayNode();
        assertNotEquals("not a resource", new JsonArrayResource(arr));
    }


    @Test
    void hashCodeConsistentWithEquals()
    {
        ArrayNode arr1 = mapper.createArrayNode();
        arr1.add(1);
        ArrayNode arr2 = mapper.createArrayNode();
        arr2.add(1);
        assertEquals(new JsonArrayResource(arr1).hashCode(),
                new JsonArrayResource(arr2).hashCode());
    }


    @Test
    void toStringContainsContent()
    {
        ArrayNode arr = mapper.createArrayNode();
        arr.add("test");
        String s = new JsonArrayResource(arr).toString();
        assertTrue(s.startsWith("JsonArrayResource["));
        assertTrue(s.contains("test"));
    }

    // --- Dynamic proxy tests ---


    @Test
    void proxyDelegatesToDefaultMethods()
    {
        ArrayNode arr = mapper.createArrayNode();
        arr.add("first-item");
        arr.add("second-item");

        SampleDomainArray proxy = JsonArrayResource.of(arr, SampleDomainArray.class);
        assertEquals(Optional.of("first-item"), proxy.first());
    }


    @Test
    void proxyToStringIncludesTypeName()
    {
        ArrayNode arr = mapper.createArrayNode();
        SampleDomainArray proxy = JsonArrayResource.of(arr, SampleDomainArray.class);
        assertTrue(proxy.toString().startsWith("SampleDomainArray["));
    }


    @Test
    void proxyEqualsComparesViaDelegateEquals()
    {
        ArrayNode arr = mapper.createArrayNode();
        arr.add(1);
        // Proxy equality delegates to JsonArrayResource.equals, which checks instanceof
        // JsonArrayResource — compare proxy to direct instance
        SampleDomainArray proxy = JsonArrayResource.of(arr, SampleDomainArray.class);
        JsonArrayResource direct = new JsonArrayResource(arr);
        assertEquals(proxy, direct);
    }


    @Test
    void proxyHashCodeUsesNodeHashCode()
    {
        ArrayNode arr = mapper.createArrayNode();
        arr.add(1);
        SampleDomainArray proxy = JsonArrayResource.of(arr, SampleDomainArray.class);
        JsonArrayResource direct = new JsonArrayResource(arr);
        assertEquals(proxy.hashCode(), direct.hashCode());
    }

    /**
     * Sample domain array interface for testing the dynamic proxy mechanism.
     */
    interface SampleDomainArray extends ApiArrayResource
    {

        default Optional<String> first()
        {
            return getString(0);
        }
    }
}
