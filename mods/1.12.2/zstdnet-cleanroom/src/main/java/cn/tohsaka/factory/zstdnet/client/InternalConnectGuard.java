package cn.tohsaka.factory.zstdnet.client;
import java.util.function.Supplier;

/** Scoped to the synchronous internal GuiConnecting constructor, never a global next-call flag. */
public final class InternalConnectGuard {
    private static final ThreadLocal<Boolean> INTERNAL = ThreadLocal.withInitial(() -> false);
    private InternalConnectGuard() {}
    public static boolean active() { return INTERNAL.get(); }
    public static <T> T construct(Supplier<T> constructor) {
        boolean previous = INTERNAL.get();
        INTERNAL.set(true);
        try { return constructor.get(); }
        finally { if (previous) INTERNAL.set(true); else INTERNAL.remove(); }
    }
}
