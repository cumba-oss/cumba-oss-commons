package net.cumba.bootstrap.itmain;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import net.cumba.bootstrap.instance.InstanceGate;
import net.cumba.bootstrap.instance.WireCodec;

/**
 * The "application" of the multi-process hand-off test: what the launcher starts when its gate says
 * PRIMARY. Runs a {@link StandInServer} on the published instance directory (one
 * {@code BUSY STARTING} heartbeat, then {@code ACCEPTED}), appends every delivered path to the
 * results file named by {@value #RESULTS_PROPERTY}, and exits once {@value #EXPECTED_PROPERTY}
 * paths arrived in total (its own arguments included) or after a bounded wait. Prints the published
 * outcome first.
 */
public final class StandInServerMain
{

    private StandInServerMain()
    {
    }

    /**
     * System property naming the results file (not an argument: an argument naming an existing file
     * is a path to hand off).
     */
    public static final String RESULTS_PROPERTY = "handoff.results";

    /** System property with the number of paths expected in total. */
    public static final String EXPECTED_PROPERTY = "handoff.expected";

    public static void main(String[] args) throws Exception
    {
        Path results = Path.of(System.getProperty(RESULTS_PROPERTY));
        int expected = Integer.getInteger(EXPECTED_PROPERTY);
        String outcome = System.getProperty(InstanceGate.OUTCOME_PROPERTY, "none");
        System.out.println("OUTCOME " + outcome + " pid=" + ProcessHandle.current().pid());
        System.out.flush();
        if (!"PRIMARY".equals(outcome))
        {
            // an EXTRA would be a lost hand-off; make it visible and exit non-zero
            System.exit(3);
        }
        Path instanceDir = Path.of(System.getProperty(InstanceGate.DIR_PROPERTY));
        // the files of the primary's own start
        record(results, List.of(args));
        try (StandInServer server = new StandInServer(instanceDir, instanceDir,
                n -> StandInServer.Script
                        .accepted(WireCodec.BUSY + "\n" + WireCodec.STATE_STARTING)))
        {
            long deadline = System.nanoTime() + 60_000_000_000L;
            while (System.nanoTime() < deadline)
            {
                int delivered = 0;
                for (List<String> open : server.opens())
                {
                    delivered += open.size();
                }
                if (delivered + args.length >= expected)
                {
                    for (List<String> open : server.opens())
                    {
                        record(results, open);
                    }
                    System.out.println("DONE " + delivered);
                    return;
                }
                Thread.sleep(50);
            }
            System.out.println("TIMEOUT");
            System.exit(4);
        }
    }


    private static synchronized void record(Path aResults, List<String> aPaths) throws IOException
    {
        for (String p : aPaths)
        {
            Files.writeString(aResults, p + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        }
    }
}
