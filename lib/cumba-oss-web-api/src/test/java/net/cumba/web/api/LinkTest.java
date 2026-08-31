package net.cumba.web.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Optional;
import net.cumba.web.api.json.JsonNodeResource;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for the {@link Link} interface default methods, especially the {@code id()} and
 * {@code id(int)} path-segment extraction.
 */
class LinkTest
{

    private final ObjectMapper mapper = new ObjectMapper();

    private Link linkWithHref(String href)
    {
        ObjectNode node = mapper.createObjectNode();
        if (href != null)
        {
            node.put("href", href);
        }
        return JsonNodeResource.of(node, Link.class);
    }

    // --- href() ---


    @Test
    void hrefReturnsValue()
    {
        assertEquals(Optional.of("/api/test"), linkWithHref("/api/test").href());
    }


    @Test
    void hrefReturnsEmptyWhenMissing()
    {
        assertTrue(linkWithHref(null).href().isEmpty());
    }

    // --- title() ---


    @Test
    void titleReturnsValue()
    {
        ObjectNode node = mapper.createObjectNode();
        node.put("title", "My Title");
        Link link = JsonNodeResource.of(node, Link.class);
        assertEquals(Optional.of("My Title"), link.title());
    }


    @Test
    void titleReturnsEmptyWhenMissing()
    {
        Link link = linkWithHref("/test");
        assertTrue(link.title().isEmpty());
    }

    // --- type() ---


    @Test
    void typeReturnsValue()
    {
        ObjectNode node = mapper.createObjectNode();
        node.put("type", "application/json");
        Link link = JsonNodeResource.of(node, Link.class);
        assertEquals(Optional.of("application/json"), link.type());
    }

    // --- templated() ---


    @Test
    void templatedReturnsTrueWhenSet()
    {
        ObjectNode node = mapper.createObjectNode();
        node.put("templated", true);
        Link link = JsonNodeResource.of(node, Link.class);
        assertTrue(link.templated());
    }


    @Test
    void templatedDefaultsToFalse()
    {
        Link link = linkWithHref("/test");
        assertFalse(link.templated());
    }


    @Test
    void templatedReturnsFalseWhenSetToFalse()
    {
        ObjectNode node = mapper.createObjectNode();
        node.put("templated", false);
        Link link = JsonNodeResource.of(node, Link.class);
        assertFalse(link.templated());
    }

    // --- hreflang(), name(), deprecation(), profile() ---


    @Test
    void hreflangReturnsValue()
    {
        ObjectNode node = mapper.createObjectNode();
        node.put("hreflang", "en-US");
        Link link = JsonNodeResource.of(node, Link.class);
        assertEquals(Optional.of("en-US"), link.hreflang());
    }


    @Test
    void nameReturnsValue()
    {
        ObjectNode node = mapper.createObjectNode();
        node.put("name", "primary");
        Link link = JsonNodeResource.of(node, Link.class);
        assertEquals(Optional.of("primary"), link.name());
    }


    @Test
    void deprecationReturnsValue()
    {
        ObjectNode node = mapper.createObjectNode();
        node.put("deprecation", "https://docs.example.com/dep");
        Link link = JsonNodeResource.of(node, Link.class);
        assertEquals(Optional.of("https://docs.example.com/dep"), link.deprecation());
    }


    @Test
    void profileReturnsValue()
    {
        ObjectNode node = mapper.createObjectNode();
        node.put("profile", "https://schema.example.com/v1");
        Link link = JsonNodeResource.of(node, Link.class);
        assertEquals(Optional.of("https://schema.example.com/v1"), link.profile());
    }

    // --- id() ---

    @Nested
    class IdNoArgs
    {

        @Test
        void returnsLastSegment()
        {
            assertEquals(Optional.of("adam-2-1"), linkWithHref("/mdr/adam/adam-2-1").id());
        }


        @Test
        void returnsEmptyForMissingHref()
        {
            assertTrue(linkWithHref(null).id().isEmpty());
        }


        @Test
        void returnsEmptyForEmptyString()
        {
            assertTrue(linkWithHref("").id().isEmpty());
        }


        @Test
        void returnsEmptyForTrailingSlash()
        {
            assertTrue(linkWithHref("/path/").id().isEmpty());
        }


        @Test
        void returnsEmptyForSlashOnly()
        {
            assertTrue(linkWithHref("/").id().isEmpty());
        }


        @Test
        void returnsEmptyForNoSlash()
        {
            // A bare word with no slash has no "last segment after slash"
            assertTrue(linkWithHref("item").id().isEmpty());
        }


        @Test
        void returnsLastSegmentForRelativePath()
        {
            assertEquals(Optional.of("item"), linkWithHref("path/item").id());
        }
    }

    // --- id(int) ---


    @Nested
    class IdWithIndex
    {

        @Test
        void indexZeroReturnsLastSegment()
        {
            assertEquals(Optional.of("adam-2-1"), linkWithHref("/mdr/adam/adam-2-1").id(0));
        }


        @Test
        void indexZeroReturnsEmptyForTrailingSlash()
        {
            assertTrue(linkWithHref("/path/").id(0).isEmpty());
        }


        @Test
        void indexZeroReturnsEmptyForSlashOnly()
        {
            assertTrue(linkWithHref("/").id(0).isEmpty());
        }


        @Test
        void positiveIndexReturnsSegment()
        {
            Link link = linkWithHref("/mdr/adam/adam-2-1");
            // split("/") -> ["", "mdr", "adam", "adam-2-1"]
            assertEquals(Optional.of("mdr"), link.id(1));
            assertEquals(Optional.of("adam"), link.id(2));
            assertEquals(Optional.of("adam-2-1"), link.id(3));
        }


        @Test
        void positiveIndexOutOfRangeReturnsEmpty()
        {
            assertTrue(linkWithHref("/mdr/adam").id(99).isEmpty());
        }


        @Test
        void positiveIndexAtBoundaryReturnsEmpty()
        {
            // parts = ["", "mdr", "adam"], length = 3
            // id(3) -> 3 >= 3 -> empty
            assertTrue(linkWithHref("/mdr/adam").id(3).isEmpty());
        }


        @Test
        void negativeIndexCountsFromEnd()
        {
            // parts = ["", "mdr", "adam", "adam-2-1"], length=4
            // -1 -> index 4-1-1=2 -> "adam"
            assertEquals(Optional.of("adam"), linkWithHref("/mdr/adam/adam-2-1").id(-1));
        }


        @Test
        void negativeIndexMinusTwoGetsThirdFromEnd()
        {
            // parts = ["", "mdr", "adam", "adam-2-1"], length=4
            // -2 -> index 4-1-2=1 -> "mdr"
            assertEquals(Optional.of("mdr"), linkWithHref("/mdr/adam/adam-2-1").id(-2));
        }


        @Test
        void negativeIndexOutOfRangeReturnsEmpty()
        {
            assertTrue(linkWithHref("/a/b").id(-99).isEmpty());
        }


        @Test
        void negativeIndexOnEmptySegmentReturnsEmpty()
        {
            // parts = ["", "mdr", "adam", ""], -1 -> index 4-1-1=2 -> "adam"
            assertEquals(Optional.of("adam"), linkWithHref("/mdr/adam/").id(-1));
        }


        @Test
        void indexReturnsEmptyForMissingHref()
        {
            assertTrue(linkWithHref(null).id(0).isEmpty());
            assertTrue(linkWithHref(null).id(1).isEmpty());
            assertTrue(linkWithHref(null).id(-1).isEmpty());
        }


        @Test
        void indexReturnsEmptyForEmptyString()
        {
            assertTrue(linkWithHref("").id(0).isEmpty());
        }


        @Test
        void positiveIndexSkipsEmptyLeadingSegment()
        {
            // Leading slash produces empty segment at index 0
            // Accessing index 0 in the parts array via positive index is not
            // possible because aIndex==0 takes the "last segment" path
            Link link = linkWithHref("/mdr/adam");
            // parts = ["", "mdr", "adam"]
            assertEquals(Optional.of("mdr"), link.id(1));
        }
    }

    // --- Link as ApiResource ---

    @Test
    void linkHasFieldAccessors()
    {
        ObjectNode node = mapper.createObjectNode();
        node.put("href", "/test");
        node.put("custom", "value");
        Link link = JsonNodeResource.of(node, Link.class);

        assertTrue(link.containsFieldName("href"));
        assertTrue(link.containsFieldName("custom"));
        assertEquals(Optional.of("value"), link.getString("custom"));
    }


    @Test
    void linkGetFieldCount()
    {
        ObjectNode node = mapper.createObjectNode();
        node.put("href", "/test");
        node.put("title", "Test");
        Link link = JsonNodeResource.of(node, Link.class);
        assertEquals(2, link.getFieldCount());
    }
}
