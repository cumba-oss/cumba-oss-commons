package net.cumba.web.api.xml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import net.cumba.web.api.ApiResource;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Tests for the mutation-hardening fixes in the XML resource wrappers.
 *
 * <ul>
 * <li>F-webapi-02 — {@code getStringList(int)} must return the text of ALL child elements, not only
 * children named after the element itself.</li>
 * <li>F-webapi-03 — {@code isInt} must be true only for values that fit a Java {@code int}, and
 * {@code getInt} must not silently narrow an out-of-range value.</li>
 * <li>F-webapi-07 — {@code isObject} must agree with {@code XmlElementResource.isComplexElement}:
 * child <em>elements</em> or non-xmlns attributes, never plain text children.</li>
 * </ul>
 */
class XmlResourceHardeningTest
{

    private static Element parseRoot(String aXml) throws Exception
    {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.parse(new ByteArrayInputStream(aXml.getBytes(StandardCharsets.UTF_8)));
        return doc.getDocumentElement();
    }


    /** Collects all child elements of the root, regardless of name. */
    private static List<Element> childElements(String aXml) throws Exception
    {
        Element root = parseRoot(aXml);
        List<Element> result = new ArrayList<>();
        NodeList children = root.getChildNodes();
        for (int i = 0; i < children.getLength(); i++)
        {
            if (children.item(i) instanceof Element elem)
            {
                result.add(elem);
            }
        }
        return result;
    }

    @Nested
    class StringListCollectsAllChildElements
    {

        @Test
        void getStringListReturnsTextOfAllChildElements() throws Exception
        {
            // The realistic shape: a wrapper element whose children carry the values.
            // The pre-fix code looked for children named "Terms" inside <Terms> and
            // returned [] for every document like this one.
            XmlChildListResource resource = new XmlChildListResource(childElements("""
                    <Root>
                        <Terms><Term>A</Term><Term>B</Term></Terms>
                    </Root>
                    """));

            assertEquals(List.of("A", "B"), resource.getStringList(0));
        }


        @Test
        void getStringListIsNameIndependent() throws Exception
        {
            XmlChildListResource resource = new XmlChildListResource(childElements("""
                    <Root>
                        <Wrap><A>x</A><B>y</B></Wrap>
                    </Root>
                    """));

            assertEquals(List.of("x", "y"), resource.getStringList(0));
        }


        @Test
        void getStringListReturnsEmptyForLeafElement() throws Exception
        {
            XmlChildListResource resource = new XmlChildListResource(childElements("""
                    <Root><Name>DM</Name></Root>
                    """));

            assertTrue(resource.getStringList(0).isEmpty());
        }


        @Test
        void getStringStreamMatchesGetStringList() throws Exception
        {
            XmlChildListResource resource = new XmlChildListResource(childElements("""
                    <Root>
                        <Terms><Term>A</Term><Term>B</Term></Terms>
                    </Root>
                    """));

            assertEquals(List.of("A", "B"), resource.getStringStream(0).toList());
        }
    }


    @Nested
    class IntPredicatesRangeCheck
    {

        private XmlChildListResource resource() throws Exception
        {
            return new XmlChildListResource(childElements("""
                    <Root>
                        <Item>2147483648</Item>
                        <Item>2147483647</Item>
                        <Item>-2147483648</Item>
                        <Item>-2147483649</Item>
                    </Root>
                    """));
        }


        @Test
        void isIntIsFalseForAValueBeyondIntMax() throws Exception
        {
            assertFalse(resource().isInt(0), "2147483648 does not fit an int");
        }


        @Test
        void getIntIsEmptyForAValueBeyondIntMax() throws Exception
        {
            assertTrue(resource().getInt(0).isEmpty(),
                    "getInt must not narrow 2147483648 to -2147483648");
        }


        @Test
        void intBoundariesAreValidInts() throws Exception
        {
            XmlChildListResource r = resource();
            assertTrue(r.isInt(1));
            assertEquals(Integer.MAX_VALUE, r.getInt(1).orElseThrow());
            assertTrue(r.isInt(2));
            assertEquals(Integer.MIN_VALUE, r.getInt(2).orElseThrow());
        }


        @Test
        void isIntIsFalseForAValueBelowIntMin() throws Exception
        {
            XmlChildListResource r = resource();
            assertFalse(r.isInt(3));
            assertTrue(r.getInt(3).isEmpty());
        }


        @Test
        void longAccessorsStillAcceptTheOutOfIntRangeValue() throws Exception
        {
            XmlChildListResource r = resource();
            assertTrue(r.isLong(0));
            assertEquals(2147483648L, r.getLong(0).orElseThrow());
        }


        @Test
        void elementResourceGetIntIsEmptyForAValueBeyondIntMax() throws Exception
        {
            ApiResource res = XmlElementResource.of(parseRoot("""
                    <Root><Big>2147483648</Big><Ok>7</Ok></Root>
                    """));

            assertTrue(res.getInt("Big").isEmpty(),
                    "XmlElementResource.getInt must not narrow 2147483648");
            assertEquals(2147483648L, res.getLong("Big").orElseThrow());
            assertEquals(7, res.getInt("Ok").orElseThrow());
        }
    }


    @Nested
    class IsObjectAgreesWithIsComplexElement
    {

        @Test
        void aTextOnlyLeafIsNotAnObject() throws Exception
        {
            XmlChildListResource resource = new XmlChildListResource(childElements("""
                    <Root><Name>DM</Name></Root>
                    """));

            assertFalse(resource.isObject(0),
                    "a text-only leaf has no child elements and no attributes");
        }


        @Test
        void anXmlnsOnlyAttributeDoesNotMakeAnObject() throws Exception
        {
            XmlChildListResource resource = new XmlChildListResource(childElements("""
                    <Root><odm:Name xmlns:odm="urn:example">DM</odm:Name></Root>
                    """));

            assertFalse(resource.isObject(0),
                    "xmlns declarations are excluded, matching isComplexElement");
        }


        @Test
        void aRealAttributeMakesAnObject() throws Exception
        {
            XmlChildListResource resource = new XmlChildListResource(childElements("""
                    <Root><Item OID="1">text</Item></Root>
                    """));

            assertTrue(resource.isObject(0));
        }


        @Test
        void aChildElementMakesAnObject() throws Exception
        {
            XmlChildListResource resource = new XmlChildListResource(childElements("""
                    <Root><Wrap><Inner/></Wrap></Root>
                    """));

            assertTrue(resource.isObject(0));
        }
    }
}
