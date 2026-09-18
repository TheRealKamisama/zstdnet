package cn.tohsaka.factory.zstdnet.network;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
/** Bounded, ordered assembly; one response at a time per client connection. */
public final class ReportAssembly {
    public static final int MAX_BYTES = 1024 * 1024;
    public static final int CHUNK_BYTES = 24 * 1024;
    private final ByteArrayOutputStream data = new ByteArrayOutputStream();
    private int next;
    private int total;
    private long started;
    public synchronized String accept(int sequence, int count, byte[] bytes) {
        if (sequence == 0) { reset(); total = count; started = System.currentTimeMillis(); }
        if (count < 1 || count > 43 || total != count || sequence != next || bytes.length > CHUNK_BYTES ||
            data.size() + bytes.length > MAX_BYTES || System.currentTimeMillis() - started > 30_000) {
            reset(); throw new IllegalArgumentException("Invalid or expired report chunk");
        }
        data.writeBytes(bytes);
        if (++next != total) return null;
        String result = data.toString(StandardCharsets.UTF_8);
        reset();
        return result;
    }
    public synchronized void reset() { data.reset(); next = 0; total = 0; started = 0; }
}
