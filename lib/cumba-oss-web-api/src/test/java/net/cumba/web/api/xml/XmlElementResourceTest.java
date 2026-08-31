package net.cumba.web.api.xml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import net.cumba.web.api.ApiResource;
import net.cumba.web.api.Link;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

class XmlElementResourceTest
{

    private static Element parseXml(String xml) throws Exception
    {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        return doc.getDocumentElement();
    }

    @Nested
    class AttributeAccess
    {

        @Test
        void getStringReturnsAttributeValue() throws Exception
        {
            Element elem = parseXml("<Item OID=\"IT.DM.AGE\" Name=\"AGE\"/>");
            XmlElementResource res = new XmlElementResource(elem);

            assertEquals("IT.DM.AGE", res.getString("OID").orElse(null));
            assertEquals("AGE", res.getString("Name").orElse(null));
        }


        @Test
        void getStringReturnsEmptyForMissingAttribute() throws Exception
        {
            Element elem = parseXml("<Item OID=\"IT.1\"/>");
            XmlElementResource res = new XmlElementResource(elem);

            assertTrue(res.getString("Label").isEmpty());
        }


        @Test
        void getIntParsesAttributeAsInteger() throws Exception
        {
            Element elem = parseXml("<ItemRef OrderNumber=\"3\" />");
            XmlElementResource res = new XmlElementResource(elem);

            assertEquals(3, res.getInt("OrderNumber").orElse(-1));
        }


        @Test
        void getDoubleParsesAttribute() throws Exception
        {
            Element elem = parseXml("<Visit Number=\"1.5\" />");
            XmlElementResource res = new XmlElementResource(elem);

            assertEquals(1.5, res.getDouble("Number").orElse(-1), 0.001);
        }


        @Test
        void getBooleanParsesAttribute() throws Exception
        {
            Element elem = parseXml("<Item IsReferenceData=\"Yes\" HasNoData=\"false\" />");
            XmlElementResource res = new XmlElementResource(elem);

            assertEquals(true, res.getBoolean("IsReferenceData").orElse(null));
            assertEquals(false, res.getBoolean("HasNoData").orElse(null));
        }


        @Test
        void containsFieldNameForAttributes() throws Exception
        {
            Element elem = parseXml("<Item OID=\"IT.1\" Name=\"AGE\" />");
            XmlElementResource res = new XmlElementResource(elem);

            assertTrue(res.containsFieldName("OID"));
            assertTrue(res.containsFieldName("Name"));
            assertFalse(res.containsFieldName("Missing"));
        }
    }


    @Nested
    class ChildElementAccess
    {

        @Test
        void getStringReturnsChildElementTextContent() throws Exception
        {
            Element elem = parseXml("""
                    <Study>
                        <StudyName>My Study</StudyName>
                        <ProtocolName>PROTO-001</ProtocolName>
                    </Study>
                    """);
            XmlElementResource res = new XmlElementResource(elem);

            assertEquals("My Study", res.getString("StudyName").orElse(null));
            assertEquals("PROTO-001", res.getString("ProtocolName").orElse(null));
        }


        @Test
        void attributeTakesPrecedenceOverChildElement() throws Exception
        {
            Element elem = parseXml("""
                    <Item Name="FromAttr">
                        <Name>FromChild</Name>
                    </Item>
                    """);
            XmlElementResource res = new XmlElementResource(elem);

            assertEquals("FromAttr", res.getString("Name").orElse(null));
        }


        @Test
        void getIntFromChildElement() throws Exception
        {
            Element elem = parseXml("""
                    <Record>
                        <Count>42</Count>
                    </Record>
                    """);
            XmlElementResource res = new XmlElementResource(elem);

            assertEquals(42, res.getInt("Count").orElse(-1));
        }


        @Test
        void getIntReturnsEmptyForNonNumeric() throws Exception
        {
            Element elem = parseXml("""
                    <Record>
                        <Label>not a number</Label>
                    </Record>
                    """);
            XmlElementResource res = new XmlElementResource(elem);

            assertTrue(res.getInt("Label").isEmpty());
        }
    }


    @Nested
    class TypeChecks
    {

        @Test
        void isStringForExistingField() throws Exception
        {
            Element elem = parseXml("<Item OID=\"IT.1\"><Label>Age</Label></Item>");
            XmlElementResource res = new XmlElementResource(elem);

            assertTrue(res.isString("OID"));
            assertTrue(res.isString("Label"));
            assertFalse(res.isString("Missing"));
        }


        @Test
        void isNumberForNumericAttribute() throws Exception
        {
            Element elem = parseXml("<Item OrderNumber=\"5\" Name=\"AGE\" />");
            XmlElementResource res = new XmlElementResource(elem);

            assertTrue(res.isNumber("OrderNumber"));
            assertFalse(res.isNumber("Name"));
        }


        @Test
        void isObjectForComplexChildElement() throws Exception
        {
            Element elem = parseXml("""
                    <ItemGroupDef>
                        <Description><TranslatedText>Demographics</TranslatedText></Description>
                        <Label>Simple text</Label>
                    </ItemGroupDef>
                    """);
            XmlElementResource res = new XmlElementResource(elem);

            assertTrue(res.isObject("Description"));
            assertFalse(res.isObject("Label"));
        }


        @Test
        void isArrayForRepeatedElements() throws Exception
        {
            Element elem = parseXml("""
                    <ItemGroupDef>
                        <ItemRef OID="IR.1"/>
                        <ItemRef OID="IR.2"/>
                        <ItemRef OID="IR.3"/>
                    </ItemGroupDef>
                    """);
            XmlElementResource res = new XmlElementResource(elem);

            assertTrue(res.isArray("ItemRef"));
        }


        @Test
        void isArrayFalseForSingleElement() throws Exception
        {
            Element elem = parseXml("""
                    <ItemGroupDef>
                        <ItemRef OID="IR.1"/>
                    </ItemGroupDef>
                    """);
            XmlElementResource res = new XmlElementResource(elem);

            assertFalse(res.isArray("ItemRef"));
        }


        @Test
        void isNullForMissingField() throws Exception
        {
            Element elem = parseXml("<Item OID=\"IT.1\"/>");
            XmlElementResource res = new XmlElementResource(elem);

            assertTrue(res.isNull("Missing"));
            assertFalse(res.isNull("OID"));
        }
    }


    @Nested
    class StructuralAccessors
    {

        @Test
        void getObjectWrapsChildElement() throws Exception
        {
            Element elem = parseXml("""
                    <ItemGroupDef OID="IG.DM">
                        <Description>
                            <TranslatedText lang="en">Demographics</TranslatedText>
                        </Description>
                    </ItemGroupDef>
                    """);
            XmlElementResource res = new XmlElementResource(elem);

            Optional<ApiResource> desc = res.getObject("Description");
            assertTrue(desc.isPresent());
            assertEquals("Demographics", desc.get().getString("TranslatedText").orElse(null));
        }


        @Test
        void getObjectReturnsEmptyForMissing() throws Exception
        {
            Element elem = parseXml("<ItemGroupDef OID=\"IG.DM\"/>");
            XmlElementResource res = new XmlElementResource(elem);

            assertTrue(res.getObject("Description").isEmpty());
        }


        @Test
        void getListReturnsAllMatchingChildren() throws Exception
        {
            Element elem = parseXml("""
                    <ItemGroupDef>
                        <ItemRef OID="IR.1" OrderNumber="1"/>
                        <ItemRef OID="IR.2" OrderNumber="2"/>
                        <ItemRef OID="IR.3" OrderNumber="3"/>
                    </ItemGroupDef>
                    """);
            XmlElementResource res = new XmlElementResource(elem);

            List<ApiResource> items = res.getList("ItemRef", ApiResource.class);
            assertEquals(3, items.size());
            assertEquals("IR.1", items.get(0).getString("OID").orElse(null));
            assertEquals("IR.2", items.get(1).getString("OID").orElse(null));
            assertEquals("IR.3", items.get(2).getString("OID").orElse(null));
        }


        @Test
        void getListReturnsEmptyForMissing() throws Exception
        {
            Element elem = parseXml("<ItemGroupDef OID=\"IG.DM\"/>");
            XmlElementResource res = new XmlElementResource(elem);

            assertTrue(res.getList("ItemRef", ApiResource.class).isEmpty());
        }


        @Test
        void getStringListReturnsTextContent() throws Exception
        {
            Element elem = parseXml("""
                    <Record>
                        <Tag>alpha</Tag>
                        <Tag>beta</Tag>
                        <Tag>gamma</Tag>
                    </Record>
                    """);
            XmlElementResource res = new XmlElementResource(elem);

            List<String> tags = res.getStringList("Tag");
            assertEquals(List.of("alpha", "beta", "gamma"), tags);
        }
    }


    @Nested
    class FieldNames
    {

        @Test
        void getFieldNamesReturnsAttributesAndChildElements() throws Exception
        {
            Element elem = parseXml("""
                    <ItemGroupDef OID="IG.DM" Name="DM">
                        <Description>Demographics</Description>
                        <ItemRef OID="IR.1"/>
                    </ItemGroupDef>
                    """);
            XmlElementResource res = new XmlElementResource(elem);

            Set<String> names = res.getFieldNames();
            assertTrue(names.contains("OID"));
            assertTrue(names.contains("Name"));
            assertTrue(names.contains("Description"));
            assertTrue(names.contains("ItemRef"));
        }


        @Test
        void getFieldNamesExcludesXmlnsAttributes() throws Exception
        {
            Element elem = parseXml(
                    "<Root xmlns=\"http://example.com\" xmlns:def=\"http://example.com/def\" OID=\"1\"/>");
            XmlElementResource res = new XmlElementResource(elem);

            Set<String> names = res.getFieldNames();
            assertTrue(names.contains("OID"));
            assertFalse(names.contains("xmlns"));
            assertFalse(names.contains("xmlns:def"));
        }


        @Test
        void getFieldCountMatchesFieldNames() throws Exception
        {
            Element elem = parseXml("<Item OID=\"1\" Name=\"AGE\"><Label>Age</Label></Item>");
            XmlElementResource res = new XmlElementResource(elem);

            assertEquals(res.getFieldNames().size(), res.getFieldCount());
        }
    }


    @Nested
    class ProxyFactory
    {

        interface StudyDef extends ApiResource
        {

            default Optional<String> oid()
            {
                return getString("OID");
            }


            default Optional<String> studyName()
            {
                return getString("StudyName");
            }
        }

        @Test
        void typedProxyDelegatesToDefaultMethods() throws Exception
        {
            Element elem = parseXml("""
                    <Study OID="S.001">
                        <StudyName>My Trial</StudyName>
                    </Study>
                    """);

            StudyDef study = XmlElementResource.of(elem, StudyDef.class);

            assertEquals("S.001", study.oid().orElse(null));
            assertEquals("My Trial", study.studyName().orElse(null));
        }


        @Test
        void typedProxyGenericAccessorsStillWork() throws Exception
        {
            Element elem = parseXml("<Study OID=\"S.001\" Label=\"Test\"/>");

            StudyDef study = XmlElementResource.of(elem, StudyDef.class);

            // Generic accessor via ApiResource interface
            assertEquals("Test", study.getString("Label").orElse(null));
        }


        @Test
        void typedProxyToStringIncludesInterfaceName() throws Exception
        {
            Element elem = parseXml("<Study OID=\"S.001\"/>");
            StudyDef study = XmlElementResource.of(elem, StudyDef.class);

            assertTrue(study.toString().contains("StudyDef"));
        }
    }


    @Nested
    class LinkNavigation
    {

        @Test
        void getLinkFromLinksElement() throws Exception
        {
            Element elem = parseXml("""
                    <Resource>
                        <_links>
                            <self href="/api/resource/1" title="Self link"/>
                        </_links>
                    </Resource>
                    """);
            XmlElementResource res = new XmlElementResource(elem);

            Optional<Link> self = res.getLink("self");
            assertTrue(self.isPresent());
            assertEquals("/api/resource/1", self.get().href().orElse(null));
        }


        @Test
        void getLinkReturnsEmptyWithoutLinksElement() throws Exception
        {
            Element elem = parseXml("<Resource Name=\"test\"/>");
            XmlElementResource res = new XmlElementResource(elem);

            assertTrue(res.getLink("self").isEmpty());
        }


        @Test
        void getLinksReturnsMultiple() throws Exception
        {
            Element elem = parseXml("""
                    <Resource>
                        <_links>
                            <item href="/items/1"/>
                            <item href="/items/2"/>
                        </_links>
                    </Resource>
                    """);
            XmlElementResource res = new XmlElementResource(elem);

            List<Link> items = res.getLinks("item");
            assertEquals(2, items.size());
            assertEquals("/items/1", items.get(0).href().orElse(null));
            assertEquals("/items/2", items.get(1).href().orElse(null));
        }
    }


    @Nested
    class ObjectOverrides
    {

        @Test
        void toStringContainsTagName() throws Exception
        {
            Element elem = parseXml("<ItemGroupDef OID=\"IG.DM\"/>");
            XmlElementResource res = new XmlElementResource(elem);

            assertTrue(res.toString().contains("ItemGroupDef"));
        }


        @Test
        void equalsAndHashCodeBasedOnElement() throws Exception
        {
            Element elem = parseXml("<Item OID=\"1\"/>");
            XmlElementResource res1 = new XmlElementResource(elem);
            XmlElementResource res2 = new XmlElementResource(elem);

            assertEquals(res1, res2);
            assertEquals(res1.hashCode(), res2.hashCode());
        }
    }


    @Nested
    class NumberAccessors
    {

        @Test
        void getLongParsesAttribute() throws Exception
        {
            Element elem = parseXml("<Record Total=\"9999999999\"/>");
            XmlElementResource res = new XmlElementResource(elem);

            assertEquals(9999999999L, res.getLong("Total").orElse(-1));
        }


        @Test
        void getNumberPrefersLongOverDouble() throws Exception
        {
            Element elem = parseXml("<Record Count=\"42\" Ratio=\"3.14\"/>");
            XmlElementResource res = new XmlElementResource(elem);

            assertEquals(42L, res.getNumber("Count").orElse(-1));
            assertEquals(3.14, res.getNumber("Ratio").orElse(-1).doubleValue(), 0.001);
        }
    }
}
