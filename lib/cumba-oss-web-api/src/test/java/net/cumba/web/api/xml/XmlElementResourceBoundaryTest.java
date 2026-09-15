package net.cumba.web.api.xml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import javax.xml.parsers.DocumentBuilderFactory;
import net.cumba.web.api.ApiResource;
import net.cumba.web.api.Link;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

/**
 * Boundary coverage for {@link XmlElementResource}: the int range, the name-matching fallback used
 * on documents parsed without namespaces, and the value semantics of the class and its proxies.
 */
class XmlElementResourceBoundaryTest
{

    private static Element parse(String aXml, boolean aNamespaceAware) throws Exception
    {
        DocumentBuilderFactory f = DocumentBuilderFactory.newDefaultInstance();
        f.setNamespaceAware(aNamespaceAware);
        return f.newDocumentBuilder()
                .parse(new ByteArrayInputStream(aXml.getBytes(StandardCharsets.UTF_8)))
                .getDocumentElement();
    }


    private static XmlElementResource of(String aXml) throws Exception
    {
        return new XmlElementResource(parse(aXml, true));
    }


    @Test
    void theExtremesOfTheIntRangeAreStillInts() throws Exception
    {
        XmlElementResource r = of("<e max='2147483647' min='-2147483648'/>");
        assertEquals(Integer.MAX_VALUE, r.getInt("max").orElseThrow());
        assertEquals(Integer.MIN_VALUE, r.getInt("min").orElseThrow());
    }


    @Test
    void oneStepOutsideTheIntRangeIsNotAnInt() throws Exception
    {
        XmlElementResource r = of("<e over='2147483648' under='-2147483649'/>");
        assertTrue(r.getInt("over").isEmpty(), "2147483648 must not become -2147483648");
        assertTrue(r.getInt("under").isEmpty());
        assertEquals(2_147_483_648L, r.getLong("over").orElseThrow());
    }


    @Test
    void withoutNamespacesTheTagNameIsUsedAndAMissingFieldStillAnswersEmpty() throws Exception
    {
        // Parsed WITHOUT namespace awareness, getLocalName() is null, so matchesName falls back
        // to getTagName(). If that fallback answered "yes" to everything, the first child would
        // be returned for any name asked for.
        XmlElementResource r = new XmlElementResource(parse("<e><name>Bob</name></e>", false));
        assertEquals(Optional.of("Bob"), r.getString("name"));
        assertEquals(Optional.empty(), r.getString("absent"),
                "a name that is not there must not match the first child");
        assertFalse(r.containsFieldName("absent"));
        assertTrue(r.containsFieldName("name"));
    }


    @Test
    void namespacePrefixesAreIgnoredWhenMatching() throws Exception
    {
        XmlElementResource r = of("<e xmlns:d='urn:d'><d:name>Bob</d:name></e>");
        assertEquals(Optional.of("Bob"), r.getString("name"));
        assertEquals(Optional.empty(), r.getString("d:name"),
                "the local name is what matches, not the prefixed form");
    }


    @Test
    void xmlnsDeclarationsAreNeitherFieldsNorEvidenceOfAnObject() throws Exception
    {
        XmlElementResource r = of("<e xmlns='urn:x'>text</e>");
        assertEquals(java.util.Set.of(), r.getFieldNames(),
                "a namespace declaration is not a field");
        assertFalse(r.isObject("anything"));
    }


    @Test
    void fieldNamesCoverAttributesAndDistinctChildNames() throws Exception
    {
        XmlElementResource r = of("<e id='1'><a>x</a><b>y</b><a>z</a></e>");
        assertEquals(java.util.Set.of("id", "a", "b"), r.getFieldNames());
        assertEquals(3, r.getFieldCount());
    }


    @Test
    void valueSemanticsFollowTheBackingElement() throws Exception
    {
        Element e = parse("<e id='1'/>", true);
        XmlElementResource a = new XmlElementResource(e);
        XmlElementResource b = new XmlElementResource(e);
        assertEquals(a, a, "reflexive");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, of("<e id='2'/>"));
        assertNotEquals(a.hashCode(), of("<e id='2'/>").hashCode(),
                "hashCode must follow the element, not be a constant");
        assertNotEquals(a, "not a resource");
        assertNotEquals(a, null);
        assertTrue(a.toString().contains("<e>"), a.toString());
    }


    @Test
    void aProxyNamesItsInterfaceAndCarriesTheSameValueSemantics() throws Exception
    {
        Element e = parse("<link href='/a/b'/>", true);
        Link a = XmlElementResource.of(e, Link.class);
        Link b = XmlElementResource.of(e, Link.class);

        assertEquals("Link[<link>]", a.toString(),
                "Object methods are answered by the handler, not forwarded to the delegate");
        assertEquals(a, a, "reflexive");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertEquals(Optional.of("b"), a.id(), "a default method runs on the interface");
        assertEquals(Optional.of("/a/b"), a.href());
        assertNotEquals(a, XmlElementResource.of(parse("<link href='/z'/>", true), Link.class));
    }


    @Test
    void aProxyToStringUsesTheLocalNameRatherThanThePrefixedTagName() throws Exception
    {
        // localName "link" but tagName "d:link" - the two differ only when a prefix is in play,
        // which is why an unprefixed document cannot tell the branches apart.
        Element e = parse("<d:link xmlns:d='urn:d' href='/a'/>", true);
        assertEquals("link", e.getLocalName());
        assertEquals("d:link", e.getTagName());
        assertEquals("Link[<link>]", XmlElementResource.of(e, Link.class).toString());
        assertEquals("XmlElementResource[<link>]", new XmlElementResource(e).toString());
    }


    @Test
    void withoutANamespaceTheTagNameIsWhatToStringShows() throws Exception
    {
        Element e = parse("<d:link href='/a'/>", false);
        assertEquals(null, e.getLocalName(), "a non-namespace-aware parse has no local name");
        assertEquals("Link[<d:link>]", XmlElementResource.of(e, Link.class).toString());
        assertEquals("XmlElementResource[<d:link>]", new XmlElementResource(e).toString());
    }


    @Test
    void theUntypedFactoryDoesNotProxy() throws Exception
    {
        ApiResource r = XmlElementResource.of(parse("<e id='1'/>", true));
        assertTrue(r instanceof XmlElementResource);
        assertEquals(Optional.of("1"), r.getString("id"));
    }
}
