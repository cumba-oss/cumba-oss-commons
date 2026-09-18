package net.cumba.web.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import net.cumba.web.api.dev.ListResource;
import net.cumba.web.api.dev.MapResource;
import net.cumba.web.api.json.JsonArrayResource;
import net.cumba.web.api.json.JsonNodeResource;
import org.junit.jupiter.api.Test;

/**
 * {@code equals} / {@code hashCode} / {@code toString} for the four non-XML resource
 * implementations. A constant {@code hashCode} would be contract-legal and useless, so each case
 * also asserts that different content hashes differently.
 */
class ValueSemanticsTest
{

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static JsonNode json(String aJson) throws Exception
    {
        return MAPPER.readTree(aJson);
    }


    @Test
    void jsonNodeResource() throws Exception
    {
        JsonNodeResource a = new JsonNodeResource(json("{\"k\":1}"));
        JsonNodeResource twin = new JsonNodeResource(json("{\"k\":1}"));
        JsonNodeResource other = new JsonNodeResource(json("{\"k\":2}"));

        assertEquals(a, a);
        assertEquals(a, twin);
        assertEquals(a.hashCode(), twin.hashCode());
        assertNotEquals(a, other);
        assertNotEquals(a.hashCode(), other.hashCode());
        assertNotEquals(a, null);
        assertFalse(a.equals("{\"k\":1}"));
        assertTrue(a.toString().startsWith("JsonNodeResource["), a.toString());
    }


    @Test
    void jsonArrayResource() throws Exception
    {
        JsonArrayResource a = new JsonArrayResource(json("[1,2]"));
        JsonArrayResource twin = new JsonArrayResource(json("[1,2]"));
        JsonArrayResource other = new JsonArrayResource(json("[1,3]"));

        assertEquals(a, a);
        assertEquals(a, twin);
        assertEquals(a.hashCode(), twin.hashCode());
        assertNotEquals(a, other);
        assertNotEquals(a.hashCode(), other.hashCode());
        assertNotEquals(a, null);
        assertFalse(a.equals("[1,2]"));
        assertTrue(a.toString().startsWith("JsonArrayResource["), a.toString());
    }


    @Test
    void listResource()
    {
        ListResource a = new ListResource(List.of("x", "y"));
        ListResource twin = new ListResource(List.of("x", "y"));
        ListResource other = new ListResource(List.of("x", "z"));

        assertEquals(a, a);
        assertEquals(a, twin);
        assertEquals(a.hashCode(), twin.hashCode());
        assertNotEquals(a, other);
        assertNotEquals(a.hashCode(), other.hashCode());
        assertNotEquals(a, null);
        assertFalse(a.equals(List.of("x", "y")));
        assertTrue(a.toString().startsWith("ListResource["), a.toString());
    }


    @Test
    void mapResource()
    {
        MapResource a = new MapResource(Map.of("k", "1"));
        MapResource twin = new MapResource(Map.of("k", "1"));
        MapResource other = new MapResource(Map.of("k", "2"));

        assertEquals(a, a);
        assertEquals(a, twin);
        assertEquals(a.hashCode(), twin.hashCode());
        assertNotEquals(a, other);
        assertNotEquals(a.hashCode(), other.hashCode());
        assertNotEquals(a, null);
        assertFalse(a.equals(Map.of("k", "1")));
    }


    @Test
    void aProxyOfOneImplementationNeverEqualsAProxyOfAnother() throws Exception
    {
        Link fromMap = MapResource.of(Map.of("href", "/a"), Link.class);
        Link fromJson = JsonNodeResource.of(json("{\"href\":\"/a\"}"), Link.class);
        assertNotEquals(fromMap, fromJson,
                "only this class's own proxies are unwrapped for comparison");
        assertNotEquals(fromJson, fromMap, "and the same in the other direction");
    }


    @Test
    void anUnrelatedDynamicProxyIsNotUnwrapped()
    {
        Object alien = java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]
                {
                        Runnable.class
                }, (p, m, args) -> "toString".equals(m.getName()) ? "alien"
                        : "equals".equals(m.getName()) ? false : Integer.valueOf(0));
        assertNotEquals(new MapResource(Map.of("k", "1")), alien);
        assertNotEquals(new ListResource(List.of("x")), alien);
    }
}
