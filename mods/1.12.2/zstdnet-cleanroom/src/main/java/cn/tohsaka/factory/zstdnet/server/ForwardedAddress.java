package cn.tohsaka.factory.zstdnet.server;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

/** Only this process's loopback proxy may assert an address. Strip client-supplied assertions. */
public final class ForwardedAddress {
    private static final String TOKEN = UUID.randomUUID().toString();
    private static final String PREFIX = "zstdnet-real-ip=";
    private static final String AUTH = "zstdnet-proxy=";
    private ForwardedAddress() {}
    public static String append(String host, String ip) {
        StringBuilder result = new StringBuilder();
        String[] parts = host.split("\0", -1);
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].startsWith(PREFIX) || parts[i].startsWith(AUTH)) continue;
            if (i > 0) result.append('\0');
            result.append(parts[i]);
        }
        return result + "\0" + PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(ip.getBytes(StandardCharsets.UTF_8)) + "\0" + AUTH + TOKEN;
    }
    public static InetSocketAddress parse(SocketAddress peer, String host) {
        if (!(peer instanceof InetSocketAddress socket) || socket.getAddress() == null || !socket.getAddress().isLoopbackAddress() || host == null) return null;
        String encoded = null;
        boolean trusted = false;
        for (String part : host.split("\0", -1)) {
            if (part.equals(AUTH + TOKEN)) trusted = true;
            if (part.startsWith(PREFIX)) { if (encoded != null) return null; encoded = part.substring(PREFIX.length()); }
        }
        if (!trusted || encoded == null || encoded.length() > 96) return null;
        try {
            String ip = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
            if (!ip.matches("[0-9a-fA-F:.]+") || (!ip.contains(":") && !ip.matches("[0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+"))) return null;
            return new InetSocketAddress(InetAddress.getByName(ip), socket.getPort());
        } catch (IllegalArgumentException | UnknownHostException ignored) { return null; }
    }
}
