package io.github.cottonmc.conveyors;

import alexiil.mc.lib.attributes.AttributeList;
import alexiil.mc.lib.attributes.AttributeProvider;
import alexiil.mc.lib.attributes.SearchOptions;
import alexiil.mc.lib.attributes.Simulation;
import alexiil.mc.lib.attributes.item.ItemAttributes;
import alexiil.mc.lib.attributes.item.ItemExtractable;
import alexiil.mc.lib.attributes.item.ItemInsertable;
import alexiil.mc.lib.attributes.item.impl.EmptyItemExtractable;
import net.fabricmc.fabric.api.block.entity.BlockEntityClientSerializable;
import net.minecraft.block.BlockState;
import net.minecraft.block.ComposterBlock;
import net.minecraft.block.HopperBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Tickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

public class ConveyorBlockEntity extends BlockEntity implements BlockEntityClientSerializable, Tickable, AttributeProvider {
	public static final int TAG_TYPE_COMPOUND = 10;
	public static final int TAG_TYPE_NUMBER = 99;
	
	protected int maxDelay = 20;
	protected ItemStack stack = ItemStack.EMPTY;
	protected int delay = maxDelay;
	
	public ConveyorBlockEntity() {
		super(UnitedConveyors.CONVEYOR_ENTITY_TYPE);
	}
	
	@Override
	public void fromTag(CompoundTag tag) {
		super.fromTag(tag);
		if (tag.containsKey("Stack", TAG_TYPE_COMPOUND)) {
			stack = ItemStack.fromTag(tag.getCompound("Stack"));
		}
		if (tag.containsKey("Delay", TAG_TYPE_NUMBER)) {
			delay = tag.getInt("Delay");
		}
	}
	
	@Override
	public CompoundTag toTag(CompoundTag tag) {
		super.toTag(tag);
		tag.put("Stack", stack.toTag(new CompoundTag()));
		tag.putInt("Delay", delay);
		return tag;
	}
	
	@Override
	public CompoundTag toInitialChunkDataTag() {
		return toTag(new CompoundTag());
	}
	
	@Override
	public void fromClientTag(CompoundTag tag) {
		fromTag(tag);
	}

	@Override
	public CompoundTag toClientTag(CompoundTag tag) {
		return toTag(tag);
	}
	
	@Override
	public void tick() {
		if (delay>0) {
			delay--;
			markDirty();
		}
		
		if (delay>0) {
			//if we're still in cooldown, just cool down.
			return;
		}
		
		if (world.isClient) {
			//TODO: Clientside handoff of items to reduce packet traffic
			return;
		}
		
		if (!this.stack.isEmpty()) {
			//Push our items down if there's an enabled hopper with room below us
			BlockPos beneath = pos.down();
			BlockState beneathState = world.getBlockState(beneath);
			if (beneathState.getBlock() instanceof HopperBlock) {
				if (beneathState.get(Properties.ENABLED)) {
					ItemInsertable insertable = ItemAttributes.INSERTABLE.get(world, beneath, SearchOptions.inDirection(Direction.DOWN));
					ItemStack stack = insertable.attemptInsertion(this.stack, Simulation.ACTION);
					if (stack.isEmpty() || stack.getCount()!=this.stack.getCount()) {
						this.stack = stack;
						markDirty();
						sync();
					}
				}
			}
		}
		
		if (!this.stack.isEmpty()) {
		
			//Push our items forward
			
			BlockState state = this.getCachedState();
			if (state.getBlock() instanceof ConveyorBlock) {
				Direction facing = state.get(Properties.HORIZONTAL_FACING);
				BlockPos ahead = pos.offset(facing);
				BlockState aheadState = world.getBlockState(ahead);
				
				if (aheadState.getBlock() instanceof ConveyorBlock) { //Conveyors have special rules for each other
					BlockEntity be = world.getBlockEntity(ahead);
					if (be!=null) {
						if (be instanceof ConveyorBlockEntity) {
							ConveyorBlockEntity other = (ConveyorBlockEntity) be;
							if (other.stack.isEmpty()) {
								//Should we insert it at full or half delay?
								boolean doHandoff = true;
								Direction aheadFacing = aheadState.get(Properties.HORIZONTAL_FACING);
								int otherDelay = other.maxDelay;
								if (facing!=aheadFacing) {
									if (facing==aheadFacing.getOpposite()) {
										doHandoff = false;
									} else {
										otherDelay = maxDelay / 2;
									}
								}
								
								if (doHandoff) {
									other.stack = stack.copy();
									other.delay = otherDelay;
									this.stack = ItemStack.EMPTY;
									other.markDirty();
									other.sync();
									markDirty();
									sync();
								}
							}
						}
					}
				} else if (aheadState.getBlock() instanceof ComposterBlock) {
					ItemInsertable insertable = ItemAttributes.INSERTABLE.get(world, ahead, SearchOptions.inDirection(Direction.DOWN));
					ItemStack stack = insertable.attemptInsertion(this.stack, Simulation.ACTION);
					if (stack.isEmpty() || stack.getCount()!=this.stack.getCount()) {
						this.stack = stack;
						markDirty();
						sync();
					}
				} else {
					ItemInsertable insertable = ItemAttributes.INSERTABLE.get(world, ahead, SearchOptions.inDirection(facing)); //For everything else, there's MasterCard
					ItemStack stack = insertable.attemptInsertion(this.stack, Simulation.ACTION);
					if (stack.isEmpty() || stack.getCount()!=this.stack.getCount()) {
						this.stack = stack;
						markDirty();
						sync();
					}
				}
			}
		}
		
		
		if (this.stack.isEmpty()) {
			//Look behind us and see if we're hooked to an inventory
			BlockState state = this.getCachedState();
			if (state.getBlock() instanceof ConveyorBlock) {
				Direction facing = state.get(Properties.HORIZONTAL_FACING);
				
				BlockPos behind = pos.offset(facing.getOpposite());
				BlockState behindState = world.getBlockState(behind);
				if (!(behindState.getBlock() instanceof ConveyorBlock)) { //Don't pull. We'll get a push
				
					ItemExtractable extractable = ItemAttributes.EXTRACTABLE.get(world, behind, SearchOptions.inDirection(facing.getOpposite()));
					ItemStack stack = extractable.attemptAnyExtraction(64, Simulation.ACTION);
					if (!stack.isEmpty()) {
						this.stack = stack;
						this.delay = maxDelay;
						this.markDirty();
						this.sync();
					}
				}
			}
		}
		
		if (this.stack.isEmpty()) {
			//Still empty?! Look for enabled hoppers above that are facing us
			BlockState up = world.getBlockState(pos.up());
			if (up.getBlock() instanceof HopperBlock) {
				
				if (up.get(Properties.HOPPER_FACING)==Direction.DOWN && up.get(Properties.ENABLED).equals(Boolean.TRUE)) {
					ItemExtractable extractable = ItemAttributes.EXTRACTABLE.get(world, pos.up(), SearchOptions.inDirection(Direction.UP));
					ItemStack stack = extractable.attemptAnyExtraction(64, Simulation.ACTION);
					if (!stack.isEmpty()) {
						this.stack = stack;
						this.delay = maxDelay / 2;
						this.markDirty();
						this.sync();
					}
				}
			}
		}
		
		//this.markDirty();
		//this.sync();
	}
	
	public void offerItemEntity(ItemEntity entity) {
		if (!entity.isAlive()) return;
		if (this.stack.isEmpty()) {
			this.stack = entity.getStack().copy();
			this.delay = maxDelay;
			this.markDirty();
			this.sync();
			entity.kill();
		}
		
		//TODO: Accept partial stacks into compatible stacks
		//if (ItemStackUtil.areEqualIgnoreAmounts(entity.getStack(), this.stack)) {
			
		//}
	}
	
	public void sync() {
		if (world instanceof ServerWorld) {
			((ServerWorld)world).method_14178().markForUpdate(pos);
		}
	}
	
	public float getProgress() {
		return delay/(float)maxDelay;
	}
	
	public float getProgress(float partialTicks) {
		float adjustedDelay = delay-partialTicks; if (adjustedDelay<0) adjustedDelay = 0;
		return adjustedDelay/(float)maxDelay;
	}

	@Override
	public void addAllAttributes(World world, BlockPos pos, BlockState state, AttributeList<?> to) {
		Direction dir = to.getSearchDirection();
		if (dir==null) return; //We're not offering anything to omnidirectional searches
		if (dir==Direction.UP) {
			to.offer(new ConveyorInsertable(this));
		} else if (dir==Direction.DOWN) {
			to.offer(new ConveyorExtractable(this));
		} else {
			if (state.getBlock() instanceof ConveyorBlock) {
				Direction facing = state.get(Properties.HORIZONTAL_FACING);
				
				if (dir==facing) {
					to.offer(EmptyItemExtractable.SUPPLIER); //Don't call us, we'll call you.
				} else if (dir==facing.getOpposite()) {
					to.offer(new ConveyorInsertable(this));
				} else {
					
					
					
				}
			}
		}
	}
}
