package net.cumba.web.api.xml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import net.cumba.web.api.ApiResource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

class XmlChildListResourceTest
{

    private static List<Element> parseChildElements(String xml, String childName) throws Exception
    {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        Element root = doc.getDocumentElement();

        List<Element> result = new ArrayList<>();
        NodeList children = root.getChildNodes();
        for (int i = 0; i < children.getLength(); i++)
        {
            if (children.item(i) instanceof Element elem && elem.getTagName().equals(childName))
            {
                result.add(elem);
            }
        }
        return result;
    }

    @Nested
    class ScalarAccess
    {

        private XmlChildListResource resource;

        @BeforeEach
        void setUp() throws Exception
        {
            List<Element> items = parseChildElements("""
                    <Root>
                        <Item>alpha</Item>
                        <Item>42</Item>
                        <Item>3.14</Item>
                        <Item>true</Item>
                    </Root>
                    """, "Item");
            resource = new XmlChildListResource(items);
        }


        @Test
        void getLength()
        {
            assertEquals(4, resource.getLength());
        }


        @Test
        void getStringByIndex()
        {
            assertEquals("alpha", resource.getString(0).orElse(null));
            assertEquals("42", resource.getString(1).orElse(null));
        }


        @Test
        void getStringReturnsEmptyForOutOfBounds()
        {
            assertTrue(resource.getString(99).isEmpty());
        }


        @Test
        void getIntByIndex()
        {
            assertEquals(42, resource.getInt(1).orElse(-1));
        }


        @Test
        void getIntReturnsEmptyForNonNumeric()
        {
            assertTrue(resource.getInt(0).isEmpty());
        }


        @Test
        void getDoubleByIndex()
        {
            assertEquals(3.14, resource.getDouble(2).orElse(-1), 0.001);
        }


        @Test
        void getBooleanByIndex()
        {
            assertEquals(true, resource.getBoolean(3).orElse(null));
        }


        @Test
        void isStringForAllElements()
        {
            assertTrue(resource.isString(0));
            assertTrue(resource.isString(1));
            assertFalse(resource.isString(99));
        }


        @Test
        void isNumberForNumericElements()
        {
            assertFalse(resource.isNumber(0));
            assertTrue(resource.isNumber(1));
            assertTrue(resource.isNumber(2));
        }
    }


    @Nested
    class ObjectAccess
    {

        interface ItemDef extends ApiResource
        {

            default Optional<String> oid()
            {
                return getString("OID");
            }


            default Optional<String> name()
            {
                return getString("Name");
            }
        }

        @Test
        void getObjectWrapsElementAsProxy() throws Exception
        {
            List<Element> items = parseChildElements("""
                    <Root>
                        <ItemRef OID="IR.1" Name="USUBJID"/>
                        <ItemRef OID="IR.2" Name="AGE"/>
                    </Root>
                    """, "ItemRef");
            XmlChildListResource resource = new XmlChildListResource(items);

            assertTrue(resource.isObject(0));

            ItemDef item0 = resource.getObject(0, ItemDef.class).orElse(null);
            assertNotNull(item0);
            assertEquals("IR.1", item0.oid().orElse(null));
            assertEquals("USUBJID", item0.name().orElse(null));

            ItemDef item1 = resource.getObject(1, ItemDef.class).orElse(null);
            assertEquals("IR.2", item1.oid().orElse(null));
        }


        @Test
        void getObjectReturnsEmptyForOutOfBounds() throws Exception
        {
            List<Element> items = parseChildElements("""
                    <Root><Item OID="1"/></Root>
                    """, "Item");
            XmlChildListResource resource = new XmlChildListResource(items);

            assertTrue(resource.getObject(5, ApiResource.class).isEmpty());
        }
    }


    @Nested
    class Metadata
    {

        @Test
        void toStringShowsSize() throws Exception
        {
            List<Element> items = parseChildElements("""
                    <Root><A/><A/><A/></Root>
                    """, "A");
            XmlChildListResource resource = new XmlChildListResource(items);

            assertTrue(resource.toString().contains("3"));
        }


        @Test
        void equalsBasedOnList() throws Exception
        {
            List<Element> items = parseChildElements("""
                    <Root><A/><A/></Root>
                    """, "A");
            XmlChildListResource r1 = new XmlChildListResource(items);
            XmlChildListResource r2 = new XmlChildListResource(items);

            assertEquals(r1, r2);
            assertEquals(r1.hashCode(), r2.hashCode());
        }
    }
}
