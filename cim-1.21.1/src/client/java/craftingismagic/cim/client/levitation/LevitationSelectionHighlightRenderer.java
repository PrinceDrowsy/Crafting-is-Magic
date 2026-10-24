package craftingismagic.cim.client.levitation;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector4f;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * Outlines the whole in-progress Levitation selection as a single box around its combined
 * bounding box (not one outline per block), visible through terrain so the player can see what
 * they're about to pick up without needing line of sight to every block in it.
 *
 * <p>This draws in HUD/screen space rather than world space, projecting the box's corners into
 * screen coordinates itself (using the world's projection/view matrices captured from a {@code
 * WorldRenderEvents} hook, since only reading them - not drawing - is needed there) and drawing
 * during {@link HudRenderCallback}. Earlier attempts drawing directly in world space (a manual
 * {@code Tessellator}, then a proper vanilla-style custom {@code RenderLayer} through the world
 * frame's own consumers) produced no visible output despite running every frame with correct
 * coordinates; a later attempt at a custom {@code RenderLayer} even in THIS HUD stage also had no
 * effect despite (confirmed via logging) valid on-screen coordinates - a plain {@link
 * DrawContext#fill} call proved HUD rendering itself composites fine here, isolating the problem
 * to custom {@code RenderLayer}s specifically (quite possibly this mod's heavy use of Veil's own
 * rendering pipeline interacting with them in some way). So each of the box's 12 edges is instead
 * drawn as a thin rotated rectangle via repeated {@code fill()} calls (translating/rotating the
 * matrix stack per edge) - the exact same primitive already proven to render correctly.
 */
public final class LevitationSelectionHighlightRenderer {
	/** Every corner-pair index into the box's 8 corners that forms one of its 12 edges. */
	private static final int[][] EDGES = {
			{0, 1}, {1, 2}, {2, 3}, {3, 0},
			{4, 5}, {5, 6}, {6, 7}, {7, 4},
			{0, 4}, {1, 5}, {2, 6}, {3, 7},
	};

	private static final int LINE_COLOR = 0xE6FF59D9;
	private static final int LINE_THICKNESS = 2;

	private static Matrix4f capturedViewProjection;
	private static Vec3d capturedCameraPos = Vec3d.ZERO;

	private LevitationSelectionHighlightRenderer() {
	}

	public static void register() {
		WorldRenderEvents.LAST.register(context -> {
			capturedViewProjection = new Matrix4f(context.projectionMatrix()).mul(context.positionMatrix());
			capturedCameraPos = context.camera().getPos();
		});

		HudRenderCallback.EVENT.register((context, tickCounter) -> render(context));
	}

	private static void render(DrawContext context) {
		if (!LevitationClientState.isSelecting() || capturedViewProjection == null || MinecraftClient.getInstance().options.hudHidden) {
			return;
		}

		BlockPos min = LevitationClientState.getSelectionMin();
		BlockPos max = LevitationClientState.getSelectionMax();
		if (min == null || max == null) {
			return;
		}

		MinecraftClient client = MinecraftClient.getInstance();
		int screenWidth = client.getWindow().getScaledWidth();
		int screenHeight = client.getWindow().getScaledHeight();

		double[][] boxCorners = {
				{min.getX(), min.getY(), min.getZ()},
				{max.getX() + 1.0, min.getY(), min.getZ()},
				{max.getX() + 1.0, min.getY(), max.getZ() + 1.0},
				{min.getX(), min.getY(), max.getZ() + 1.0},
				{min.getX(), max.getY() + 1.0, min.getZ()},
				{max.getX() + 1.0, max.getY() + 1.0, min.getZ()},
				{max.getX() + 1.0, max.getY() + 1.0, max.getZ() + 1.0},
				{min.getX(), max.getY() + 1.0, max.getZ() + 1.0},
		};

		float[] screenX = new float[8];
		float[] screenY = new float[8];
		boolean[] valid = new boolean[8];

		for (int i = 0; i < 8; i++) {
			Vector4f clip = new Vector4f(
					(float) (boxCorners[i][0] - capturedCameraPos.x),
					(float) (boxCorners[i][1] - capturedCameraPos.y),
					(float) (boxCorners[i][2] - capturedCameraPos.z),
					1.0F);
			capturedViewProjection.transform(clip);

			if (clip.w() < 0.00001F) {
				valid[i] = false;
				continue;
			}

			valid[i] = true;
			float ndcX = clip.x() / clip.w();
			float ndcY = clip.y() / clip.w();
			screenX[i] = (ndcX * 0.5F + 0.5F) * screenWidth;
			screenY[i] = (1.0F - (ndcY * 0.5F + 0.5F)) * screenHeight;
		}

		MatrixStack matrices = context.getMatrices();

		for (int[] edge : EDGES) {
			int a = edge[0];
			int b = edge[1];
			if (!valid[a] || !valid[b]) {
				continue;
			}

			float dx = screenX[b] - screenX[a];
			float dy = screenY[b] - screenY[a];
			float length = (float) Math.sqrt(dx * dx + dy * dy);
			if (length < 0.5F) {
				continue;
			}
			float angle = (float) Math.atan2(dy, dx);

			matrices.push();
			matrices.translate((screenX[a] + screenX[b]) / 2.0F, (screenY[a] + screenY[b]) / 2.0F, 0);
			matrices.multiply(new Quaternionf().rotateZ(angle));
			int half = LINE_THICKNESS / 2;
			context.fill(-(int) (length / 2), -half, (int) (length / 2) + 1, half + 1, LINE_COLOR);
			matrices.pop();
		}
	}
}
