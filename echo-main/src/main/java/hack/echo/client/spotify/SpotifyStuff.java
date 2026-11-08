package hack.echo.client.spotify;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import hack.echo.client.Echo;
import hack.echo.client.api.ChatCompat;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public class SpotifyStuff {

    private static final String CLIENT_ID = "4e414ad028fb43b5a4590082f9d20d41";
    private static final String REDIRECT_URI = "http://127.0.0.1:8080";

    private final SecureRandom rand = new SecureRandom();
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final String codeVerifier;
    private final AtomicBoolean sentAuthRequest = new AtomicBoolean(false);
    private final AtomicBoolean sentRefreshRequest = new AtomicBoolean(false);
    private String refreshToken;

    public SpotifyStuff() {
        this.codeVerifier = generateCodeVerifier();
    }

    private String generateCodeVerifier() {
        byte[] bytes = new byte[96];
        rand.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String generateCodeChallenge(String verifier) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(verifier.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hashed);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    @FunctionalInterface
    interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    static <T> CompletableFuture<T> withFlags(AtomicBoolean flag, T fallback, ThrowingSupplier<T> task) {
        if (!flag.compareAndSet(false, true)) return CompletableFuture.completedFuture(fallback);
        return CompletableFuture.supplyAsync(() -> {
            try {
                return task.get();
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            } finally {
                flag.set(false);
            }
        });
    }

    public CompletableFuture<String> getRefreshToken() {
        return withFlags(sentAuthRequest, refreshToken, () -> {
            String challenge = generateCodeChallenge(codeVerifier);
            String authUrl = "https://accounts.spotify.com/authorize?" +
                    "client_id=" + CLIENT_ID +
                    "&response_type=code" +
                    "&redirect_uri=" + URLEncoder.encode(REDIRECT_URI, StandardCharsets.UTF_8) +
                    "&scope=" + URLEncoder.encode("user-read-currently-playing user-read-playback-state", StandardCharsets.UTF_8) +
                    "&code_challenge_method=S256" +
                    "&code_challenge=" + challenge;

            var uri = new URI(authUrl);
            var style = Style.EMPTY.withClickEvent(new ClickEvent.OpenUrl(uri));
            ChatCompat.addMessage(Component.literal("Click here to authenticate").setStyle(style));

            String code = null;
            try (ServerSocket server = new ServerSocket(8080)) {
                server.setSoTimeout(120_000);
                while (code == null) {
                    try (Socket client = server.accept()) {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream()));
                        String requestLine = reader.readLine();
                        if (requestLine == null || !requestLine.contains("code=")) continue;
                        code = requestLine.split("code=")[1].split("[ &]")[0];
                        client.getOutputStream().write("""
                                HTTP/1.1 200 OK\r
                                Content-Type: text/html\r
                                \r
                                Authorized! You can close this tab.
                                """.getBytes());
                    }
                }
            }

            JsonObject tokens = postToken(
                    "grant_type=authorization_code" +
                    "&code=" + encode(code) +
                    "&redirect_uri=" + URLEncoder.encode(REDIRECT_URI, StandardCharsets.UTF_8) +
                    "&client_id=" + CLIENT_ID +
                    "&code_verifier=" + codeVerifier
            );

            this.refreshToken = tokens.get("refresh_token").getAsString();
            return this.refreshToken;
        });
    }

    public CompletableFuture<ApiKey> getAccessToken() {
        if (refreshToken == null) return CompletableFuture.completedFuture(null);
        return withFlags(sentRefreshRequest, null, () -> {
            JsonObject tokens = postToken(
                    "grant_type=refresh_token" +
                    "&refresh_token=" + encode(refreshToken) +
                    "&client_id=" + CLIENT_ID
            );

            if (tokens.has("refresh_token")) {
                this.refreshToken = tokens.get("refresh_token").getAsString();
            }

            return new ApiKey(
                    tokens.get("access_token").getAsString(),
                    tokens.get("expires_in").getAsLong() * 1000L
            );
        });
    }

    static final class ApiResponse {
        final int code;
        final JsonObject json;
        final String error;

        ApiResponse(int code, JsonObject json) {
            this(code, json, null);
        }

        ApiResponse(int code, JsonObject json, String error) {
            this.code = code;
            this.json = json;
            this.error = error;
        }
    }

    CompletableFuture<ApiResponse> get(String url, String accessToken) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Authorization", "Bearer " + accessToken)
                        .GET()
                        .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                int code = response.statusCode();
                String body = response.body();
                if (code != 200) {
                    if (code != 204 && code != 401) {
                        Echo.LOGGER.warn("Spotify API returned status {}: {}", code, body.length() > 200 ? body.substring(0, 200) : body);
                    }
                    return new ApiResponse(code, null, extractError(body));
                }
                if (body.isBlank()) return new ApiResponse(204, null);
                try {
                    return new ApiResponse(200, JsonParser.parseString(body).getAsJsonObject());
                } catch (JsonSyntaxException e) {
                    Echo.LOGGER.warn("Spotify API returned malformed JSON: {}...", body.length() > 200 ? body.substring(0, 200) : body);
                    return new ApiResponse(code, null);
                }
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        });
    }

    private static String extractError(String body) {
        if (body == null || body.isBlank()) return null;
        try {
            JsonObject obj = JsonParser.parseString(body).getAsJsonObject();
            if (obj.has("error")) {
                var error = obj.get("error");
                if (error.isJsonObject() && error.getAsJsonObject().has("message")) {
                    return error.getAsJsonObject().get("message").getAsString();
                }
                if (error.isJsonPrimitive()) {
                    return error.getAsString();
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    void sendPlaybackCommand(String action, boolean usePost, String token) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("https://api.spotify.com/v1/me/player/" + action))
                .header("Authorization", "Bearer " + token);
            if (usePost) {
                builder.POST(HttpRequest.BodyPublishers.noBody());
            } else {
                builder.PUT(HttpRequest.BodyPublishers.noBody());
            }
            httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (Exception ignored) {}
    }

    private JsonObject postToken(String formBody) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://accounts.spotify.com/api/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formBody))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }
}
