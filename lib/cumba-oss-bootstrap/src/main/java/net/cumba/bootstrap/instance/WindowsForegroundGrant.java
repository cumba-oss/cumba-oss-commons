package net.cumba.bootstrap.instance;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;

/**
 * The Windows binding of {@link ForegroundGrant}: {@code AllowSetForegroundWindow(DWORD)} from
 * {@code user32.dll} through the foreign function API, linked with
 * {@code Linker.Option.captureCallState("GetLastError")} so the caller can log why a refusal
 * happened.
 *
 * <p>
 * No branches by design: the lookup, the handle and the downcall only. A Linux build host can never
 * execute a {@code user32} downcall, so this one class is excluded from the module's coverage and
 * mutation floors (the module pom states the policy); everything that decides anything lives in
 * covered classes.
 */
final class WindowsForegroundGrant implements ForegroundGrant
{

    private static final StructLayout CAPTURE = Linker.Option.captureStateLayout();

    private static final VarHandle LAST_ERROR = CAPTURE
            .varHandle(MemoryLayout.PathElement.groupElement("GetLastError"));

    private final MethodHandle allowSetForegroundWindow;

    // The restricted-method warning is the JDK's reminder that --enable-native-access is needed;
    // every launcher of the data browser passes it (databrowser.jvmargs, the macOS javaOptions).
    @SuppressWarnings("restricted")
    WindowsForegroundGrant()
    {
        Linker linker = Linker.nativeLinker();
        SymbolLookup user32 = SymbolLookup.libraryLookup("user32.dll", Arena.global());
        allowSetForegroundWindow = linker.downcallHandle(
                user32.findOrThrow("AllowSetForegroundWindow"),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT),
                Linker.Option.captureCallState("GetLastError"));
    }


    @Override
    public Result allow(long aPid) throws NativeCallException
    {
        try (Arena arena = Arena.ofConfined())
        {
            MemorySegment state = arena.allocate(CAPTURE);
            int bool = (int) allowSetForegroundWindow.invokeExact(state, (int) aPid);
            int lastError = (int) LAST_ERROR.get(state, 0L);
            return new Result(bool, lastError);
        }
        catch (Throwable t)
        {
            throw new NativeCallException(t);
        }
    }
}
