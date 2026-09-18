package cn.tohsaka.factory.zstdnet.network;

import cn.tohsaka.factory.zstdnet.Zstdnet;
import cn.tohsaka.factory.zstdnet.server.ServerProxyBootstrap;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.*;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraft.util.text.TextComponentString;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class ControlNetwork {
    private static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel("zstdnet");
    private static final Map<UUID, Long> LAST_REQUEST = new HashMap<>();
    public static void init() {
        CHANNEL.registerMessage(RequestHandler.class, ReportRequest.class, 0, Side.SERVER);
        CHANNEL.registerMessage(ChunkHandler.class, ReportChunk.class, 1, Side.CLIENT);
    }
    public static void requestReport(int range) { CHANNEL.sendToServer(new ReportRequest(range)); }
    public static void clear() { LAST_REQUEST.clear(); }
    // Public no-arg constructors are required by the 1.12.2 FML codec.
    public static final class ReportRequest implements IMessage {
        int range;
        public ReportRequest() {}
        public ReportRequest(int range) { this.range = range; }
        @Override public void fromBytes(ByteBuf buf) {
            if (buf.readableBytes() != 1) throw new IllegalArgumentException("Invalid report request");
            range = buf.readUnsignedByte();
            if (range > 4) throw new IllegalArgumentException("Invalid range");
        }
        @Override public void toBytes(ByteBuf buf) { buf.writeByte(range); }
    }
    public static final class ReportChunk implements IMessage {
        int sequence, count;
        byte[] data;
        public ReportChunk() {}
        public ReportChunk(int sequence, int count, byte[] data) { this.sequence = sequence; this.count = count; this.data = data; }
        @Override public void fromBytes(ByteBuf buf) {
            if (buf.readableBytes() < 4 || buf.readableBytes() > ReportAssembly.CHUNK_BYTES + 4) throw new IllegalArgumentException("Invalid report chunk size");
            sequence = buf.readUnsignedShort(); count = buf.readUnsignedShort();
            if (count < 1 || count > 43 || sequence >= count) throw new IllegalArgumentException("Invalid report sequence");
            data = new byte[buf.readableBytes()]; buf.readBytes(data);
        }
        @Override public void toBytes(ByteBuf buf) { buf.writeShort(sequence); buf.writeShort(count); buf.writeBytes(data); }
    }
    public static final class RequestHandler implements IMessageHandler<ReportRequest, IMessage> {
        @Override public IMessage onMessage(ReportRequest message, MessageContext context) {
            var player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (!player.canUseCommand(4, "zstdnet-server")) { player.sendMessage(new TextComponentString("ZstdNet reports require administrator permission.")); return; }
                long now = System.currentTimeMillis();
                LAST_REQUEST.entrySet().removeIf(entry -> now - entry.getValue() >= 30_000);
                if (LAST_REQUEST.putIfAbsent(player.getUniqueID(), now) != null) return;
                String[] ranges = {"session", "today", "24h", "7d", "30d"};
                try {
                    byte[] bytes = ServerProxyBootstrap.report(ranges[message.range]).getBytes(StandardCharsets.UTF_8);
                    if (bytes.length > ReportAssembly.MAX_BYTES) throw new IllegalStateException("Report exceeds 1 MiB");
                    int count = (bytes.length + ReportAssembly.CHUNK_BYTES - 1) / ReportAssembly.CHUNK_BYTES;
                    for (int i = 0; i < count; i++) {
                        int start = i * ReportAssembly.CHUNK_BYTES;
                        CHANNEL.sendTo(new ReportChunk(i, count, Arrays.copyOfRange(bytes, start, Math.min(bytes.length, start + ReportAssembly.CHUNK_BYTES))), player);
                    }
                } catch (Exception e) { player.sendMessage(new TextComponentString("ZstdNet report failed: " + e.getMessage())); }
            });
            return null;
        }
    }
    public static final class ChunkHandler implements IMessageHandler<ReportChunk, IMessage> {
        @Override public IMessage onMessage(ReportChunk message, MessageContext context) {
            Zstdnet.proxy.receiveReport(message.sequence, message.count, message.data);
            return null;
        }
    }
}
