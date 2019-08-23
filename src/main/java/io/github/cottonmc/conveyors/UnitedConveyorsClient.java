package io.github.cottonmc.conveyors;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.render.BlockEntityRendererRegistry;

public class UnitedConveyorsClient implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		BlockEntityRendererRegistry.INSTANCE.register(ConveyorBlockEntity.class, new ConveyorBlockEntityRenderer());
	}

}
