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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.cumba.web.api.ApiArrayResource;
import net.cumba.web.api.ApiResource;
import net.cumba.web.api.Link;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MapResourceTest
{

    // --- Static factory tests ---

    @Test
    void ofMapReturnsMapResource()
    {
        ApiResource res = MapResource.of(Map.of("k", "v"));
        assertInstanceOf(MapResource.class, res);
    }


    @Test
    void ofWithApiResourceClassReturnsMapResource()
    {
        ApiResource res = MapResource.of(Map.of("k", "v"), ApiResource.class);
        assertInstanceOf(MapResource.class, res);
    }


    @Test
    void ofWithDomainInterfaceReturnsProxy()
    {
        SampleDomainResource proxy = MapResource.of(Map.of("name", "test"),
                SampleDomainResource.class);
        assertNotNull(proxy);
        assertEquals(Optional.of("test"), proxy.getName());
    }


    @Test
    void constructorRejectsNull()
    {
        assertThrows(NullPointerException.class, () -> new MapResource(null));
    }

    // --- Type-check tests ---

    @Nested
    class TypeChecks
    {

        @Test
        void isStringTrueForString()
        {
            assertTrue(new MapResource(Map.of("s", "hello")).isString("s"));
        }


        @Test
        void isStringFalseForNumber()
        {
            assertFalse(new MapResource(Map.of("n", 42)).isString("n"));
        }


        @Test
        void isStringFalseForMissing()
        {
            assertFalse(new MapResource(Map.of()).isString("x"));
        }


        @Test
        void isNumberTrueForInteger()
        {
            assertTrue(new MapResource(Map.of("n", 42)).isNumber("n"));
        }


        @Test
        void isNumberTrueForDouble()
        {
            assertTrue(new MapResource(Map.of("n", 3.14)).isNumber("n"));
        }


        @Test
        void isNumberFalseForString()
        {
            assertFalse(new MapResource(Map.of("s", "text")).isNumber("s"));
        }


        @Test
        void isNumberFalseForMissing()
        {
            assertFalse(new MapResource(Map.of()).isNumber("x"));
        }


        @Test
        void isBooleanTrueForBoolean()
        {
            assertTrue(new MapResource(Map.of("b", true)).isBoolean("b"));
        }


        @Test
        void isBooleanFalseForString()
        {
            assertFalse(new MapResource(Map.of("s", "true")).isBoolean("s"));
        }


        @Test
        void isObjectTrueForMap()
        {
            assertTrue(new MapResource(Map.of("o", Map.of("k", "v"))).isObject("o"));
        }


        @Test
        void isObjectFalseForString()
        {
            assertFalse(new MapResource(Map.of("s", "text")).isObject("s"));
        }


        @Test
        void isArrayTrueForList()
        {
            assertTrue(new MapResource(Map.of("a", List.of(1, 2))).isArray("a"));
        }


        @Test
        void isArrayFalseForString()
        {
            assertFalse(new MapResource(Map.of("s", "text")).isArray("s"));
        }


        @Test
        void isNullTrueForExplicitNull()
        {
            Map<String, Object> m = new HashMap<>();
            m.put("nil", null);
            assertTrue(new MapResource(m).isNull("nil"));
        }


        @Test
        void isNullFalseForMissing()
        {
            assertFalse(new MapResource(Map.of()).isNull("missing"));
        }


        @Test
        void isNullFalseForNonNullValue()
        {
            assertFalse(new MapResource(Map.of("x", "value")).isNull("x"));
        }
    }

    // --- Scalar accessors ---


    @Nested
    class ScalarAccess
    {

        @Test
        void getStringReturnsValue()
        {
            assertEquals(Optional.of("hello"),
                    new MapResource(Map.of("k", "hello")).getString("k"));
        }


        @Test
        void getStringConvertsNonString()
        {
            // map-based resource uses toString() for non-string values
            assertEquals(Optional.of("42"), new MapResource(Map.of("k", 42)).getString("k"));
        }


        @Test
        void getStringReturnsEmptyForMissing()
        {
            assertTrue(new MapResource(Map.of()).getString("missing").isEmpty());
        }


        @Test
        void getStringReturnsEmptyForExplicitNull()
        {
            Map<String, Object> m = new HashMap<>();
            m.put("k", null);
            assertTrue(new MapResource(m).getString("k").isEmpty());
        }


        @Test
        void getIntReturnsValueForInteger()
        {
            assertEquals(42, new MapResource(Map.of("n", 42)).getInt("n").orElse(-1));
        }


        @Test
        void getIntReturnsValueForDouble()
        {
            assertEquals(3, new MapResource(Map.of("n", 3.7)).getInt("n").orElse(-1));
        }


        @Test
        void getIntReturnsEmptyForString()
        {
            assertTrue(new MapResource(Map.of("s", "text")).getInt("s").isEmpty());
        }


        @Test
        void getIntReturnsEmptyForMissing()
        {
            assertTrue(new MapResource(Map.of()).getInt("x").isEmpty());
        }


        @Test
        void getLongReturnsValue()
        {
            assertEquals(9_999_999_999L,
                    new MapResource(Map.of("n", 9_999_999_999L)).getLong("n").orElse(-1));
        }


        @Test
        void getLongReturnsEmptyForString()
        {
            assertTrue(new MapResource(Map.of("s", "text")).getLong("s").isEmpty());
        }


        @Test
        void getLongReturnsEmptyForMissing()
        {
            assertTrue(new MapResource(Map.of()).getLong("x").isEmpty());
        }


        @Test
        void getDoubleReturnsValue()
        {
            assertEquals(3.14, new MapResource(Map.of("n", 3.14)).getDouble("n").orElse(0), 0.001);
        }


        @Test
        void getDoubleReturnsEmptyForString()
        {
            assertTrue(new MapResource(Map.of("s", "text")).getDouble("s").isEmpty());
        }


        @Test
        void getDoubleReturnsEmptyForMissing()
        {
            assertTrue(new MapResource(Map.of()).getDouble("x").isEmpty());
        }


        @Test
        void getBooleanReturnsTrue()
        {
            assertEquals(Optional.of(true),
                    new MapResource(Map.of("flag", true)).getBoolean("flag"));
        }


        @Test
        void getBooleanReturnsFalse()
        {
            assertEquals(Optional.of(false),
                    new MapResource(Map.of("flag", false)).getBoolean("flag"));
        }


        @Test
        void getBooleanReturnsEmptyForString()
        {
            assertTrue(new MapResource(Map.of("flag", "yes")).getBoolean("flag").isEmpty());
        }


        @Test
        void getBooleanReturnsEmptyForMissing()
        {
            assertTrue(new MapResource(Map.of()).getBoolean("x").isEmpty());
        }


        @Test
        void getNumberReturnsValue()
        {
            assertEquals(99,
                    new MapResource(Map.of("n", 99)).getNumber("n").orElseThrow().intValue());
        }


        @Test
        void getNumberReturnsEmptyForString()
        {
            assertTrue(new MapResource(Map.of("s", "text")).getNumber("s").isEmpty());
        }


        @Test
        void getNumberReturnsEmptyForMissing()
        {
            assertTrue(new MapResource(Map.of()).getNumber("x").isEmpty());
        }
    }

    // --- Field introspection ---


    @Nested
    class FieldIntrospection
    {

        @Test
        void getFieldNamesReturnsKeys()
        {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("a", 1);
            m.put("b", 2);
            assertEquals(Set.of("a", "b"), new MapResource(m).getFieldNames());
        }


        @Test
        void getFieldNamesFiltersNonStringKeys()
        {
            Map<Object, Object> m = new LinkedHashMap<>();
            m.put("a", 1);
            m.put(42, 2);
            assertEquals(Set.of("a"), new MapResource(m).getFieldNames());
        }


        @Test
        void getFieldNamesReturnsEmptyForEmptyMap()
        {
            assertTrue(new MapResource(Map.of()).getFieldNames().isEmpty());
        }


        @Test
        void containsFieldNameReturnsTrueForPresentKey()
        {
            assertTrue(new MapResource(Map.of("present", 1)).containsFieldName("present"));
        }


        @Test
        void containsFieldNameReturnsFalseForAbsentKey()
        {
            assertFalse(new MapResource(Map.of("present", 1)).containsFieldName("absent"));
        }


        @Test
        void getFieldCount()
        {
            assertEquals(3, new MapResource(Map.of("a", 1, "b", 2, "c", 3)).getFieldCount());
        }


        @Test
        void getFieldCountZeroForEmpty()
        {
            assertEquals(0, new MapResource(Map.of()).getFieldCount());
        }
    }

    // --- Structural accessors ---


    @Nested
    class StructuralAccess
    {

        @Test
        void getObjectWrapsNestedMap()
        {
            MapResource res = new MapResource(Map.of("child", Map.of("name", "inner")));
            Optional<ApiResource> child = res.getObject("child", ApiResource.class);
            assertTrue(child.isPresent());
            assertEquals(Optional.of("inner"), child.get().getString("name"));
        }


        @Test
        void getObjectReturnsEmptyForMissingField()
        {
            assertTrue(new MapResource(Map.of()).getObject("x", ApiResource.class).isEmpty());
        }


        @Test
        void getObjectReturnsEmptyForNonMapField()
        {
            assertTrue(new MapResource(Map.of("s", "text")).getObject("s", ApiResource.class)
                    .isEmpty());
        }


        @Test
        void getObjectDefaultMethod()
        {
            MapResource res = new MapResource(Map.of("child", Map.of("name", "inner")));
            Optional<ApiResource> child = res.getObject("child");
            assertTrue(child.isPresent());
            assertEquals(Optional.of("inner"), child.get().getString("name"));
        }


        @Test
        void getArrayWrapsListField()
        {
            MapResource res = new MapResource(Map.of("items", List.of(1, 2)));
            Optional<ApiArrayResource> arr = res.getArray("items", ApiArrayResource.class);
            assertTrue(arr.isPresent());
            assertEquals(2, arr.get().getLength());
        }


        @Test
        void getArrayReturnsEmptyForMissing()
        {
            assertTrue(new MapResource(Map.of()).getArray("x", ApiArrayResource.class).isEmpty());
        }


        @Test
        void getArrayReturnsEmptyForNonList()
        {
            assertTrue(new MapResource(Map.of("x", 42)).getArray("x", ApiArrayResource.class)
                    .isEmpty());
        }


        @Test
        void getListReturnsWrappedMaps()
        {
            MapResource res = new MapResource(
                    Map.of("items", List.of(Map.of("id", "a"), Map.of("id", "b"))));
            List<ApiResource> list = res.getList("items", ApiResource.class);
            assertEquals(2, list.size());
            assertEquals(Optional.of("a"), list.get(0).getString("id"));
            assertEquals(Optional.of("b"), list.get(1).getString("id"));
        }


        @Test
        void getListPassThroughApiResourceElements()
        {
            ApiResource child = MapResource.of(Map.of("name", "wrapped"));
            MapResource res = new MapResource(Map.of("items", List.of(child)));
            List<ApiResource> list = res.getList("items", ApiResource.class);
            assertEquals(1, list.size());
            assertEquals(Optional.of("wrapped"), list.get(0).getString("name"));
        }


        @Test
        void getListReturnsNullForNullElement()
        {
            MapResource res = new MapResource(
                    Map.of("items", Arrays.asList(Map.of("k", "v"), null)));
            List<ApiResource> list = res.getList("items", ApiResource.class);
            assertEquals(2, list.size());
            assertEquals(null, list.get(1));
        }


        @Test
        void getListThrowsForUnsupportedElementType()
        {
            MapResource res = new MapResource(Map.of("items", List.of(42)));
            List<ApiResource> list = res.getList("items", ApiResource.class);
            assertThrows(IllegalArgumentException.class, () -> list.get(0));
        }


        @Test
        void getListReturnsEmptyForMissing()
        {
            assertTrue(new MapResource(Map.of()).getList("x", ApiResource.class).isEmpty());
        }


        @Test
        void getListReturnsEmptyForNonList()
        {
            assertTrue(new MapResource(Map.of("x", 42)).getList("x", ApiResource.class).isEmpty());
        }


        @Test
        void getStreamReturnsElements()
        {
            MapResource res = new MapResource(Map.of("items", List.of(Map.of("v", 1))));
            List<ApiResource> collected = res.getStream("items", ApiResource.class).toList();
            assertEquals(1, collected.size());
            assertEquals(1, collected.get(0).getInt("v").orElse(-1));
        }


        @Test
        void getStreamReturnsEmptyForMissing()
        {
            assertEquals(0, new MapResource(Map.of()).getStream("x", ApiResource.class).count());
        }


        @Test
        void getStringListReturnsToStringOfElements()
        {
            List<String> list = new MapResource(Map.of("tags", List.of("a", "b", "c")))
                    .getStringList("tags");
            assertEquals(List.of("a", "b", "c"), list);
        }


        @Test
        void getStringListNullForNullElement()
        {
            List<String> list = new MapResource(Map.of("tags", Arrays.asList("a", null, "b")))
                    .getStringList("tags");
            assertEquals(3, list.size());
            assertEquals(null, list.get(1));
        }


        @Test
        void getStringListReturnsEmptyForMissing()
        {
            assertTrue(new MapResource(Map.of()).getStringList("missing").isEmpty());
        }


        @Test
        void getStringListReturnsEmptyForNonList()
        {
            assertTrue(new MapResource(Map.of("x", "single")).getStringList("x").isEmpty());
        }


        @Test
        void getStringStreamReturnsValues()
        {
            assertEquals(List.of("a", "b"), new MapResource(Map.of("vals", List.of("a", "b")))
                    .getStringStream("vals").toList());
        }


        @Test
        void getStringStreamReturnsEmptyForMissing()
        {
            assertEquals(0, new MapResource(Map.of()).getStringStream("missing").count());
        }
    }

    // --- HATEOAS link navigation ---


    @Nested
    class Links
    {

        @Test
        void getLinkReturnsSingleLink()
        {
            Map<String, Object> map = Map.of("_links", Map.of("self", Map.of("href",
                    "/api/studies/123", "title", "Study 123", "type", "application/json")));
            MapResource res = new MapResource(map);
            Optional<Link> link = res.getLink("self");
            assertTrue(link.isPresent());
            assertEquals(Optional.of("/api/studies/123"), link.get().href());
            assertEquals(Optional.of("Study 123"), link.get().title());
            assertEquals(Optional.of("application/json"), link.get().type());
        }


        @Test
        void getLinkReturnsFirstFromList()
        {
            Map<String, Object> map = Map.of("_links",
                    Map.of("items", List.of(Map.of("href", "/first"), Map.of("href", "/second"))));
            Optional<Link> link = new MapResource(map).getLink("items");
            assertTrue(link.isPresent());
            assertEquals(Optional.of("/first"), link.get().href());
        }


        @Test
        void getLinkReturnsEmptyForMissingRel()
        {
            Map<String, Object> map = Map.of("_links", Map.of());
            assertTrue(new MapResource(map).getLink("missing").isEmpty());
        }


        @Test
        void getLinkReturnsEmptyWithoutLinksMap()
        {
            assertTrue(new MapResource(Map.of()).getLink("self").isEmpty());
        }


        @Test
        void getLinkReturnsEmptyForLinksNonMap()
        {
            Map<String, Object> map = Map.of("_links", "not a map");
            assertTrue(new MapResource(map).getLink("self").isEmpty());
        }


        @Test
        void getLinkReturnsEmptyForEmptyList()
        {
            Map<String, Object> map = Map.of("_links", Map.of("items", List.of()));
            assertTrue(new MapResource(map).getLink("items").isEmpty());
        }


        @Test
        void getLinkReturnsEmptyForListWithNonMapFirst()
        {
            Map<String, Object> map = Map.of("_links", Map.of("items", List.of("not-a-map")));
            assertTrue(new MapResource(map).getLink("items").isEmpty());
        }


        @Test
        void getLinksReturnsSingletonForMap()
        {
            Map<String, Object> map = Map.of("_links", Map.of("self", Map.of("href", "/single")));
            List<Link> links = new MapResource(map).getLinks("self");
            assertEquals(1, links.size());
            assertEquals(Optional.of("/single"), links.get(0).href());
        }


        @Test
        void getLinksReturnsAllFromList()
        {
            Map<String, Object> map = Map.of("_links",
                    Map.of("items", List.of(Map.of("href", "/a"), Map.of("href", "/b"))));
            List<Link> links = new MapResource(map).getLinks("items");
            assertEquals(2, links.size());
            assertEquals(Optional.of("/a"), links.get(0).href());
            assertEquals(Optional.of("/b"), links.get(1).href());
        }


        @Test
        void getLinksSkipsNonMapEntries()
        {
            Map<String, Object> map = Map.of("_links", Map.of("items",
                    List.of(Map.of("href", "/a"), "skipped", Map.of("href", "/b"))));
            List<Link> links = new MapResource(map).getLinks("items");
            assertEquals(2, links.size());
        }


        @Test
        void getLinksReturnsEmptyForMissing()
        {
            Map<String, Object> map = Map.of("_links", Map.of());
            assertTrue(new MapResource(map).getLinks("missing").isEmpty());
        }


        @Test
        void getLinksReturnsEmptyWithoutLinksMap()
        {
            assertTrue(new MapResource(Map.of()).getLinks("self").isEmpty());
        }


        @Test
        void getLinksReturnsEmptyForLinksNonMap()
        {
            Map<String, Object> map = Map.of("_links", "not a map");
            assertTrue(new MapResource(map).getLinks("self").isEmpty());
        }


        @Test
        void getLinksReturnsEmptyForRelOfNeitherMapNorList()
        {
            Map<String, Object> map = Map.of("_links", Map.of("self", "not a link"));
            assertTrue(new MapResource(map).getLinks("self").isEmpty());
        }
    }

    // --- Object overrides ---


    @Nested
    class ObjectOverrides
    {

        @Test
        void equalsReturnsTrueForSameMap()
        {
            Map<String, Object> m = Map.of("a", 1);
            assertEquals(new MapResource(m), new MapResource(m));
        }


        @Test
        void equalsReturnsTrueForEqualMaps()
        {
            assertEquals(new MapResource(Map.of("a", 1)), new MapResource(Map.of("a", 1)));
        }


        @Test
        void equalsReturnsFalseForDifferentMaps()
        {
            assertNotEquals(new MapResource(Map.of("a", 1)), new MapResource(Map.of("a", 2)));
        }


        @Test
        void equalsReturnsFalseForNull()
        {
            assertNotEquals(null, new MapResource(Map.of()));
        }


        @Test
        void equalsReturnsFalseForDifferentType()
        {
            assertNotEquals("not a resource", new MapResource(Map.of()));
        }


        @Test
        void equalsReturnsTrueForSelf()
        {
            MapResource r = new MapResource(Map.of());
            assertEquals(r, r);
        }


        @Test
        void hashCodeConsistentWithEquals()
        {
            assertEquals(new MapResource(Map.of("a", 1)).hashCode(),
                    new MapResource(Map.of("a", 1)).hashCode());
        }


        @Test
        void toStringContainsContent()
        {
            String s = new MapResource(Map.of("k", "v")).toString();
            assertTrue(s.startsWith("HashMapResource["));
            assertTrue(s.contains("k"));
        }
    }

    // --- Dynamic proxy tests ---


    @Nested
    class DynamicProxy
    {

        @Test
        void proxyDelegatesToDefaultMethods()
        {
            SampleDomainResource proxy = MapResource
                    .of(Map.of("name", "Study-1", "label", "My Study"), SampleDomainResource.class);
            assertEquals(Optional.of("Study-1"), proxy.getName());
            assertEquals(Optional.of("My Study"), proxy.getLabel());
        }


        @Test
        void proxyDelegatesGenericAccessors()
        {
            SampleDomainResource proxy = MapResource.of(Map.of("k", "v"),
                    SampleDomainResource.class);
            assertEquals(Optional.of("v"), proxy.getString("k"));
            assertTrue(proxy.containsFieldName("k"));
        }


        @Test
        void proxyToStringIncludesTypeName()
        {
            SampleDomainResource proxy = MapResource.of(Map.of(), SampleDomainResource.class);
            assertTrue(proxy.toString().startsWith("SampleDomainResource["));
        }


        @Test
        void proxyEqualsComparesViaDelegateEquals()
        {
            Map<String, Object> data = Map.of("x", 1);
            SampleDomainResource proxy = MapResource.of(data, SampleDomainResource.class);
            MapResource direct = new MapResource(data);
            assertEquals(proxy, direct);
        }


        @Test
        void proxyHashCodeMatchesDelegate()
        {
            Map<String, Object> data = Map.of("x", 1);
            SampleDomainResource proxy = MapResource.of(data, SampleDomainResource.class);
            MapResource direct = new MapResource(data);
            assertEquals(direct.hashCode(), proxy.hashCode());
        }


        @Test
        void unmodifiableListResultFromCollectionsEmpty()
        {
            // Exercises the empty-list path one more time so the unmodifiable wrapper around
            // an absent relation is covered.
            assertEquals(Collections.emptyList(), new MapResource(Map.of()).getLinks("any"));
        }
    }


    /**
     * Sample domain interface for testing the dynamic proxy mechanism.
     */
    interface SampleDomainResource extends ApiResource
    {

        default Optional<String> getName()
        {
            return getString("name");
        }


        default Optional<String> getLabel()
        {
            return getString("label");
        }
    }
}
