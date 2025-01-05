package io.github.princedrowsy.cim;

import org.quiltmc.loader.api.ModContainer;
import org.quiltmc.qsl.base.api.entrypoint.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.github.princedrowsy.cim.common.item.cimItems;

public class cim implements ModInitializer {
	public static final String MOD_ID = "cim";
    public static final Logger LOGGER = LoggerFactory.getLogger("Crafting is Magic");

    @Override
    public void onInitialize(ModContainer mod) {
		cimItems.registerModItems();
    }
}
