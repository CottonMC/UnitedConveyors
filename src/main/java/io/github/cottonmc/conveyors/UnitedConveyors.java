package io.github.cottonmc.conveyors;

import net.fabricmc.api.ModInitializer;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.util.registry.Registry;

public class UnitedConveyors implements ModInitializer {
	public static final String MODID = "united-conveyors";
	public static BlockEntityType<ConveyorBlockEntity> CONVEYOR_ENTITY_TYPE;
	public static ConveyorBlock CONVEYOR_BLOCK;
	public static BlockItem CONVEYOR_ITEM;
	
	@Override
	public void onInitialize() {
		CONVEYOR_BLOCK = new ConveyorBlock();
		Registry.register(Registry.BLOCK, ConveyorBlock.ID, CONVEYOR_BLOCK);
		CONVEYOR_ITEM = new BlockItem(CONVEYOR_BLOCK, new Item.Settings().group(ItemGroup.TRANSPORTATION));
		Registry.register(Registry.ITEM, ConveyorBlock.ID, CONVEYOR_ITEM);
		CONVEYOR_ENTITY_TYPE = BlockEntityType.Builder.create(ConveyorBlockEntity::new, CONVEYOR_BLOCK).build(null);
		Registry.register(Registry.BLOCK_ENTITY, ConveyorBlock.ID, CONVEYOR_ENTITY_TYPE);
	}

}
