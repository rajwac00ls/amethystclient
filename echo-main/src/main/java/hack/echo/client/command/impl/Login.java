package hack.echo.client.command.impl;

import hack.echo.client.auth.TokenSession;
import hack.echo.client.command.Command;
import hack.echo.client.utils.ChatUtils;
import net.minecraft.client.User;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

public class Login extends Command {

    public Login() {
        super("login", "Log in with a session token.");
    }

    @Override
    public void execute(String[] args) {
        if (args.length == 0 || args[0].isBlank()) {
            ChatUtils.chat("usage: .login <token> | .login <name> <token> | .login revert");
            return;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("revert")) {
            TokenSession.revert();
            ChatUtils.chat("reverted to your original account.");
            return;
        }

        if (args.length >= 2) {
            String name = args[0].trim();
            String token = args[1].trim();
            User user = new User(name, UUID.nameUUIDFromBytes(token.getBytes(StandardCharsets.UTF_8)), token, Optional.empty(), Optional.empty());
            TokenSession.apply(user);
            ChatUtils.chat("session set to " + name + " (offline-style). Join a server to verify.");
            return;
        }

        String token = args[0].trim();
        ChatUtils.chat("resolving token...");
        TokenSession.login(token, ChatUtils::chat);
    }
}
