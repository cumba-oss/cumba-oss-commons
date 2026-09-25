package net.cumba.bootstrap;

import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;

/**
 * Reads a configuration file as UTF-8 lines, turning every I/O failure into a
 * {@link BootstrapException} that names the file and how it was chosen. Shared by the bundled
 * config ({@link Bootstrap}) and the user overlay ({@link Launcher}), so an error in either names
 * the file it is actually about.
 */
final class ConfigFiles
{

    private ConfigFiles()
    {
    }


    /**
     * Reads {@code path}.
     *
     * @param path
     *            the file to read
     * @param what
     *            what the file is, for messages ("bootstrap config", "user config")
     * @param hint
     *            how the location was chosen or can be changed, appended to the not-found message
     * @return the file's lines
     * @throws BootstrapException
     *             if the file is missing, not UTF-8, or unreadable
     */
    static List<String> read(Path path, String what, String hint)
    {
        try
        {
            return Files.readAllLines(path, StandardCharsets.UTF_8);
        }
        catch (NoSuchFileException e)
        {
            throw new BootstrapException(what + " not found: " + path + " (" + hint + ")", e);
        }
        catch (MalformedInputException e)
        {
            throw new BootstrapException(what + " " + path + " is not UTF-8 text"
                    + " (save it as UTF-8; a byte-order mark is fine)", e);
        }
        catch (IOException e)
        {
            throw new BootstrapException("cannot read " + what + " " + path + ": " + e.getMessage(),
                    e);
        }
    }
}
