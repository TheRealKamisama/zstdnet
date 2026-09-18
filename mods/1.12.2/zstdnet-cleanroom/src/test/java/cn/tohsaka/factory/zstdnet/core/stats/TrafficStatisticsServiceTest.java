package cn.tohsaka.factory.zstdnet.core.stats;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrafficStatisticsServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void aggregatesTcpAndUdpAndRecoversInterruptedRun() throws Exception {
        ZoneId zone = ZoneId.of("Asia/Hong_Kong");
        TrafficStats stats = new TrafficStats();
        TrafficStatisticsService service = new TrafficStatisticsService(tempDir, zone, stats);
        service.startSession();

        stats.addRawUp(4096L);
        stats.addRawDown(8192L);
        stats.addZstdUp(1024L);
        stats.addZstdDown(2048L);
        stats.addUdpIngress(128L);
        stats.addUdpEgress(256L);

        long reportAt = System.currentTimeMillis() + 31_000L;
        TrafficReport report = service.buildReport(TrafficReportRange.SESSION, "dedicated", "0.0.0.0:35565", reportAt);

        assertEquals(1024L, report.selected.tcpIngressBytes);
        assertEquals(2048L, report.selected.tcpEgressBytes);
        assertEquals(128L, report.selected.udpIngressBytes);
        assertEquals(256L, report.selected.udpEgressBytes);
        assertEquals(1152L, report.selected.wireIngressBytes);
        assertEquals(2304L, report.selected.wireEgressBytes);
        assertEquals(4096L, report.session.rawIngressBytes);
        assertEquals(8192L, report.session.rawEgressBytes);
        assertFalse(report.series.isEmpty());

        TrafficReport last24Hours = service.buildReport(
            TrafficReportRange.LAST_24_HOURS,
            "dedicated",
            "0.0.0.0:35565",
            reportAt
        );
        assertTrue(last24Hours.selected.durationSeconds >= 30L);
        assertTrue(last24Hours.selected.durationSeconds < 60L);
        assertEquals(37L, last24Hours.selected.averageIngressBytesPerSecond);
        assertEquals(74L, last24Hours.selected.averageEgressBytesPerSecond);
        assertEquals(1152L, last24Hours.selected.wireIngressBytes);
        assertEquals(2304L, last24Hours.selected.wireEgressBytes);
        assertEquals(last24Hours.selected.durationSeconds, last24Hours.today.durationSeconds);

        LocalDate date = java.time.Instant.ofEpochMilli(reportAt).atZone(zone).toLocalDate();
        assertTrue(Files.isRegularFile(tempDir.resolve("state-v1.json")));
        assertTrue(Files.isRegularFile(tempDir.resolve("daily").resolve(date + ".json")));
        assertTrue(Files.isRegularFile(tempDir.resolve("series").resolve(date + ".json")));

        TrafficStats recoveredStats = new TrafficStats();
        TrafficStatisticsService recovered = new TrafficStatisticsService(tempDir, zone, recoveredStats);
        recovered.startSession();
        try (var runs = Files.list(tempDir.resolve("runs"))) {
            assertEquals(1L, runs.filter(Files::isRegularFile).count());
        }
        recovered.stopSession();
    }
}
