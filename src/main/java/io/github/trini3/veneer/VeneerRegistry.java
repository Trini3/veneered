package io.github.trini3.veneer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.WoodType;

import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;

import io.github.trini3.veneer.block.*;

public final class VeneerRegistry {
	private static final List<Block> FRAMES = new ArrayList<>();
	private static final List<Item> ITEMS = new ArrayList<>();

	public static final Block FRAME_BLOCK = register("frame_block", VeneerFullBlock::new, props(Blocks.OAK_PLANKS));
	public static final Block FRAME_SLAB = register("frame_slab", VeneerSlabBlock::new, props(Blocks.OAK_SLAB));
	public static final Block FRAME_PANEL = register("frame_panel", VeneerPanelBlock::new, props(Blocks.OAK_SLAB));
	public static final Block FRAME_STAIRS = register("frame_stairs", p -> new VeneerStairBlock(Blocks.OAK_PLANKS.defaultBlockState(), p), props(Blocks.OAK_STAIRS));
	public static final Block FRAME_WALL = register("frame_wall", VeneerWallBlock::new, props(Blocks.OAK_PLANKS).forceSolidOn());
	public static final Block FRAME_FENCE = register("frame_fence", VeneerFenceBlock::new, props(Blocks.OAK_FENCE));
	public static final Block FRAME_FENCE_GATE = register("frame_fence_gate", p -> new VeneerFenceGateBlock(WoodType.OAK, p), props(Blocks.OAK_FENCE_GATE));
	public static final Block FRAME_DOOR = register("frame_door", p -> new VeneerDoorBlock(BlockSetType.OAK, p), props(Blocks.OAK_DOOR), DoubleHighBlockItem::new);
	public static final Block FRAME_TRAPDOOR = register("frame_trapdoor", p -> new VeneerTrapDoorBlock(BlockSetType.OAK, p), props(Blocks.OAK_TRAPDOOR));
	public static final Block FRAME_PRESSURE_PLATE = register("frame_pressure_plate", p -> new VeneerPressurePlateBlock(BlockSetType.OAK, p), props(Blocks.OAK_PRESSURE_PLATE));
	public static final Block FRAME_BUTTON = register("frame_button", p -> new VeneerButtonBlock(BlockSetType.OAK, 30, p), props(Blocks.OAK_BUTTON));

	public static final BlockEntityType<VeneerBlockEntity> VENEER_BLOCK_ENTITY = Registry.register(
			BuiltInRegistries.BLOCK_ENTITY_TYPE,
			Veneer.id("frame"),
			FabricBlockEntityTypeBuilder.create(VeneerBlockEntity::new, FRAMES.toArray(Block[]::new)).build()
	);

	public static final ResourceKey<CreativeModeTab> TAB = ResourceKey.create(Registries.CREATIVE_MODE_TAB, Veneer.id("frames"));

	private VeneerRegistry() {
	}

	/** Frames copy their vanilla oak counterpart's behaviour but never occlude, since they can look like glass. */
	private static BlockBehaviour.Properties props(Block base) {
		return BlockBehaviour.Properties.ofFullCopy(base).noOcclusion();
	}

	private static Block register(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
		return register(name, factory, properties, BlockItem::new);
	}

	private static Block register(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties,
			BiFunction<Block, Item.Properties, Item> itemFactory) {
		ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, Veneer.id(name));
		Block block = Registry.register(BuiltInRegistries.BLOCK, blockKey, factory.apply(properties.setId(blockKey)));

		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, Veneer.id(name));
		Item item = Registry.register(BuiltInRegistries.ITEM, itemKey, itemFactory.apply(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));

		FRAMES.add(block);
		ITEMS.add(item);
		return block;
	}

	public static List<Block> frames() {
		return List.copyOf(FRAMES);
	}

	public static void init() {
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, TAB, FabricCreativeModeTab.builder()
				.title(Component.translatable("itemGroup.veneer.frames"))
				.icon(() -> new ItemStack(FRAME_BLOCK))
				.displayItems((context, entries) -> ITEMS.forEach(entries::accept))
				.build());
	}
}
