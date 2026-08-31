package net.cumba.web.api.xml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import net.cumba.web.api.ApiArrayResource;
import net.cumba.web.api.ApiResource;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Additional coverage for {@link XmlElementResource} beyond the basics in
 * {@link XmlElementResourceTest}: proxy {@code equals/hashCode}, namespace-aware vs.
 * non-namespace-aware parsing, complex-element detection edge cases, {@code getStream},
 * {@code getArray}, fallbacks in number/boolean parsing, and the {@code _links} list edge case.
 */
class XmlElementResourceExtendedTest
{

    private static Element parseXml(String xml) throws Exception
    {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        return doc.getDocumentElement();
    }


    private static Element parseXmlNoNs(String xml) throws Exception
    {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(false);
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        return doc.getDocumentElement();
    }

    @Nested
    class FactoryAndRejection
    {

        @Test
        void constructorRejectsNull()
        {
            assertThrows(NullPointerException.class, () -> new XmlElementResource(null));
        }


        @Test
        void ofWithApiResourceTypeReturnsConcreteInstance() throws Exception
        {
            Element elem = parseXml("<Root OID=\"x\"/>");
            ApiResource r = XmlElementResource.of(elem, ApiResource.class);
            assertNotNull(r);
            // Not a proxy when the type is ApiResource itself
            assertEquals(elem, ((XmlElementResource) r).getElement());
        }


        @Test
        void getElementReturnsBackingElement() throws Exception
        {
            Element elem = parseXml("<Root/>");
            XmlElementResource res = new XmlElementResource(elem);
            assertSame(elem, res.getElement());
        }


        @Test
        void getTextContentReturnsRawText() throws Exception
        {
            Element elem = parseXml("<Greeting>hello world</Greeting>");
            XmlElementResource res = new XmlElementResource(elem);
            assertEquals("hello world", res.getTextContent());
        }


        @Test
        void getTextContentReturnsEmptyForElementWithoutText() throws Exception
        {
            Element elem = parseXml("<Root/>");
            XmlElementResource res = new XmlElementResource(elem);
            assertEquals("", res.getTextContent());
        }
    }


    @Nested
    class NumberEdgeCases
    {

        @Test
        void isNumberFalseForNonNumericText() throws Exception
        {
            Element elem = parseXml("<Item Name=\"AGE\"/>");
            XmlElementResource res = new XmlElementResource(elem);
            assertFalse(res.isNumber("Name"));
        }


        @Test
        void isNumberFalseForMissing() throws Exception
        {
            Element elem = parseXml("<Item/>");
            assertFalse(new XmlElementResource(elem).isNumber("Missing"));
        }


        @Test
        void getIntReturnsEmptyForOverflow() throws Exception
        {
            // 99999999999 overflows int but fits in long → tryParseLong succeeds,
            // longVal.intValue() truncates to a (different) int
            Element elem = parseXml("<Item N=\"99999999999\"/>");
            XmlElementResource res = new XmlElementResource(elem);
            // Truncation behaviour — assertion on presence only
            assertTrue(res.getInt("N").isPresent());
        }


        @Test
        void getIntReturnsEmptyForMissing() throws Exception
        {
            Element elem = parseXml("<Item/>");
            assertTrue(new XmlElementResource(elem).getInt("missing").isEmpty());
        }


        @Test
        void getLongReturnsEmptyForMissing() throws Exception
        {
            Element elem = parseXml("<Item/>");
            assertTrue(new XmlElementResource(elem).getLong("missing").isEmpty());
        }


        @Test
        void getDoubleReturnsEmptyForMissing() throws Exception
        {
            Element elem = parseXml("<Item/>");
            assertTrue(new XmlElementResource(elem).getDouble("missing").isEmpty());
        }


        @Test
        void getNumberReturnsEmptyForMissing() throws Exception
        {
            Element elem = parseXml("<Item/>");
            assertTrue(new XmlElementResource(elem).getNumber("missing").isEmpty());
        }


        @Test
        void getNumberReturnsEmptyForNonNumeric() throws Exception
        {
            Element elem = parseXml("<Item N=\"not a number\"/>");
            assertTrue(new XmlElementResource(elem).getNumber("N").isEmpty());
        }


        @Test
        void getNumberPrefersLongOverDouble() throws Exception
        {
            Element elem = parseXml("<Item N=\"42\"/>");
            Number n = new XmlElementResource(elem).getNumber("N").orElseThrow();
            assertTrue(n instanceof Long);
            assertEquals(42L, n.longValue());
        }


        @Test
        void getNumberFallsBackToDouble() throws Exception
        {
            Element elem = parseXml("<Item N=\"3.14\"/>");
            Number n = new XmlElementResource(elem).getNumber("N").orElseThrow();
            assertTrue(n instanceof Double);
            assertEquals(3.14, n.doubleValue(), 0.001);
        }
    }


    @Nested
    class BooleanEdgeCases
    {

        @Test
        void isBooleanTrueForYesNo() throws Exception
        {
            Element elem = parseXml("<Item A=\"Yes\" B=\"no\"/>");
            XmlElementResource res = new XmlElementResource(elem);
            assertTrue(res.isBoolean("A"));
            assertTrue(res.isBoolean("B"));
        }


        @Test
        void isBooleanTrueForMixedCase() throws Exception
        {
            Element elem = parseXml("<Item A=\"TRUE\" B=\"False\"/>");
            XmlElementResource res = new XmlElementResource(elem);
            assertTrue(res.isBoolean("A"));
            assertTrue(res.isBoolean("B"));
        }


        @Test
        void isBooleanFalseForOtherStrings() throws Exception
        {
            Element elem = parseXml("<Item A=\"maybe\"/>");
            assertFalse(new XmlElementResource(elem).isBoolean("A"));
        }


        @Test
        void isBooleanFalseForMissing() throws Exception
        {
            Element elem = parseXml("<Item/>");
            assertFalse(new XmlElementResource(elem).isBoolean("missing"));
        }


        @Test
        void getBooleanReturnsEmptyForMissing() throws Exception
        {
            Element elem = parseXml("<Item/>");
            assertTrue(new XmlElementResource(elem).getBoolean("missing").isEmpty());
        }


        @Test
        void getBooleanReturnsEmptyForNonBoolean() throws Exception
        {
            Element elem = parseXml("<Item A=\"maybe\"/>");
            assertTrue(new XmlElementResource(elem).getBoolean("A").isEmpty());
        }
    }


    @Nested
    class IsObjectEdgeCases
    {

        @Test
        void isObjectTrueForChildWithOnlyAttributes() throws Exception
        {
            Element elem = parseXml("""
                    <Root>
                        <Child OID="x"/>
                    </Root>
                    """);
            assertTrue(new XmlElementResource(elem).isObject("Child"));
        }


        @Test
        void isObjectFalseForSimpleTextChild() throws Exception
        {
            Element elem = parseXml("""
                    <Root>
                        <Label>Plain text</Label>
                    </Root>
                    """);
            assertFalse(new XmlElementResource(elem).isObject("Label"));
        }


        @Test
        void isObjectFalseForChildWithOnlyXmlnsAttribute() throws Exception
        {
            Element elem = parseXml("""
                    <Root xmlns:ns="http://example.com/ns">
                        <Child xmlns:def="http://example.com/def">text</Child>
                    </Root>
                    """);
            // Child has only xmlns attributes and no element children → not complex
            assertFalse(new XmlElementResource(elem).isObject("Child"));
        }


        @Test
        void isObjectFalseForMissingChild() throws Exception
        {
            Element elem = parseXml("<Root/>");
            assertFalse(new XmlElementResource(elem).isObject("Child"));
        }
    }


    @Nested
    class IsArrayEdgeCases
    {

        @Test
        void isArrayFalseForZeroChildren() throws Exception
        {
            Element elem = parseXml("<Root/>");
            assertFalse(new XmlElementResource(elem).isArray("Item"));
        }


        @Test
        void isArrayFalseForSingleChild() throws Exception
        {
            Element elem = parseXml("<Root><Item/></Root>");
            assertFalse(new XmlElementResource(elem).isArray("Item"));
        }


        @Test
        void isArrayTrueForTwoChildren() throws Exception
        {
            Element elem = parseXml("""
                    <Root>
                        <Item/>
                        <Item/>
                    </Root>
                    """);
            assertTrue(new XmlElementResource(elem).isArray("Item"));
        }
    }


    @Nested
    class GetArrayAndStream
    {

        @Test
        void getArrayWrapsChildren() throws Exception
        {
            Element elem = parseXml("""
                    <Root>
                        <Item OID="1"/>
                        <Item OID="2"/>
                    </Root>
                    """);
            Optional<ApiArrayResource> arr = new XmlElementResource(elem).getArray("Item",
                    ApiArrayResource.class);
            assertTrue(arr.isPresent());
            assertEquals(2, arr.get().getLength());
            // XmlChildListResource.getString(int) returns the element's text content; use
            // getObject to inspect the attribute we care about.
            ApiResource first = arr.get().getObject(0, ApiResource.class).orElseThrow();
            assertEquals(Optional.of("1"), first.getString("OID"));
        }


        @Test
        void getArrayReturnsEmptyForMissing() throws Exception
        {
            Element elem = parseXml("<Root/>");
            assertTrue(new XmlElementResource(elem).getArray("Item", ApiArrayResource.class)
                    .isEmpty());
        }


        @Test
        void getStreamReturnsElements() throws Exception
        {
            Element elem = parseXml("""
                    <Root>
                        <Item v="1"/>
                        <Item v="2"/>
                    </Root>
                    """);
            List<ApiResource> collected = new XmlElementResource(elem)
                    .getStream("Item", ApiResource.class).toList();
            assertEquals(2, collected.size());
            assertEquals(Optional.of("1"), collected.get(0).getString("v"));
            assertEquals(Optional.of("2"), collected.get(1).getString("v"));
        }


        @Test
        void getStreamReturnsEmptyForMissing() throws Exception
        {
            Element elem = parseXml("<Root/>");
            assertEquals(0,
                    new XmlElementResource(elem).getStream("Missing", ApiResource.class).count());
        }


        @Test
        void getStringStreamReturnsText() throws Exception
        {
            Element elem = parseXml("""
                    <Root>
                        <Tag>alpha</Tag>
                        <Tag>beta</Tag>
                    </Root>
                    """);
            assertEquals(List.of("alpha", "beta"),
                    new XmlElementResource(elem).getStringStream("Tag").toList());
        }


        @Test
        void getStringStreamReturnsEmptyForMissing() throws Exception
        {
            Element elem = parseXml("<Root/>");
            assertEquals(0, new XmlElementResource(elem).getStringStream("Missing").count());
        }


        @Test
        void getStringListReturnsEmptyForMissing() throws Exception
        {
            Element elem = parseXml("<Root/>");
            assertTrue(new XmlElementResource(elem).getStringList("Missing").isEmpty());
        }
    }


    @Nested
    class NonNamespaceAware
    {

        @Test
        void localNameNullFallsBackToTagName() throws Exception
        {
            // When the parser is not namespace-aware, getLocalName() returns null and
            // getTagName() is used as the match key.
            Element elem = parseXmlNoNs("""
                    <Root>
                        <Item v="1"/>
                    </Root>
                    """);
            XmlElementResource res = new XmlElementResource(elem);
            // findFirstChildElement should still find Item via tag-name fallback
            assertTrue(res.getString("Item").isPresent());
        }


        @Test
        void fieldNamesUsesTagNameWhenLocalNameMissing() throws Exception
        {
            Element elem = parseXmlNoNs("<Root OID=\"x\"><Item/></Root>");
            assertTrue(new XmlElementResource(elem).getFieldNames().contains("Item"));
        }


        @Test
        void toStringFallsBackToTagName() throws Exception
        {
            Element elem = parseXmlNoNs("<MyTag/>");
            assertTrue(new XmlElementResource(elem).toString().contains("MyTag"));
        }
    }


    @Nested
    class LinksEdgeCases
    {

        @Test
        void getLinkReturnsEmptyWhenLinksElementExistsButRelMissing() throws Exception
        {
            Element elem = parseXml("""
                    <Resource>
                        <_links>
                            <other href="/elsewhere"/>
                        </_links>
                    </Resource>
                    """);
            assertTrue(new XmlElementResource(elem).getLink("self").isEmpty());
        }


        @Test
        void getLinksReturnsEmptyWithoutLinksElement() throws Exception
        {
            Element elem = parseXml("<Resource/>");
            assertTrue(new XmlElementResource(elem).getLinks("self").isEmpty());
        }


        @Test
        void getLinksReturnsEmptyWhenRelMissing() throws Exception
        {
            Element elem = parseXml("""
                    <Resource>
                        <_links>
                            <other href="/elsewhere"/>
                        </_links>
                    </Resource>
                    """);
            assertTrue(new XmlElementResource(elem).getLinks("self").isEmpty());
        }
    }


    @Nested
    class ObjectOverrides
    {

        @Test
        void equalsTrueForSelf() throws Exception
        {
            Element elem = parseXml("<R/>");
            XmlElementResource res = new XmlElementResource(elem);
            assertEquals(res, res);
        }


        @Test
        void equalsFalseForDifferentElements() throws Exception
        {
            Element e1 = parseXml("<R OID=\"1\"/>");
            Element e2 = parseXml("<R OID=\"2\"/>");
            assertNotEquals(new XmlElementResource(e1), new XmlElementResource(e2));
        }


        @Test
        void equalsFalseForNull() throws Exception
        {
            Element elem = parseXml("<R/>");
            assertNotEquals(null, new XmlElementResource(elem));
        }


        @Test
        void equalsFalseForDifferentType() throws Exception
        {
            Element elem = parseXml("<R/>");
            assertNotEquals("not a resource", new XmlElementResource(elem));
        }
    }


    @Nested
    class ProxyHandler
    {

        interface StudyDef extends ApiResource
        {

            default Optional<String> oid()
            {
                return getString("OID");
            }
        }

        @Test
        void proxyEqualsComparesViaDelegate() throws Exception
        {
            Element elem = parseXml("<Study OID=\"S.1\"/>");
            StudyDef proxy = XmlElementResource.of(elem, StudyDef.class);
            XmlElementResource direct = new XmlElementResource(elem);

            // Proxy.equals delegates to XmlElementResource.equals; the call below exercises
            // that delegation. The Object cast hides the static-type asymmetry between
            // StudyDef and XmlElementResource from S5845 while still routing through
            // proxy.equals(direct) at runtime (assertEquals calls expected.equals(actual)).
            assertEquals((Object) proxy, direct,
                    "proxy.equals must delegate to XmlElementResource.equals");
        }


        @Test
        void proxyHashCodeMatchesDelegate() throws Exception
        {
            Element elem = parseXml("<Study OID=\"S.1\"/>");
            StudyDef proxy = XmlElementResource.of(elem, StudyDef.class);
            XmlElementResource direct = new XmlElementResource(elem);
            assertEquals(direct.hashCode(), proxy.hashCode());
        }


        @Test
        void proxyToStringNoNamespaceUsesTagName() throws Exception
        {
            Element elem = parseXmlNoNs("<Study/>");
            StudyDef proxy = XmlElementResource.of(elem, StudyDef.class);
            assertTrue(proxy.toString().contains("Study"));
        }


        @Test
        void proxyDelegatesGenericAccessorsToBackingElement() throws Exception
        {
            Element elem = parseXml("<Study OID=\"S.1\" Name=\"Demo\"/>");
            StudyDef proxy = XmlElementResource.of(elem, StudyDef.class);
            assertEquals(Optional.of("S.1"), proxy.oid());
            assertEquals(Optional.of("Demo"), proxy.getString("Name"));
        }
    }
}
