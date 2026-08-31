package net.cumba.web.api.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

/**
 * Edge-case and integration tests for {@link JsonNodeResource} that complement the main
 * {@link JsonNodeResourceTest}.
 */
class JsonNodeResourceEdgeCaseTest
{

    private final ObjectMapper mapper = new ObjectMapper();

    // --- Null JSON values ---

    @Nested
    class NullJsonValues
    {

        @Test
        void getStringReturnsEmptyForNullValue()
        {
            ObjectNode node = mapper.createObjectNode();
            node.putNull("field");
            assertTrue(JsonNodeResource.of(node).getString("field").isEmpty());
        }


        @Test
        void getIntReturnsEmptyForNullValue()
        {
            ObjectNode node = mapper.createObjectNode();
            node.putNull("field");
            assertTrue(JsonNodeResource.of(node).getInt("field").isEmpty());
        }


        @Test
        void getBooleanReturnsEmptyForNullValue()
        {
            ObjectNode node = mapper.createObjectNode();
            node.putNull("field");
            assertTrue(JsonNodeResource.of(node).getBoolean("field").isEmpty());
        }


        @Test
        void getObjectReturnsEmptyForNullValue()
        {
            ObjectNode node = mapper.createObjectNode();
            node.putNull("field");
            assertTrue(JsonNodeResource.of(node).getObject("field", ApiResource.class).isEmpty());
        }


        @Test
        void getArrayReturnsEmptyForNullValue()
        {
            ObjectNode node = mapper.createObjectNode();
            node.putNull("field");
            assertTrue(
                    JsonNodeResource.of(node).getArray("field", ApiArrayResource.class).isEmpty());
        }
    }

    // --- getLinks edge cases ---


    @Nested
    class LinksEdgeCases
    {

        @Test
        void getLinksReturnsEmptyForNonObjectLinksNode()
        {
            ObjectNode node = mapper.createObjectNode();
            node.put("_links", "not an object");
            assertTrue(JsonNodeResource.of(node).getLinks("self").isEmpty());
        }


        @Test
        void getLinkReturnsEmptyForNonObjectLinksNode()
        {
            ObjectNode node = mapper.createObjectNode();
            node.put("_links", "not an object");
            assertTrue(JsonNodeResource.of(node).getLink("self").isEmpty());
        }


        @Test
        void getLinksReturnsEmptyForNonObjectNonArrayRelNode()
        {
            ObjectNode links = mapper.createObjectNode();
            links.put("self", "just a string");
            ObjectNode node = mapper.createObjectNode();
            node.set("_links", links);
            assertTrue(JsonNodeResource.of(node).getLinks("self").isEmpty());
        }


        @Test
        void getLinkReturnsEmptyForNonObjectNonArrayRelNode()
        {
            ObjectNode links = mapper.createObjectNode();
            links.put("self", 42);
            ObjectNode node = mapper.createObjectNode();
            node.set("_links", links);
            assertTrue(JsonNodeResource.of(node).getLink("self").isEmpty());
        }


        @Test
        void getLinksReturnsUnmodifiableList()
        {
            ObjectNode linkObj = mapper.createObjectNode();
            linkObj.put("href", "/test");
            ArrayNode arr = mapper.createArrayNode();
            arr.add(linkObj);
            ObjectNode links = mapper.createObjectNode();
            links.set("items", arr);
            ObjectNode node = mapper.createObjectNode();
            node.set("_links", links);

            List<Link> result = JsonNodeResource.of(node).getLinks("items");
            try
            {
                result.add(null);
                // If we get here, list is not unmodifiable
                assertFalse(true, "Expected UnsupportedOperationException");
            }
            catch (UnsupportedOperationException _)
            {
                // expected
            }
        }
    }

    // --- getFieldNames edge cases ---


    @Nested
    class FieldNameEdgeCases
    {

        @Test
        void getFieldNamesSetSizeMatchesNodeSize()
        {
            ObjectNode node = mapper.createObjectNode();
            node.put("a", 1);
            node.put("b", 2);
            Set<String> names = JsonNodeResource.of(node).getFieldNames();
            assertEquals(2, names.size());
        }


        // Deliberately probes Set.contains(Object) with a non-String to verify the type-mismatch
        // fallback.
        @SuppressWarnings(
        {
                "unlikely-arg-type", "CollectionIncompatibleType"
        })
        @Test
        void getFieldNamesContainsReturnsFalseForNonString()
        {
            ObjectNode node = mapper.createObjectNode();
            node.put("a", 1);
            Set<String> names = JsonNodeResource.of(node).getFieldNames();
            assertFalse(names.contains(42));
            assertFalse(names.contains(null));
        }


        @Test
        void getFieldNamesIteratorWorks()
        {
            ObjectNode node = mapper.createObjectNode();
            node.put("x", 1);
            node.put("y", 2);
            Set<String> names = JsonNodeResource.of(node).getFieldNames();
            int count = 0;
            for (String name : names)
            {
                assertNotNull(name);
                count++;
            }
            assertEquals(2, count);
        }
    }

    // --- getList lazy evaluation ---


    @Nested
    class LazyList
    {

        @Test
        void getListIsLazyNotEager()
        {
            ObjectNode item = mapper.createObjectNode();
            item.put("id", "a");
            ArrayNode arr = mapper.createArrayNode();
            arr.add(item);
            ObjectNode node = mapper.createObjectNode();
            node.set("items", arr);

            List<ApiResource> list = JsonNodeResource.of(node).getList("items", ApiResource.class);
            // The list should work even after the node is modified
            // (since it references the ArrayNode directly)
            assertEquals(1, list.size());
            assertEquals(Optional.of("a"), list.get(0).getString("id"));
        }


        @Test
        void getStringListIsLazy()
        {
            ArrayNode arr = mapper.createArrayNode();
            arr.add("hello");
            arr.add("world");
            ObjectNode node = mapper.createObjectNode();
            node.set("items", arr);

            List<String> list = JsonNodeResource.of(node).getStringList("items");
            assertEquals("hello", list.get(0));
            assertEquals("world", list.get(1));
            assertEquals(2, list.size());
        }
    }

    // --- getStream ---

    @Test
    void getStreamWithMultipleElements()
    {
        ObjectNode item1 = mapper.createObjectNode();
        item1.put("v", 1);
        ObjectNode item2 = mapper.createObjectNode();
        item2.put("v", 2);
        ObjectNode item3 = mapper.createObjectNode();
        item3.put("v", 3);
        ArrayNode arr = mapper.createArrayNode();
        arr.add(item1);
        arr.add(item2);
        arr.add(item3);
        ObjectNode node = mapper.createObjectNode();
        node.set("items", arr);

        List<Integer> values = JsonNodeResource.of(node).getStream("items", ApiResource.class)
                .map(r -> r.getInt("v").orElse(-1)).toList();
        assertEquals(List.of(1, 2, 3), values);
    }

    // --- Domain proxy with nested default methods ---


    @Test
    void proxyWithChainedDefaultMethods()
    {
        ObjectNode node = mapper.createObjectNode();
        node.put("firstName", "John");
        node.put("lastName", "Doe");

        NamedResource proxy = JsonNodeResource.of(node, NamedResource.class);
        assertEquals(Optional.of("John"), proxy.firstName());
        assertEquals(Optional.of("Doe"), proxy.lastName());
        assertEquals("John Doe", proxy.fullName());
    }


    @Test
    void proxyWithMissingFieldsInChainedDefaults()
    {
        ObjectNode node = mapper.createObjectNode();
        node.put("firstName", "John");

        NamedResource proxy = JsonNodeResource.of(node, NamedResource.class);
        assertEquals("John ", proxy.fullName());
    }

    // --- getObject default method (no type arg) ---


    @Test
    void getObjectDefaultMethodDelegatesToTypedVersion()
    {
        ObjectNode inner = mapper.createObjectNode();
        inner.put("key", "value");
        ObjectNode outer = mapper.createObjectNode();
        outer.set("nested", inner);

        ApiResource r = JsonNodeResource.of(outer);
        Optional<ApiResource> nested = r.getObject("nested");
        assertTrue(nested.isPresent());
        assertEquals(Optional.of("value"), nested.get().getString("key"));
    }

    /**
     * Domain interface with chained default methods for testing proxy behavior.
     */
    interface NamedResource extends ApiResource
    {

        default Optional<String> firstName()
        {
            return getString("firstName");
        }


        default Optional<String> lastName()
        {
            return getString("lastName");
        }


        default String fullName()
        {
            return firstName().orElse("") + " " + lastName().orElse("");
        }
    }
}
