package craftingismagic.cim.client.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Mod Menu's entrypoint (fabric.mod.json's "modmenu" key) - only Mod Menu ever queries it, so it's
 * inherently safe without Mod Menu installed. The YACL check keeps {@link CimConfigScreen} (which
 * references YACL classes) from ever loading when YACL itself is missing: Mod Menu then simply shows
 * no config button, and the JSON files in the config folder still work.
 */
public final class CimModMenu implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		if (!FabricLoader.getInstance().isModLoaded("yet_another_config_lib_v3")) {
			return parent -> null;
		}
		return CimConfigScreen::create;
	}
}
