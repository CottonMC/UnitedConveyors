package io.github.cottonmc.conveyors;

import net.fabricmc.fabric.api.block.entity.BlockEntityClientSerializable;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.inventory.Inventory;
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
		boolean dirty = false;
		boolean update = false;
		if (delay>0) {
			delay--;
			dirty = true;
		}
		
		if (delay>0) {
			//if we're still in cooldown, just cool down.
			if (dirty) markDirty();
			return;
		}
		
		if (world.isClient) {
			if (dirty) markDirty();
			return;
		}
		
		if (!this.stack.isEmpty()) {
			BlockState state = this.getCachedState();
			if (state.getBlock() instanceof ConveyorBlock) {
				Direction facing = state.get(Properties.HORIZONTAL_FACING);
				
				BlockPos ahead = pos.offset(facing);
				BlockState aheadState = world.getBlockState(ahead);
				if (aheadState.getBlock() instanceof ChestBlock) {
					Inventory inv = ChestBlock.getInventory(aheadState, world, ahead, false); //that last boolean is whether to override chest blocking
					if (insertInto(inv)) {
						dirty = true;
						update = true;
						this.sync();
					}
				} else {
					BlockEntity be = world.getBlockEntity(ahead);
					if (be!=null) {
						if (be instanceof ConveyorBlockEntity) {
							ConveyorBlockEntity other = (ConveyorBlockEntity) be;
							if (other.stack.isEmpty()) {
								other.stack = stack.copy();
								other.delay = other.maxDelay;
								this.stack = ItemStack.EMPTY;
								dirty = true;
								update = true;
								other.sync();
								sync(); //Shouldn't be needed but is failing otherwise
							}
						} else if (be instanceof Inventory) {
							if (insertInto((Inventory) be)) {
								dirty = true;
								update = true;
								this.sync();
							}
						}
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
				
				if (behindState.getBlock() instanceof ChestBlock) {
					Inventory inv = ChestBlock.getInventory(behindState, world, behind, false);
					if (extractFrom(inv)) {
						dirty = true;
						update = true;
						//System.out.println("Extracted "+stack);
					}
				} else {
					BlockEntity be = world.getBlockEntity(behind);
					if (be!=null && be instanceof Inventory) {
						if (extractFrom((Inventory) be)) {
							dirty = true;
							update = true;
						}
					}
				}
			}
		}
		
		if (dirty) this.markDirty();
		if (update) this.sync();
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
