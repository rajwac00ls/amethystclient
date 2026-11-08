package com.birowo.client.core.gui;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

/**
 * Reads and writes the layout of the interface.
 *
 * <p>Panel positions are stored apart from the module settings because they belong to the
 * interface rather than to any module. Entries the current build does not know about are kept
 * verbatim, so a panel that is added and later removed does not cost the user the rest of their
 * layout.
 */
public final class GuiLayoutStore {
	private static final Logger LOGGER = LoggerFactory.getLogger("BirowoClient/GuiLayoutStore");

	private static final String PANELS_KEY = "panels";
	private static final String X_KEY = "x";
	private static final String Y_KEY = "y";
	private static final String COLLAPSED_KEY = "collapsed";
	private static final String VERSION_KEY = "version";

	/**
	 * The version of the layout file.
	 *
	 * <p>The version is raised whenever the default layout changes shape, because positions that
	 * were chosen for the old layout would put the panels on top of each other.
	 */
	private static final int VERSION = 2;

	private final Path file;
	private final Gson gson = new GsonBuilder()
			.setPrettyPrinting()
			.disableHtmlEscaping()
			.create();

	/**
	 * @param file the file to read from and write to
	 */
	public GuiLayoutStore(Path file) {
		this.file = file;
	}

	/**
	 * Restores the position and the collapsed state of every given panel.
	 *
	 * @param panels the panels to restore
	 */
	public void load(Collection<PanelState> panels) {
		JsonObject root = read();

		if (root == null) {
			return;
		}

		JsonElement panelsElement = root.get(PANELS_KEY);

		if (panelsElement == null || !panelsElement.isJsonObject()) {
			return;
		}

		JsonObject stored = panelsElement.getAsJsonObject();

		JsonElement version = root.get(VERSION_KEY);

		if (version == null || !version.isJsonPrimitive() || version.getAsInt() != VERSION) {
			LOGGER.info("The interface layout was written by another version; the default layout is used instead");
			return;
		}

		for (PanelState panel : panels) {
			JsonElement entry = stored.get(panel.id());

			if (entry == null || !entry.isJsonObject()) {
				continue;
			}

			JsonObject node = entry.getAsJsonObject();
			JsonElement x = node.get(X_KEY);
			JsonElement y = node.get(Y_KEY);
			JsonElement collapsed = node.get(COLLAPSED_KEY);

			if (x != null && x.isJsonPrimitive() && x.getAsJsonPrimitive().isNumber()
					&& y != null && y.isJsonPrimitive() && y.getAsJsonPrimitive().isNumber()) {
				panel.snapTo(x.getAsInt(), y.getAsInt());
			}

			if (collapsed != null && collapsed.isJsonPrimitive() && collapsed.getAsJsonPrimitive().isBoolean()) {
				panel.setCollapsed(collapsed.getAsBoolean());
			}
		}
	}

	/**
	 * Writes the position and the collapsed state of every given panel.
	 *
	 * @param panels the panels to persist
	 */
	public void save(Collection<PanelState> panels) {
		JsonObject root = read();
		JsonObject stored = new JsonObject();
		JsonElement panelsElement = root == null ? null : root.get(PANELS_KEY);

		if (panelsElement != null && panelsElement.isJsonObject()) {
			stored = panelsElement.getAsJsonObject();
		}

		for (PanelState panel : panels) {
			// A panel that has never been placed has no position worth keeping.
			if (!panel.placed()) {
				continue;
			}

			JsonObject node = new JsonObject();
			JsonElement existing = stored.get(panel.id());

			if (existing != null && existing.isJsonObject()) {
				node = existing.getAsJsonObject();
			}

			node.addProperty(X_KEY, panel.restingX());
			node.addProperty(Y_KEY, panel.restingY());
			node.addProperty(COLLAPSED_KEY, panel.collapsed());
			stored.add(panel.id(), node);
		}

		JsonObject output = root == null ? new JsonObject() : root;
		output.addProperty(VERSION_KEY, VERSION);
		output.add(PANELS_KEY, stored);
		write(output);
	}

	private JsonObject read() {
		if (!Files.isRegularFile(file)) {
			return null;
		}

		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			JsonElement parsed = JsonParser.parseReader(reader);

			if (!parsed.isJsonObject()) {
				return null;
			}

			return parsed.getAsJsonObject();
		} catch (JsonParseException e) {
			LOGGER.warn("Interface layout file {} could not be parsed; using the default layout", file, e);
			return null;
		} catch (IOException e) {
			LOGGER.warn("Interface layout file {} could not be read; using the default layout", file, e);
			return null;
		}
	}

	private void write(JsonObject root) {
		try {
			Path parent = file.getParent();

			if (parent != null) {
				Files.createDirectories(parent);
			}

			Path temporary = file.resolveSibling(file.getFileName() + ".tmp");

			try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
				gson.toJson(root, writer);
			}

			Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			LOGGER.error("Interface layout file {} could not be written", file, e);
		}
	}
}
