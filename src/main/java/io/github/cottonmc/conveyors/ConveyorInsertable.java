package io.github.cottonmc.conveyors;

import alexiil.mc.lib.attributes.Simulation;
import alexiil.mc.lib.attributes.item.ItemInsertable;
import net.minecraft.item.ItemStack;

public class ConveyorInsertable implements ItemInsertable {
	protected final ConveyorBlockEntity delegate;
	
	public ConveyorInsertable(ConveyorBlockEntity delegate) {
		this.delegate = delegate;
	}
	
	@Override
	public ItemStack attemptInsertion(ItemStack stack, Simulation simulation) {
		if (delegate.stack.isEmpty()) {
			if (simulation==Simulation.ACTION) {
				delegate.stack = stack.copy();
				delegate.delay = delegate.maxDelay;
				delegate.markDirty();
				delegate.sync();
			}
			return ItemStack.EMPTY;
		} else {
			return stack;
		}
	}

}
