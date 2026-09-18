package cn.tohsaka.factory.zstdnet.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrafficReportGeneratorTest {
    @TempDir
    Path tempDir;

    @Test
    void generatesSelfContainedLatestAndArchiveReports() throws Exception {
        String json = "{\"schemaVersion\":1,\"range\":\"today\",\"server\":{},\"selected\":{},\"today\":{},\"session\":{},\"current\":{},\"series\":[],\"daily\":[],\"runs\":[]}";

        TrafficReportGenerator.GeneratedReport generated = TrafficReportGenerator.generate(tempDir, json);

        assertTrue(Files.isRegularFile(generated.latest()));
        assertTrue(Files.isRegularFile(generated.archive()));
        String html = Files.readString(generated.latest(), StandardCharsets.UTF_8);
        assertTrue(html.contains("ZstdNet Analytics"));
        assertTrue(html.contains("\"schemaVersion\":1"));
        assertTrue(html.contains("data:image/png;base64,"));
        assertFalse(html.contains("/*__ZSTDNET_REPORT_JSON__*/"));
        assertFalse(html.contains("/*__ZSTDNET_LOGO__*/"));
        assertFalse(html.contains("/*__CURSEFORGE_ICON__*/"));
        assertFalse(html.contains("不会开启 HTTP 端口或上传数据"));
        assertFalse(html.contains("<script src="));
        assertFalse(html.contains("<link rel=\"stylesheet\""));
        assertEquals(html, Files.readString(generated.archive(), StandardCharsets.UTF_8));
    }

    @Test
    void keepsTenArchivedReportsPerServer() throws Exception {
        String json = "{\"schemaVersion\":1}";
        TrafficReportGenerator.GeneratedReport firstServer = TrafficReportGenerator.generate(
            tempDir,
            "play.example.com:25565",
            json
        );
        TrafficReportGenerator.GeneratedReport secondServer = TrafficReportGenerator.generate(
            tempDir,
            "backup.example.com:25565",
            json
        );

        assertFalse(firstServer.archive().getParent().equals(secondServer.archive().getParent()));
        for (int i = 0; i < 12; i++) {
            Files.writeString(
                firstServer.archive().getParent().resolve(String.format("zstdnet-report-20200101-0000%02d.html", i)),
                "old"
            );
        }

        TrafficReportGenerator.GeneratedReport newest = TrafficReportGenerator.generate(
            tempDir,
            "play.example.com:25565",
            json
        );

        assertEquals(10L, countArchives(newest.archive().getParent()));
        assertEquals(1L, countArchives(secondServer.archive().getParent()));
        assertTrue(Files.isRegularFile(newest.latest()));
        assertTrue(Files.isRegularFile(secondServer.latest()));
        assertFalse(Files.exists(newest.archive().getParent().resolve("zstdnet-report-20200101-000000.html")));
    }

    @Test
    void embedsTheClientConnectionAddressWithoutReplacingTheServerListener() throws Exception {
        String json = "{\"schemaVersion\":1,\"server\":{\"listen\":\"0.0.0.0:25565\"}}";

        TrafficReportGenerator.GeneratedReport generated = TrafficReportGenerator.generate(
            tempDir,
            "cdr4.齿轮盛宴.com",
            json
        );

        String html = Files.readString(generated.latest(), StandardCharsets.UTF_8);
        assertTrue(html.contains("\"connectedAddress\":\"cdr4.齿轮盛宴.com\""));
        assertTrue(html.contains("\"listen\":\"0.0.0.0:25565\""));
        assertTrue(html.contains("server.connectedAddress || server.listen"));
    }

    private static long countArchives(Path reportsDir) throws Exception {
        try (Stream<Path> files = Files.list(reportsDir)) {
            return files
                .filter(path -> path.getFileName().toString().startsWith("zstdnet-report-"))
                .filter(path -> path.getFileName().toString().endsWith(".html"))
                .count();
        }
    }
}
