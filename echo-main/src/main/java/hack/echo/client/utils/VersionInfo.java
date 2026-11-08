package hack.echo.client.utils;

import hack.echo.client.utils.strings.Concat;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class VersionInfo {

    private static final String RESOURCE_PATH = "/echo.version";

    private static Concat cached;

    private VersionInfo() {}

    public static Concat get() {
        Concat result = cached;
        if (result == null) {
            result = load();
            cached = result;
        }
        return result;
    }

    private static Concat load() {
        try (InputStream in = VersionInfo.class.getResourceAsStream(RESOURCE_PATH)) {
            if (in == null) return Concat.of("unknown");
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String version = reader.readLine();
            if (version == null || version.isBlank()) return Concat.of("unknown");
            String commit = reader.readLine();
            if (commit == null || commit.isBlank()) return Concat.of(version);
            return Concat.of(version, " (", commit, ")");
        } catch (Exception e) {
            return Concat.of("unknown");
        }
    }
}
