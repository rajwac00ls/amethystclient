package hack.echo.client.auth;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import hack.echo.client.mixin.accessors.MinecraftAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

public final class TokenSession {

    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final String PROFILE_URL = "https://api.minecraftservices.com/minecraft/profile";
    private static final String XBL_URL = "https://user.auth.xboxlive.com/user/authenticate";
    private static final String XSTS_URL = "https://xsts.auth.xboxlive.com/xsts/authorize";
    private static final String MC_LOGIN_URL = "https://api.minecraftservices.com/authentication/login_with_xbox";

    private static volatile User originalUser;

    private TokenSession() {
    }

    /**
     * Tries to use the token directly as a Minecraft bearer token first, then falls
     * back to treating it as a Microsoft OAuth token and converting it via the
     * XBL -> XSTS -> Minecraft login flow.
     */
    public static void login(String token, Consumer<String> status) {
        new Thread(() -> {
            try {
                User user = profileFromToken(token);
                if (user != null) {
                    apply(user, status);
                    return;
                }

                String mcToken = microsoftToMinecraft(token);
                if (mcToken == null) {
                    status.accept("token rejected (not a valid Minecraft or Microsoft token)");
                    return;
                }

                User mcUser = profileFromToken(mcToken);
                if (mcUser == null) {
                    status.accept("token rejected (Microsoft login ok, but no Minecraft profile on this account)");
                    return;
                }

                apply(mcUser, status);
            } catch (Exception e) {
                status.accept("login failed: " + e.getMessage());
            }
        }).start();
    }

    public static void apply(User user) {
        apply(user, null);
    }

    private static void apply(User user, Consumer<String> status) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            if (originalUser == null) {
                originalUser = minecraft.getUser();
            }
            ((MinecraftAccessor) minecraft).setUser(user);
            if (status != null) {
                status.accept("logged in as " + user.getName());
            }
        });
    }

    public static void revert() {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            if (originalUser == null) {
                originalUser = minecraft.getUser();
            }
            ((MinecraftAccessor) minecraft).setUser(originalUser);
        });
    }

    private static User profileFromToken(String token) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(PROFILE_URL))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            return null;
        }
        JsonObject profile = JsonParser.parseString(response.body()).getAsJsonObject();
        String name = profile.get("name").getAsString();
        UUID uuid = parseUuid(profile.get("id").getAsString());
        return new User(name, uuid, token, Optional.empty(), Optional.empty());
    }

    private static String microsoftToMinecraft(String msToken) throws Exception {
        JsonObject xblProperties = new JsonObject();
        xblProperties.addProperty("AuthMethod", "RPS");
        xblProperties.addProperty("SiteName", "user.auth.xboxlive.com");
        xblProperties.addProperty("RpsTicket", "d=" + msToken);
        JsonObject xblBody = new JsonObject();
        xblBody.add("Properties", xblProperties);
        xblBody.addProperty("RelyingParty", "http://auth.xboxlive.com");
        xblBody.addProperty("TokenType", "JWT");
        JsonObject xbl = postJson(XBL_URL, xblBody);

        JsonArray xui = xbl.getAsJsonObject("DisplayClaims").getAsJsonArray("xui");
        String uhs = xui.get(0).getAsJsonObject().get("uhs").getAsString();
        String xblToken = xbl.get("Token").getAsString();

        JsonArray userTokens = new JsonArray();
        userTokens.add(xblToken);
        JsonObject xstsProperties = new JsonObject();
        xstsProperties.addProperty("SandboxId", "RETAIL");
        xstsProperties.add("UserTokens", userTokens);
        JsonObject xstsBody = new JsonObject();
        xstsBody.add("Properties", xstsProperties);
        xstsBody.addProperty("RelyingParty", "rp://api.minecraftservices.com/");
        xstsBody.addProperty("TokenType", "JWT");
        JsonObject xsts = postJson(XSTS_URL, xstsBody);
        if (!xsts.has("Token")) {
            return null;
        }
        String xstsToken = xsts.get("Token").getAsString();

        JsonObject mcBody = new JsonObject();
        mcBody.addProperty("identityToken", "XBL3.0 x=" + uhs + ";" + xstsToken);
        JsonObject mc = postJson(MC_LOGIN_URL, mcBody);
        return mc.get("access_token").getAsString();
    }

    private static JsonObject postJson(String url, JsonObject body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    private static UUID parseUuid(String id) {
        return UUID.fromString(id.replaceFirst(
                "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{12})",
                "$1-$2-$3-$4-$5"));
    }
}
