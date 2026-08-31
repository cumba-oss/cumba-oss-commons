package net.cumba.bootstrap;

import java.nio.file.Path;

/**
 * Unchecked exception for any bootstrap failure: a malformed config file, an unresolved variable, a
 * missing classpath entry under {@code ERROR} mode, or a failure to locate / invoke the target main
 * class. Messages are written for an operator reading a terminal, not a developer reading a stack
 * trace.
 */
public final class BootstrapException extends RuntimeException
{

    private static final long serialVersionUID = 1L;

    /**
     * Creates an exception with a plain message.
     *
     * @param message
     *            human-readable description of the failure
     */
    public BootstrapException(String message)
    {
        super(message);
    }


    /**
     * Creates an exception wrapping an underlying cause.
     *
     * @param message
     *            human-readable description of the failure
     * @param cause
     *            the underlying failure
     */
    public BootstrapException(String message, Throwable cause)
    {
        super(message, cause);
    }


    /**
     * Builds an exception whose message is prefixed with {@code <source>:<line>} so the operator
     * can jump straight to the offending config line. Line numbers are reported 1-based even though
     * the caller iterates 0-based.
     *
     * @param source
     *            the config file the problem was found in
     * @param zeroBasedLine
     *            the offending line index (0-based)
     * @param message
     *            description of the problem
     * @return a ready-to-throw exception
     */
    public static BootstrapException at(Path source, int zeroBasedLine, String message)
    {
        return new BootstrapException(source + ":" + (zeroBasedLine + 1) + ": " + message);
    }
}
