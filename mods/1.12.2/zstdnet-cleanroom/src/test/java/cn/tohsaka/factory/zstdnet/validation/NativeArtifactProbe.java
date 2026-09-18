package cn.tohsaka.factory.zstdnet.validation;
import com.github.luben.zstd.*;
import java.io.*;
import java.util.*;
public final class NativeArtifactProbe {
    public static void main(String[] args) throws Exception {
        byte[] raw = new byte[1024 * 1024 + 101];
        new Random(25).nextBytes(raw);
        var bytes = new ByteArrayOutputStream();
        try (var out = new ZstdOutputStream(bytes, 3)) { out.write(raw); }
        try (var in = new ZstdInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            if (!Arrays.equals(raw, in.readAllBytes())) throw new AssertionError("Native artifact roundtrip mismatch");
        }
        System.out.println("PACKAGED_JNI_OK " + System.getProperty("java.version") + " " + System.getProperty("os.name") + " " + System.getProperty("os.arch"));
    }
}
