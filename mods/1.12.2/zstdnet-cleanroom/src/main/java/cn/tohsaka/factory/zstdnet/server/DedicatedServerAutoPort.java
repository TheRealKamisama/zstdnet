package cn.tohsaka.factory.zstdnet.server;

import java.io.IOException;
import java.io.Reader;
import java.net.*;
import java.nio.file.Files;
import java.util.Properties;

/** Plan is made immediately before the backend bind. Public server.properties is never rewritten. */
public final class DedicatedServerAutoPort {
    private static volatile AutoPortPlan plan;
    public static AutoPortPlan activePlan() { return plan; }
    public static InetSocketAddress prepare(InetAddress address, int port) throws IOException {
        plan = null;
        Properties props = new Properties();
        if (Files.exists(ServerProxyConfigFile.path())) {
            try (Reader reader = Files.newBufferedReader(ServerProxyConfigFile.path())) { props.load(reader); }
        }
        if (!Boolean.parseBoolean(props.getProperty("enabled", "true")) ||
            !Boolean.parseBoolean(props.getProperty("auto_takeover", "false"))) return new InetSocketAddress(address, port);
        String publicHost = address == null ? "0.0.0.0" : address.getHostAddress();
        int targetPort = ServerProxyConfigFile.readTargetPort();
        if (targetPort == port) targetPort = 0;
        // A port reservation cannot be handed to Netty; a failed bind remains a startup error.
        try (ServerSocket candidate = new ServerSocket()) {
            candidate.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), targetPort));
            targetPort = candidate.getLocalPort();
        }
        plan = new AutoPortPlan(publicHost, port, "127.0.0.1", targetPort);
        return new InetSocketAddress("127.0.0.1", targetPort);
    }
    public record AutoPortPlan(String listenHost, int listenPort, String targetHost, int targetPort) {}
}
