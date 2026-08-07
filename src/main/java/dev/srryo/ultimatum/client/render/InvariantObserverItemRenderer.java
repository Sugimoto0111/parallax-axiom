package dev.srryo.ultimatum.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.srryo.ultimatum.UltimatumMod;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.io.IOException;
import java.io.InputStream;

/** Uses Final Conclusion's layered contour treatment for the Observer's full mask. */
@Mod.EventBusSubscriber(modid = UltimatumMod.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class InvariantObserverItemRenderer extends BlockEntityWithoutLevelRenderer {
    private static final ResourceLocation MASK = new ResourceLocation(
            UltimatumMod.MOD_ID, "textures/item/invariant_observer.png");
    private static final float MASK_MIN = 0.045F;
    private static final float MASK_MAX = 0.955F;
    private static final float CENTRE_Z = 0.500F;
    private static final float HALF_DEPTH = 0.052F;
    private static final int SIDE_ALPHA = 42;
    private static final int[] DEPTH_ALPHA = {76, 24, 20, 18, 20, 24, 76};
    private static volatile Silhouette silhouette;

    public InvariantObserverItemRenderer() {
        this(Minecraft.getInstance().getBlockEntityRenderDispatcher(),
                Minecraft.getInstance().getEntityModels());
    }

    private InvariantObserverItemRenderer(BlockEntityRenderDispatcher dispatcher,
                                          EntityModelSet modelSet) {
        super(dispatcher, modelSet);
    }

    @SubscribeEvent
    public static void registerReloadListener(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) resourceManager ->
                silhouette = null);
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context,
                             PoseStack poseStack, MultiBufferSource buffers,
                             int packedLight, int packedOverlay) {
        float time = (Util.getMillis() % 600_000L) / 1000.0F;
        InvariantObserverShaders.prepare(time);
        VertexConsumer trace = buffers.getBuffer(InvariantObserverRenderTypes.TRACE);

        // Keep the complete supplied image on every plane, exactly like Final
        // Conclusion. Nothing is divided by radius, so incomplete rings remain intact.
        for (int layer = 0; layer < DEPTH_ALPHA.length; layer++) {
            float progress = layer / (float) (DEPTH_ALPHA.length - 1);
            float phase = layerPhase(layer);
            float driftX = layerX(time, phase);
            float driftY = layerY(time, phase);
            float depth = layerDepth(time, phase, progress);
            texturedPlane(poseStack, trace, driftX, driftY, depth,
                    255, 255, 255, DEPTH_ALPHA[layer],
                    LightTexture.FULL_BRIGHT, packedOverlay);
        }

        float echoX = Mth.sin(time * 1.07F) * 0.005F;
        float echoY = Mth.cos(time * 0.91F) * 0.004F;
        texturedPlane(poseStack, trace, echoX - 0.005F, echoY + 0.003F,
                CENTRE_Z - HALF_DEPTH - 0.007F,
                255, 255, 255, 38,
                LightTexture.FULL_BRIGHT, packedOverlay);
        texturedPlane(poseStack, trace, -echoX + 0.004F, -echoY - 0.003F,
                CENTRE_Z + HALF_DEPTH + 0.007F,
                255, 255, 255, 25,
                LightTexture.FULL_BRIGHT, packedOverlay);

        // As on the sword, the alpha boundary alone connects the moving outer planes.
        // Request this different vertex format only after every textured pass is done.
        int lastLayer = DEPTH_ALPHA.length - 1;
        float rearPhase = layerPhase(0);
        float frontPhase = layerPhase(lastLayer);
        int sideColour = filmColor(time);
        renderSideShell(poseStack, buffers.getBuffer(ObserverRenderTypes.FILM), silhouette(),
                layerX(time, rearPhase), layerY(time, rearPhase),
                layerDepth(time, rearPhase, 0.0F), sideColour,
                layerX(time, frontPhase), layerY(time, frontPhase),
                layerDepth(time, frontPhase, 1.0F),
                sideColour);
    }

    private static float layerPhase(int layer) {
        return layer * 2.431F + 0.73F;
    }

    private static float layerX(float time, float phase) {
        return layeredDrift(time, phase, 0.41F, 0.18F, 0.0120F);
    }

    private static float layerY(float time, float phase) {
        return layeredDrift(time, phase + 1.67F, 0.35F, 0.22F, 0.0100F);
    }

    private static float layerDepth(float time, float phase, float progress) {
        float drift = layeredDrift(time, phase + 3.11F,
                0.28F, 0.16F, 0.0058F);
        return Mth.lerp(progress, CENTRE_Z - HALF_DEPTH,
                CENTRE_Z + HALF_DEPTH) + drift;
    }

    private static float layeredDrift(float time, float phase, float primarySpeed,
                                      float secondarySpeed, float amplitude) {
        float primary = Mth.sin(time * primarySpeed + phase);
        float secondary = Mth.sin(time * secondarySpeed + phase * 1.783F + 0.91F);
        return (primary * 0.68F + secondary * 0.32F) * amplitude;
    }

    private static int filmColor(float time) {
        float hue = Mth.positiveModulo(time * 0.036F, 1.0F);
        return Mth.hsvToRgb(hue, 0.42F, 0.96F);
    }

    private static int red(int colour) {
        return colour >> 16 & 255;
    }

    private static int green(int colour) {
        return colour >> 8 & 255;
    }

    private static int blue(int colour) {
        return colour & 255;
    }

    private static void renderSideShell(PoseStack poseStack, VertexConsumer consumer,
                                        Silhouette mask,
                                        float rearX, float rearY, float rearZ, int rearColour,
                                        float frontX, float frontY, float frontZ, int frontColour) {
        Matrix4f matrix = poseStack.last().pose();
        float pixelWidth = (MASK_MAX - MASK_MIN) / mask.width();
        float pixelHeight = (MASK_MAX - MASK_MIN) / mask.height();

        for (int y = 0; y < mask.height(); y++) {
            float top = MASK_MAX - y * pixelHeight;
            float bottom = top - pixelHeight;
            for (int x = 0; x < mask.width(); x++) {
                if (!mask.solid(x, y)) {
                    continue;
                }
                float left = MASK_MIN + x * pixelWidth;
                float right = left + pixelWidth;
                if (!mask.solid(x - 1, y)) {
                    sideQuad(consumer, matrix, left, bottom, left, top,
                            rearX, rearY, rearZ, rearColour,
                            frontX, frontY, frontZ, frontColour);
                }
                if (!mask.solid(x + 1, y)) {
                    sideQuad(consumer, matrix, right, top, right, bottom,
                            rearX, rearY, rearZ, rearColour,
                            frontX, frontY, frontZ, frontColour);
                }
                if (!mask.solid(x, y - 1)) {
                    sideQuad(consumer, matrix, right, top, left, top,
                            rearX, rearY, rearZ, rearColour,
                            frontX, frontY, frontZ, frontColour);
                }
                if (!mask.solid(x, y + 1)) {
                    sideQuad(consumer, matrix, left, bottom, right, bottom,
                            rearX, rearY, rearZ, rearColour,
                            frontX, frontY, frontZ, frontColour);
                }
            }
        }
    }

    private static void sideQuad(VertexConsumer consumer, Matrix4f matrix,
                                 float x0, float y0, float x1, float y1,
                                 float rearX, float rearY, float rearZ, int rearColour,
                                 float frontX, float frontY, float frontZ, int frontColour) {
        sideVertex(consumer, matrix, x0 + rearX, y0 + rearY, rearZ, rearColour);
        sideVertex(consumer, matrix, x1 + rearX, y1 + rearY, rearZ, rearColour);
        sideVertex(consumer, matrix, x1 + frontX, y1 + frontY, frontZ, frontColour);
        sideVertex(consumer, matrix, x0 + frontX, y0 + frontY, frontZ, frontColour);
    }

    private static void sideVertex(VertexConsumer consumer, Matrix4f matrix,
                                   float x, float y, float z, int colour) {
        consumer.vertex(matrix, x, y, z)
                .color(red(colour), green(colour), blue(colour), SIDE_ALPHA)
                .endVertex();
    }

    private static Silhouette silhouette() {
        Silhouette current = silhouette;
        if (current != null) {
            return current;
        }
        synchronized (InvariantObserverItemRenderer.class) {
            current = silhouette;
            if (current == null) {
                current = loadSilhouette();
                silhouette = current;
            }
        }
        return current;
    }

    private static Silhouette loadSilhouette() {
        try (InputStream stream = Minecraft.getInstance().getResourceManager().open(MASK);
             NativeImage image = NativeImage.read(stream)) {
            boolean[][] pixels = new boolean[image.getHeight()][image.getWidth()];
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    pixels[y][x] = FastColor.ABGR32.alpha(image.getPixelRGBA(x, y)) >= 26;
                }
            }
            return new Silhouette(image.getWidth(), image.getHeight(), pixels);
        } catch (IOException error) {
            UltimatumMod.LOGGER.error("Could not read Invariant Observer's silhouette mask",
                    error);
            return Silhouette.EMPTY;
        }
    }

    private static void texturedPlane(PoseStack poseStack, VertexConsumer consumer,
                                      float offsetX, float offsetY, float depth,
                                      int red, int green, int blue, int alpha,
                                      int packedLight, int packedOverlay) {
        Matrix4f pose = poseStack.last().pose();
        Matrix3f normal = poseStack.last().normal();

        vertex(consumer, pose, normal, MASK_MIN + offsetX, MASK_MIN + offsetY,
                depth, 0.0F, 1.0F, red, green, blue, alpha, packedLight, packedOverlay);
        vertex(consumer, pose, normal, MASK_MAX + offsetX, MASK_MIN + offsetY,
                depth, 1.0F, 1.0F, red, green, blue, alpha, packedLight, packedOverlay);
        vertex(consumer, pose, normal, MASK_MAX + offsetX, MASK_MAX + offsetY,
                depth, 1.0F, 0.0F, red, green, blue, alpha, packedLight, packedOverlay);
        vertex(consumer, pose, normal, MASK_MIN + offsetX, MASK_MAX + offsetY,
                depth, 0.0F, 0.0F, red, green, blue, alpha, packedLight, packedOverlay);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f pose, Matrix3f normal,
                               float x, float y, float z, float u, float v,
                               int red, int green, int blue, int alpha,
                               int packedLight, int packedOverlay) {
        consumer.vertex(pose, x, y, z)
                .color(red, green, blue, alpha)
                .uv(u, v)
                .overlayCoords(packedOverlay)
                .uv2(packedLight)
                .normal(normal, 0.0F, 0.0F, 1.0F)
                .endVertex();
    }

    private record Silhouette(int width, int height, boolean[][] pixels) {
        private static final Silhouette EMPTY = new Silhouette(1, 1,
                new boolean[][]{{false}});

        private boolean solid(int x, int y) {
            return x >= 0 && y >= 0 && x < width && y < height && pixels[y][x];
        }
    }
}
