package hack.echo.client.features.impl.misc;

import hack.echo.client.event.EventSubscribe;
import hack.echo.client.event.impl.EventPacketReceive;
import hack.echo.client.event.impl.EventTick;
import hack.echo.client.features.Category;
import hack.echo.client.features.Feature;
import hack.echo.client.features.FeatureInfo;
import hack.echo.client.features.impl.render.hud.NotificationsHudModule;
import hack.echo.client.features.settings.impl.BoolSetting;
import hack.echo.client.features.settings.impl.ColorSetting;
import hack.echo.client.features.settings.impl.IntSetting;
import hack.echo.client.utils.ChatUtils;
import hack.echo.client.utils.strings.Concat;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.world.level.GameType;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

public class GameModeNotify extends Feature {

    private final BoolSetting notifyHud = new BoolSetting(
        Concat.of("Notifications"), true);
    private final BoolSetting notifyChat = new BoolSetting(
        Concat.of("Chat Message"), true);
    private final BoolSetting notifyCommands = new BoolSetting(
        Concat.of("Command Notifications"), true, o -> notifyHud.getValue() || notifyChat.getValue());
    private final ColorSetting textColor = new ColorSetting(
        Concat.of("Text Color"), 0xFFFFFFFF, o -> notifyHud.getValue() || notifyChat.getValue(), false);
    private final IntSetting textSize = new IntSetting(
        Concat.of("Text Size"), 7, 4, 20, o -> notifyHud.getValue());

    private static final DateTimeFormatter TIME_FORMATTER =
        DateTimeFormatter.ofPattern("HH:mm:ss");

    private final Map<String, GameType> lastGameModes = new HashMap<>();

    public GameModeNotify() {
        super(new FeatureInfo(
            Concat.of("Gamemode Notify"),
            Concat.of("Notifies when a player changes their gamemode"),
            Category.INTERNALS,
            false
        ));
    }

    @Override
    public void onEnable() {
        lastGameModes.clear();
        super.onEnable();
    }

    @Override
    public void onDisable() {
        lastGameModes.clear();
        super.onDisable();
    }

    @EventSubscribe
    private void onTick(EventTick event) {
        if (isNull() || mc.getConnection() == null) return;

        for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
            String name = info.getProfile().name();
            if (name == null || name.isBlank()) continue;
            if (name.equalsIgnoreCase(mc.player.getName().getString())) continue;

            GameType current = info.getGameMode();
            if (current == null || current.getId() == -1) {
                lastGameModes.remove(name);
                continue;
            }

            GameType previous = lastGameModes.put(name, current);
            if (previous == null || previous == current || previous.getId() == -1) continue;

            notifyChange(name, previous, current);
        }
    }

    private void notifyChange(String name, GameType previous, GameType current) {
        String message = Concat.of(
            name,
            " changed his gamemode from ",
            previous.getName(),
            " to ",
            current.getName(),
            " at ",
            LocalTime.now().format(TIME_FORMATTER)
        ).toString();

        if (notifyHud.getValue()) {
            NotificationsHudModule.notifyCustom(this, message, textColor.getARGB(), textSize.getValue());
        }
        if (notifyChat.getValue()) {
            ChatUtils.chat(hexColorPrefix(textColor.getRGB()) + message);
        }
    }

    @EventSubscribe
    private void onPacketReceive(EventPacketReceive event) {
        if (!notifyCommands.getValue()) return;
        if (isNull() || mc.getConnection() == null) return;
        if (!(event.getPacket() instanceof ClientboundSystemChatPacket packet)) return;

        if (!(packet.content().getContents() instanceof TranslatableContents translatable)) return;
        if (!"chat.type.admin".equals(translatable.getKey())) return;

        Object[] args = translatable.getArgs();
        if (args.length < 2) return;

        String command = args[1].toString();
        if (!command.startsWith("/")) return;

        String sender = args[0] instanceof Component component ? component.getString() : args[0].toString();
        if (sender.isBlank() || sender.equalsIgnoreCase(mc.player.getName().getString())) return;

        notifyCommand(sender, command);
    }

    private void notifyCommand(String name, String command) {
        String message = Concat.of(
            name,
            " issued server command: ",
            command,
            " at ",
            LocalTime.now().format(TIME_FORMATTER)
        ).toString();

        if (notifyHud.getValue()) {
            NotificationsHudModule.notifyCustom(this, message, textColor.getARGB(), textSize.getValue());
        }
        if (notifyChat.getValue()) {
            ChatUtils.chat(hexColorPrefix(textColor.getRGB()) + message);
        }
    }

    private static String hexColorPrefix(int rgb) {
        StringBuilder prefix = new StringBuilder("§x");
        for (char c : String.format("%06x", rgb).toCharArray()) {
            prefix.append('§').append(c);
        }
        return prefix.toString();
    }
}
