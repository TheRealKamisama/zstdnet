package cn.tohsaka.factory.zstdnet.server;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import static org.junit.jupiter.api.Assertions.*;
class ForwardedAddressTest {
    @Test void onlyThisProcessLoopbackProxyCanAssertAddress() {
        var local = new InetSocketAddress("127.0.0.1", 1234);
        String host = ForwardedAddress.append("example\0FML\0\0zstdnet-real-ip=ZmFrZQ", "2001:db8::1");
        assertTrue(host.contains("\0FML\0"));
        assertNotNull(ForwardedAddress.parse(local, host));
        assertNull(ForwardedAddress.parse(new InetSocketAddress("192.0.2.10", 1234), host));
        assertNull(ForwardedAddress.parse(local, "example\0zstdnet-real-ip=MTI3LjAuMC4x"));
        assertNull(ForwardedAddress.parse(local, ForwardedAddress.append("example", "attacker.example")));
    }
}
