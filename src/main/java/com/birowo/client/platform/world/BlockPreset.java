package com.birowo.client.platform.world;

import java.util.LinkedHashSet;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

/**
 * The groups of blocks the block overlay looks for.
 *
 * <p>The groups are fixed: they name the blocks that are worth looking for in a survival world.
 * A block that is not in the registry of the game is skipped, so a group stays usable even when
 * a mod takes a block away.
 */
public enum BlockPreset {
	/**
	 * The ores that are worth digging for.
	 */
	ORE(
			"minecraft:diamond_ore", "minecraft:deepslate_diamond_ore",
			"minecraft:gold_ore", "minecraft:deepslate_gold_ore", "minecraft:nether_gold_ore",
			"minecraft:iron_ore", "minecraft:deepslate_iron_ore",
			"minecraft:copper_ore", "minecraft:deepslate_copper_ore",
			"minecraft:emerald_ore", "minecraft:deepslate_emerald_ore",
			"minecraft:redstone_ore", "minecraft:deepslate_redstone_ore",
			"minecraft:lapis_ore", "minecraft:deepslate_lapis_ore",
			"minecraft:coal_ore", "minecraft:deepslate_coal_ore",
			"minecraft:nether_quartz_ore", "minecraft:ancient_debris"),

	/**
	 * The ores and the blocks that are worth carrying away.
	 */
	VALUABLE(
			true,
			"minecraft:chest", "minecraft:ender_chest", "minecraft:barrel", "minecraft:shulker_box",
			"minecraft:hopper", "minecraft:dropper", "minecraft:dispenser", "minecraft:brewing_stand",
			"minecraft:furnace", "minecraft:blast_furnace", "minecraft:smoker",
			"minecraft:spawner", "minecraft:enchanting_table", "minecraft:anvil",
			"minecraft:chipped_anvil", "minecraft:damaged_anvil", "minecraft:beacon",
			"minecraft:end_portal_frame", "minecraft:diamond_block", "minecraft:gold_block",
			"minecraft:iron_block", "minecraft:emerald_block", "minecraft:netherite_block",
			"minecraft:lapis_block", "minecraft:redstone_block", "minecraft:coal_block"),

	/**
	 * The blocks that are rare and worth a lot.
	 */
	RARE(
			"minecraft:diamond_block", "minecraft:gold_block", "minecraft:netherite_block",
			"minecraft:emerald_block", "minecraft:ancient_debris", "minecraft:dragon_egg",
			"minecraft:end_portal_frame", "minecraft:beacon", "minecraft:enchanting_table",
			"minecraft:spawner", "minecraft:ender_chest");

	/**
	 * The colours a shulker box comes in.
	 */
	private static final String[] SHULKER_COLOURS = {
			"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
			"light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"};

	private final String[] identifiers;

	/**
	 * Whether the shulker boxes belong to the group.
	 */
	private final boolean shulkers;

	/**
	 * The blocks of the group, resolved out of the registry of the game.
	 */
	private Set<Block> blocks;

	BlockPreset(String... identifiers) {
		this(false, identifiers);
	}

	/**
	 * @param shulkers    whether the shulker boxes belong to the group
	 * @param identifiers the identifiers of the blocks of the group
	 */
	private BlockPreset(boolean shulkers, String... identifiers) {
		this.shulkers = shulkers;
		this.identifiers = identifiers;
	}

	/**
	 * @return the blocks of the group, never {@code null} and never empty
	 */
	public Set<Block> blocks() {
		if (blocks == null) {
			Set<Block> resolved = new LinkedHashSet<>();

			resolve(resolved, identifiers);

			// The valuable group holds the ores as well.
			if (this == VALUABLE) {
				resolve(resolved, ORE.identifiers);
			}

			if (shulkers) {
				for (String colour : SHULKER_COLOURS) {
					Identifier parsed = Identifier.tryParse("minecraft:" + colour + "_shulker_box");

					if (parsed != null) {
						Registries.BLOCK.getOptionalValue(parsed).ifPresent(resolved::add);
					}
				}
			}

			blocks = Set.copyOf(resolved);
		}

		return blocks;
	}

	/**
	 * Adds the blocks of the given identifiers to the set.
	 *
	 * @param target      the set the blocks are added to
	 * @param identifiers the identifiers of the blocks
	 */
	private static void resolve(Set<Block> target, String[] identifiers) {
		for (String identifier : identifiers) {
			Identifier parsed = Identifier.tryParse(identifier);

			if (parsed == null) {
				continue;
			}

			Registries.BLOCK.getOptionalValue(parsed).ifPresent(target::add);
		}
	}
}
