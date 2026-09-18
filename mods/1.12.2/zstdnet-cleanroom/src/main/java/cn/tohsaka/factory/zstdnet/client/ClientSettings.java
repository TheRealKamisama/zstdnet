package cn.tohsaka.factory.zstdnet.client;
import cn.tohsaka.factory.zstdnet.platform.Platform;
import java.nio.file.*;
import java.util.Properties;

final class ClientSettings {
    static String target = "localhost:35565";
    static int level = 3;
    static boolean hud = true;
    static boolean interceptMultiplayer = true;
    static void load() {
        Path path = Platform.gameDir().resolve("config/zstdnet-client.properties");
        if (!Files.isRegularFile(path)) return;
        try (var reader = Files.newBufferedReader(path)) {
            Properties props = new Properties(); props.load(reader);
            target = props.getProperty("target", target);
            level = Math.clamp(Integer.parseInt(props.getProperty("level", "3")), 1, 22);
            hud = Boolean.parseBoolean(props.getProperty("hud", "true"));
            interceptMultiplayer = Boolean.parseBoolean(props.getProperty("intercept_multiplayer", "true"));
        } catch (Exception e) { org.slf4j.LoggerFactory.getLogger(ClientSettings.class).warn("Cannot read client config", e); }
    }
    static void save() {
        Path path = Platform.gameDir().resolve("config/zstdnet-client.properties");
        Properties props = new Properties();
        props.setProperty("target", target); props.setProperty("level", Integer.toString(level)); props.setProperty("hud", Boolean.toString(hud));
        props.setProperty("intercept_multiplayer", Boolean.toString(interceptMultiplayer));
        try {
            Files.createDirectories(path.getParent());
            try (var writer = Files.newBufferedWriter(path)) { props.store(writer, "ZstdNet Cleanroom client"); }
        } catch (Exception e) { org.slf4j.LoggerFactory.getLogger(ClientSettings.class).warn("Cannot save client config", e); }
    }
}
