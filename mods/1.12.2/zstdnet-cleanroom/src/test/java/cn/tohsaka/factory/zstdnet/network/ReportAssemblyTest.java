package cn.tohsaka.factory.zstdnet.network;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;
class ReportAssemblyTest {
    @Test void reassemblesUtf8AcrossChunkBoundaries() {
        byte[] bytes = "测试报告".getBytes(StandardCharsets.UTF_8);
        ReportAssembly assembly = new ReportAssembly();
        assertNull(assembly.accept(0, 2, java.util.Arrays.copyOfRange(bytes, 0, 4)));
        assertEquals("测试报告", assembly.accept(1, 2, java.util.Arrays.copyOfRange(bytes, 4, bytes.length)));
    }
    @Test void rejectsOutOfOrderAndOversizedResponses() {
        ReportAssembly assembly = new ReportAssembly();
        assertThrows(IllegalArgumentException.class, () -> assembly.accept(1, 2, new byte[1]));
        assertThrows(IllegalArgumentException.class, () -> assembly.accept(0, 44, new byte[1]));
        assertThrows(IllegalArgumentException.class, () -> assembly.accept(0, 1, new byte[ReportAssembly.CHUNK_BYTES + 1]));
    }
}
