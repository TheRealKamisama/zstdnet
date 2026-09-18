package cn.tohsaka.factory.zstdnet.validation;

import cn.tohsaka.factory.zstdnet.core.protocol.*;
import cn.tohsaka.factory.zstdnet.proxy.LocalZstdNet;
import com.google.gson.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.NettyCompressionDecoder;

/** Wire-level probe, not a game client: acknowledges registries but does not install them. */
public final class ProductionProbe {
    private final Socket socket;
    private int threshold = -1;
    private EmbeddedChannel decoder;
    private ProductionProbe(Socket socket) { this.socket = socket; }
    public static void main(String[] args) throws Exception {
        String host = args.length > 0 ? args[0] : "127.0.0.1";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 35586;
        JsonObject status;
        try (Socket socket = new Socket(host, port)) {
            socket.setSoTimeout(10000);
            var probe = new ProductionProbe(socket);
            probe.write(handshake(host, port, 1));
            probe.write(new byte[]{0});
            var packet = new ByteArrayInputStream(probe.read());
            if (VarIntCodec.read(packet) != 0) throw new IOException("Unexpected status response");
            status = JsonParser.parseString(string(packet)).getAsJsonObject();
            if (status.getAsJsonObject("version").get("protocol").getAsInt() != 340) throw new IOException("Expected protocol 340");
            byte[] ping = new byte[]{1, 0, 0, 0, 0, 0, 0, 0, 25};
            probe.write(ping);
            if (!Arrays.equals(ping, probe.read())) throw new IOException("Ping mismatch");
            System.out.println("PRODUCTION_STATUS_OK " + status);
        }
        for (int round = 1; round <= 3; round++) {
            boolean raw = Boolean.getBoolean("zstdnet.probeRaw");
            try (var proxy = raw ? null : LocalZstdNet.start(host, port, 3, LocalZstdNet.Mode.ZSTD);
                 Socket client = new Socket(raw ? host : "127.0.0.1", raw ? port : proxy.localPort())) {
                client.setSoTimeout(1000);
                var probe = new ProductionProbe(client);
                try { probe.login(host, port, status, round); }
                finally { if (probe.decoder != null) { probe.decoder.pipeline().remove(NettyCompressionDecoder.class); probe.decoder.finishAndReleaseAll(); } }
            }
            Thread.sleep(500);
        }
        System.out.println("PRODUCTION_PROBE_SUCCESS status, ping, FML wire handshake, chunks, three reconnects");
    }
    private void login(String host, int port, JsonObject status, int round) throws Exception {
        write(handshake(host, port, 2));
        write(ByteArrayOps.concat(new byte[]{0}, string("ZstdProbe")));
        boolean login = true, fmlDone = false, joined = false, reportSent = false, reportDone = false;
        boolean expectReport = Boolean.getBoolean("zstdnet.probeReportAllowed");
        if (Boolean.getBoolean("zstdnet.probeSkipReport")) { reportSent = true; reportDone = true; }
        if (expectReport && round > 1) { reportSent = true; reportDone = true; }
        var reports = new cn.tohsaka.factory.zstdnet.network.ReportAssembly();
        int registryCount = 0, ack = 4, chunks = 0;
        boolean pipelineRequested = false, pipelineChecked = !Boolean.getBoolean("zstdnet.probeCheckPipeline");
        long playStarted = 0;
        long deadline = System.currentTimeMillis() + 25000;
        while (System.currentTimeMillis() < deadline) {
            if (playStarted != 0 && reportDone && pipelineChecked && System.currentTimeMillis() - playStarted >= 5000) {
                int expected = Integer.getInteger("zstdnet.probeExpectedThreshold", threshold);
                if (threshold != expected) throw new IOException("Announcement mismatch: expected=" + expected + " actual=" + threshold);
                System.out.printf("PRODUCTION_FML_WIRE_OK round=%d registries=%d chunks=%d threshold=%d strictMinecraftDecoder=true sustainedPlayMillis=%d%n", round, registryCount, chunks, threshold, System.currentTimeMillis() - playStarted);
                return;
            }
            byte[] bytes;
            try { bytes = read(); } catch (SocketTimeoutException idle) { continue; }
            var packet = new ByteArrayInputStream(bytes);
            int id = VarIntCodec.read(packet);
            if (login) {
                if (id == 0) throw new IOException("Login rejected: " + string(packet));
                if (id == 1) throw new IOException("Encryption requires authenticated game client");
                if (id == 3) {
                    threshold = VarIntCodec.read(packet);
                    decoder = new EmbeddedChannel(new NettyCompressionDecoder(threshold));
                    System.out.println("LOGIN_COMPRESSION " + threshold);
                }
                if (id == 2) login = false;
                continue;
            }
            if (id == 0x1a) throw new IOException("Disconnected: " + string(packet));
            if (id == 0x23) joined = true;
            if (id == 0x20) chunks++;
            if (id == 0x0f) {
                String chat = chatText(JsonParser.parseString(string(packet)));
                if (chat.contains("ZSTDNET_COMPRESSION")) System.out.println("SERVER_COMPRESSION_DIAGNOSTIC " + chat);
                var match = java.util.regex.Pattern.compile("ZSTDNET_COMPRESSION expected=(-?\\d+) encoder=(-?\\d+) decoder=(-?\\d+) proxied=(true|false)").matcher(chat);
                if (match.find()) {
                    if (Integer.parseInt(match.group(1)) != threshold || Integer.parseInt(match.group(2)) != threshold || Integer.parseInt(match.group(3)) != threshold || Boolean.parseBoolean(match.group(4)) == Boolean.getBoolean("zstdnet.probeRaw")) throw new IOException("Actual server pipeline mismatch: " + match.group());
                    pipelineChecked = true;
                    System.out.println("SERVER_PIPELINE_MATCH " + match.group());
                }
                if (chat.contains("require administrator permission")) {
                    if (expectReport) throw new IOException("Authorized report was denied");
                    reportDone = true;
                    System.out.println("PRODUCTION_REPORT_PERMISSION_DENIED_OK");
                }
            }
            if (id == 0x1f) write(ByteArrayOps.concat(new byte[]{0x0b}, packet.readAllBytes()));
            if (id == 0x2f) {
                packet.skipNBytes(33);
                write(ByteArrayOps.concat(new byte[]{0}, VarIntCodec.encode(VarIntCodec.read(packet))));
            }
            if (id == 0x18) {
                String channel = string(packet);
                byte[] data = packet.readAllBytes();
                if (channel.equals("zstdnet") && data.length >= 5 && data[0] == 1) {
                    if (!expectReport) throw new IOException("Unauthorized report was disclosed");
                    int seq = ((data[1] & 255) << 8) | (data[2] & 255);
                    int count = ((data[3] & 255) << 8) | (data[4] & 255);
                    String json = reports.accept(seq, count, Arrays.copyOfRange(data, 5, data.length));
                    if (json != null) {
                        if (JsonParser.parseString(json).getAsJsonObject().get("schemaVersion").getAsInt() != 1) throw new IOException("Report schema");
                        reportDone = true;
                        System.out.println("PRODUCTION_REPORT_OK chunks=" + count + " bytes=" + json.getBytes(StandardCharsets.UTF_8).length);
                    }
                }
                if (channel.equals("FML|HS") && data.length > 0) {
                    int kind = data[0] & 255;
                    if (kind == 0) {
                        custom("REGISTER", "FML|HS\0FML\0FML|MP\0FORGE\0zstdnet".getBytes(StandardCharsets.UTF_8));
                        custom("FML|HS", new byte[]{1, 2});
                        ByteArrayOutputStream mods = new ByteArrayOutputStream();
                        mods.write(2);
                        JsonArray list = status.getAsJsonObject("modinfo").getAsJsonArray("modList");
                        mods.writeBytes(VarIntCodec.encode(list.size()));
                        for (JsonElement entry : list) {
                            mods.writeBytes(string(entry.getAsJsonObject().get("modid").getAsString()));
                            mods.writeBytes(string(entry.getAsJsonObject().get("version").getAsString()));
                        }
                        custom("FML|HS", mods.toByteArray());
                    } else if (kind == 2) custom("FML|HS", new byte[]{(byte) 255, 2});
                    else if (kind == 3) {
                        registryCount++;
                        if (data[1] == 0) custom("FML|HS", new byte[]{(byte) 255, 3});
                    } else if (kind == 255) {
                        custom("FML|HS", new byte[]{(byte) 255, (byte) ack});
                        fmlDone = ack++ == 5;
                    }
                }
            }
            if (joined && fmlDone && chunks > 0) {
                if (playStarted == 0) playStarted = System.currentTimeMillis();
                if (!pipelineRequested && !pipelineChecked) { write(ByteArrayOps.concat(new byte[]{2}, string("/zstdnet-server compression"))); pipelineRequested = true; }
                if (!reportSent) { custom("zstdnet", new byte[]{0, 4}); reportSent = true; }
            }
        }
        throw new IOException("Handshake deadline exceeded joined=" + joined + " fml=" + fmlDone + " chunks=" + chunks + " report=" + reportDone + " pipeline=" + pipelineChecked);
    }
    private static String chatText(JsonElement value) {
        if (value.isJsonPrimitive()) return value.getAsString();
        if (value.isJsonArray()) {
            StringBuilder text = new StringBuilder();
            for (JsonElement part : value.getAsJsonArray()) text.append(chatText(part));
            return text.toString();
        }
        if (!value.isJsonObject()) return "";
        JsonObject object = value.getAsJsonObject();
        return (object.has("text") ? object.get("text").getAsString() : "") + (object.has("extra") ? chatText(object.get("extra")) : "");
    }
    private void custom(String channel, byte[] data) throws IOException { write(ByteArrayOps.concat(new byte[]{9}, string(channel), data)); }
    private static byte[] handshake(String host, int port, int state) {
        return ByteArrayOps.concat(new byte[]{0}, VarIntCodec.encode(340), string(host + "\0FML\0"), new byte[]{(byte)(port >>> 8), (byte)port}, VarIntCodec.encode(state));
    }
    private static byte[] string(String value) { byte[] bytes = value.getBytes(StandardCharsets.UTF_8); return ByteArrayOps.concat(VarIntCodec.encode(bytes.length), bytes); }
    private static String string(InputStream in) throws IOException {
        int size = VarIntCodec.read(in);
        if (size < 0 || size > 2 * 1024 * 1024) throw new IOException("String size");
        return new String(PacketIo.readFully(in, size), StandardCharsets.UTF_8);
    }
    private byte[] read() throws IOException {
        // Only an idle wait may be retried; never discard a partially read frame.
        InputStream input = socket.getInputStream();
        int first = input.read();
        if (first < 0) throw new EOFException("Connection closed");
        int previousTimeout = socket.getSoTimeout();
        byte[] bytes;
        try {
            socket.setSoTimeout(10000);
            bytes = PacketIo.extractPacketPayload(PacketIo.readPacketWire(new SequenceInputStream(new ByteArrayInputStream(new byte[]{(byte) first}), input), 3 * 1024 * 1024));
        } catch (SocketTimeoutException partialFrame) {
            throw new IOException("Timed out within a packet", partialFrame);
        } finally { socket.setSoTimeout(previousTimeout); }
        if (threshold < 0) return bytes;
        decoder.writeInbound(Unpooled.wrappedBuffer(bytes));
        ByteBuf decoded = decoder.readInbound();
        if (decoded == null) throw new IOException("Incomplete compressed packet");
        try { byte[] result = new byte[decoded.readableBytes()]; decoded.readBytes(result); return result; }
        finally { decoded.release(); }
    }
    private void write(byte[] data) throws IOException {
        if (threshold >= 0) {
            var encoded = new ByteArrayOutputStream();
            if (data.length >= threshold) {
                encoded.writeBytes(VarIntCodec.encode(data.length));
                try (var deflater = new DeflaterOutputStream(encoded)) { deflater.write(data); }
            } else { encoded.write(0); encoded.writeBytes(data); }
            data = encoded.toByteArray();
        }
        PacketIo.writePacket(socket.getOutputStream(), data);
        socket.getOutputStream().flush();
    }
}
