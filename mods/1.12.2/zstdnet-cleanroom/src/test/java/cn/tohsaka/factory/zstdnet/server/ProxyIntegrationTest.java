package cn.tohsaka.factory.zstdnet.server;

import cn.tohsaka.factory.zstdnet.core.protocol.*;
import cn.tohsaka.factory.zstdnet.platform.Platform;
import cn.tohsaka.factory.zstdnet.proxy.LocalZstdNet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Random;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class ProxyIntegrationTest {
    @TempDir Path directory;
    static byte[] handshake(int state) {
        byte[] host = "backend.test\0FML\0".getBytes(StandardCharsets.UTF_8);
        return ByteArrayOps.concat(VarIntCodec.encode(0), VarIntCodec.encode(340), VarIntCodec.encode(host.length), host, new byte[]{0x63, (byte) 0xdd}, VarIntCodec.encode(state));
    }
    @Test void nativeZstdBidirectionalLargePayloadReconnectAndRelease() throws Exception { roundtrip("127.0.0.1"); }
    @Test void ipv6ZstdBidirectionalLargePayloadReconnectAndRelease() throws Exception { roundtrip("::1"); }
    private void roundtrip(String host) throws Exception {
        Platform.initialize(directory, false);
        InetAddress loopback = InetAddress.getByName(host);
        try (ServerSocket backend = new ServerSocket(0, 16, loopback)) {
            int entrance;
            try (ServerSocket reservation = new ServerSocket(0, 16, loopback)) { entrance = reservation.getLocalPort(); }
            Files.createDirectories(directory.resolve("config"));
            Files.writeString(ServerProxyConfigFile.path(), "enabled=true\nauto_takeover=false\nlisten=[" + host + "]:" + entrance + "\ntarget=[" + host + "]:" + backend.getLocalPort() + "\nlevel=3\nvoice_chat_passthrough=false\n");
            backend.setSoTimeout(10000);
            byte[] payload = new byte[2 * 1024 * 1024 + 317];
            new Random(25).nextBytes(payload);
            var executor = Executors.newSingleThreadExecutor();
            Future<?> echo = executor.submit(() -> {
                try {
                    for (int i = 0; i < 3; i++) {
                        try (Socket connection = backend.accept()) {
                            connection.setSoTimeout(10000);
                            byte[] packet = PacketIo.readPacket(connection.getInputStream());
                            String content = new String(packet, StandardCharsets.UTF_8);
                            assertTrue(content.contains("\0FML\0"));
                            assertFalse(content.contains("FML2"));
                            assertTrue(content.contains("zstdnet-real-ip="));
                            byte[] received = connection.getInputStream().readNBytes(payload.length);
                            assertArrayEquals(payload, received);
                            connection.getOutputStream().write(received);
                            connection.getOutputStream().flush();
                            // Keep alive until the client closes; no EOF shortcut for framing.
                            connection.getInputStream().read();
                        }
                    }
                } catch (IOException e) { throw new UncheckedIOException(e); }
            });
            ServerProxyRuntime server = new ServerProxyRuntime();
            server.start(backend.getLocalPort());
            assertTrue(server.isRunning());
            int localPort = 0;
            try {
                for (int i = 0; i < 3; i++) {
                    try (var local = LocalZstdNet.start(host, entrance, "backend.test", backend.getLocalPort(), 3, LocalZstdNet.Mode.ZSTD);
                         Socket client = new Socket("127.0.0.1", local.localPort())) {
                        localPort = local.localPort();
                        client.setSoTimeout(10000);
                        PacketIo.writePacket(client.getOutputStream(), handshake(2));
                        client.getOutputStream().write(payload);
                        client.getOutputStream().flush();
                        assertArrayEquals(payload, client.getInputStream().readNBytes(payload.length));
                    }
                }
                echo.get(15, TimeUnit.SECONDS);
            } finally { server.stop(); executor.shutdownNow(); }
            assertFalse(server.isRunning());
            try (ServerSocket rebound = new ServerSocket(entrance, 16, loopback);
                 ServerSocket reboundClient = new ServerSocket(localPort, 16, InetAddress.getByName("127.0.0.1"))) { assertTrue(rebound.isBound() && reboundClient.isBound()); }
        }
    }
}
