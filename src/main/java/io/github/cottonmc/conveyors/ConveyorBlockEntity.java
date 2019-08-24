package io.github.cottonmc.conveyors;

import alexiil.mc.lib.attributes.SearchOptions;
import alexiil.mc.lib.attributes.Simulation;
import alexiil.mc.lib.attributes.item.ItemAttributes;
import alexiil.mc.lib.attributes.item.ItemExtractable;
import alexiil.mc.lib.attributes.item.ItemInsertable;
import alexiil.mc.lib.attributes.item.ItemStackUtil;
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

public class ConveyorBlockEntity extends BlockEntity implements BlockEntityClientSerializable, Tickable {
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
			//Push our items down if there's a hopper with room below us
			BlockPos beneath = pos.down();
			BlockState beneathState = world.getBlockState(beneath);
			if (beneathState.getBlock() instanceof HopperBlock) {
				ItemInsertable insertable = ItemAttributes.INSERTABLE.get(world, beneath, SearchOptions.inDirection(Direction.DOWN));
				ItemStack stack = insertable.attemptInsertion(this.stack, Simulation.ACTION);
				if (stack.isEmpty() || stack.getCount()!=this.stack.getCount()) {
					this.stack = stack;
					markDirty();
					sync();
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
								other.stack = stack.copy();
								other.delay = other.maxDelay;
								this.stack = ItemStack.EMPTY;
								other.markDirty();
								other.sync();
								markDirty();
								sync();
							}
						}
					}
				} else if (aheadState.getBlock() instanceof ComposterBlock) {
					//SidedInventory inv = ((ComposterBlock) aheadState.getBlock()).getInventory(aheadState, world, ahead);
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
				if (behindState.getBlock() instanceof ConveyorBlock) return; //Don't pull. We'll get a push
				
				ItemExtractable extractable = ItemAttributes.EXTRACTABLE.get(world, behind, SearchOptions.inDirection(facing.getOpposite()));
				ItemStack stack = extractable.attemptAnyExtraction(64, Simulation.ACTION);
				if (!stack.isEmpty()) {
					this.stack = stack;
					this.delay = maxDelay;
					this.markDirty();
					this.sync();
				}
				/*
				if (behindState.getBlock() instanceof ChestBlock) {
					Inventory inv = ChestBlock.getInventory(behindState, world, behind, false);
					if (extractFrom(inv)) {
						this.markDirty();
						this.sync();
					}
				} else {
					BlockEntity be = world.getBlockEntity(behind);
					if (be!=null && be instanceof Inventory) {
						if (extractFrom((Inventory) be)) {
							this.markDirty();
							this.sync();
						}
					}
				}*/
			}
		}
		
		this.markDirty();
		this.sync();
	}
	/*
	public ItemStack extractFrom(World world, BlockPos pos, int limit) {
		BlockState state = world.getBlockState(pos);
		if (state.getBlock() instanceof ConveyorBlock) return ItemStack.EMPTY; //Don't ever extract from conveyors
		if (state.getBlock() instanceof ChestBlock) {
			Inventory inv = ChestBlock.getInventory(state, world, pos, false);
			if (inv==null) return ItemStack.EMPTY;
			if (extractFrom(inv)) {
				markDirty();
				sync();
				return this.stack;
			} else {
				return ItemStack.EMPTY;
			}
		} else if (state.getBlock() instanceof InventoryProvider) {
			SidedInventory sided = ((InventoryProvider)state.getBlock()).getInventory(state, world, pos);
			if (sided==null) return ItemStack.EMPTY;
			
			
			
			
		}
		
		return ItemStack.EMPTY;
	}
	
	public boolean extractFrom(Inventory inv) {
		for(int i=0; i<inv.getInvSize(); i++) {
			ItemStack stack = inv.getInvStack(i);
			if (!stack.isEmpty()) {
				//Take some or all of the stack
				this.stack = stack.copy();
				inv.removeInvStack(i);
				delay = maxDelay;
				inv.markDirty();
				return true;
			}
		}
		return false;
	}
	
	public boolean extractFromSided(SidedInventory inv, Direction d) {
		for(int i : inv.getInvAvailableSlots(d)) {
			ItemStack stack = inv.getInvStack(i);
			if (!stack.isEmpty()) {
				//Take some or all of the stack
				this.stack = stack.copy();
				inv.removeInvStack(i);
				delay = maxDelay;
				inv.markDirty();
				return true;
			}
		}
		return false;
	}
	
	public boolean insertInto(Inventory inv) {
		for(int i=0; i<inv.getInvSize(); i++) {
			ItemStack stack = inv.getInvStack(i);
			if (stack.isEmpty()) {
				inv.setInvStack(i, this.stack);
				this.stack = ItemStack.EMPTY;
				inv.markDirty();
				return true;
			}
		}
		return false;
	}*/
	
	public void offerItemEntity(ItemEntity entity) {
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
}
