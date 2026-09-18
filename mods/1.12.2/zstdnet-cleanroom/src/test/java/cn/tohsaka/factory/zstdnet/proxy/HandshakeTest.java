package cn.tohsaka.factory.zstdnet.proxy;
import cn.tohsaka.factory.zstdnet.core.protocol.*;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class HandshakeTest {
    @Test void legacyMarkerPreservesExtensionFields() {
        assertEquals("\0FML\0\0other=value", LocalZstdNet.ensureForgeHandshakeSuffix("\0FML\0\0other=value"));
        assertEquals("\0FML\0\0other=value", LocalZstdNet.ensureForgeHandshakeSuffix("\0FML2\0\0other=value"));
        assertEquals("\0FML\0", LocalZstdNet.ensureForgeHandshakeSuffix("\0FML3\0"));
        assertEquals("\0extension\0FML\0", LocalZstdNet.ensureForgeHandshakeSuffix("\0extension"));
    }
    @Test void rewritePreservesProtocolPortAndStatusSuffix() throws Exception {
        var method = LocalZstdNet.class.getDeclaredMethod("rewriteHandshakeDestination", byte[].class, String.class, int.class);
        method.setAccessible(true);
        for (int state : new int[]{1, 2}) {
            byte[] host = "old\0extra".getBytes(StandardCharsets.UTF_8);
            byte[] packet = ByteArrayOps.concat(VarIntCodec.encode(0), VarIntCodec.encode(340), VarIntCodec.encode(host.length), host, new byte[]{0, 1}, VarIntCodec.encode(state));
            byte[] result = (byte[]) method.invoke(null, packet, "::1", 35565);
            var id = VarIntCodec.read(result, 0);
            var protocol = VarIntCodec.read(result, id.next());
            assertEquals(340, protocol.value());
            var length = VarIntCodec.read(result, protocol.next());
            String rewritten = new String(result, length.next(), length.value(), StandardCharsets.UTF_8);
            assertEquals(state == 1 ? "::1\0extra" : "::1\0extra\0FML\0", rewritten);
            int pos = length.next() + length.value();
            assertEquals(35565, ((result[pos] & 255) << 8) | (result[pos + 1] & 255));
            assertEquals(state, result[pos + 2]);
        }
    }
}
