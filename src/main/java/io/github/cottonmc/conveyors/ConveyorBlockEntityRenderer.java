package io.github.cottonmc.conveyors;

import com.mojang.blaze3d.platform.GlStateManager;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.model.json.ModelTransformation;
import net.minecraft.item.ItemStack;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.Direction;

public class ConveyorBlockEntityRenderer extends BlockEntityRenderer<ConveyorBlockEntity> {
	@Override
	public void render(ConveyorBlockEntity conveyor, double x, double y, double z, float float_1, int int_1) {
		super.render(conveyor, x, y, z, float_1, int_1);
		
		Direction dir = conveyor.getCachedState().get(Properties.HORIZONTAL_FACING);
		
		ItemStack stack = conveyor.stack;
		
		if (!stack.isEmpty()) {
			GlStateManager.pushMatrix();
			
			float progress = -(conveyor.getProgress()-0.3f);
			
			GlStateManager.translatef((float)x + 0.5f, (float)y + 0.6f, (float)z + 0.5f);
			GlStateManager.translatef(progress*dir.getOffsetX(), progress*dir.getOffsetY(), progress*dir.getOffsetZ());
			GlStateManager.rotatef(90, 1, 0, 0);
			
			GlStateManager.scalef(0.8f, 0.8f, 0.8f);
			MinecraftClient.getInstance().getItemRenderer().renderItem(stack, ModelTransformation.Type.FIXED);
			GlStateManager.popMatrix();
			
		}
	}
}
