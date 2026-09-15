package net.cumba.web.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
 * Pins the F-webapi-03 rule — <i>never hand back a number that is not the number that was there</i>
 * — across every implementation of {@link ApiResource} and {@link ApiArrayResource}.
 *
 * <p>
 * The rule was applied to {@code JsonArrayResource.getInt} and to the XML implementations when it
 * was first ruled, and this file was written when the remaining four implementations turned out
 * never to have been swept. Measured before the fix: {@code JsonArrayResource.getLong} answered
 * {@code 3} for {@code 3.99} while its own {@code isLong} answered {@code false};
 * {@code JsonNodeResource.getInt} answered {@code -2147483648} for {@code 2147483648};
 * {@code ListResource.isInt} was true for {@code 9_999_999_999L}, whose {@code getInt} then
 * answered {@code 1215752191}.
 * </p>
 */
class NumericStrictnessTest
{

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static JsonNode json(String aJson) throws Exception
    {
        return MAPPER.readTree(aJson);
    }


    @Test
    void jsonArrayGetLongRejectsWhatItsOwnPredicateRejects() throws Exception
    {
        JsonArrayResource r = new JsonArrayResource(json("[3.99, 9223372036854775808, 42]"));

        assertFalse(r.isLong(0));
        assertTrue(r.getLong(0).isEmpty(), "3.99 must not be truncated to 3");

        assertFalse(r.isLong(1));
        assertTrue(r.getLong(1).isEmpty(), "2^63 must not wrap round to -9223372036854775808");

        assertTrue(r.isLong(2));
        assertEquals(42L, r.getLong(2).orElseThrow(), "a value that does fit still comes back");
    }


    @Test
    void jsonArrayStillReadsAnIntegerTooBigForAnIntAsALong() throws Exception
    {
        JsonArrayResource r = new JsonArrayResource(json("[2147483648]"));
        assertFalse(r.isInt(0));
        assertTrue(r.getInt(0).isEmpty());
        assertTrue(r.isLong(0));
        assertEquals(2_147_483_648L, r.getLong(0).orElseThrow());
    }


    @Test
    void jsonObjectGetIntAndGetLongRefuseToNarrow() throws Exception
    {
        JsonNodeResource r = new JsonNodeResource(
                json("{\"d\":3.14,\"big\":2147483648,\"huge\":9223372036854775808,\"ok\":7}"));

        assertTrue(r.getInt("d").isEmpty(), "3.14 is not an int");
        assertTrue(r.getLong("d").isEmpty(), "3.14 is not a long");
        assertTrue(r.getInt("big").isEmpty(), "2147483648 must not become -2147483648");
        assertEquals(2_147_483_648L, r.getLong("big").orElseThrow());
        assertTrue(r.getLong("huge").isEmpty(), "2^63 must not wrap round");
        assertEquals(7, r.getInt("ok").orElseThrow());
        assertEquals(7L, r.getLong("ok").orElseThrow());
    }


    @Test
    void jsonObjectStillWidensAnIntegerToADouble() throws Exception
    {
        JsonNodeResource r = new JsonNodeResource(json("{\"n\":7}"));
        assertEquals(7.0d, r.getDouble("n").orElseThrow(),
                "widening is lossless and stays a documented convenience");
    }


    @Test
    void listResourcePredicatesAreNoLongerOneAndTheSameTest()
    {
        ListResource r = new ListResource(List.of(9_999_999_999L, 3.14d, 42));

        assertFalse(r.isInt(0), "does not fit an int");
        assertTrue(r.isLong(0));
        assertTrue(r.isDouble(0));

        assertFalse(r.isInt(1), "a fractional value is not an int");
        assertFalse(r.isLong(1), "a fractional value is not a long");
        assertTrue(r.isDouble(1));

        assertTrue(r.isInt(2));
        assertTrue(r.isLong(2));
        assertTrue(r.isDouble(2));
    }


    @Test
    void listResourceAccessorsAgreeWithItsPredicates()
    {
        ListResource r = new ListResource(List.of(9_999_999_999L, 3.7d, 42));
        assertTrue(r.getInt(0).isEmpty(), "9_999_999_999 must not become 1215752191");
        assertEquals(9_999_999_999L, r.getLong(0).orElseThrow());
        assertTrue(r.getInt(1).isEmpty(), "3.7 must not be truncated to 3");
        assertTrue(r.getLong(1).isEmpty());
        assertEquals(42, r.getInt(2).orElseThrow());
        assertEquals(42L, r.getLong(2).orElseThrow());
    }


    /**
     * Reversed by the Q12 ruling of 2026-09-11. This test used to assert that {@code 42.0} held as
     * a {@link Double} reads back as the integer {@code 42} — and it was the last place where the
     * dev copies still disagreed with the other four implementations, because
     * {@code JsonNodeResource} answers empty for the JSON {@code 42.0} (Jackson types it
     * {@code DoubleNode}) and {@code XmlElementResource} answers empty for the text {@code "42.0"}.
     * What counts as an integer is the value's type, not whether its fraction happens to be zero.
     */
    @Test
    void aWholeNumberHeldAsADoubleIsNotAnInteger()
    {
        ListResource r = new ListResource(List.of(42.0d));
        assertFalse(r.isInt(0), "a floating-point value is not an integer");
        assertFalse(r.isLong(0));
        assertTrue(r.getInt(0).isEmpty());
        assertTrue(r.getLong(0).isEmpty());
        assertEquals(42.0d, r.getDouble(0).orElseThrow(), "it is still a perfectly good double");
    }


    @Test
    void nonFiniteValuesAreNoIntegerAtAll()
    {
        ListResource r = new ListResource(List.of(Double.NaN, Double.POSITIVE_INFINITY));
        assertFalse(r.isInt(0));
        assertFalse(r.isLong(0));
        assertTrue(r.getInt(0).isEmpty());
        assertTrue(r.getLong(1).isEmpty());
    }


    @Test
    void mapResourceAccessorsRefuseToNarrow()
    {
        MapResource r = new MapResource(
                Map.of("big", 2_147_483_648L, "d", 3.7d, "ok", 5, "s", "text"));
        assertTrue(r.getInt("big").isEmpty(), "2147483648 must not become -2147483648");
        assertEquals(2_147_483_648L, r.getLong("big").orElseThrow());
        assertTrue(r.getInt("d").isEmpty(), "3.7 must not be truncated to 3");
        assertTrue(r.getLong("d").isEmpty());
        assertEquals(5, r.getInt("ok").orElseThrow());
        assertEquals(5L, r.getLong("ok").orElseThrow());
        assertTrue(r.getInt("s").isEmpty(), "a string is not a number");
        assertTrue(r.getLong("s").isEmpty());
        assertTrue(r.getInt("missing").isEmpty());
        assertTrue(r.getLong("missing").isEmpty());
    }


    @Test
    void theExactEndsOfEachRangeAreInsideIt()
    {
        ListResource r = new ListResource(
                List.of(Integer.MIN_VALUE, Integer.MAX_VALUE, Long.MIN_VALUE, Long.MAX_VALUE));

        assertTrue(r.isInt(0), "Integer.MIN_VALUE is an int");
        assertEquals(Integer.MIN_VALUE, r.getInt(0).orElseThrow());
        assertTrue(r.isInt(1), "Integer.MAX_VALUE is an int");
        assertEquals(Integer.MAX_VALUE, r.getInt(1).orElseThrow());

        assertTrue(r.isLong(2), "Long.MIN_VALUE is a long");
        assertEquals(Long.MIN_VALUE, r.getLong(2).orElseThrow());
        assertTrue(r.isLong(3), "Long.MAX_VALUE is a long");
        assertEquals(Long.MAX_VALUE, r.getLong(3).orElseThrow());

        assertFalse(r.isInt(2), "Long.MIN_VALUE does not fit an int");
        assertFalse(r.isInt(3));
    }


    @Test
    void theExactEndsOfEachRangeAreInsideItForAMapToo()
    {
        MapResource r = new MapResource(Map.of("imin", Integer.MIN_VALUE, "imax", Integer.MAX_VALUE,
                "lmin", Long.MIN_VALUE, "lmax", Long.MAX_VALUE));
        assertEquals(Integer.MIN_VALUE, r.getInt("imin").orElseThrow());
        assertEquals(Integer.MAX_VALUE, r.getInt("imax").orElseThrow());
        assertEquals(Long.MIN_VALUE, r.getLong("lmin").orElseThrow());
        assertEquals(Long.MAX_VALUE, r.getLong("lmax").orElseThrow());
        assertTrue(r.getInt("lmin").isEmpty(), "Long.MIN_VALUE does not fit an int");
        assertTrue(r.getInt("lmax").isEmpty());
    }


    @Test
    void anArbitraryPrecisionValueIsJudgedExactlyRatherThanThroughADouble()
    {
        // 2^63 exactly: doubleValue() would round it to 9.223372036854776E18 and longValue()
        // would clamp; only an exact comparison answers "does not fit".
        ListResource r = new ListResource(List.of(new java.math.BigInteger("9223372036854775808")));
        assertFalse(r.isLong(0));
        assertTrue(r.getLong(0).isEmpty());
    }
}
