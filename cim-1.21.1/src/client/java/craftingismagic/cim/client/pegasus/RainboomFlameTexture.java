package craftingismagic.cim.client.pegasus;

import java.io.IOException;
import java.io.InputStream;

import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;

import craftingismagic.cim.Cim;

/**
 * The full 32-frame vanilla fire animation strip (16x512), re-registered under our own id as a
 * plain, non-animated texture for {@code rainbow_ring.fsh} to sample - the shader picks frames
 * itself, so it needs the whole strip, not one frame.
 * <p>
 * Binding {@code minecraft:textures/block/fire_0.png} directly isn't safe: it ships with a {@code
 * .mcmeta} animation, and GeckoLib's {@code TextureManagerMixin} replaces any standalone texture
 * with one of those with its own single-frame {@code AnimatableTexture} (16x16, animated by
 * GeckoLib) - against which the whole ring band reads one texel row and the flame tongues vanish.
 * Building our own {@link NativeImageBackedTexture} from the raw image bypasses that load path
 * entirely (for GeckoLib or any other mod doing the same), without redistributing the vanilla
 * asset - it's read from whatever resource pack stack is active, re-read on every resource reload.
 */
public final class RainboomFlameTexture {
	public static final Identifier ID = Identifier.of(Cim.MOD_ID, "rainboom_flame_strip");
	private static final Identifier SOURCE = Identifier.of("minecraft", "textures/block/fire_0.png");

	private RainboomFlameTexture() {
	}

	public static void register() {
		ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
			@Override
			public Identifier getFabricId() {
				return ID;
			}

			@Override
			public void reload(ResourceManager manager) {
				try (InputStream stream = manager.open(SOURCE)) {
					MinecraftClient.getInstance().getTextureManager().registerTexture(ID, new NativeImageBackedTexture(NativeImage.read(stream)));
				} catch (IOException e) {
					Cim.LOGGER.error("Failed to load the Sonic Rainboom flame strip from {}", SOURCE, e);
				}
			}
		});
	}
}
