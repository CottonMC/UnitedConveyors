package io.github.cottonmc.conveyors;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import alexiil.mc.lib.attributes.SearchOptions;
import alexiil.mc.lib.attributes.item.ItemAttributes;
import net.fabricmc.fabric.api.block.FabricBlockSettings;
import net.fabricmc.fabric.api.tools.FabricToolTags;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityContext;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.state.StateFactory.Builder;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.IWorld;
import net.minecraft.world.World;

public class ConveyorBlock extends Block implements BlockEntityProvider {
	public static final Identifier ID = new Identifier(UnitedConveyors.MODID, "conveyor");
	public static final BooleanProperty FRONT = BooleanProperty.of("front");
	public static final BooleanProperty REAR = BooleanProperty.of("rear");
	
	private static VoxelShape[] FRONT_ROTATIONS = VoxelMath.rotationsOf(new Box(0, 0, 0, 16/16.0, 8/16.0, 8/16.0));
	private static VoxelShape[] NO_FRONT_ROTATIONS = VoxelMath.rotationsOf(new Box(0, 0, 4/16.0, 16/16.0, 8/16.0, 8/16.0));
	private static VoxelShape[] REAR_ROTATIONS = VoxelMath.rotationsOf(new Box(0, 0, 8/16.0, 16/16.0, 8/16.0, 16/16.0));
	private static VoxelShape[] NO_REAR_ROTATIONS = VoxelMath.rotationsOf(new Box(0, 0, 8/16.0, 16/16.0, 8/16.0, 12/16.0));
	
	private static Map<BlockState, VoxelShape> STATE_TO_SHAPE = new HashMap<>();
	
	public ConveyorBlock() {
		super(FabricBlockSettings.copy(Blocks.GRAY_CONCRETE).dynamicBounds().breakByTool(FabricToolTags.PICKAXES, 0).build());
	}
	
	@Override
	protected void appendProperties(Builder<Block, BlockState> builder) {
		builder.add(Properties.HORIZONTAL_FACING, FRONT, REAR);
	}

	@Override
	public BlockEntity createBlockEntity(BlockView world) {
		return new ConveyorBlockEntity();
	}
	
	@Override
	public BlockState getPlacementState(ItemPlacementContext context) {
		World world = context.getWorld();
		BlockPos pos = context.getBlockPos();
		Direction facing = context.getPlayerFacing(); //mis-deobfuscated from getPlayerHorizontalFacing
		
		
		return this.getDefaultState()
				.with(Properties.HORIZONTAL_FACING, context.getPlayerFacing())
				.with(FRONT, canConnectForwards(world, pos, facing))
				.with(REAR, canConnectBackwards(world, pos, facing.getOpposite()));
	}
	
	@Override
	public BlockState getStateForNeighborUpdate(BlockState oldState, Direction dir, BlockState neighborState, IWorld world, BlockPos pos, BlockPos neighborPos) {
		if (!(world instanceof World)) return oldState;
		Direction facing = oldState.get(Properties.HORIZONTAL_FACING);
		return oldState
				.with(FRONT, canConnectForwards((World)world, pos, facing))
				.with(REAR, canConnectBackwards((World)world, pos, facing.getOpposite()));
	}
	
	public boolean canConnectForwards(World world, BlockPos pos, Direction dir) {
		if (world.getBlockState(pos.offset(dir)).getBlock() instanceof ConveyorBlock) return true;
		if (ItemAttributes.INSERTABLE.getFirstOrNull(world, pos.offset(dir), SearchOptions.inDirection(dir))!=null) return true;
		return false;
	}
	
	public boolean canConnectBackwards(World world, BlockPos pos, Direction dir) {
		if (world.getBlockState(pos.offset(dir)).getBlock() instanceof ConveyorBlock) return true;
		if (world.getBlockState(pos.offset(dir.rotateYClockwise())).getBlock() instanceof ConveyorBlock) return true;
		if (world.getBlockState(pos.offset(dir.rotateYCounterclockwise())).getBlock() instanceof ConveyorBlock) return true;
		
		if (ItemAttributes.EXTRACTABLE.getFirstOrNull(world, pos.offset(dir), SearchOptions.inDirection(dir))!=null) return true;
		return false;
	}
	
	@Override
	public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, EntityContext context) {
		VoxelShape shape = STATE_TO_SHAPE.get(state);
		if (shape!=null) return shape;
		
		int dir = state.get(Properties.HORIZONTAL_FACING).ordinal()-2;
		VoxelShape front = state.get(FRONT) ? FRONT_ROTATIONS[dir] : NO_FRONT_ROTATIONS[dir];
		VoxelShape rear = state.get(REAR) ? REAR_ROTATIONS[dir] : NO_REAR_ROTATIONS[dir];
		
		VoxelShape result = VoxelShapes.union(front, rear);
		STATE_TO_SHAPE.put(state, result);
		return result;
	}
	
	@Override
	public boolean isOpaque(BlockState state) {
		return false;
	}
	
	@Override
	public void onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
		if (!world.isClient) {
			if (!player.abilities.creativeMode) {
				BlockEntity be = world.getBlockEntity(pos);
				if (be!=null && be instanceof ConveyorBlockEntity) {
					ItemStack toDrop = ((ConveyorBlockEntity)be).stack;
					if (!toDrop.isEmpty()) {
						Block.dropStack(world, pos, toDrop);
					}
				}
			}
		}
		super.onBreak(world, pos, state, player);
	}
	
	@Override
	public void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity) {
		
		if (entity instanceof ItemEntity && !world.isClient) {
			BlockEntity be = world.getBlockEntity(pos);
			if (be instanceof ConveyorBlockEntity) {
				((ConveyorBlockEntity)be).offerItemEntity((ItemEntity) entity);
			}
		} else if (entity instanceof PlayerEntity && world.isClient) {
			if (!entity.isSneaking()) {
				Direction d = state.get(Properties.HORIZONTAL_FACING);
				accelerate(entity, d, 0.05f);
			}
		} else {
			Direction d = state.get(Properties.HORIZONTAL_FACING);
			accelerate(entity, d, 0.05f);
		}
		
		super.onEntityCollision(state, world, pos, entity);
	}
	
	private void accelerate(Entity entity, Direction d, float magnitude) {
		entity.addVelocity(d.getOffsetX()*magnitude, d.getOffsetY()*magnitude, d.getOffsetZ()*magnitude);
	}
	
	@Override
	public List<ItemStack> getDroppedStacks(BlockState blockState_1, net.minecraft.world.loot.context.LootContext.Builder lootContext$Builder_1) {
		
		List<ItemStack> result = super.getDroppedStacks(blockState_1, lootContext$Builder_1);
		System.out.println(result);
		
		return result;
	}
}
