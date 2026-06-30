package cn.tohsaka.factory.zstdnet.core.protocol;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

public final class PacketIo {
    private PacketIo() {
    }

    public static byte[] readPacket(InputStream in) throws IOException {
        int length = VarIntCodec.read(in);
        if (length <= 0) {
            return new byte[0];
        }
        return readFully(in, length);
    }

    public static void writePacket(OutputStream out, byte[] payload) throws IOException {
        out.write(VarIntCodec.encode(payload.length));
        if (payload.length > 0) {
            out.write(payload);
        }
    }

    public static byte[] readPacketWire(InputStream in, int maxPayloadLength) throws IOException {
        byte[] prefix = new byte[5];
        int prefixLength = 0;
        while (prefixLength < prefix.length) {
            int next = in.read();
            if (next < 0) {
                throw new EOFException("eof during packet length");
            }

            prefix[prefixLength++] = (byte) next;
            VarIntRead packetLength = VarIntCodec.read(prefix, 0, prefixLength);
            if (packetLength == null) {
                continue;
            }

            if (packetLength.value() < 0 || packetLength.value() > maxPayloadLength) {
                throw new IOException("packet payload too large: " + packetLength.value());
            }

            byte[] payload = readFully(in, packetLength.value());
            byte[] packet = new byte[prefixLength + payload.length];
            System.arraycopy(prefix, 0, packet, 0, prefixLength);
            System.arraycopy(payload, 0, packet, prefixLength, payload.length);
            return packet;
        }

        throw new IOException("packet length varint too large");
    }

    public static byte[] readFully(InputStream in, int length) throws IOException {
        byte[] data = new byte[length];
        int offset = 0;
        while (offset < length) {
            int read = in.read(data, offset, length - offset);
            if (read < 0) {
                throw new EOFException("unexpected eof");
            }
            offset += read;
        }
        return data;
    }

    public static byte[] extractPacketPayload(byte[] packetWire) throws IOException {
        VarIntRead packetLength = VarIntCodec.read(packetWire, 0, packetWire.length);
        if (packetLength == null || packetLength.value() < 0 || packetLength.next() + packetLength.value() > packetWire.length) {
            throw new IOException("invalid packet payload");
        }
        return Arrays.copyOfRange(packetWire, packetLength.next(), packetLength.next() + packetLength.value());
    }
}
