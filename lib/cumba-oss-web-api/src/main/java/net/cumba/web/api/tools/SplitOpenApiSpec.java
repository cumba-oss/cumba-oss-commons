package net.cumba.web.api.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Map;

/**
 * Splits cdisc-library-api.json into individual files per path and per component schema.
 *
 * <pre>
 * Usage: java -cp ... SplitOpenApiSpec [input.json] [outputDir]
 * </pre>
 *
 * Defaults: input = ./cdisc-library-api.json, output = ./cdisc-library-api/
 */
public class SplitOpenApiSpec
{

    public static void main(String[] args) throws IOException
    {
        String inputFile = args.length > 0 ? args[0] : "cdisc-library-api.json";
        String outputDir = args.length > 1 ? args[1] : "cdisc-library-api";

        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        JsonNode root = mapper.readTree(Path.of(inputFile).toFile());

        Path outPath = Path.of(outputDir);
        Files.createDirectories(outPath);

        // Split paths
        JsonNode paths = root.get("paths");
        if (paths != null)
        {
            splitEntries(mapper, outPath, paths, "path");
        }

        // Split components/schemas
        JsonNode schemas = root.path("components").path("schemas");
        if (!schemas.isMissingNode())
        {
            splitEntries(mapper, outPath, schemas, "components_schemas");
        }

        System.out.println("Done. Output written to " + outPath.toAbsolutePath());
    }


    private static void splitEntries(ObjectMapper mapper, Path outPath, JsonNode parent,
            String prefix)
        throws IOException
    {
        Iterator<Map.Entry<String, JsonNode>> it = parent.properties().iterator();
        int count = 0;
        while (it.hasNext())
        {
            Map.Entry<String, JsonNode> entry = it.next();
            String name = entry.getKey();
            // Replace leading slash and all remaining slashes with underscore
            String safeName = name.startsWith("/") ? name.substring(1) : name;
            safeName = safeName.replace('/', '_');
            // Replace any other problematic characters
            safeName = safeName.replace('{', '_').replace('}', '_');

            String fileName = prefix + "_" + safeName + ".json";
            Path file = outPath.resolve(fileName);
            mapper.writeValue(file.toFile(), entry.getValue());
            count++;
        }
        System.out.printf("Wrote %d files for %s%n", count, prefix);
    }
}
