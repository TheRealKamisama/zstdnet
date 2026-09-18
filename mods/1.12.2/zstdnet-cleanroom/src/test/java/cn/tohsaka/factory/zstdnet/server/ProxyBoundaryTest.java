package cn.tohsaka.factory.zstdnet.server;
import cn.tohsaka.factory.zstdnet.platform.Platform;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class ProxyBoundaryTest {
    @TempDir Path directory;
    private void config(int port, String extra) throws IOException {
        Platform.initialize(directory, false);
        Files.createDirectories(directory.resolve("config"));
        Files.writeString(ServerProxyConfigFile.path(), "enabled=true\nauto_takeover=false\nlisten=127.0.0.1:" + port + "\ntarget=127.0.0.1:9\nvoice_chat_passthrough=false\n" + extra);
    }
    @Test void occupiedEntranceDoesNotStartAndCanBeReusedAfterRelease() throws Exception {
        ServerProxyRuntime runtime = new ServerProxyRuntime();
        int port;
        try (ServerSocket busy = new ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))) {
            port = busy.getLocalPort(); config(port, "");
            runtime.start(9); assertFalse(runtime.isRunning());
        }
        try { runtime.start(9); assertTrue(runtime.isRunning()); } finally { runtime.stop(); }
        try (ServerSocket released = new ServerSocket(port, 16, InetAddress.getByName("127.0.0.1"))) { assertTrue(released.isBound()); }
    }
    @Test void proxyV2RequiresValidHeaderAndExplicitTrustedPeer() throws Exception {
        int port;
        try (ServerSocket free = new ServerSocket(0)) { port = free.getLocalPort(); }
        config(port, "trust_proxy_protocol=true\ntrusted_proxy_ips=127.0.0.1\n");
        ServerProxyRuntime runtime = new ServerProxyRuntime();
        try {
            runtime.start(9); assertTrue(runtime.isRunning());
            byte[] frame = {13,10,13,10,0,13,10,81,85,73,84,10, 0x21,0x11,0,12, (byte)203,0,113,9, 127,0,0,1, 0x30,0x39,0x63,(byte)0xdd};
            var parser = ServerProxyRuntime.class.getDeclaredMethod("parseProxyProtocolV2", PushbackInputStream.class);
            parser.setAccessible(true);
            Object info = parser.invoke(runtime, new PushbackInputStream(new ByteArrayInputStream(frame), 256));
            var resolve = ServerProxyRuntime.class.getDeclaredMethod("resolveForwardedSourceIp", String.class, info.getClass());
            resolve.setAccessible(true);
            assertEquals("203.0.113.9", resolve.invoke(runtime, "127.0.0.1", info));
            assertNull(resolve.invoke(runtime, "198.51.100.8", info));
            frame[12] = 0x11; // version 1 with v2 framing is invalid
            Object bad = parser.invoke(runtime, new PushbackInputStream(new ByteArrayInputStream(frame), 256));
            assertNull(resolve.invoke(runtime, "127.0.0.1", bad));
        } finally { runtime.stop(); }
    }
}
