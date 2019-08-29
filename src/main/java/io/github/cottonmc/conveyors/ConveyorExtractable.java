package io.github.cottonmc.conveyors;

import alexiil.mc.lib.attributes.Simulation;
import alexiil.mc.lib.attributes.item.ItemExtractable;
import alexiil.mc.lib.attributes.item.filter.ItemFilter;
import net.minecraft.item.ItemStack;

public class ConveyorExtractable implements ItemExtractable {
	protected final ConveyorBlockEntity delegate;
	
	public ConveyorExtractable(ConveyorBlockEntity delegate) {
		this.delegate = delegate;
	}
	
	@Override
	public ItemStack attemptExtraction(ItemFilter filter, int maxAmount, Simulation simulation) {
		if (!delegate.stack.isEmpty()) {
			if (filter.matches(delegate.stack)) {
				if (maxAmount>delegate.stack.getCount()) {
					ItemStack extracted = delegate.stack.copy();
					extracted.setCount(maxAmount);
					
					delegate.stack.decrement(maxAmount);
					delegate.markDirty();
					delegate.sync();
					
					return extracted;
				} else {
					ItemStack extracted = delegate.stack;
					
					delegate.stack = null;
					delegate.markDirty();
					delegate.sync();
					
					return extracted;
				}
			}
		}
		
		return ItemStack.EMPTY;
	}

}
