package net.cumba.web.api.xml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.xml.parsers.DocumentBuilderFactory;
import net.cumba.web.api.ApiArrayResource;
import net.cumba.web.api.ApiResource;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Covers the {@link XmlChildListResource} accessors that no scenario reached before: the boolean
 * and number families, the structural predicates, the out-of-range paths through {@code safeGet},
 * and the dynamic-proxy invocation handler.
 */
class XmlChildListResourceAccessorTest
{

    /** A domain array interface with a default method, so the proxy's two paths both run. */
    interface Row extends ApiArrayResource
    {

        default Optional<String> first()
        {
            return getString(0);
        }
    }

    private static List<Element> childrenOf(String aXml) throws Exception
    {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        Element root = f.newDocumentBuilder()
                .parse(new ByteArrayInputStream(aXml.getBytes(StandardCharsets.UTF_8)))
                .getDocumentElement();
        List<Element> out = new ArrayList<>();
        NodeList kids = root.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++)
        {
            Node n = kids.item(i);
            if (n instanceof Element e)
            {
                out.add(e);
            }
        }
        return out;
    }


    private static XmlChildListResource of(String aXml) throws Exception
    {
        return new XmlChildListResource(childrenOf(aXml));
    }

    @Nested
    class BooleanAccessors
    {

        @Test
        void everyAcceptedSpellingOfTrueIsRecognised() throws Exception
        {
            XmlChildListResource r = of("<r><a>true</a><a>TRUE</a><a>yes</a><a>YES</a></r>");
            for (int i = 0; i < 4; i++)
            {
                assertTrue(r.isBoolean(i), "index " + i);
                assertEquals(Optional.of(Boolean.TRUE), r.getBoolean(i), "index " + i);
            }
        }


        @Test
        void everyAcceptedSpellingOfFalseIsRecognised() throws Exception
        {
            XmlChildListResource r = of("<r><a>false</a><a>False</a><a>no</a><a>NO</a></r>");
            for (int i = 0; i < 4; i++)
            {
                assertTrue(r.isBoolean(i), "index " + i);
                assertEquals(Optional.of(Boolean.FALSE), r.getBoolean(i), "index " + i);
            }
        }


        @Test
        void aWordThatIsNotABooleanIsNeitherTrueNorFalse() throws Exception
        {
            XmlChildListResource r = of("<r><a>maybe</a><a>1</a><a></a></r>");
            for (int i = 0; i < 3; i++)
            {
                assertFalse(r.isBoolean(i), "index " + i);
                assertEquals(Optional.empty(), r.getBoolean(i), "index " + i);
            }
        }


        @Test
        void anOutOfRangeIndexIsNotABoolean() throws Exception
        {
            XmlChildListResource r = of("<r><a>true</a></r>");
            assertFalse(r.isBoolean(1));
            assertFalse(r.isBoolean(-1));
            assertEquals(Optional.empty(), r.getBoolean(1));
            assertEquals(Optional.empty(), r.getBoolean(-1));
        }
    }


    @Nested
    class NumberAccessors
    {

        @Test
        void anIntegralValueComesBackAsALong() throws Exception
        {
            XmlChildListResource r = of("<r><a>42</a></r>");
            Optional<Number> n = r.getNumber(0);
            assertEquals(Optional.of(42L), n);
            assertInstanceOf(Long.class, n.orElseThrow(),
                    "an integral value must not be widened to a double");
        }


        @Test
        void aFractionalValueComesBackAsADouble() throws Exception
        {
            XmlChildListResource r = of("<r><a>1.5</a></r>");
            Optional<Number> n = r.getNumber(0);
            assertInstanceOf(Double.class, n.orElseThrow());
            assertEquals(1.5d, n.orElseThrow().doubleValue());
        }


        @Test
        void aNonNumericValueIsNotANumber() throws Exception
        {
            XmlChildListResource r = of("<r><a>abc</a></r>");
            assertEquals(Optional.empty(), r.getNumber(0));
            assertFalse(r.isNumber(0));
            assertFalse(r.isDouble(0));
            assertFalse(r.isLong(0));
            assertFalse(r.isInt(0));
        }


        @Test
        void anOutOfRangeIndexIsNotANumber() throws Exception
        {
            XmlChildListResource r = of("<r><a>1</a></r>");
            assertEquals(Optional.empty(), r.getNumber(9));
            assertFalse(r.isNumber(9), "no element, so nothing to parse");
            assertFalse(r.isDouble(9));
            assertFalse(r.isLong(9));
            assertTrue(r.getDouble(9).isEmpty());
            assertTrue(r.getLong(9).isEmpty());
        }


        @Test
        void aFractionalValueIsADoubleButNotALong() throws Exception
        {
            XmlChildListResource r = of("<r><a>2.75</a></r>");
            assertTrue(r.isDouble(0));
            assertFalse(r.isLong(0));
            assertEquals(2.75d, r.getDouble(0).orElseThrow());
        }
    }


    @Nested
    class StructuralPredicates
    {

        @Test
        void anElementWithChildrenOrAttributesIsAnObject() throws Exception
        {
            XmlChildListResource r = of("<r><a><b/></a><a id='1'>x</a></r>");
            assertTrue(r.isObject(0), "has a child element");
            assertTrue(r.isObject(1), "has a non-xmlns attribute");
        }


        @Test
        void aPlainTextElementIsNotAnObject() throws Exception
        {
            XmlChildListResource r = of("<r><a>text</a></r>");
            assertFalse(r.isObject(0));
            assertFalse(r.isObject(7), "out of range");
        }


        @Test
        void noElementOfTheListIsItselfAnArray() throws Exception
        {
            XmlChildListResource r = of("<r><a><b/><b/></a></r>");
            assertFalse(r.isArray(0), "a repeated-element list holds elements, not nested arrays");
            assertTrue(r.getArray(0, ApiArrayResource.class).isEmpty());
        }


        @Test
        void getStringListReadsTheTextOfEveryChildRegardlessOfName() throws Exception
        {
            XmlChildListResource r = of("<r><a><x>1</x><y>2</y>ignored<z>3</z></a></r>");
            assertEquals(List.of("1", "2", "3"), r.getStringList(0));
            assertEquals(List.of("1", "2", "3"), r.getStringStream(0).toList());
        }


        @Test
        void getStringListOfAnOutOfRangeIndexIsEmpty() throws Exception
        {
            XmlChildListResource r = of("<r><a><x>1</x></a></r>");
            assertEquals(List.of(), r.getStringList(5));
            assertEquals(List.of(), r.getStringList(-1));
        }
    }


    @Nested
    class IndexBounds
    {

        @Test
        void theFirstAndLastElementsAreReachable() throws Exception
        {
            XmlChildListResource r = of("<r><a>first</a><a>last</a></r>");
            assertEquals(2, r.getLength());
            assertEquals(Optional.of("first"), r.getString(0));
            assertEquals(Optional.of("last"), r.getString(1));
            assertTrue(r.isString(0));
            assertTrue(r.isString(1));
        }


        @Test
        void oneIndexPastTheEndIsEmptyRatherThanAnError() throws Exception
        {
            XmlChildListResource r = of("<r><a>only</a></r>");
            assertEquals(Optional.empty(), r.getString(1),
                    "index == size must be rejected before the backing list is touched");
            assertFalse(r.isString(1));
        }
    }


    @Nested
    class Factories
    {

        @Test
        void theUntypedFactoryReturnsThePlainImplementation() throws Exception
        {
            ApiArrayResource r = XmlChildListResource.of(childrenOf("<r><a>x</a></r>"));
            assertInstanceOf(XmlChildListResource.class, r);
            assertEquals(1, r.getLength());
        }


        @Test
        void askingForApiArrayResourceItselfSkipsTheProxy() throws Exception
        {
            ApiArrayResource r = XmlChildListResource.of(childrenOf("<r><a>x</a></r>"),
                    ApiArrayResource.class);
            assertInstanceOf(XmlChildListResource.class, r,
                    "the base interface needs no proxy - the implementation already is one");
        }


        @Test
        void askingForADomainInterfaceReturnsAProxy() throws Exception
        {
            Row r = XmlChildListResource.of(childrenOf("<r><a>x</a></r>"), Row.class);
            assertFalse((Object) r instanceof XmlChildListResource,
                    "a domain interface is served by a dynamic proxy");
            assertEquals(1, r.getLength());
        }
    }


    @Nested
    class ProxyDispatch
    {

        @Test
        void inheritedAccessorsReachTheDelegate() throws Exception
        {
            Row r = XmlChildListResource.of(childrenOf("<r><a>x</a><a>y</a></r>"), Row.class);
            assertEquals(2, r.getLength());
            assertEquals(Optional.of("y"), r.getString(1));
        }


        @Test
        void defaultMethodsRunOnTheInterface() throws Exception
        {
            Row r = XmlChildListResource.of(childrenOf("<r><a>x</a></r>"), Row.class);
            assertEquals(Optional.of("x"), r.first(),
                    "a default method must execute, not be forwarded to the delegate");
        }


        @Test
        void toStringNamesTheRequestedInterface() throws Exception
        {
            Row r = XmlChildListResource.of(childrenOf("<r><a>x</a><a>y</a></r>"), Row.class);
            assertEquals("Row[size=2]", r.toString());
        }


        @Test
        void equalsAndHashCodeFollowTheBackingElements() throws Exception
        {
            List<Element> kids = childrenOf("<r><a>x</a></r>");
            Row a = XmlChildListResource.of(kids, Row.class);
            Row b = XmlChildListResource.of(kids, Row.class);
            assertEquals(a, a, "reflexive");
            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
            assertNotEquals(a, XmlChildListResource.of(childrenOf("<r><a>z</a></r>"), Row.class));
            assertNotEquals(a, "not a resource");
        }
    }


    @Nested
    class ValueSemantics
    {

        @Test
        void twoViewsOfTheSameElementsAreEqual() throws Exception
        {
            List<Element> kids = childrenOf("<r><a>x</a></r>");
            XmlChildListResource a = new XmlChildListResource(kids);
            XmlChildListResource b = new XmlChildListResource(new ArrayList<>(kids));
            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
            assertSame(a, a);
            assertEquals(a, a);
        }


        @Test
        void differentElementsAreNotEqualAndHashDifferently() throws Exception
        {
            XmlChildListResource a = of("<r><a>x</a></r>");
            XmlChildListResource b = of("<r><a>y</a></r>");
            assertNotEquals(a, b);
            assertNotEquals(a.hashCode(), b.hashCode(),
                    "hashCode must follow the elements, not be a constant");
            assertNotEquals(a, null);
            assertNotEquals(a, "not a resource");
        }


        @Test
        void toStringReportsTheSize() throws Exception
        {
            assertEquals("XmlChildListResource[size=3]",
                    of("<r><a>1</a><a>2</a><a>3</a></r>").toString());
        }


        @Test
        void getObjectWrapsTheElementAndIsEmptyOutOfRange() throws Exception
        {
            XmlChildListResource r = of("<r><a id='1'/></r>");
            Optional<ApiResource> o = r.getObject(0, ApiResource.class);
            assertTrue(o.isPresent());
            assertEquals(Optional.of("1"), o.orElseThrow().getString("id"));
            assertTrue(r.getObject(4, ApiResource.class).isEmpty());
            assertNotNull(r);
        }
    }
}
