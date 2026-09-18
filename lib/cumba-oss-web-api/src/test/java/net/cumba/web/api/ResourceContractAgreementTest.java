package net.cumba.web.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.DocumentBuilderFactory;
import net.cumba.web.api.dev.ListResource;
import net.cumba.web.api.dev.MapResource;
import net.cumba.web.api.json.JsonArrayResource;
import net.cumba.web.api.json.JsonNodeResource;
import net.cumba.web.api.xml.XmlChildListResource;
import net.cumba.web.api.xml.XmlElementResource;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Pins the two 2026-09-11 rulings that say every implementation of {@link ApiResource} and
 * {@link ApiArrayResource} must answer the same question the same way.
 *
 * <p>
 * <b>Q12 — strict on both sides.</b> A numeric accessor never hands back a number that is not the
 * number that was there, and never accepts a value its own predicate rejects. Entry 20 of the bug
 * ledger swept {@code getInt} / {@code getLong} / {@code isInt} / {@code isLong}; measured here
 * before this file was written, two accessors were still out of step with the other five
 * implementations: {@code MapResource} / {@code ListResource} read a <i>floating-point</i>
 * {@code 3.0} as the integer {@code 3} where JSON and XML both answer empty, and
 * {@code JsonArrayResource.isDouble} answered {@code false} for an integer whose own
 * {@code getDouble} handed back {@code 42.0}.
 * </p>
 *
 * <p>
 * <b>Q13 — a value that is not a string reads as empty.</b> {@code getStringList} used to render a
 * JSON {@code null} as the four-character string {@code "null"} and a number as its digits, so a
 * controlled-terminology vocabulary silently gained terms that were never in it. The element keeps
 * its position — dropping it was rejected — and answers the empty string.
 * </p>
 *
 * <p>
 * ⚠ The agreement is <i>per data model</i>. XML is untyped: every leaf is text, so {@code 7} in an
 * XML document <b>is</b> the string {@code "7"} and reads back as one. The only XML shape that is
 * not a string is an element that has element children — its {@code getTextContent()} is the
 * concatenation of its descendants' text, i.e. a value no single element ever carried.
 * </p>
 */
class ResourceContractAgreementTest
{

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static JsonNode json(String aJson) throws Exception
    {
        return MAPPER.readTree(aJson);
    }


    private static Element xml(String aXml) throws Exception
    {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder()
                .parse(new ByteArrayInputStream(aXml.getBytes(StandardCharsets.UTF_8)))
                .getDocumentElement();
    }


    /** The child elements of the parsed root, as {@link XmlChildListResource} consumes them. */
    private static XmlChildListResource xmlList(String aXml) throws Exception
    {
        Element root = xml(aXml);
        List<Element> elements = new ArrayList<>();
        NodeList children = root.getChildNodes();
        for (int i = 0; i < children.getLength(); i++)
        {
            Node child = children.item(i);
            if (child instanceof Element element)
            {
                elements.add(element);
            }
        }
        return new XmlChildListResource(elements);
    }

    // ------------------------------------------------------------------
    // Q12 — the numeric accessors
    // ------------------------------------------------------------------

    /**
     * The five numeric shapes every implementation is asked about. The field / element order is
     * fixed so the three array implementations can be indexed identically.
     */
    private static final String JSON_NUMBERS = """
            {"inRange":42,"tooBigForInt":9999999999,"tooBigForLong":9223372036854775808,\
            "fractional":3.7,"integralFloat":3.0}""";

    private static final String JSON_NUMBER_ARRAY = "[42, 9999999999, 9223372036854775808, 3.7, 3.0]";

    private static final String XML_NUMBERS = """
            <Rec inRange="42" tooBigForInt="9999999999" tooBigForLong="9223372036854775808"\
             fractional="3.7" integralFloat="3.0"/>""";

    private static final String XML_NUMBER_LIST = """
            <r><v>42</v><v>9999999999</v><v>9223372036854775808</v><v>3.7</v><v>3.0</v></r>""";

    /**
     * The map/list counterparts. {@code 3.0} is a {@link Double} and {@code 3.7} a
     * {@link BigDecimal} on purpose: both are floating-point <i>types</i>, which is what makes them
     * not integers, and the distinction has to survive the {@code BigDecimal} route the dev copies
     * use to answer exactly.
     */
    private static List<Object> devNumbers()
    {
        return List.of(42, 9_999_999_999L, new BigInteger("9223372036854775808"),
                new BigDecimal("3.7"), 3.0d);
    }


    private static Map<String, Object> devNumberMap()
    {
        Map<String, Object> map = new LinkedHashMap<>();
        List<Object> values = devNumbers();
        map.put("inRange", values.get(0));
        map.put("tooBigForInt", values.get(1));
        map.put("tooBigForLong", values.get(2));
        map.put("fractional", values.get(3));
        map.put("integralFloat", values.get(4));
        return map;
    }


    /** Asserts the agreed answer of every {@link ApiResource} numeric accessor. */
    private static void assertResourceNumbers(ApiResource aResource, String aWho)
    {
        assertEquals(42, aResource.getInt("inRange").orElseThrow(), aWho);
        assertEquals(42L, aResource.getLong("inRange").orElseThrow(), aWho);
        assertEquals(42.0d, aResource.getDouble("inRange").orElseThrow(), aWho);

        assertTrue(aResource.getInt("tooBigForInt").isEmpty(), aWho + ": no narrowing");
        assertEquals(9_999_999_999L, aResource.getLong("tooBigForInt").orElseThrow(), aWho);

        assertTrue(aResource.getInt("tooBigForLong").isEmpty(), aWho + ": no narrowing");
        assertTrue(aResource.getLong("tooBigForLong").isEmpty(), aWho + ": no wrap-around");

        assertTrue(aResource.getInt("fractional").isEmpty(), aWho + ": no truncation");
        assertTrue(aResource.getLong("fractional").isEmpty(), aWho + ": no truncation");
        assertEquals(3.7d, aResource.getDouble("fractional").orElseThrow(), aWho);

        assertTrue(aResource.getInt("integralFloat").isEmpty(),
                aWho + ": a floating-point value is not an integer, whatever its fraction");
        assertTrue(aResource.getLong("integralFloat").isEmpty(), aWho);
        assertEquals(3.0d, aResource.getDouble("integralFloat").orElseThrow(), aWho);

        assertTrue(aResource.isNumber("inRange"), aWho);
        assertTrue(aResource.isNumber("fractional"), aWho);
        assertTrue(aResource.getNumber("tooBigForLong").isPresent(), aWho);
    }


    /** Asserts the agreed answer of every {@link ApiArrayResource} numeric accessor. */
    private static void assertArrayNumbers(ApiArrayResource aResource, String aWho)
    {
        assertEquals(42, aResource.getInt(0).orElseThrow(), aWho);
        assertEquals(42L, aResource.getLong(0).orElseThrow(), aWho);
        assertEquals(42.0d, aResource.getDouble(0).orElseThrow(), aWho);

        assertTrue(aResource.getInt(1).isEmpty(), aWho + ": no narrowing");
        assertEquals(9_999_999_999L, aResource.getLong(1).orElseThrow(), aWho);

        assertTrue(aResource.getInt(2).isEmpty(), aWho + ": no narrowing");
        assertTrue(aResource.getLong(2).isEmpty(), aWho + ": no wrap-around");

        assertTrue(aResource.getInt(3).isEmpty(), aWho + ": no truncation");
        assertTrue(aResource.getLong(3).isEmpty(), aWho + ": no truncation");
        assertEquals(3.7d, aResource.getDouble(3).orElseThrow(), aWho);

        assertTrue(aResource.getInt(4).isEmpty(),
                aWho + ": a floating-point value is not an integer, whatever its fraction");
        assertTrue(aResource.getLong(4).isEmpty(), aWho);
        assertEquals(3.0d, aResource.getDouble(4).orElseThrow(), aWho);

        // Every predicate answers exactly what its own accessor does.
        for (int i = 0; i < 5; i++)
        {
            assertEquals(aResource.getInt(i).isPresent(), aResource.isInt(i),
                    aWho + ": isInt disagrees with getInt at " + i);
            assertEquals(aResource.getLong(i).isPresent(), aResource.isLong(i),
                    aWho + ": isLong disagrees with getLong at " + i);
            assertEquals(aResource.getDouble(i).isPresent(), aResource.isDouble(i),
                    aWho + ": isDouble disagrees with getDouble at " + i);
            assertEquals(aResource.getNumber(i).isPresent(), aResource.isNumber(i),
                    aWho + ": isNumber disagrees with getNumber at " + i);
        }
    }

    @Nested
    class NumericAccessorsAgree
    {

        @Test
        void jsonObject() throws Exception
        {
            assertResourceNumbers(new JsonNodeResource(json(JSON_NUMBERS)), "JsonNodeResource");
        }


        @Test
        void mapObject()
        {
            assertResourceNumbers(new MapResource(devNumberMap()), "MapResource");
        }


        @Test
        void xmlObject() throws Exception
        {
            assertResourceNumbers(new XmlElementResource(xml(XML_NUMBERS)), "XmlElementResource");
        }


        @Test
        void jsonArray() throws Exception
        {
            assertArrayNumbers(new JsonArrayResource(json(JSON_NUMBER_ARRAY)), "JsonArrayResource");
        }


        @Test
        void listArray()
        {
            assertArrayNumbers(new ListResource(devNumbers()), "ListResource");
        }


        @Test
        void xmlArray() throws Exception
        {
            assertArrayNumbers(xmlList(XML_NUMBER_LIST), "XmlChildListResource");
        }
    }

    // ------------------------------------------------------------------
    // Q13 — a value that is not a string reads as empty
    // ------------------------------------------------------------------

    /** {@code "A"}, a null, an object, a number, a boolean, an array — in that order. */
    private static final String JSON_MIXED_TERMS = """
            {"terms":["A",null,{"x":1},7,true,[1]]}""";

    private static final List<String> EXPECTED_TERMS = List.of("A", "", "", "", "", "");

    private static List<Object> devMixedTerms()
    {
        return Arrays.asList("A", null, Map.of("x", 1), 7, true, List.of(1));
    }

    @Nested
    class EveryIntegralTypeIsReadExactly
    {

        /** The types Jackson calls integral, which is what the dev copies accept. */
        private static final List<Object> INTEGRAL = List.of((byte) 1, (short) 2, 3, 4L,
                BigInteger.valueOf(5));

        /** The floating-point types, whose zero fraction does not make them integers. */
        private static final List<Object> FLOATING = List.of(1.0f, 2.0d, new BigDecimal("3.0"));

        @Test
        void listResourceReadsEveryIntegralTypeAndNoFloatingOne()
        {
            ApiArrayResource integral = new ListResource(INTEGRAL);
            for (int i = 0; i < INTEGRAL.size(); i++)
            {
                assertEquals(i + 1, integral.getInt(i).orElseThrow(),
                        INTEGRAL.get(i).getClass().getSimpleName());
                assertEquals(i + 1L, integral.getLong(i).orElseThrow());
            }

            ApiArrayResource floating = new ListResource(FLOATING);
            for (int i = 0; i < FLOATING.size(); i++)
            {
                assertTrue(floating.getInt(i).isEmpty(),
                        FLOATING.get(i).getClass().getSimpleName());
                assertTrue(floating.getLong(i).isEmpty());
            }
        }


        @Test
        void mapResourceReadsEveryIntegralTypeAndNoFloatingOne()
        {
            Map<String, Object> map = new LinkedHashMap<>();
            for (int i = 0; i < INTEGRAL.size(); i++)
            {
                map.put("i" + i, INTEGRAL.get(i));
                map.put("f" + i, i < FLOATING.size() ? FLOATING.get(i) : 0.5d);
            }
            ApiResource r = new MapResource(map);
            for (int i = 0; i < INTEGRAL.size(); i++)
            {
                assertEquals(i + 1, r.getInt("i" + i).orElseThrow(),
                        INTEGRAL.get(i).getClass().getSimpleName());
                assertEquals(i + 1L, r.getLong("i" + i).orElseThrow());
                assertTrue(r.getInt("f" + i).isEmpty());
                assertTrue(r.getLong("f" + i).isEmpty());
            }
        }
    }


    @Nested
    class StringListsDropWhatIsNotAString
    {

        @Test
        void jsonObject() throws Exception
        {
            ApiResource r = new JsonNodeResource(json(JSON_MIXED_TERMS));
            assertEquals(EXPECTED_TERMS, r.getStringList("terms"));
            assertEquals(EXPECTED_TERMS, r.getStringStream("terms").toList());
        }


        @Test
        void jsonArray() throws Exception
        {
            ApiArrayResource r = new JsonArrayResource(json("[[\"A\",null,{\"x\":1},7,true,[1]]]"));
            assertEquals(EXPECTED_TERMS, r.getStringList(0));
            assertEquals(EXPECTED_TERMS, r.getStringStream(0).toList());
        }


        @Test
        void mapObject()
        {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("terms", devMixedTerms());
            ApiResource r = new MapResource(map);
            assertEquals(EXPECTED_TERMS, r.getStringList("terms"));
            assertEquals(EXPECTED_TERMS, r.getStringStream("terms").toList());
        }


        @Test
        void listArray()
        {
            ApiArrayResource r = new ListResource(List.of(devMixedTerms()));
            assertEquals(EXPECTED_TERMS, r.getStringList(0));
            assertEquals(EXPECTED_TERMS, r.getStringStream(0).toList());
        }


        @Test
        void xmlObjectKeepsTextAndEmptiesOnlyTheCompoundElement() throws Exception
        {
            // XML is untyped: "7" is a string here, and an empty element is an empty string. Only
            // <terms><x>1</x></terms> has no string value of its own.
            ApiResource r = new XmlElementResource(
                    xml("<r><terms>A</terms><terms/><terms><x>1</x></terms><terms>7</terms></r>"));
            assertEquals(List.of("A", "", "", "7"), r.getStringList("terms"));
            assertEquals(List.of("A", "", "", "7"), r.getStringStream("terms").toList());
        }


        @Test
        void xmlArrayKeepsTextAndEmptiesOnlyTheCompoundElement() throws Exception
        {
            ApiArrayResource r = xmlList("<r><a><x>1</x><y><z>2</z></y><w/></a></r>");
            assertEquals(List.of("1", "", ""), r.getStringList(0));
            assertEquals(List.of("1", "", ""), r.getStringStream(0).toList());
        }


        @Test
        void aTextElementWithAttributesStillHasItsText() throws Exception
        {
            // An attribute qualifies a text value; it does not remove it. Using the object-ness
            // test here instead would have emptied every xml:lang-tagged description in a document.
            ApiResource r = new XmlElementResource(
                    xml("<r><terms xml:lang=\"en\">alpha</terms></r>"));
            assertEquals(List.of("alpha"), r.getStringList("terms"));
        }
    }


    @Nested
    class ScalarStringAccessorsAgreeWithTheirOwnPredicate
    {

        @Test
        void mapObjectDoesNotCoerceANonString()
        {
            ApiResource r = new MapResource(Map.of("n", 42, "m", Map.of("x", 1), "s", "A"));
            assertFalse(r.isString("n"));
            assertTrue(r.getString("n").isEmpty(), "42 is not the string \"42\"");
            assertTrue(r.getString("m").isEmpty(), "an object is not the string \"{x=1}\"");
            assertEquals("A", r.getString("s").orElseThrow());
        }


        @Test
        void listArrayDoesNotCoerceANonString()
        {
            ApiArrayResource r = new ListResource(List.of(42, Map.of("x", 1), "A"));
            assertFalse(r.isString(0));
            assertTrue(r.getString(0).isEmpty(), "42 is not the string \"42\"");
            assertTrue(r.getString(1).isEmpty());
            assertEquals("A", r.getString(2).orElseThrow());
        }


        @Test
        void jsonAnswersTheSame() throws Exception
        {
            ApiResource r = new JsonNodeResource(json("{\"n\":42,\"m\":{\"x\":1},\"s\":\"A\"}"));
            assertTrue(r.getString("n").isEmpty());
            assertTrue(r.getString("m").isEmpty());
            assertEquals("A", r.getString("s").orElseThrow());
        }
    }
}
