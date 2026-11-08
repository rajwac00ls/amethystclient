package com.birowo.client.platform.combat;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.Text;

/**
 * Decides which PvP rules the server the player is on runs with.
 *
 * <p>The rules are read from the version the server announces in the list of servers. A server
 * that runs 1.8.9 announces a version that starts with "1.8", and every server from 1.9 onwards
 * announces a version that is newer, so the first two numbers of the announcement are enough to
 * tell the two apart.
 *
 * <p>The announcement is not always there: a server that is joined without being looked at in
 * the list of servers announces nothing, and a proxy may rewrite the announcement to hide the
 * version of the server behind it. The version may therefore be set by hand, which is what the
 * command {@code pvp} is for. The version that is in use is shown in the readout of the client.
 */
public final class PvpVersionService {
	/**
	 * The first two numbers of a version announcement, such as the "1.21" of "Paper 1.21.4".
	 */
	private static final Pattern VERSION = Pattern.compile("(\\d+)\\.(\\d+)");

	/**
	 * The version the player chose by hand, or {@code null} when the announcement decides.
	 */
	private volatile PvpVersion configured;

	/**
	 * The server entry the detected version was read from.
	 */
	private volatile ServerInfo detectedFrom;

	/**
	 * The version that was read from the last server entry.
	 */
	private volatile PvpVersion detected = PvpVersion.MODERN_1_21;

	/**
	 * @return the version of the PvP rules that are in use
	 */
	public PvpVersion current() {
		PvpVersion chosen = configured;

		return chosen != null ? chosen : detect();
	}

	/**
	 * Sets the version by hand.
	 *
	 * @param version the version to use, or {@code null} to let the announcement decide again
	 */
	public void configure(PvpVersion version) {
		this.configured = version;
	}

	/**
	 * @return the version that was set by hand, or {@code null} when the announcement decides
	 */
	public PvpVersion configured() {
		return configured;
	}

	/**
	 * Reads the version the server announces.
	 *
	 * <p>The result is remembered for as long as the player stays on the same server, so that
	 * the announcement is not read again on every tick.
	 *
	 * @return the version the announcement names
	 */
	public PvpVersion detect() {
		MinecraftClient client = MinecraftClient.getInstance();
		ServerInfo entry = client.getCurrentServerEntry();

		if (entry == null) {
			return PvpVersion.MODERN_1_21;
		}

		if (entry == detectedFrom) {
			return detected;
		}

		detectedFrom = entry;
		detected = read(entry);

		return detected;
	}

	/**
	 * @param entry the server entry the version is read from
	 * @return the version the entry announces
	 */
	private static PvpVersion read(ServerInfo entry) {
		Text announcement = entry.version;

		if (announcement == null) {
			return PvpVersion.MODERN_1_21;
		}

		Matcher matcher = VERSION.matcher(announcement.getString());

		if (!matcher.find()) {
			return PvpVersion.MODERN_1_21;
		}

		int major = Integer.parseInt(matcher.group(1));
		int minor = Integer.parseInt(matcher.group(2));

		return major == 1 && minor <= 8 ? PvpVersion.LEGACY_1_8_9 : PvpVersion.MODERN_1_21;
	}
}
