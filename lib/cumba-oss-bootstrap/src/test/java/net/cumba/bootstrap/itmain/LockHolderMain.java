package net.cumba.bootstrap.itmain;

import java.io.IOException;
import java.nio.file.Path;
import net.cumba.bootstrap.instance.InstanceLock;
import net.cumba.bootstrap.instance.LockProbe;

/**
 * A forked process that takes the instance lock on the file named by {@code args[0]}, prints the
 * result, and holds it until its stdin closes - the "separate process" the real lock is tested
 * against.
 */
public final class LockHolderMain
{

    private LockHolderMain()
    {
    }


    public static void main(String[] args) throws IOException
    {
        LockProbe.Status status = new InstanceLock().tryLock(Path.of(args[0]));
        System.out.println("LOCK " + status);
        System.out.flush();
        // hold until the parent closes our stdin
        int c;
        do
        {
            c = System.in.read();
        }
        while (c >= 0);
    }
}
