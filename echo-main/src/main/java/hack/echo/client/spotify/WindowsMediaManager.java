package hack.echo.client.spotify;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import hack.echo.client.render2.api.CrossTexture;
import lombok.Getter;
import net.minecraft.client.Minecraft;

import java.io.*;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WindowsMediaManager {

    private static final WindowsMediaManager INSTANCE = new WindowsMediaManager();
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Echo Windows Media Reader");
        t.setDaemon(true);
        return t;
    });
    private static final ExecutorService ARTWORK_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Echo Windows Artwork Search");
        t.setDaemon(true);
        return t;
    });

    public record MediaStatus(String title, String artist, double positionSeconds, double durationSeconds, String artworkPath, boolean isPlaying, int volumePercent, String sourceApp) {
        public static final MediaStatus EMPTY = new MediaStatus("", "", 0.0, 0.0, "", false, 100, "");

        public boolean hasMedia() { return !title.isBlank() || !artist.isBlank(); }

        public float progress() {
            if (durationSeconds <= 0.0) return 0.0f;
            return (float) Math.max(0.0, Math.min(1.0, positionSeconds / durationSeconds));
        }
    }

    @Getter
    private volatile MediaStatus currentStatus = MediaStatus.EMPTY;
    @Getter
    private volatile CrossTexture artwork;
    private volatile boolean running = false;
    private volatile boolean bridgeReady = false;
    private Process bridgeProcess;
    private OutputStream bridgeStdin;
    private Path bridgeExePath;
    private String loadedArtworkPath = "";
    private String searchedArtworkKey = "";

    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    public static WindowsMediaManager getInstance() { return INSTANCE; }

    public boolean isConfigured() { return bridgeReady; }

    public String getBridgePath() {
        return Path.of(Minecraft.getInstance().gameDirectory.getAbsolutePath(), "config", "SMTCBridge.exe").toString();
    }

    public boolean isBridgePresent() {
        return Files.exists(Path.of(getBridgePath()));
    }

    public void start() {
        if (running) return;
        running = true;

        Path exePath = Path.of(getBridgePath());
        if (!Files.exists(exePath)) {
            System.out.println("[WindowsMedia] SMTCBridge.exe not found at " + exePath);
            running = false;
            return;
        }
        bridgeExePath = exePath;

        EXECUTOR.execute(() -> {
            try {
                launchBridge();
            } catch (Exception e) {
                System.err.println("[WindowsMedia] Failed to start bridge: " + e.getMessage());
                e.printStackTrace();
                running = false;
            }
        });
    }

    public void stop() {
        running = false;
        bridgeReady = false;
        if (bridgeProcess != null) {
            try { bridgeProcess.destroyForcibly(); } catch (Exception ignored) { }
            bridgeProcess = null;
        }
    }

    private void launchBridge() {
        try {
            String artworkPath = Path.of(Minecraft.getInstance().gameDirectory.getAbsolutePath(), "config", "windows-media-art.png").toString();
            ProcessBuilder pb = new ProcessBuilder(bridgeExePath.toString(), artworkPath);
            pb.redirectErrorStream(false);
            bridgeProcess = pb.start();
            bridgeStdin = bridgeProcess.getOutputStream();
            bridgeReady = true;
            System.out.println("[WindowsMedia] Bridge started (PID " + bridgeProcess.pid() + ")");

            Thread stderrThread = new Thread(() -> {
                try (BufferedReader err = new BufferedReader(new InputStreamReader(bridgeProcess.getErrorStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = err.readLine()) != null) System.err.println("[SMTCBridge/err] " + line);
                } catch (Exception ignored) { }
            }, "Echo Windows Media Stderr");
            stderrThread.setDaemon(true);
            stderrThread.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(bridgeProcess.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while (running && (line = reader.readLine()) != null) {
                    try { parseStatusLine(line); } catch (Exception e) {
                        System.err.println("[WindowsMedia] Parse error: " + e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[WindowsMedia] Bridge error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            bridgeReady = false;
            if (running) {
                try { Thread.sleep(2000); } catch (InterruptedException ignored) { }
                if (running) launchBridge();
            }
        }
    }

    private void parseStatusLine(String json) {
        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
        String title = obj.has("Title") && !obj.get("Title").isJsonNull() ? obj.get("Title").getAsString() : "";
        String artist = obj.has("Artist") && !obj.get("Artist").isJsonNull() ? obj.get("Artist").getAsString() : "";
        boolean isPlaying = obj.has("IsPlaying") && obj.get("IsPlaying").getAsBoolean();
        double pos = obj.has("PositionSeconds") ? obj.get("PositionSeconds").getAsDouble() : 0.0;
        double dur = obj.has("DurationSeconds") ? obj.get("DurationSeconds").getAsDouble() : 0.0;
        int vol = obj.has("VolumePercent") ? obj.get("VolumePercent").getAsInt() : 100;
        String art = obj.has("ArtworkPath") && !obj.get("ArtworkPath").isJsonNull() ? obj.get("ArtworkPath").getAsString() : "";
        String sourceApp = obj.has("SourceApp") && !obj.get("SourceApp").isJsonNull() ? obj.get("SourceApp").getAsString() : "";
        currentStatus = new MediaStatus(title, artist, pos, dur, art, isPlaying, vol, sourceApp);
        loadArtwork(art, title, artist);
    }

    private void loadArtwork(String path, String title, String artist) {
        if (path != null && !path.isBlank()) {
            if (path.equals(loadedArtworkPath)) return;
            loadedArtworkPath = path;
            try {
                byte[] bytes = Files.readAllBytes(Path.of(path));
                artwork = CrossTexture.from(bytes);
                if (artwork != null) return;
            } catch (Exception e) {
                System.err.println("[WindowsMedia] Failed to load artwork: " + e.getMessage());
            }
            artwork = null;
            return;
        }
        if (loadedArtworkPath.isEmpty()) searchArtworkOnline(title, artist);
    }

    private void searchArtworkOnline(String title, String artist) {
        if (title == null || title.isBlank()) return;
        String key = (title + "|" + artist).toLowerCase();
        if (key.equals(searchedArtworkKey)) return;
        searchedArtworkKey = key;
        ARTWORK_EXECUTOR.execute(() -> {
            try {
                String query = URLEncoder.encode((artist + " " + title).trim(), StandardCharsets.UTF_8);
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("https://api.deezer.com/search?q=" + query + "&limit=1"))
                        .header("User-Agent", "Echo/1.0")
                        .GET()
                        .build();
                HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) return;
                JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                JsonElement data = json.get("data");
                if (data == null || !data.isJsonArray() || data.getAsJsonArray().size() == 0) return;
                JsonObject first = data.getAsJsonArray().get(0).getAsJsonObject();
                JsonObject album = first.getAsJsonObject("album");
                if (album == null) return;
                String coverUrl = album.has("cover_medium") ? album.get("cover_medium").getAsString()
                        : album.has("cover_big") ? album.get("cover_big").getAsString()
                        : album.has("cover") ? album.get("cover").getAsString() : "";
                if (coverUrl == null || coverUrl.isBlank()) return;
                HttpRequest imgRequest = HttpRequest.newBuilder()
                        .uri(URI.create(coverUrl))
                        .header("User-Agent", "Echo/1.0")
                        .GET()
                        .build();
                byte[] bytes = HTTP_CLIENT.send(imgRequest, HttpResponse.BodyHandlers.ofByteArray()).body();
                artwork = CrossTexture.from(bytes);
            } catch (Exception e) {
                System.err.println("[WindowsMedia] Online artwork failed: " + e.getMessage());
            }
        });
    }

    private void sendCommand(String command) {
        if (!bridgeReady || bridgeStdin == null) return;
        try {
            bridgeStdin.write((command + "\n").getBytes(StandardCharsets.UTF_8));
            bridgeStdin.flush();
        } catch (Exception e) {
            System.err.println("[WindowsMedia] Failed to send command: " + command);
        }
    }

    public static String friendlyAppName(String aumid) {
        if (aumid == null || aumid.isBlank()) return "";
        String name = aumid.trim();
        if (name.endsWith(".exe")) name = name.substring(0, name.length() - 4);
        int lastDot = name.lastIndexOf('.');
        if (lastDot >= 0 && lastDot < name.length() - 1) name = name.substring(lastDot + 1);
        return name;
    }

    public void togglePlayPause() { sendCommand("playpause"); }
    public void next() { sendCommand("next"); }
    public void previous() { sendCommand("previous"); }

    private long lastVolumeCmd = 0L;
    public void setVolume(int percent) {
        long now = System.currentTimeMillis();
        if (now - lastVolumeCmd > 100L) {
            lastVolumeCmd = now;
            sendCommand("volume " + Math.max(0, Math.min(100, percent)));
        }
    }
}
