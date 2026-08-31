package net.cumba.web.api.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.cumba.web.api.ApiArrayResource;
import net.cumba.web.api.ApiResource;
import net.cumba.web.api.Link;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class JsonNodeResourceTest
{

    private final ObjectMapper mapper = new ObjectMapper();

    // --- Static factory tests ---

    @Test
    void ofJsonNodeReturnsJsonNodeResource()
    {
        ObjectNode node = mapper.createObjectNode();
        ApiResource resource = JsonNodeResource.of(node);
        assertInstanceOf(JsonNodeResource.class, resource);
    }


    @Test
    void ofWithApiResourceClassReturnsJsonNodeResource()
    {
        ObjectNode node = mapper.createObjectNode();
        ApiResource resource = JsonNodeResource.of(node, ApiResource.class);
        assertInstanceOf(JsonNodeResource.class, resource);
    }


    @Test
    void ofWithDomainInterfaceReturnsProxy()
    {
        ObjectNode node = mapper.createObjectNode();
        node.put("name", "test-study");
        SampleDomainResource resource = JsonNodeResource.of(node, SampleDomainResource.class);

        assertNotNull(resource);
        assertEquals(Optional.of("test-study"), resource.getName());
    }


    @Test
    void constructorRejectsNull()
    {
        assertThrows(NullPointerException.class, () -> new JsonNodeResource(null));
    }

    // --- Type check tests ---

    @Nested
    class TypeChecks
    {

        @Test
        void isStringTrueForText()
        {
            ObjectNode node = mapper.createObjectNode();
            node.put("s", "hello");
            ApiResource r = JsonNodeResource.of(node);
            assertTrue(r.isString("s"));
        }


        @Test
        void isStringFalseForNumber()
        {
            ObjectNode node = mapper.createObjectNode();
            node.put("n", 42);
            assertFalse(JsonNodeResource.of(node).isString("n"));
        }


        @Test
        void isStringFalseForMissing()
        {
            assertFalse(JsonNodeResource.of(mapper.createObjectNode()).isString("x"));
        }


        @Test
        void isNumberTrueForInt()
        {
            ObjectNode node = mapper.createObjectNode();
            node.put("n", 42);
            assertTrue(JsonNodeResource.of(node).isNumber("n"));
        }


        @Test
        void isNumberTrueForDouble()
        {
            ObjectNode node = mapper.createObjectNode();
            node.put("n", 3.14);
            assertTrue(JsonNodeResource.of(node).isNumber("n"));
        }


        @Test
        void isNumberTrueForLong()
        {
            ObjectNode node = mapper.createObjectNode();
            node.put("n", 9_999_999_999L);
            assertTrue(JsonNodeResource.of(node).isNumber("n"));
        }


        @Test
        void isNumberFalseForString()
        {
            ObjectNode node = mapper.createObjectNode();
            node.put("s", "text");
            assertFalse(JsonNodeResource.of(node).isNumber("s"));
        }


        @Test
        void isNumberFalseForMissing()
        {
            assertFalse(JsonNodeResource.of(mapper.createObjectNode()).isNumber("x"));
        }


        @Test
        void isBooleanTrueForBoolean()
        {
            ObjectNode node = mapper.createObjectNode();
            node.put("b", true);
            assertTrue(JsonNodeResource.of(node).isBoolean("b"));
        }


        @Test
        void isBooleanFalseForString()
        {
            ObjectNode node = mapper.createObjectNode();
            node.put("s", "true");
            assertFalse(JsonNodeResource.of(node).isBoolean("s"));
        }


        @Test
        void isBooleanFalseForMissing()
        {
            assertFalse(JsonNodeResource.of(mapper.createObjectNode()).isBoolean("x"));
        }


        @Test
        void isObjectTrueForObject()
        {
            ObjectNode inner = mapper.createObjectNode();
            inner.put("k", "v");
            ObjectNode node = mapper.createObjectNode();
            node.set("obj", inner);
            assertTrue(JsonNodeResource.of(node).isObject("obj"));
        }


        @Test
        void isObjectFalseForString()
        {
            ObjectNode node = mapper.createObjectNode();
            node.put("s", "text");
            assertFalse(JsonNodeResource.of(node).isObject("s"));
        }


        @Test
        void isObjectFalseForMissing()
        {
            assertFalse(JsonNodeResource.of(mapper.createObjectNode()).isObject("x"));
        }


        @Test
        void isArrayTrueForArray()
        {
            ArrayNode arr = mapper.createArrayNode();
            arr.add(1);
            ObjectNode node = mapper.createObjectNode();
            node.set("arr", arr);
            assertTrue(JsonNodeResource.of(node).isArray("arr"));
        }


        @Test
        void isArrayFalseForString()
        {
            ObjectNode node = mapper.createObjectNode();
            node.put("s", "text");
            assertFalse(JsonNodeResource.of(node).isArray("s"));
        }


        @Test
        void isArrayFalseForMissing()
        {
            assertFalse(JsonNodeResource.of(mapper.createObjectNode()).isArray("x"));
        }


        @Test
        void isNullTrueForNullValue()
        {
            ObjectNode node = mapper.createObjectNode();
            node.putNull("nil");
            assertTrue(JsonNodeResource.of(node).isNull("nil"));
        }


        @Test
        void isNullFalseForString()
        {
            ObjectNode node = mapper.createObjectNode();
            node.put("s", "text");
            assertFalse(JsonNodeResource.of(node).isNull("s"));
        }


        @Test
        void isNullFalseForMissing()
        {
            assertFalse(JsonNodeResource.of(mapper.createObjectNode()).isNull("x"));
        }
    }

    // --- Scalar accessor tests ---


    @Nested
    class StringAccessors
    {

        @Test
        void getStringReturnsValueForTextField()
        {
            ObjectNode node = mapper.createObjectNode();
            node.put("title", "Hello");
            ApiResource r = JsonNodeResource.of(node);
            assertEquals(Optional.of("Hello"), r.getString("title"));
        }


        @Test
        void getStringReturnsEmptyForMissingField()
        {
            ObjectNode node = mapper.createObjectNode();
            ApiResource r = JsonNodeResource.of(node);
            assertEquals(Optional.empty(), r.getString("missing"));
        }


        @Test
        void getStringReturnsEmptyForNonTextualField()
        {
            ObjectNode node = mapper.createObjectNode();
            node.put("count", 42);
            ApiResource r = JsonNodeResource.of(node);
            assertEquals(Optional.empty(), r.getString("count"));
        }
    }


    @Nested
    class NumericAccessors
    {

        @Test
        void getIntReturnsValueForIntField()
        {
            ObjectNode node = mapper.createObjectNode();
            node.put("count", 42);
            ApiResource r = JsonNodeResource.of(node);
            assertEquals(42, r.getInt("count").orElse(-1));
        }


        @Test
        void getIntReturnsValueForDoubleField()
        {
            ObjectNode node = mapper.createObjectNode();
            node.put("value", 3.14);
            ApiResource r = JsonNodeResource.of(node);
            // isNumber() is true for doubles, asInt() truncates
            assertEquals(3, r.getInt("value").orElse(-1));
        }


        @Test
        void getIntReturnsEmptyForStringField()
        {
            ObjectNode node = mapper.createObjectNode();
            node.put("name", "hello");
            ApiResource r = JsonNodeResource.of(node);
            assertTrue(r.getInt("name").isEmpty());
        }


        @Test
        void getIntReturnsEmptyForMissingField()
        {
            ObjectNode node = mapper.createObjectNode();
            ApiResource r = JsonNodeResource.of(node);
            assertTrue(r.getInt("missing").isEmpty());
        }


        @Test
        void getLongReturnsValueForLongField()
        {
            ObjectNode node = mapper.createObjectNode();
            node.put("big", 9_999_999_999L);
            ApiResource r = JsonNodeResource.of(node);
            assertEquals(9_999_999_999L, r.getLong("big").orElse(-1));
        }


        @Test
        void getLongReturnsEmptyForMissingField()
        {
            ObjectNode node = mapper.createObjectNode();
            ApiResource r = JsonNodeResource.of(node);
            assertTrue(r.getLong("missing").isEmpty());
        }


        @Test
        void getDoubleReturnsValueForDoubleField()
        {
            ObjectNode node = mapper.createObjectNode();
            node.put("pi", 3.14);
            ApiResource r = JsonNodeResource.of(node);
            assertEquals(3.14, r.getDouble("pi").orElse(0), 0.001);
        }


        @Test
        void getDoubleReturnsValueForIntField()
        {
            ObjectNode node = mapper.createObjectNode();
            node.put("count", 7);
            ApiResource r = JsonNodeResource.of(node);
            assertEquals(7.0, r.getDouble("count").orElse(0), 0.001);
        }


        @Test
        void getDoubleReturnsEmptyForMissingField()
        {
            ObjectNode node = mapper.createObjectNode();
            ApiResource r = JsonNodeResource.of(node);
            assertTrue(r.getDouble("missing").isEmpty());
        }


        @Test
        void getNumberReturnsValueForIntField()
        {
            ObjectNode node = mapper.createObjectNode();
            node.put("n", 99);
            ApiResource r = JsonNodeResource.of(node);
            assertEquals(99, r.getNumber("n").orElseThrow().intValue());
        }


        @Test
        void getNumberReturnsEmptyForTextField()
        {
            ObjectNode node = mapper.createObjectNode();
            node.put("n", "not a number");
            ApiResource r = JsonNodeResource.of(node);
            assertTrue(r.getNumber("n").isEmpty());
        }


        @Test
        void getNumberReturnsEmptyForMissingField()
        {
            ObjectNode node = mapper.createObjectNode();
            ApiResource r = JsonNodeResource.of(node);
            assertTrue(r.getNumber("missing").isEmpty());
        }
    }


    @Nested
    class BooleanAccessors
    {

        @Test
        void getBooleanReturnsTrue()
        {
            ObjectNode node = mapper.createObjectNode();
            node.put("flag", true);
            ApiResource r = JsonNodeResource.of(node);
            assertEquals(Optional.of(true), r.getBoolean("flag"));
        }


        @Test
        void getBooleanReturnsFalse()
        {
            ObjectNode node = mapper.createObjectNode();
            node.put("flag", false);
            ApiResource r = JsonNodeResource.of(node);
            assertEquals(Optional.of(false), r.getBoolean("flag"));
        }


        @Test
        void getBooleanReturnsEmptyForNonBooleanField()
        {
            ObjectNode node = mapper.createObjectNode();
            node.put("flag", "yes");
            ApiResource r = JsonNodeResource.of(node);
            assertTrue(r.getBoolean("flag").isEmpty());
        }


        @Test
        void getBooleanReturnsEmptyForMissingField()
        {
            ObjectNode node = mapper.createObjectNode();
            ApiResource r = JsonNodeResource.of(node);
            assertTrue(r.getBoolean("missing").isEmpty());
        }
    }

    // --- Field introspection tests ---

    @Test
    void getFieldNamesReturnsAllKeys()
    {
        ObjectNode node = mapper.createObjectNode();
        node.put("a", 1);
        node.put("b", 2);
        ApiResource r = JsonNodeResource.of(node);
        assertEquals(Set.of("a", "b"), r.getFieldNames());
    }


    @Test
    @SuppressWarnings(
    {
            "unlikely-arg-type", "CollectionIncompatibleType"
    })
    void getFieldNamesContainsWorks()
    {
        ObjectNode node = mapper.createObjectNode();
        node.put("x", 1);
        ApiResource r = JsonNodeResource.of(node);
        assertTrue(r.getFieldNames().contains("x"));
        assertFalse(r.getFieldNames().contains("y"));
        assertFalse(r.getFieldNames().contains(42)); // non-String
    }


    @Test
    void containsFieldName()
    {
        ObjectNode node = mapper.createObjectNode();
        node.put("present", 1);
        ApiResource r = JsonNodeResource.of(node);
        assertTrue(r.containsFieldName("present"));
        assertFalse(r.containsFieldName("absent"));
    }


    @Test
    void getFieldCount()
    {
        ObjectNode node = mapper.createObjectNode();
        node.put("a", 1);
        node.put("b", 2);
        node.put("c", 3);
        ApiResource r = JsonNodeResource.of(node);
        assertEquals(3, r.getFieldCount());
    }


    @Test
    void emptyNodeHasZeroFieldCount()
    {
        ObjectNode node = mapper.createObjectNode();
        ApiResource r = JsonNodeResource.of(node);
        assertEquals(0, r.getFieldCount());
        assertTrue(r.getFieldNames().isEmpty());
    }

    // --- Structural accessor tests ---


    @Test
    void getObjectReturnsNestedResource()
    {
        ObjectNode inner = mapper.createObjectNode();
        inner.put("name", "inner");
        ObjectNode outer = mapper.createObjectNode();
        outer.set("child", inner);

        ApiResource r = JsonNodeResource.of(outer);
        Optional<ApiResource> child = r.getObject("child", ApiResource.class);
        assertTrue(child.isPresent());
        assertEquals(Optional.of("inner"), child.get().getString("name"));
    }


    @Test
    void getObjectReturnsEmptyForMissingField()
    {
        ObjectNode node = mapper.createObjectNode();
        ApiResource r = JsonNodeResource.of(node);
        assertTrue(r.getObject("missing", ApiResource.class).isEmpty());
    }


    @Test
    void getObjectReturnsEmptyForNonObjectField()
    {
        ObjectNode node = mapper.createObjectNode();
        node.put("name", "text");
        ApiResource r = JsonNodeResource.of(node);
        assertTrue(r.getObject("name", ApiResource.class).isEmpty());
    }


    @Test
    void getObjectDefaultMethodReturnsNestedResource()
    {
        ObjectNode inner = mapper.createObjectNode();
        inner.put("name", "inner");
        ObjectNode outer = mapper.createObjectNode();
        outer.set("child", inner);

        ApiResource r = JsonNodeResource.of(outer);
        Optional<ApiResource> child = r.getObject("child");
        assertTrue(child.isPresent());
        assertEquals(Optional.of("inner"), child.get().getString("name"));
    }


    @Test
    void getArrayReturnsWrappedArray()
    {
        ArrayNode arr = mapper.createArrayNode();
        arr.add(1);
        arr.add(2);
        ObjectNode node = mapper.createObjectNode();
        node.set("items", arr);

        ApiResource r = JsonNodeResource.of(node);
        Optional<ApiArrayResource> result = r.getArray("items", ApiArrayResource.class);
        assertTrue(result.isPresent());
        assertEquals(2, result.get().getLength());
    }


    @Test
    void getArrayReturnsEmptyForMissingField()
    {
        ObjectNode node = mapper.createObjectNode();
        ApiResource r = JsonNodeResource.of(node);
        assertTrue(r.getArray("missing", ApiArrayResource.class).isEmpty());
    }


    @Test
    void getArrayReturnsEmptyForNonArrayField()
    {
        ObjectNode node = mapper.createObjectNode();
        node.put("name", "text");
        ApiResource r = JsonNodeResource.of(node);
        assertTrue(r.getArray("name", ApiArrayResource.class).isEmpty());
    }


    @Test
    void getListReturnsLazyList()
    {
        ObjectNode item1 = mapper.createObjectNode();
        item1.put("id", "a");
        ObjectNode item2 = mapper.createObjectNode();
        item2.put("id", "b");
        ArrayNode arr = mapper.createArrayNode();
        arr.add(item1);
        arr.add(item2);
        ObjectNode node = mapper.createObjectNode();
        node.set("items", arr);

        ApiResource r = JsonNodeResource.of(node);
        List<ApiResource> list = r.getList("items", ApiResource.class);
        assertEquals(2, list.size());
        assertEquals(Optional.of("a"), list.get(0).getString("id"));
        assertEquals(Optional.of("b"), list.get(1).getString("id"));
    }


    @Test
    void getListReturnsEmptyForMissingField()
    {
        ObjectNode node = mapper.createObjectNode();
        ApiResource r = JsonNodeResource.of(node);
        assertTrue(r.getList("missing", ApiResource.class).isEmpty());
    }


    @Test
    void getListReturnsEmptyForNonArrayField()
    {
        ObjectNode node = mapper.createObjectNode();
        node.put("x", 1);
        ApiResource r = JsonNodeResource.of(node);
        assertTrue(r.getList("x", ApiResource.class).isEmpty());
    }


    @Test
    void getStreamReturnsElements()
    {
        ObjectNode item = mapper.createObjectNode();
        item.put("v", 1);
        ArrayNode arr = mapper.createArrayNode();
        arr.add(item);
        ObjectNode node = mapper.createObjectNode();
        node.set("items", arr);

        ApiResource r = JsonNodeResource.of(node);
        List<ApiResource> collected = r.getStream("items", ApiResource.class).toList();
        assertEquals(1, collected.size());
        assertEquals(1, collected.get(0).getInt("v").orElse(-1));
    }


    @Test
    void getStreamReturnsEmptyForMissingField()
    {
        ObjectNode node = mapper.createObjectNode();
        ApiResource r = JsonNodeResource.of(node);
        assertEquals(0, r.getStream("missing", ApiResource.class).count());
    }


    @Test
    void getStreamReturnsEmptyForNonArrayField()
    {
        ObjectNode node = mapper.createObjectNode();
        node.put("x", 1);
        ApiResource r = JsonNodeResource.of(node);
        assertEquals(0, r.getStream("x", ApiResource.class).count());
    }


    @Test
    void getStringListReturnsValues()
    {
        ArrayNode arr = mapper.createArrayNode();
        arr.add("hello");
        arr.add("world");
        ObjectNode node = mapper.createObjectNode();
        node.set("tags", arr);

        ApiResource r = JsonNodeResource.of(node);
        assertEquals(List.of("hello", "world"), r.getStringList("tags"));
    }


    @Test
    void getStringListReturnsEmptyForMissingField()
    {
        ObjectNode node = mapper.createObjectNode();
        ApiResource r = JsonNodeResource.of(node);
        assertTrue(r.getStringList("missing").isEmpty());
    }


    @Test
    void getStringListReturnsEmptyForNonArrayField()
    {
        ObjectNode node = mapper.createObjectNode();
        node.put("x", "single");
        ApiResource r = JsonNodeResource.of(node);
        assertTrue(r.getStringList("x").isEmpty());
    }


    @Test
    void getStringStreamReturnsValues()
    {
        ArrayNode arr = mapper.createArrayNode();
        arr.add("a");
        arr.add("b");
        ObjectNode node = mapper.createObjectNode();
        node.set("vals", arr);

        ApiResource r = JsonNodeResource.of(node);
        assertEquals(List.of("a", "b"), r.getStringStream("vals").toList());
    }


    @Test
    void getStringStreamReturnsEmptyForMissingField()
    {
        ObjectNode node = mapper.createObjectNode();
        ApiResource r = JsonNodeResource.of(node);
        assertEquals(0, r.getStringStream("missing").count());
    }

    // --- HATEOAS link tests ---

    @Nested
    class LinkTests
    {

        @Test
        void getLinkReturnsSingleLink()
        {
            ObjectNode linkObj = mapper.createObjectNode();
            linkObj.put("href", "/api/studies/123");
            linkObj.put("title", "Study 123");
            linkObj.put("type", "application/json");

            ObjectNode links = mapper.createObjectNode();
            links.set("self", linkObj);

            ObjectNode node = mapper.createObjectNode();
            node.set("_links", links);

            ApiResource r = JsonNodeResource.of(node);
            Optional<Link> link = r.getLink("self");
            assertTrue(link.isPresent());
            assertEquals(Optional.of("/api/studies/123"), link.get().href());
            assertEquals(Optional.of("Study 123"), link.get().title());
            assertEquals(Optional.of("application/json"), link.get().type());
        }


        @Test
        void getLinkReturnsFirstFromArray()
        {
            ObjectNode link1 = mapper.createObjectNode();
            link1.put("href", "/first");
            ObjectNode link2 = mapper.createObjectNode();
            link2.put("href", "/second");

            ArrayNode linkArr = mapper.createArrayNode();
            linkArr.add(link1);
            linkArr.add(link2);

            ObjectNode links = mapper.createObjectNode();
            links.set("items", linkArr);

            ObjectNode node = mapper.createObjectNode();
            node.set("_links", links);

            ApiResource r = JsonNodeResource.of(node);
            Optional<Link> link = r.getLink("items");
            assertTrue(link.isPresent());
            assertEquals(Optional.of("/first"), link.get().href());
        }


        @Test
        void getLinkReturnsEmptyForMissingRel()
        {
            ObjectNode links = mapper.createObjectNode();
            ObjectNode node = mapper.createObjectNode();
            node.set("_links", links);

            ApiResource r = JsonNodeResource.of(node);
            assertTrue(r.getLink("missing").isEmpty());
        }


        @Test
        void getLinkReturnsEmptyWithoutLinksObject()
        {
            ObjectNode node = mapper.createObjectNode();
            ApiResource r = JsonNodeResource.of(node);
            assertTrue(r.getLink("self").isEmpty());
        }


        @Test
        void getLinkReturnsEmptyForEmptyArray()
        {
            ArrayNode emptyArr = mapper.createArrayNode();
            ObjectNode links = mapper.createObjectNode();
            links.set("items", emptyArr);
            ObjectNode node = mapper.createObjectNode();
            node.set("_links", links);

            ApiResource r = JsonNodeResource.of(node);
            assertTrue(r.getLink("items").isEmpty());
        }


        @Test
        void getLinksReturnsSingletonListForObject()
        {
            ObjectNode linkObj = mapper.createObjectNode();
            linkObj.put("href", "/single");

            ObjectNode links = mapper.createObjectNode();
            links.set("self", linkObj);

            ObjectNode node = mapper.createObjectNode();
            node.set("_links", links);

            ApiResource r = JsonNodeResource.of(node);
            List<Link> result = r.getLinks("self");
            assertEquals(1, result.size());
            assertEquals(Optional.of("/single"), result.get(0).href());
        }


        @Test
        void getLinksReturnsAllFromArray()
        {
            ObjectNode link1 = mapper.createObjectNode();
            link1.put("href", "/a");
            ObjectNode link2 = mapper.createObjectNode();
            link2.put("href", "/b");

            ArrayNode arr = mapper.createArrayNode();
            arr.add(link1);
            arr.add(link2);

            ObjectNode links = mapper.createObjectNode();
            links.set("datasets", arr);

            ObjectNode node = mapper.createObjectNode();
            node.set("_links", links);

            ApiResource r = JsonNodeResource.of(node);
            List<Link> result = r.getLinks("datasets");
            assertEquals(2, result.size());
            assertEquals(Optional.of("/a"), result.get(0).href());
            assertEquals(Optional.of("/b"), result.get(1).href());
        }


        @Test
        void getLinksReturnsEmptyForMissingRel()
        {
            ObjectNode links = mapper.createObjectNode();
            ObjectNode node = mapper.createObjectNode();
            node.set("_links", links);

            ApiResource r = JsonNodeResource.of(node);
            assertTrue(r.getLinks("missing").isEmpty());
        }


        @Test
        void getLinksReturnsEmptyWithoutLinksObject()
        {
            ObjectNode node = mapper.createObjectNode();
            ApiResource r = JsonNodeResource.of(node);
            assertTrue(r.getLinks("self").isEmpty());
        }


        @Test
        void linkWithMissingFields()
        {
            ObjectNode linkObj = mapper.createObjectNode();
            linkObj.put("href", "/test");
            // title and type not set

            ObjectNode links = mapper.createObjectNode();
            links.set("self", linkObj);

            ObjectNode node = mapper.createObjectNode();
            node.set("_links", links);

            ApiResource r = JsonNodeResource.of(node);
            Link link = r.getLink("self").orElseThrow();
            assertEquals(Optional.of("/test"), link.href());
            assertTrue(link.title().isEmpty());
            assertTrue(link.type().isEmpty());
        }


        @Test
        void linkTemplatedDefaultsToFalse()
        {
            ObjectNode linkObj = mapper.createObjectNode();
            linkObj.put("href", "/items");

            ObjectNode links = mapper.createObjectNode();
            links.set("self", linkObj);
            ObjectNode node = mapper.createObjectNode();
            node.set("_links", links);

            Link link = JsonNodeResource.of(node).getLink("self").orElseThrow();
            assertFalse(link.templated());
        }


        @Test
        void linkTemplatedTrue()
        {
            ObjectNode linkObj = mapper.createObjectNode();
            linkObj.put("href", "/items{?page,size}");
            linkObj.put("templated", true);

            ObjectNode links = mapper.createObjectNode();
            links.set("search", linkObj);
            ObjectNode node = mapper.createObjectNode();
            node.set("_links", links);

            Link link = JsonNodeResource.of(node).getLink("search").orElseThrow();
            assertTrue(link.templated());
        }


        @Test
        void linkHreflang()
        {
            ObjectNode linkObj = mapper.createObjectNode();
            linkObj.put("href", "/doc");
            linkObj.put("hreflang", "en-US");

            ObjectNode links = mapper.createObjectNode();
            links.set("doc", linkObj);
            ObjectNode node = mapper.createObjectNode();
            node.set("_links", links);

            Link link = JsonNodeResource.of(node).getLink("doc").orElseThrow();
            assertEquals(Optional.of("en-US"), link.hreflang());
        }


        @Test
        void linkHreflangEmptyWhenMissing()
        {
            ObjectNode linkObj = mapper.createObjectNode();
            linkObj.put("href", "/doc");

            ObjectNode links = mapper.createObjectNode();
            links.set("doc", linkObj);
            ObjectNode node = mapper.createObjectNode();
            node.set("_links", links);

            Link link = JsonNodeResource.of(node).getLink("doc").orElseThrow();
            assertTrue(link.hreflang().isEmpty());
        }


        @Test
        void linkName()
        {
            ObjectNode linkObj = mapper.createObjectNode();
            linkObj.put("href", "/items/123");
            linkObj.put("name", "primary");

            ObjectNode links = mapper.createObjectNode();
            links.set("item", linkObj);
            ObjectNode node = mapper.createObjectNode();
            node.set("_links", links);

            Link link = JsonNodeResource.of(node).getLink("item").orElseThrow();
            assertEquals(Optional.of("primary"), link.name());
        }


        @Test
        void linkNameEmptyWhenMissing()
        {
            ObjectNode linkObj = mapper.createObjectNode();
            linkObj.put("href", "/items/123");

            ObjectNode links = mapper.createObjectNode();
            links.set("item", linkObj);
            ObjectNode node = mapper.createObjectNode();
            node.set("_links", links);

            Link link = JsonNodeResource.of(node).getLink("item").orElseThrow();
            assertTrue(link.name().isEmpty());
        }


        @Test
        void linkDeprecation()
        {
            ObjectNode linkObj = mapper.createObjectNode();
            linkObj.put("href", "/old-endpoint");
            linkObj.put("deprecation", "https://docs.example.com/deprecations/old-endpoint");

            ObjectNode links = mapper.createObjectNode();
            links.set("old", linkObj);
            ObjectNode node = mapper.createObjectNode();
            node.set("_links", links);

            Link link = JsonNodeResource.of(node).getLink("old").orElseThrow();
            assertEquals(Optional.of("https://docs.example.com/deprecations/old-endpoint"),
                    link.deprecation());
        }


        @Test
        void linkDeprecationEmptyWhenMissing()
        {
            ObjectNode linkObj = mapper.createObjectNode();
            linkObj.put("href", "/current");

            ObjectNode links = mapper.createObjectNode();
            links.set("self", linkObj);
            ObjectNode node = mapper.createObjectNode();
            node.set("_links", links);

            Link link = JsonNodeResource.of(node).getLink("self").orElseThrow();
            assertTrue(link.deprecation().isEmpty());
        }


        @Test
        void linkProfile()
        {
            ObjectNode linkObj = mapper.createObjectNode();
            linkObj.put("href", "/data");
            linkObj.put("profile", "https://schema.example.com/data-v2");

            ObjectNode links = mapper.createObjectNode();
            links.set("data", linkObj);
            ObjectNode node = mapper.createObjectNode();
            node.set("_links", links);

            Link link = JsonNodeResource.of(node).getLink("data").orElseThrow();
            assertEquals(Optional.of("https://schema.example.com/data-v2"), link.profile());
        }


        @Test
        void linkProfileEmptyWhenMissing()
        {
            ObjectNode linkObj = mapper.createObjectNode();
            linkObj.put("href", "/data");

            ObjectNode links = mapper.createObjectNode();
            links.set("data", linkObj);
            ObjectNode node = mapper.createObjectNode();
            node.set("_links", links);

            Link link = JsonNodeResource.of(node).getLink("data").orElseThrow();
            assertTrue(link.profile().isEmpty());
        }


        @Test
        void linkAllPropertiesPresent()
        {
            ObjectNode linkObj = mapper.createObjectNode();
            linkObj.put("href", "/items{?q}");
            linkObj.put("templated", true);
            linkObj.put("type", "application/json");
            linkObj.put("title", "Search items");
            linkObj.put("hreflang", "de");
            linkObj.put("name", "search");
            linkObj.put("deprecation", "https://docs.example.com/dep");
            linkObj.put("profile", "https://schema.example.com/v1");

            ObjectNode links = mapper.createObjectNode();
            links.set("search", linkObj);
            ObjectNode node = mapper.createObjectNode();
            node.set("_links", links);

            Link link = JsonNodeResource.of(node).getLink("search").orElseThrow();
            assertEquals(Optional.of("/items{?q}"), link.href());
            assertTrue(link.templated());
            assertEquals(Optional.of("application/json"), link.type());
            assertEquals(Optional.of("Search items"), link.title());
            assertEquals(Optional.of("de"), link.hreflang());
            assertEquals(Optional.of("search"), link.name());
            assertEquals(Optional.of("https://docs.example.com/dep"), link.deprecation());
            assertEquals(Optional.of("https://schema.example.com/v1"), link.profile());
        }
    }

    // --- Link.id() tests ---


    @Nested
    class LinkIdTests
    {

        private Link linkWithHref(String href)
        {
            ObjectNode node = mapper.createObjectNode();
            if (href != null)
            {
                node.put("href", href);
            }
            return JsonNodeResource.of(node, Link.class);
        }


        @Test
        void idReturnsLastSegment()
        {
            assertEquals(Optional.of("adam-2-1"), linkWithHref("/mdr/adam/adam-2-1").id());
        }


        @Test
        void idReturnsEmptyForMissingHref()
        {
            assertTrue(linkWithHref(null).id().isEmpty());
        }


        @Test
        void idReturnsEmptyForEmptyString()
        {
            assertTrue(linkWithHref("").id().isEmpty());
        }


        @Test
        void idReturnsEmptyForTrailingSlash()
        {
            assertTrue(linkWithHref("/path/").id().isEmpty());
        }


        @Test
        void idWithIndexZeroReturnsLastSegment()
        {
            assertEquals(Optional.of("adam-2-1"), linkWithHref("/mdr/adam/adam-2-1").id(0));
        }


        @Test
        void idWithPositiveIndexReturnsSegment()
        {
            Link link = linkWithHref("/mdr/adam/adam-2-1");
            // split("/") → ["", "mdr", "adam", "adam-2-1"]
            assertEquals(Optional.of("mdr"), link.id(1));
            assertEquals(Optional.of("adam"), link.id(2));
            assertEquals(Optional.of("adam-2-1"), link.id(3));
        }


        @Test
        void idWithPositiveIndexOutOfRangeReturnsEmpty()
        {
            assertTrue(linkWithHref("/mdr/adam").id(99).isEmpty());
        }


        @Test
        void idWithNegativeIndexCountsFromEnd()
        {
            // parts = ["", "mdr", "adam", "adam-2-1"], length=4
            // -1 → index 4-1-1=2 → "adam"
            assertEquals(Optional.of("adam"), linkWithHref("/mdr/adam/adam-2-1").id(-1));
        }


        @Test
        void idWithNegativeIndexOutOfRangeReturnsEmpty()
        {
            assertTrue(linkWithHref("/a/b").id(-99).isEmpty());
        }


        @Test
        void idWithIndexReturnsEmptyForMissingHref()
        {
            assertTrue(linkWithHref(null).id(0).isEmpty());
        }


        @Test
        void idWithIndexReturnsEmptyForEmptyString()
        {
            assertTrue(linkWithHref("").id(0).isEmpty());
        }
    }

    // --- Object method tests ---

    @Test
    void equalsReturnsTrueForSameNode()
    {
        ObjectNode node = mapper.createObjectNode();
        node.put("a", 1);
        JsonNodeResource r1 = new JsonNodeResource(node);
        JsonNodeResource r2 = new JsonNodeResource(node);
        assertEquals(r1, r2);
    }


    @Test
    void equalsReturnsTrueForEqualNodes()
    {
        ObjectNode node1 = mapper.createObjectNode();
        node1.put("a", 1);
        ObjectNode node2 = mapper.createObjectNode();
        node2.put("a", 1);
        assertEquals(new JsonNodeResource(node1), new JsonNodeResource(node2));
    }


    @Test
    void equalsReturnsFalseForDifferentNodes()
    {
        ObjectNode node1 = mapper.createObjectNode();
        node1.put("a", 1);
        ObjectNode node2 = mapper.createObjectNode();
        node2.put("a", 2);
        assertNotEquals(new JsonNodeResource(node1), new JsonNodeResource(node2));
    }


    @Test
    void equalsReturnsFalseForNull()
    {
        ObjectNode node = mapper.createObjectNode();
        assertNotEquals(null, new JsonNodeResource(node));
    }


    @Test
    void equalsReturnsFalseForDifferentType()
    {
        ObjectNode node = mapper.createObjectNode();
        assertNotEquals("not a resource", new JsonNodeResource(node));
    }


    @Test
    void hashCodeConsistentWithEquals()
    {
        ObjectNode node1 = mapper.createObjectNode();
        node1.put("a", 1);
        ObjectNode node2 = mapper.createObjectNode();
        node2.put("a", 1);
        assertEquals(new JsonNodeResource(node1).hashCode(),
                new JsonNodeResource(node2).hashCode());
    }


    @Test
    void toStringContainsNodeContent()
    {
        ObjectNode node = mapper.createObjectNode();
        node.put("k", "v");
        String s = new JsonNodeResource(node).toString();
        assertTrue(s.startsWith("JsonNodeResource["));
        assertTrue(s.contains("\"k\""));
    }

    // --- Dynamic proxy tests ---


    @Test
    void proxyDelegatesToDefaultMethods()
    {
        ObjectNode node = mapper.createObjectNode();
        node.put("name", "Study-1");
        node.put("label", "My Study");
        SampleDomainResource proxy = JsonNodeResource.of(node, SampleDomainResource.class);

        assertEquals(Optional.of("Study-1"), proxy.getName());
        assertEquals(Optional.of("My Study"), proxy.getLabel());
    }


    @Test
    void proxyToStringIncludesTypeName()
    {
        ObjectNode node = mapper.createObjectNode();
        SampleDomainResource proxy = JsonNodeResource.of(node, SampleDomainResource.class);
        assertTrue(proxy.toString().startsWith("SampleDomainResource["));
    }


    @Test
    void proxyEqualsComparesViaDelegateEquals()
    {
        ObjectNode node = mapper.createObjectNode();
        node.put("x", 1);
        // Proxy equality delegates to JsonNodeResource.equals, which checks instanceof
        // JsonNodeResource — two proxies won't be equal via equals(), but a proxy compared
        // to the underlying JsonNodeResource will work
        SampleDomainResource proxy = JsonNodeResource.of(node, SampleDomainResource.class);
        JsonNodeResource direct = new JsonNodeResource(node);
        assertEquals(proxy, direct);
    }


    @Test
    void proxyHashCodeUsesNodeHashCode()
    {
        ObjectNode node = mapper.createObjectNode();
        node.put("x", 1);
        SampleDomainResource proxy = JsonNodeResource.of(node, SampleDomainResource.class);
        JsonNodeResource direct = new JsonNodeResource(node);
        assertEquals(proxy.hashCode(), direct.hashCode());
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
