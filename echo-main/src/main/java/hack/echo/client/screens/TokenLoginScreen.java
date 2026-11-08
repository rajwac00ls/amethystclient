package hack.echo.client.screens;

import hack.echo.client.api.MinecraftCompat;
import hack.echo.client.auth.TokenSession;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class TokenLoginScreen extends Screen {

    private static final String SUCCESS_PREFIX = "logged in as ";

    private final Screen parent;
    private EditBox tokenBox;
    private Button loginButton;
    private Button cancelButton;
    private boolean working;

    public TokenLoginScreen(Screen parent) {
        super(Component.literal("Echo Login"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        this.tokenBox = new EditBox(Minecraft.getInstance().font, centerX - 150, centerY - 34, 300, 20, Component.literal("Session token"));
        this.tokenBox.setMaxLength(2048);
        this.addRenderableWidget(this.tokenBox);
        this.loginButton = Button.builder(Component.literal("Login"), b -> this.doLogin())
                .bounds(centerX - 150, centerY - 8, 145, 20)
                .build();
        this.cancelButton = Button.builder(Component.literal("Cancel"), b -> MinecraftCompat.setScreen(this.parent))
                .bounds(centerX + 5, centerY - 8, 145, 20)
                .build();
        this.addRenderableWidget(this.loginButton);
        this.addRenderableWidget(this.cancelButton);
    }

    private void doLogin() {
        String token = this.tokenBox.getValue().trim();
        if (token.isEmpty() || this.working) {
            return;
        }
        this.working = true;
        this.loginButton.active = false;
        this.tokenBox.setEditable(false);
        this.loginButton.setMessage(Component.literal("Logging in..."));
        TokenSession.login(token, message -> {
            if (message.startsWith(SUCCESS_PREFIX)) {
                this.loginButton.setMessage(Component.literal("Logged in"));
                this.cancelButton.setMessage(Component.literal("Done"));
                MinecraftCompat.setScreen(this.parent);
            } else {
                this.loginButton.active = true;
                this.tokenBox.setEditable(true);
                this.loginButton.setMessage(Component.literal(message));
            }
        });
    }
}
