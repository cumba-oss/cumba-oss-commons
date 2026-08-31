package net.cumba.web.api.dev;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.cumba.web.api.ApiArrayResource;
import net.cumba.web.api.ApiResource;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ListResourceTest
{

    // --- Static factory tests ---

    @Test
    void ofListReturnsListResource()
    {
        ApiArrayResource res = ListResource.of(List.of("a", "b"));
        assertInstanceOf(ListResource.class, res);
    }


    @Test
    void ofWithApiArrayResourceClassReturnsListResource()
    {
        ApiArrayResource res = ListResource.of(List.of("a"), ApiArrayResource.class);
        assertInstanceOf(ListResource.class, res);
    }


    @Test
    void ofWithDomainInterfaceReturnsProxy()
    {
        SampleDomainArray proxy = ListResource.of(List.of("hello"), SampleDomainArray.class);
        assertNotNull(proxy);
        assertEquals(Optional.of("hello"), proxy.first());
    }


    @Test
    void constructorRejectsNull()
    {
        assertThrows(NullPointerException.class, () -> new ListResource(null));
    }

    // --- Length ---


    @Test
    void getLengthReturnsSize()
    {
        assertEquals(3, new ListResource(List.of(1, 2, 3)).getLength());
    }


    @Test
    void getLengthReturnsZeroForEmptyList()
    {
        assertEquals(0, new ListResource(List.of()).getLength());
    }

    // --- String accessors ---

    @Nested
    class StringAccessors
    {

        @Test
        void isStringReturnsTrueForStringElement()
        {
            assertTrue(new ListResource(List.of("hello")).isString(0));
        }


        @Test
        void isStringReturnsFalseForNumber()
        {
            assertFalse(new ListResource(List.of(42)).isString(0));
        }


        @Test
        void isStringReturnsFalseForOutOfBounds()
        {
            assertFalse(new ListResource(List.of()).isString(0));
        }


        @Test
        void isStringReturnsFalseForNegativeIndex()
        {
            assertFalse(new ListResource(List.of("hello")).isString(-1));
        }


        @Test
        void getStringReturnsValue()
        {
            assertEquals(Optional.of("world"), new ListResource(List.of("world")).getString(0));
        }


        @Test
        void getStringCallsToStringForNonStrings()
        {
            // ListResource's getString uses Object.toString(), so numbers are accepted.
            assertEquals(Optional.of("42"), new ListResource(List.of(42)).getString(0));
        }


        @Test
        void getStringReturnsEmptyForOutOfBounds()
        {
            assertTrue(new ListResource(List.of()).getString(5).isEmpty());
        }


        @Test
        void getStringReturnsEmptyForNullElement()
        {
            assertTrue(new ListResource(Collections.singletonList(null)).getString(0).isEmpty());
        }
    }

    // --- Numeric accessors ---


    @Nested
    class NumericAccessors
    {

        @Test
        void isIntReturnsTrueForInteger()
        {
            assertTrue(new ListResource(List.of(42)).isInt(0));
        }


        @Test
        void isIntReturnsTrueForDouble()
        {
            assertTrue(new ListResource(List.of(3.14)).isInt(0));
        }


        @Test
        void isIntReturnsTrueForLong()
        {
            assertTrue(new ListResource(List.of(9_999_999_999L)).isInt(0));
        }


        @Test
        void isIntReturnsFalseForString()
        {
            assertFalse(new ListResource(List.of("text")).isInt(0));
        }


        @Test
        void isIntReturnsFalseForOutOfBounds()
        {
            assertFalse(new ListResource(List.of()).isInt(0));
        }


        @Test
        void getIntReturnsValueForInteger()
        {
            assertEquals(42, new ListResource(List.of(42)).getInt(0).orElse(-1));
        }


        @Test
        void getIntReturnsValueForDouble()
        {
            assertEquals(3, new ListResource(List.of(3.7)).getInt(0).orElse(-1));
        }


        @Test
        void getIntReturnsEmptyForString()
        {
            assertTrue(new ListResource(List.of("text")).getInt(0).isEmpty());
        }


        @Test
        void getIntReturnsEmptyForOutOfBounds()
        {
            assertTrue(new ListResource(List.of()).getInt(0).isEmpty());
        }


        @Test
        void isLongReturnsTrueForLong()
        {
            assertTrue(new ListResource(List.of(9_999_999_999L)).isLong(0));
        }


        @Test
        void isLongReturnsFalseForString()
        {
            assertFalse(new ListResource(List.of("text")).isLong(0));
        }


        @Test
        void isLongReturnsFalseForOutOfBounds()
        {
            assertFalse(new ListResource(List.of()).isLong(0));
        }


        @Test
        void getLongReturnsValue()
        {
            assertEquals(9_999_999_999L,
                    new ListResource(List.of(9_999_999_999L)).getLong(0).orElse(-1));
        }


        @Test
        void getLongReturnsEmptyForString()
        {
            assertTrue(new ListResource(List.of("text")).getLong(0).isEmpty());
        }


        @Test
        void getLongReturnsEmptyForOutOfBounds()
        {
            assertTrue(new ListResource(List.of()).getLong(0).isEmpty());
        }


        @Test
        void isDoubleReturnsTrueForDouble()
        {
            assertTrue(new ListResource(List.of(3.14)).isDouble(0));
        }


        @Test
        void isDoubleReturnsTrueForInteger()
        {
            assertTrue(new ListResource(List.of(42)).isDouble(0));
        }


        @Test
        void isDoubleReturnsFalseForString()
        {
            assertFalse(new ListResource(List.of("text")).isDouble(0));
        }


        @Test
        void isDoubleReturnsFalseForOutOfBounds()
        {
            assertFalse(new ListResource(List.of()).isDouble(0));
        }


        @Test
        void getDoubleReturnsValue()
        {
            assertEquals(3.14, new ListResource(List.of(3.14)).getDouble(0).orElse(0), 0.001);
        }


        @Test
        void getDoubleReturnsEmptyForString()
        {
            assertTrue(new ListResource(List.of("text")).getDouble(0).isEmpty());
        }


        @Test
        void getDoubleReturnsEmptyForOutOfBounds()
        {
            assertTrue(new ListResource(List.of()).getDouble(0).isEmpty());
        }


        @Test
        void isNumberReturnsTrueForInteger()
        {
            assertTrue(new ListResource(List.of(42)).isNumber(0));
        }


        @Test
        void isNumberReturnsFalseForString()
        {
            assertFalse(new ListResource(List.of("text")).isNumber(0));
        }


        @Test
        void isNumberReturnsFalseForOutOfBounds()
        {
            assertFalse(new ListResource(List.of()).isNumber(0));
        }


        @Test
        void getNumberReturnsValue()
        {
            assertEquals(99, new ListResource(List.of(99)).getNumber(0).orElseThrow().intValue());
        }


        @Test
        void getNumberReturnsEmptyForString()
        {
            assertTrue(new ListResource(List.of("text")).getNumber(0).isEmpty());
        }


        @Test
        void getNumberReturnsEmptyForOutOfBounds()
        {
            assertTrue(new ListResource(List.of()).getNumber(0).isEmpty());
        }
    }

    // --- Boolean accessors ---


    @Nested
    class BooleanAccessors
    {

        @Test
        void isBooleanReturnsTrueForBoolean()
        {
            assertTrue(new ListResource(List.of(true)).isBoolean(0));
        }


        @Test
        void isBooleanReturnsFalseForString()
        {
            assertFalse(new ListResource(List.of("true")).isBoolean(0));
        }


        @Test
        void isBooleanReturnsFalseForOutOfBounds()
        {
            assertFalse(new ListResource(List.of()).isBoolean(0));
        }


        @Test
        void getBooleanReturnsTrue()
        {
            assertEquals(Optional.of(true), new ListResource(List.of(true)).getBoolean(0));
        }


        @Test
        void getBooleanReturnsFalse()
        {
            assertEquals(Optional.of(false), new ListResource(List.of(false)).getBoolean(0));
        }


        @Test
        void getBooleanReturnsEmptyForNonBoolean()
        {
            assertTrue(new ListResource(List.of(42)).getBoolean(0).isEmpty());
        }


        @Test
        void getBooleanReturnsEmptyForOutOfBounds()
        {
            assertTrue(new ListResource(List.of()).getBoolean(5).isEmpty());
        }
    }

    // --- Object accessors ---


    @Nested
    class ObjectAccessors
    {

        @Test
        void isObjectReturnsTrueForMap()
        {
            assertTrue(new ListResource(List.of(Map.of("id", "abc"))).isObject(0));
        }


        @Test
        void isObjectReturnsFalseForString()
        {
            assertFalse(new ListResource(List.of("text")).isObject(0));
        }


        @Test
        void isObjectReturnsFalseForOutOfBounds()
        {
            assertFalse(new ListResource(List.of()).isObject(0));
        }


        @Test
        void getObjectWrapsMapElement()
        {
            ListResource res = new ListResource(List.of(Map.of("name", "item")));
            Optional<ApiResource> result = res.getObject(0, ApiResource.class);
            assertTrue(result.isPresent());
            assertEquals(Optional.of("item"), result.get().getString("name"));
        }


        @Test
        void getObjectReturnsEmptyForNonMap()
        {
            assertTrue(new ListResource(List.of(42)).getObject(0, ApiResource.class).isEmpty());
        }


        @Test
        void getObjectReturnsEmptyForOutOfBounds()
        {
            assertTrue(new ListResource(List.of()).getObject(0, ApiResource.class).isEmpty());
        }
    }

    // --- Array accessors ---


    @Nested
    class ArrayAccessors
    {

        @Test
        void isArrayReturnsTrueForNestedList()
        {
            assertTrue(new ListResource(List.of(List.of(1, 2))).isArray(0));
        }


        @Test
        void isArrayReturnsFalseForString()
        {
            assertFalse(new ListResource(List.of("text")).isArray(0));
        }


        @Test
        void isArrayReturnsFalseForOutOfBounds()
        {
            assertFalse(new ListResource(List.of()).isArray(0));
        }


        @Test
        void getArrayWrapsListElement()
        {
            ListResource res = new ListResource(List.of(List.of("a", "b")));
            Optional<ApiArrayResource> result = res.getArray(0, ApiArrayResource.class);
            assertTrue(result.isPresent());
            assertEquals(2, result.get().getLength());
            assertEquals(Optional.of("a"), result.get().getString(0));
        }


        @Test
        void getArrayReturnsEmptyForNonList()
        {
            assertTrue(new ListResource(List.of(42)).getArray(0, ApiArrayResource.class).isEmpty());
        }


        @Test
        void getArrayReturnsEmptyForOutOfBounds()
        {
            assertTrue(new ListResource(List.of()).getArray(0, ApiArrayResource.class).isEmpty());
        }
    }

    // --- String list / stream ---


    @Nested
    class StringListAccessors
    {

        @Test
        void getStringListReturnsToStringOfElements()
        {
            List<String> result = new ListResource(List.of(List.of("x", 42, "y"))).getStringList(0);
            assertEquals(3, result.size());
            assertEquals("x", result.get(0));
            assertEquals("42", result.get(1));
            assertEquals("y", result.get(2));
        }


        @Test
        void getStringListReturnsNullForNullElement()
        {
            List<String> result = new ListResource(List.of(Arrays.asList("a", null, "b")))
                    .getStringList(0);
            assertEquals(3, result.size());
            assertEquals("a", result.get(0));
            assertEquals(null, result.get(1));
            assertEquals("b", result.get(2));
        }


        @Test
        void getStringListReturnsEmptyForNonList()
        {
            assertTrue(new ListResource(List.of("text")).getStringList(0).isEmpty());
        }


        @Test
        void getStringListReturnsEmptyForOutOfBounds()
        {
            assertTrue(new ListResource(List.of()).getStringList(5).isEmpty());
        }


        @Test
        void getStringStreamReturnsValues()
        {
            assertEquals(List.of("a", "b"),
                    new ListResource(List.of(List.of("a", "b"))).getStringStream(0).toList());
        }


        @Test
        void getStringStreamReturnsEmptyForOutOfBounds()
        {
            assertEquals(0, new ListResource(List.of()).getStringStream(5).count());
        }
    }

    // --- Object overrides ---


    @Nested
    class ObjectOverrides
    {

        @Test
        void equalsReturnsTrueForSameList()
        {
            List<Object> data = List.of(1, "a");
            assertEquals(new ListResource(data), new ListResource(data));
        }


        @Test
        void equalsReturnsTrueForEqualLists()
        {
            assertEquals(new ListResource(List.of(1, "a")), new ListResource(List.of(1, "a")));
        }


        @Test
        void equalsReturnsFalseForDifferentLists()
        {
            assertNotEquals(new ListResource(List.of(1)), new ListResource(List.of(2)));
        }


        @Test
        void equalsReturnsFalseForNull()
        {
            assertNotEquals(null, new ListResource(List.of()));
        }


        @Test
        void equalsReturnsFalseForDifferentType()
        {
            assertNotEquals("not a resource", new ListResource(List.of()));
        }


        @Test
        void equalsReturnsTrueForSelf()
        {
            ListResource r = new ListResource(List.of(1));
            assertEquals(r, r);
        }


        @Test
        void hashCodeConsistentWithEquals()
        {
            assertEquals(new ListResource(List.of(1)).hashCode(),
                    new ListResource(List.of(1)).hashCode());
        }


        @Test
        void toStringContainsContent()
        {
            String s = new ListResource(List.of("test")).toString();
            assertTrue(s.startsWith("ListResource["));
            assertTrue(s.contains("test"));
        }
    }

    // --- Dynamic proxy tests ---


    @Nested
    class DynamicProxy
    {

        @Test
        void proxyDelegatesToDefaultMethods()
        {
            SampleDomainArray proxy = ListResource.of(List.of("first-item", "second-item"),
                    SampleDomainArray.class);
            assertEquals(Optional.of("first-item"), proxy.first());
        }


        @Test
        void proxyDelegatesGenericAccessors()
        {
            SampleDomainArray proxy = ListResource.of(List.of("hello", "world"),
                    SampleDomainArray.class);
            assertEquals(2, proxy.getLength());
            assertEquals(Optional.of("world"), proxy.getString(1));
        }


        @Test
        void proxyToStringIncludesTypeName()
        {
            SampleDomainArray proxy = ListResource.of(List.of(), SampleDomainArray.class);
            assertTrue(proxy.toString().startsWith("SampleDomainArray["));
        }


        @Test
        void proxyEqualsComparesViaDelegateEquals()
        {
            List<Object> data = List.of(1);
            SampleDomainArray proxy = ListResource.of(data, SampleDomainArray.class);
            ListResource direct = new ListResource(data);
            assertEquals(proxy, direct);
        }


        @Test
        void proxyHashCodeMatchesDelegate()
        {
            List<Object> data = List.of(1);
            SampleDomainArray proxy = ListResource.of(data, SampleDomainArray.class);
            ListResource direct = new ListResource(data);
            assertEquals(direct.hashCode(), proxy.hashCode());
        }
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
