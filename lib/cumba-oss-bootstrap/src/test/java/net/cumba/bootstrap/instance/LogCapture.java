package net.cumba.bootstrap.instance;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.jspecify.annotations.Nullable;

/**
 * Captures what a class of the single-instance gate logged, so a test can assert on it.
 *
 * <p>
 * <b>Why this exists.</b> The gate is an optimisation that never fails a start: every refusal,
 * time-out and broken connection ends in a separate instance, and the log line is the only place
 * that says <em>why</em> a second window opened. A test that drives such a path without asserting
 * the line asserts half the contract - delete the log and the start still "works", it just stops
 * being diagnosable (precedent: {@code LogCapture} in {@code cumba-json-rpc}).
 * </p>
 *
 * <p>
 * The classes log through {@link System.Logger}, backed by {@code java.util.logging}: a
 * {@code System.Logger} obtained for {@code Foo.class.getName()} publishes to the JUL logger of
 * that name. Level mapping: {@code DEBUG→FINE}, {@code INFO→INFO}, {@code WARNING→WARNING}.
 * </p>
 *
 * <p>
 * ⚠ The JUL logger is held in a field for the capture's lifetime on purpose: JUL keeps only weak
 * references to loggers, so one obtained and dropped can be collected together with the handler
 * installed on it, and the capture would then silently see nothing.
 * </p>
 */
final class LogCapture implements AutoCloseable
{

    private final Logger jul;

    private final @Nullable Level savedLevel;

    private final Handler handler;

    private final List<LogRecord> records = Collections.synchronizedList(new ArrayList<>());

    private LogCapture(String aLoggerName)
    {
        jul = Logger.getLogger(aLoggerName);
        savedLevel = jul.getLevel();
        handler = new Handler()
        {

            @Override
            public void publish(LogRecord aRecord)
            {
                records.add(aRecord);
            }


            @Override
            public void flush()
            {
                // nothing is buffered
            }


            @Override
            public void close()
            {
                // nothing to release
            }
        };
        handler.setLevel(Level.ALL);
        jul.setLevel(Level.ALL);
        jul.addHandler(handler);
    }


    /** Starts capturing everything logged by {@code aClass}. */
    static LogCapture on(Class<?> aClass)
    {
        return new LogCapture(aClass.getName());
    }


    /** Every record captured so far, in order. */
    List<LogRecord> records()
    {
        synchronized (records)
        {
            return List.copyOf(records);
        }
    }


    /** Asserts a record at exactly {@code aLevel} whose message contains {@code aNeedle}. */
    void assertLogged(Level aLevel, String aNeedle)
    {
        assertTrue(
                records().stream()
                        .anyMatch(r -> r.getLevel().equals(aLevel)
                                && String.valueOf(r.getMessage()).contains(aNeedle)),
                () -> "expected a " + aLevel + " log containing \"" + aNeedle + "\" but captured: "
                        + rendered());
    }


    /**
     * Asserts a record at exactly {@code aLevel} carrying a {@code aThrown} - the stack trace a
     * bare message loses.
     */
    void assertLoggedWithThrowable(Level aLevel, Class<? extends Throwable> aThrown)
    {
        assertTrue(
                records().stream().anyMatch(
                        r -> r.getLevel().equals(aLevel) && aThrown.isInstance(r.getThrown())),
                () -> "expected a " + aLevel + " log carrying a " + aThrown.getSimpleName()
                        + " but captured: " + rendered());
    }


    private List<String> rendered()
    {
        return records().stream().map(r -> r.getLevel() + " " + r.getMessage()).toList();
    }


    @Override
    public void close()
    {
        jul.removeHandler(handler);
        jul.setLevel(savedLevel);
    }
}
