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
}
