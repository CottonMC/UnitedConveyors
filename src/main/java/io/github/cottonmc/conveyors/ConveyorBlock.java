package io.github.cottonmc.conveyors;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
				.with(FRONT, canConnect(world, pos.offset(facing)))
				.with(REAR, canConnect(world, pos.offset(facing.getOpposite())));
	}
	
	@Override
	public BlockState getStateForNeighborUpdate(BlockState oldState, Direction dir, BlockState neighborState, IWorld world, BlockPos pos, BlockPos neighborPos) {
		Direction facing = oldState.get(Properties.HORIZONTAL_FACING);
		return oldState
				.with(FRONT, canConnect(world, pos.offset(facing)))
				.with(REAR, canConnect(world, pos.offset(facing.getOpposite())));
	}
	
	public boolean canConnect(IWorld world, BlockPos pos) {
		if (world.getBlockState(pos).getBlock() instanceof ConveyorBlock) return true;
		if (world.getBlockEntity(pos) instanceof Inventory) return true;
		//TODO: Check for inventories and capability support
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
		} else {
			Direction d = state.get(Properties.HORIZONTAL_FACING);
			float magnitude = 0.05f;
			entity.addVelocity(d.getOffsetX()*magnitude, d.getOffsetY()*magnitude, d.getOffsetZ()*magnitude);
		}
		
		
		super.onEntityCollision(state, world, pos, entity);
	}
	
	@Override
	public List<ItemStack> getDroppedStacks(BlockState blockState_1, net.minecraft.world.loot.context.LootContext.Builder lootContext$Builder_1) {
		
		List<ItemStack> result = super.getDroppedStacks(blockState_1, lootContext$Builder_1);
		System.out.println(result);
		
		return result;
	}
}
