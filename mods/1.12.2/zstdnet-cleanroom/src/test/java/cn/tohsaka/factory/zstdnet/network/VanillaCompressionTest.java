package cn.tohsaka.factory.zstdnet.network;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.NettyCompressionDecoder;
import net.minecraft.network.NettyCompressionEncoder;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VanillaCompressionTest {
    @Test void actualMinecraftDecoderRejectsTheReportedSplitThreshold() {
        var encoder = new EmbeddedChannel(new NettyCompressionEncoder(256));
        var decoder = new EmbeddedChannel(new NettyCompressionDecoder(1048576));
        try {
            encoder.writeOutbound(Unpooled.wrappedBuffer(new byte[4096]));
            ByteBuf wire = encoder.readOutbound();
            var error = assertThrows(DecoderException.class, () -> decoder.writeInbound(wire));
            assertTrue(error.getMessage().contains("below server threshold"));
        } finally {
            // Failed decode leaves unread compressed bytes; do not decode those again in decodeLast.
            decoder.pipeline().remove(NettyCompressionDecoder.class);
            encoder.finishAndReleaseAll(); decoder.finishAndReleaseAll();
        }
    }
    @Test void actualMinecraftCodecAcceptsBothSidesOfTheSameThreshold() {
        for (int threshold : new int[]{256, 1048576}) {
            var encoder = new EmbeddedChannel(new NettyCompressionEncoder(threshold));
            var decoder = new EmbeddedChannel(new NettyCompressionDecoder(threshold));
            try {
                for (int size : new int[]{128, 256, 4096, 1048576}) {
                    byte[] input = new byte[size]; new java.util.Random(size).nextBytes(input);
                    encoder.writeOutbound(Unpooled.wrappedBuffer(input));
                    decoder.writeInbound((ByteBuf) encoder.readOutbound());
                    ByteBuf decoded = decoder.readInbound();
                    try { byte[] actual = new byte[decoded.readableBytes()]; decoded.readBytes(actual); assertArrayEquals(input, actual); }
                    finally { decoded.release(); }
                }
            } finally { encoder.finishAndReleaseAll(); decoder.finishAndReleaseAll(); }
        }
    }
}
