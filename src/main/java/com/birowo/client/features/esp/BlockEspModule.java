package com.birowo.client.features.esp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.lwjgl.glfw.GLFW;

import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;

import com.birowo.client.core.config.ConfigValue;
import com.birowo.client.core.config.ConfigValues;
import com.birowo.client.core.keybind.KeyBindFactory;
import com.birowo.client.core.module.Module;
import com.birowo.client.core.module.ModuleCategory;
import com.birowo.client.platform.render.ShapeRenderer;
import com.birowo.client.platform.render.WorldLayers;
import com.birowo.client.platform.render.WorldRenderable;
import com.birowo.client.platform.world.BlockPreset;
import com.birowo.client.platform.world.BlockScanner;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;

/**
 * Draws a box around the blocks that are worth looking for.
 *
 * <p>The blocks are looked for in the chunks around the player. A chunk holds thousands of
 * blocks, so the chunks are not looked through again and again: the result of a chunk is kept
 * until it grows old, and only a few chunks are looked through in one tick, so that the frame
 * rate does not suffer while the player walks around.
 *
 * <p>The result of a chunk grows old after a while, because a block that was mined and a block
 * that was placed are not noticed until the chunk is looked through again.
 *
 * <p>The boxes are drawn twice when they are asked to shine through walls: once with the layer
 * that ignores the depth of the world, and once with the layer of the game, so that the edges of
 * a block that is in sight are drawn over the world rather than through it.
 */
public final class BlockEspModule extends Module implements WorldRenderable {
	/**
	 * How many chunks may be looked through in one tick.
	 */
	private static final int CHUNKS_PER_TICK = 4;

	/**
	 * How many ticks the result of a chunk is trusted, in ticks.
	 */
	private static final long REVALIDATE_TICKS = 100L;

	/**
	 * The largest radius of chunks that may be looked through, in chunks.
	 */
	private static final int MAX_CHUNK_RADIUS = 12;

	/**
	 * The largest number of blocks that may be shown at once.
	 */
	private static final int MAX_TARGETS = 8192;

	private final ConfigValue<BlockPreset> preset = setting(ConfigValues.ofEnum("preset", BlockPreset.VALUABLE));
	private final ConfigValue<Integer> chunkRadius = setting(ConfigValues.ofInt("chunkRadius", 4));
	private final ConfigValue<Integer> maxTargets = setting(ConfigValues.ofInt("maxTargets", 1024));
	private final ConfigValue<Integer> color = setting(ConfigValues.ofColor("color", 0xCCFF3B3B));
	private final ConfigValue<Boolean> throughWalls = setting(ConfigValues.ofBoolean("throughWalls", true));

	/**
	 * The result of the chunks that have been looked through, by the place of the chunk.
	 */
	private final Map<Long, Entry> chunks = new HashMap<>();

	/**
	 * The blocks that are shown this frame, published by the tick and read by the render thread.
	 */
	private volatile List<Box> targets = List.of();

	/**
	 * The number of ticks the module has been running, used to tell how old a result is.
	 */
	private long tick;

	/**
	 * @param keyBinds the factory used to register this module's key binding
	 */
	public BlockEspModule(KeyBindFactory keyBinds) {
		super("blockesp", "BlockESP", "Draws a box around the blocks that are worth looking for.",
				ModuleCategory.RENDER);

		keyBinds.bind("blockesp", "key.birowo.blockesp", GLFW.GLFW_KEY_B, this::toggle);
	}

	@Override
	public void onTick() {
		MinecraftClient client = MinecraftClient.getInstance();
		ClientPlayerEntity player = client.player;
		ClientWorld world = client.world;

		if (player == null || world == null) {
			targets = List.of();
			return;
		}

		long now = ++tick;
		int radius = Math.max(0, Math.min(MAX_CHUNK_RADIUS, chunkRadius.value()));
		int centerX = (int) Math.floor(player.getX()) >> 4;
		int centerZ = (int) Math.floor(player.getZ()) >> 4;
		Set<Block> wanted = preset.value().blocks();

		chunks.keySet().removeIf(key -> outside(key, centerX, centerZ, radius));

		int scanned = 0;

		for (int dx = -radius; dx <= radius && scanned < CHUNKS_PER_TICK; dx++) {
			for (int dz = -radius; dz <= radius && scanned < CHUNKS_PER_TICK; dz++) {
				long key = ChunkPos.toLong(centerX + dx, centerZ + dz);
				Entry entry = chunks.get(key);

				if (entry != null && now - entry.scannedAt() <= REVALIDATE_TICKS) {
					continue;
				}

				Chunk chunk = world.getChunk(centerX + dx, centerZ + dz, ChunkStatus.FULL, false);

				if (chunk == null) {
					// The chunk is not loaded yet; there is nothing to look through.
					chunks.remove(key);
					continue;
				}

				chunks.put(key, new Entry(now, BlockScanner.scan(world, chunk, wanted)));
				scanned++;
			}
		}

		targets = collect(player);
	}

	@Override
	public void onWorldRender(WorldRenderContext context) {
		List<Box> snapshot = targets;

		if (snapshot.isEmpty()) {
			return;
		}

		MatrixStack matrices = context.matrices();

		if (matrices == null) {
			return;
		}

		MinecraftClient client = MinecraftClient.getInstance();
		Camera camera = client.gameRenderer.getCamera();
		Vec3d cameraPos = camera.getCameraPos();

		float lineWidth = client.getWindow().getMinimumLineWidth();
		int argb = color.value();

		VertexConsumer throughWallLines = null;

		if (throughWalls.value()) {
			throughWallLines = context.consumers().getBuffer(WorldLayers.linesNoDepth());
		}

		VertexConsumer visible = context.consumers().getBuffer(RenderLayers.lines());
		MatrixStack.Entry entry = matrices.peek();

		for (Box box : snapshot) {
			double minX = box.minX - cameraPos.x;
			double minY = box.minY - cameraPos.y;
			double minZ = box.minZ - cameraPos.z;
			double maxX = box.maxX - cameraPos.x;
			double maxY = box.maxY - cameraPos.y;
			double maxZ = box.maxZ - cameraPos.z;

			if (throughWallLines != null) {
				ShapeRenderer.drawBoxEdges(throughWallLines, entry, minX, minY, minZ, maxX, maxY, maxZ,
						argb, lineWidth);
			}

			ShapeRenderer.drawBoxEdges(visible, entry, minX, minY, minZ, maxX, maxY, maxZ, argb, lineWidth);
		}
	}

	@Override
	protected void onDisable() {
		chunks.clear();
		targets = List.of();
	}

	/**
	 * Gathers the blocks that are to be shown, the ones that are nearest to the player first.
	 *
	 * @param player the player the blocks are looked for around
	 * @return the blocks that are to be shown
	 */
	private List<Box> collect(ClientPlayerEntity player) {
		int limit = Math.max(1, Math.min(MAX_TARGETS, maxTargets.value()));
		List<Map.Entry<Long, Entry>> ordered = new ArrayList<>(chunks.entrySet());

		ordered.sort((first, second) -> Double.compare(distance(first.getKey(), player),
				distance(second.getKey(), player)));

		List<Box> found = new ArrayList<>();

		for (Map.Entry<Long, Entry> chunk : ordered) {
			for (Box box : chunk.getValue().boxes()) {
				found.add(box);

				if (found.size() >= limit) {
					return List.copyOf(found);
				}
			}
		}

		return List.copyOf(found);
	}

	/**
	 * @param key     the place of a chunk
	 * @param centerX the column of chunks the player is in
	 * @param centerZ the row of chunks the player is in
	 * @param radius  the radius of the chunks that are looked through, in chunks
	 * @return whether the chunk lies outside the radius
	 */
	private static boolean outside(long key, int centerX, int centerZ, int radius) {
		return Math.abs(ChunkPos.getPackedX(key) - centerX) > radius
				|| Math.abs(ChunkPos.getPackedZ(key) - centerZ) > radius;
	}

	/**
	 * @param key    the place of a chunk
	 * @param player the player the distance is measured from
	 * @return the distance between the player and the middle of the chunk, in blocks
	 */
	private static double distance(long key, ClientPlayerEntity player) {
		double dx = ChunkPos.getPackedX(key) * 16 + 8 - player.getX();
		double dz = ChunkPos.getPackedZ(key) * 16 + 8 - player.getZ();

		return dx * dx + dz * dz;
	}

	/**
	 * The result of looking through one chunk.
	 *
	 * @param scannedAt the tick the chunk was looked through in
	 * @param boxes     the blocks that were found, in world coordinates
	 */
	private record Entry(long scannedAt, List<Box> boxes) {
	}
}
