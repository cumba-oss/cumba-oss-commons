package net.cumba.web.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.DocumentBuilderFactory;
import net.cumba.web.api.dev.ListResource;
import net.cumba.web.api.dev.MapResource;
import net.cumba.web.api.json.JsonArrayResource;
import net.cumba.web.api.json.JsonNodeResource;
import net.cumba.web.api.xml.XmlChildListResource;
import net.cumba.web.api.xml.XmlElementResource;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

class ProxyEqualityTest
{

    interface Row extends ApiArrayResource
    {
    }

    private static Element parse(String aXml) throws Exception
    {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        return f.newDocumentBuilder()
                .parse(new ByteArrayInputStream(aXml.getBytes(StandardCharsets.UTF_8)))
                .getDocumentElement();
    }


    @Test
    void mapResourceProxyIsReflexive()
    {
        Link a = MapResource.of(Map.of("href", "/a"), Link.class);
        assertTrue(a.equals(a), "a proxy must equal itself");
    }


    @Test
    void listResourceProxyIsReflexive()
    {
        Row a = ListResource.of(List.of("x"), Row.class);
        assertTrue(a.equals(a), "a proxy must equal itself");
    }


    @Test
    void jsonNodeProxyIsReflexive()
    {
        JsonNode n = new ObjectMapper().createObjectNode().put("href", "/a");
        Link a = JsonNodeResource.of(n, Link.class);
        assertTrue(a.equals(a), "a proxy must equal itself");
    }


    @Test
    void jsonArrayProxyIsReflexive()
    {
        JsonNode n = new ObjectMapper().createArrayNode().add("x");
        Row a = JsonArrayResource.of(n, Row.class);
        assertTrue(a.equals(a), "a proxy must equal itself");
    }


    @Test
    void xmlElementProxyIsReflexive() throws Exception
    {
        Element e = parse("<link href='/a'/>");
        Link a = XmlElementResource.of(e, Link.class);
        assertTrue(a.equals(a), "a proxy must equal itself");
    }


    @Test
    void xmlChildListProxyIsReflexive() throws Exception
    {
        Element root = parse("<r><i>x</i></r>");
        Row a = XmlChildListResource.of(List.of((Element) root.getFirstChild()), Row.class);
        assertTrue(a.equals(a), "a proxy must equal itself");
    }


    @Test
    void twoProxiesOverEqualDataAreEqualAndHashAlike()
    {
        Link a = MapResource.of(Map.of("href", "/a"), Link.class);
        Link b = MapResource.of(Map.of("href", "/a"), Link.class);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }


    @Test
    void proxyEqualityIsSymmetricWithThePlainResource()
    {
        ApiResource plain = MapResource.of(Map.of("href", "/a"));
        Link proxy = MapResource.of(Map.of("href", "/a"), Link.class);
        assertEquals(proxy, plain, "proxy.equals(plain)");
        assertEquals(plain, proxy, "plain.equals(proxy)");
    }


    @Test
    void proxiesOverDifferentDataAreNotEqual()
    {
        Link a = MapResource.of(Map.of("href", "/a"), Link.class);
        Link b = MapResource.of(Map.of("href", "/b"), Link.class);
        assertNotEquals(a, b);
    }
}
