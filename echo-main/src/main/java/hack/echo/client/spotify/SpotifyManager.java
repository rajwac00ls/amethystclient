package hack.echo.client.spotify;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.Getter;

import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicBoolean;

public class SpotifyManager {

    private String refreshToken;
    private ApiKey accessToken;
    @Getter
    volatile Song currentSong;
    @Getter
    volatile String statusMessage = "";

    private final SpotifyStuff auth = new SpotifyStuff();
    private final AtomicBoolean sentSongRequest = new AtomicBoolean(false);
    private final AtomicBoolean authInProgress = new AtomicBoolean(false);
    private final AtomicBoolean tokenRefreshInProgress = new AtomicBoolean(false);

    private void ensureKey() {
        if (refreshToken == null) {
            if (!authInProgress.compareAndSet(false, true)) return;
            statusMessage = "Authenticating...";
            auth.getRefreshToken().thenAccept(token -> {
                authInProgress.set(false);
                if (token == null) {
                    statusMessage = "Auth failed (port 8080 in use?)";
                    return;
                }
                this.refreshToken = token;
                statusMessage = "Getting access token...";
                auth.getAccessToken().thenAccept(key -> {
                    if (key != null) {
                        this.accessToken = key;
                        statusMessage = "";
                    } else {
                        statusMessage = "Failed to get access token";
                    }
                });
            });
            return;
        }

        if (accessToken == null || accessToken.expiresAt <= System.currentTimeMillis()) {
            if (!tokenRefreshInProgress.compareAndSet(false, true)) return;
            statusMessage = "Refreshing token...";
            auth.getAccessToken().thenAccept(key -> {
                tokenRefreshInProgress.set(false);
                if (key != null) {
                    this.accessToken = key;
                    statusMessage = "";
                } else {
                    statusMessage = "Token refresh failed";
                }
            });
        }
    }

    public void sendPlaybackCommand(String action, boolean usePost) {
        if (accessToken == null) return;
        auth.sendPlaybackCommand(action, usePost, accessToken.key);
    }

    public void updateCurrentSong() {
        ensureKey();
        if (accessToken == null) return;

        SpotifyStuff.withFlags(sentSongRequest, null, () -> {
            var resp = auth.get("https://api.spotify.com/v1/me/player/currently-playing", accessToken.key).join();
            if (resp == null) {
                statusMessage = "Network error";
                return null;
            }
            if (resp.code == 204) {
                if (currentSong != null) {
                    statusMessage = "";
                } else {
                    statusMessage = "Nothing playing";
                }
                return null;
            }
            if (resp.code == 401) {
                accessToken = null;
                statusMessage = "Token expired, refreshing...";
                return null;
            }
            if (resp.code != 200) {
                statusMessage = "API error (" + resp.code + ")"
                        + (resp.error != null && !resp.error.isBlank() ? ": " + resp.error : "");
                return null;
            }

            JsonObject json = resp.json;
            JsonElement itemEl = json.get("item");
            if (itemEl == null || itemEl.isJsonNull()) {
                if (currentSong != null) {
                    statusMessage = "";
                } else {
                    statusMessage = "No song data from API";
                }
                return null;
            }
            JsonObject item = itemEl.getAsJsonObject();

            statusMessage = "";

            String id = item.get("id").getAsString();
            long progressMs = json.get("progress_ms").getAsLong();
            boolean isPlaying = json.get("is_playing").getAsBoolean();

            if (currentSong == null || !currentSong.id.equals(id)) {
                var artistsArray = item.getAsJsonArray("artists");
                var artists = new StringJoiner(", ");
                for (var artist : artistsArray) {
                    artists.add(artist.getAsJsonObject().get("name").getAsString());
                }
                var images = item.getAsJsonObject("album").getAsJsonArray("images");
                String coverUrl = images.get(0).getAsJsonObject().get("url").getAsString();
                currentSong = new Song(
                        id,
                        item.get("name").getAsString(),
                        artists.toString(),
                        item.getAsJsonObject("album").get("name").getAsString(),
                        item.get("duration_ms").getAsLong(),
                        coverUrl
                );
            }

            currentSong.progressMs = progressMs;
            currentSong.isPlaying = isPlaying;
            currentSong.initTimeMs = System.currentTimeMillis();
            return null;
        });
    }
}
