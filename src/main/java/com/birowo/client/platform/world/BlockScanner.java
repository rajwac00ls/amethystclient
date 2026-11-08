package com.birowo.client.platform.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;

/**
 * Looks through the chunks of the world for the blocks that are worth showing.
 *
 * <p>A chunk is made of sections of sixteen by sixteen by sixteen blocks. A section that holds
 * none of the blocks that are looked for is skipped without being read, because that check only
 * walks through the few kinds of block the section is made of.
 *
 * <p>The scanner is not meant to be run over the whole world at once: a chunk holds thousands of
 * blocks. The caller decides how many chunks are looked through in one tick.
 */
public final class BlockScanner {
	/**
	 * The height, the width and the depth of a section, in blocks.
	 */
	private static final int SECTION_SIZE = 16;

	private BlockScanner() {
	}

	/**
	 * Looks through one chunk for the blocks that are wanted.
	 *
	 * @param world  the world the chunk is in, used to read the shape of the blocks
	 * @param chunk  the chunk that is looked through
	 * @param wanted the blocks that are looked for
	 * @return the outlines of the blocks that were found, in world coordinates
	 */
	public static List<Box> scan(ClientWorld world, Chunk chunk, Set<Block> wanted) {
		List<Box> found = new ArrayList<>();
		ChunkPos position = chunk.getPos();
		BlockPos.Mutable cursor = new BlockPos.Mutable();
		ChunkSection[] sections = chunk.getSectionArray();
		int bottom = world.getBottomY();

		for (int index = 0; index < sections.length; index++) {
			ChunkSection section = sections[index];

			if (section.isEmpty() || !section.hasAny(state -> wanted.contains(state.getBlock()))) {
				continue;
			}

			int baseY = bottom + index * SECTION_SIZE;

			for (int y = 0; y < SECTION_SIZE; y++) {
				for (int x = 0; x < SECTION_SIZE; x++) {
					for (int z = 0; z < SECTION_SIZE; z++) {
						BlockState state = section.getBlockState(x, y, z);

						if (!wanted.contains(state.getBlock())) {
							continue;
						}

						cursor.set(position.getStartX() + x, baseY + y, position.getStartZ() + z);
						found.add(outline(world, cursor, state));
					}
				}
			}
		}

		return found;
	}

	/**
	 * The outline of one block.
	 *
	 * @param world the world the block is in
	 * @param pos   the place of the block, in world coordinates
	 * @param state the state of the block
	 * @return the outline of the block, in world coordinates
	 */
	private static Box outline(ClientWorld world, BlockPos pos, BlockState state) {
		VoxelShape shape = state.getOutlineShape(world, pos);

		if (shape.isEmpty()) {
			return new Box(pos);
		}

		return shape.getBoundingBox().offset(pos);
	}
}
