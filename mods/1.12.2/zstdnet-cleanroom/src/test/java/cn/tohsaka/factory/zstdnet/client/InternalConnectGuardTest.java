package cn.tohsaka.factory.zstdnet.client;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;
class InternalConnectGuardTest {
    @Test void internalConstructionBypassesExactlyItsScopeIncludingNestedCalls() {
        assertFalse(InternalConnectGuard.active());
        InternalConnectGuard.construct(() -> {
            assertTrue(InternalConnectGuard.active());
            InternalConnectGuard.construct(() -> { assertTrue(InternalConnectGuard.active()); return null; });
            assertTrue(InternalConnectGuard.active()); return null;
        });
        assertFalse(InternalConnectGuard.active());
    }
    @Test void exceptionsAndOtherThreadsDoNotLeakBypass() throws Exception {
        AtomicBoolean otherThread = new AtomicBoolean(true);
        assertThrows(IllegalStateException.class, () -> InternalConnectGuard.construct(() -> {
            Thread other = new Thread(() -> otherThread.set(InternalConnectGuard.active()));
            other.start();
            try { other.join(); } catch (InterruptedException e) { throw new AssertionError(e); }
            throw new IllegalStateException("constructor failed");
        }));
        assertFalse(otherThread.get()); assertFalse(InternalConnectGuard.active());
    }
}
