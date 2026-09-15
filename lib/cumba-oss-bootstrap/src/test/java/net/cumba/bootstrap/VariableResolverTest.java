package net.cumba.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;

class VariableResolverTest
{

    private static final Path SRC = Path.of("test.conf");

    private final Map<String, String> env = new HashMap<>();

    private final Map<String, String> sys = new HashMap<>();

    private final Map<String, String> earlier = new HashMap<>();

    private VariableResolver resolver()
    {
        UnaryOperator<String> envLookup = env::get;
        UnaryOperator<String> sysLookup = sys::get;
        return new VariableResolver(envLookup, sysLookup, earlier);
    }


    private String resolve(String value)
    {
        return resolver().resolve(value, SRC);
    }


    @Test
    void plainTextIsUnchanged()
    {
        assertEquals("/opt/app/lib", resolve("/opt/app/lib"));
    }


    @Test
    void resolvesEnvReference()
    {
        env.put("APP_HOME", "/srv/app");
        assertEquals("/srv/app/conf", resolve("${env:APP_HOME}/conf"));
    }


    @Test
    void resolvesSysReference()
    {
        sys.put("user.dir", "/here");
        assertEquals("/here", resolve("${sys:user.dir}"));
    }


    @Test
    void bareReferencePrefersEarlierProperties()
    {
        earlier.put("a", "from-earlier");
        sys.put("a", "from-sys");
        assertEquals("from-earlier", resolve("${a}"));
    }


    @Test
    void bareReferenceFallsBackToSystemProperty()
    {
        sys.put("a", "from-sys");
        assertEquals("from-sys", resolve("${a}"));
    }


    @Test
    void defaultUsedWhenUnset()
    {
        assertEquals("/opt/app", resolve("${env:APP_HOME:-/opt/app}"));
    }


    @Test
    void defaultUsedWhenEmpty()
    {
        env.put("APP_HOME", "");
        assertEquals("fallback", resolve("${env:APP_HOME:-fallback}"));
    }


    @Test
    void bareEmptyEarlierPropertyTriggersDefault()
    {
        earlier.put("a", "");
        assertEquals("def", resolve("${a:-def}"));
    }


    @Test
    void presentEmptyWithoutDefaultYieldsEmpty()
    {
        sys.put("x", "");
        assertEquals("", resolve("${sys:x}"));
    }


    @Test
    void unresolvedWithoutDefaultFails()
    {
        BootstrapException ex = assertThrows(BootstrapException.class,
                () -> resolve("${env:NOPE}"));
        assertTrue(ex.getMessage().contains("unresolved variable"));
        assertTrue(ex.getMessage().contains("NOPE"));
    }


    @Test
    void doubleDollarIsLiteralDollar()
    {
        assertEquals("price=$5", resolve("price=$$5"));
    }


    @Test
    void multipleReferencesInOneValue()
    {
        earlier.put("a", "A");
        earlier.put("b", "B");
        assertEquals("A-B", resolve("${a}-${b}"));
    }


    @Test
    void defaultMayContainColonDash()
    {
        assertEquals("x:-y", resolve("${env:MISSING:-x:-y}"));
    }


    @Test
    void unterminatedReferenceFails()
    {
        BootstrapException ex = assertThrows(BootstrapException.class, () -> resolve("${env:X"));
        assertTrue(ex.getMessage().contains("unterminated"));
    }


    @Test
    void nestedReferenceInDefaultIsResolved()
    {
        sys.put("bootstrap.dir", "/bd");
        // The README's headline idiom: fall back to ${bootstrap.dir} when APP_HOME is unset.
        assertEquals("/bd/conf", resolve("${env:APP_HOME:-${bootstrap.dir}}/conf"));
    }


    @Test
    void nestedReferenceInDefaultUsesOuterWhenSet()
    {
        env.put("APP_HOME", "/srv");
        sys.put("bootstrap.dir", "/bd");
        assertEquals("/srv", resolve("${env:APP_HOME:-${bootstrap.dir}}"));
    }


    @Test
    void adjacentReferencesResolve()
    {
        earlier.put("a", "A");
        earlier.put("b", "B");
        assertEquals("AB", resolve("${a}${b}"));
    }


    @Test
    void emptyVariableNameFails()
    {
        BootstrapException ex = assertThrows(BootstrapException.class, () -> resolve("${}"));
        assertTrue(ex.getMessage().contains("empty variable name"));
    }


    @Test
    void emptyEnvNameFails()
    {
        BootstrapException ex = assertThrows(BootstrapException.class, () -> resolve("${env:}"));
        assertTrue(ex.getMessage().contains("empty variable name"));
    }


    @Test
    void loneDollarIsLiteral()
    {
        // A '$' with nothing after it is emitted verbatim. Reading one character past it is the
        // classic off-by-one here, and it turns a config value into a crash rather than a path.
        assertEquals("$", resolve("$"));
    }


    @Test
    void trailingDollarIsLiteral()
    {
        assertEquals("a$", resolve("a$"));
    }


    @Test
    void doubleDollarAloneIsOneLiteralDollar()
    {
        assertEquals("$", resolve("$$"));
    }


    @Test
    void braceNotPrecededByDollarIsLiteral()
    {
        // Only "${" opens a reference. A '$' whose *preceding* character is '{' must stay literal,
        // or "{$x}" would be read as a reference to the empty name.
        assertEquals("{$x}", resolve("{$x}"));
    }


    @Test
    void unterminatedReferenceEndingInDollarFails()
    {
        // The brace scan must stop at the end of the value even when the last character is a '$'.
        BootstrapException ex = assertThrows(BootstrapException.class, () -> resolve("${a$"));
        assertTrue(ex.getMessage().contains("unterminated"), ex::getMessage);
    }


    @Test
    void onlyDollarBraceOpensANestedGroup()
    {
        // A bare '{' inside a reference does not nest: the first '}' closes it, so the token is
        // "{$" and the error names that. Counting a lone '{' as nesting would swallow the closing
        // brace and misreport the value as unterminated.
        BootstrapException ex = assertThrows(BootstrapException.class, () -> resolve("${{$}"));
        assertTrue(ex.getMessage().contains("unresolved variable"), ex::getMessage);
        assertTrue(ex.getMessage().contains("{$"), ex::getMessage);
    }


    @Test
    void defaultSeparatorAtTheStartIsAnEmptyName()
    {
        // "${:-x}" has no variable name. That is an operator error; quietly looking up a variable
        // literally called ":-x" instead would report the wrong problem.
        BootstrapException ex = assertThrows(BootstrapException.class, () -> resolve("${:-x}"));
        assertTrue(ex.getMessage().contains("empty variable name"), ex::getMessage);
    }


    @Test
    void defaultIsNotEvaluatedWhenThePrimaryIsSet()
    {
        env.put("APP_HOME", "/srv");
        // Shell ':-' is lazy. The fallback here has no default of its own, so evaluating it
        // eagerly would refuse to start a launcher whose primary value was present all along.
        assertEquals("/srv", resolve("${env:APP_HOME:-${env:CUMBA_HOME}}"));
    }


    @Test
    void anUnresolvableDefaultStillFailsWhenItIsActuallyNeeded()
    {
        BootstrapException ex = assertThrows(BootstrapException.class,
                () -> resolve("${env:APP_HOME:-${env:CUMBA_HOME}}"));
        assertTrue(ex.getMessage().contains("CUMBA_HOME"), ex::getMessage);
    }


    @Test
    void separatorInsideANestedReferenceIsNotTheOuterSeparator()
    {
        earlier.put("b", "B");
        // The ':-' belongs to the INNER reference: 'a' is unset so it yields 'b', and the outer
        // reference then resolves ${b}. Splitting the token on the first ':-' textually would
        // look up 'a' with the default 'b}' and hand back a plausible wrong string.
        assertEquals("B", resolve("${${a:-b}}"));
    }


    @Test
    void separatorAfterANestedReferenceIsStillFound()
    {
        earlier.put("a", "b");
        earlier.put("b", "B");
        // The nested group closes before the ':-', so the separator IS the outer one: the name is
        // ${a} -> 'b' and the default is unused. Mis-counting the nesting either way swallows the
        // separator and looks up a variable literally called 'b:-def'.
        assertEquals("B", resolve("${${a}:-def}"));
    }


    @Test
    void nestedReferenceInTheNameIsResolved()
    {
        sys.put("WHICH", "APP");
        env.put("APP_HOME", "/srv");
        assertEquals("/srv", resolve("${env:${WHICH}_HOME}"));
    }


    @Test
    void trailingColonInANameIsNotADefaultSeparator()
    {
        BootstrapException ex = assertThrows(BootstrapException.class, () -> resolve("${a:}"));
        assertTrue(ex.getMessage().contains("unresolved variable"), ex::getMessage);
    }


    @Test
    void trailingDollarInANameDoesNotOpenAGroup()
    {
        BootstrapException ex = assertThrows(BootstrapException.class, () -> resolve("${a$}"));
        assertTrue(ex.getMessage().contains("unresolved variable"), ex::getMessage);
    }
}
