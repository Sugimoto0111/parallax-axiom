package dev.srryo.ultimatum.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.srryo.ultimatum.UltimatumMod;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
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
import org.joml.Matrix4f;

import java.io.IOException;
import java.io.InputStream;

/** Converts the supplied white pixel mask into four independently moving 3D rings. */
@Mod.EventBusSubscriber(modid = UltimatumMod.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class InvariantObserverItemRenderer extends BlockEntityWithoutLevelRenderer {
    private static final ResourceLocation MASK = new ResourceLocation(
            UltimatumMod.MOD_ID, "textures/item/invariant_observer.png");
    private static final int BAND_COUNT = 4;
    private static final float MASK_MIN = 0.045F;
    private static final float MASK_MAX = 0.955F;
    private static final float HALF_THICKNESS = 0.0125F;
    private static volatile VoxelMask voxelMask;

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
                voxelMask = null);
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context,
                             PoseStack poseStack, MultiBufferSource buffers,
                             int packedLight, int packedOverlay) {
        float time = (Util.getMillis() % 600_000L) / 1000.0F;
        VoxelMask mask = mask();
        VertexConsumer film = buffers.getBuffer(ObserverRenderTypes.FILM);

        for (int band = 0; band < BAND_COUNT; band++) {
            renderBand(poseStack, film, mask, time, band);
        }
    }

    private static void renderBand(PoseStack poseStack, VertexConsumer consumer,
                                   VoxelMask mask, float time, int band) {
        float phase = band * 1.917F + 0.41F;
        float horizontal = layeredDrift(time, phase,
                0.31F + band * 0.019F, 0.14F, 0.0070F);
        float vertical = layeredDrift(time, phase + 1.73F,
                0.27F + band * 0.015F, 0.12F, 0.0058F);
        float depth = 0.458F + band * 0.028F
                + Mth.sin(time * (0.19F + band * 0.011F) + phase) * 0.0035F;
        float rotation = Mth.sin(time * (0.23F + band * 0.014F) + phase * 0.77F)
                * (1.1F + band * 0.32F);

        poseStack.pushPose();
        poseStack.translate(0.5F + horizontal, 0.5F + vertical, depth);
        poseStack.mulPose(Axis.ZP.rotationDegrees(rotation));
        poseStack.translate(-0.5F, -0.5F, 0.0F);
        renderBandGeometry(consumer, poseStack.last().pose(), mask, time, band);
        poseStack.popPose();
    }

    private static void renderBandGeometry(VertexConsumer consumer, Matrix4f matrix,
                                           VoxelMask mask, float time, int band) {
        float pixelWidth = (MASK_MAX - MASK_MIN) / mask.width();
        float pixelHeight = (MASK_MAX - MASK_MIN) / mask.height();

        for (int y = 0; y < mask.height(); y++) {
            float top = MASK_MAX - y * pixelHeight;
            float bottom = top - pixelHeight;
            for (int x = 0; x < mask.width(); x++) {
                if (mask.bandAt(x, y) != band) {
                    continue;
                }

                float left = MASK_MIN + x * pixelWidth;
                float right = left + pixelWidth;
                int colour = filmColour(time, band, x, y, mask.width(), mask.height());

                faceQuad(consumer, matrix, left, bottom, right, top,
                        HALF_THICKNESS, colour, 84);
                faceQuad(consumer, matrix, right, bottom, left, top,
                        -HALF_THICKNESS, colour, 84);

                if (mask.bandAt(x - 1, y) != band) {
                    sideQuad(consumer, matrix, left, bottom, left, top, colour);
                }
                if (mask.bandAt(x + 1, y) != band) {
                    sideQuad(consumer, matrix, right, top, right, bottom, colour);
                }
                if (mask.bandAt(x, y - 1) != band) {
                    sideQuad(consumer, matrix, right, top, left, top, colour);
                }
                if (mask.bandAt(x, y + 1) != band) {
                    sideQuad(consumer, matrix, left, bottom, right, bottom, colour);
                }
            }
        }
    }

    private static void faceQuad(VertexConsumer consumer, Matrix4f matrix,
                                 float x0, float y0, float x1, float y1,
                                 float z, int colour, int alpha) {
        modelVertex(consumer, matrix, x0, y0, z, colour, alpha);
        modelVertex(consumer, matrix, x1, y0, z, colour, alpha);
        modelVertex(consumer, matrix, x1, y1, z, colour, alpha);
        modelVertex(consumer, matrix, x0, y1, z, colour, alpha);
    }

    private static void sideQuad(VertexConsumer consumer, Matrix4f matrix,
                                 float x0, float y0, float x1, float y1, int colour) {
        modelVertex(consumer, matrix, x0, y0, -HALF_THICKNESS, colour, 112);
        modelVertex(consumer, matrix, x1, y1, -HALF_THICKNESS, colour, 112);
        modelVertex(consumer, matrix, x1, y1, HALF_THICKNESS, colour, 112);
        modelVertex(consumer, matrix, x0, y0, HALF_THICKNESS, colour, 112);
    }

    private static void modelVertex(VertexConsumer consumer, Matrix4f matrix,
                                    float x, float y, float z, int colour, int alpha) {
        consumer.vertex(matrix, x, y, z)
                .color(red(colour), green(colour), blue(colour), alpha)
                .endVertex();
    }

    private static float layeredDrift(float time, float phase, float primarySpeed,
                                      float secondarySpeed, float amplitude) {
        float primary = Mth.sin(time * primarySpeed + phase);
        float secondary = Mth.sin(time * secondarySpeed + phase * 1.611F + 0.83F);
        return (primary * 0.7F + secondary * 0.3F) * amplitude;
    }

    private static int filmColour(float time, int band, int x, int y,
                                  int width, int height) {
        float relativeX = (x + 0.5F) / width - 0.5F;
        float relativeY = 0.5F - (y + 0.5F) / height;
        float radius = Mth.sqrt(relativeX * relativeX + relativeY * relativeY);
        float angle = Mth.positiveModulo(
                (float) (Math.atan2(relativeY, relativeX) / (Math.PI * 2.0)), 1.0F);
        float hue = Mth.positiveModulo(time * 0.036F + band * 0.17F
                + angle * 0.11F + radius * 0.19F, 1.0F);
        int film = Mth.hsvToRgb(hue, 0.42F, 0.96F);

        float sweepPhase = Mth.positiveModulo(angle - time * 0.115F
                + band * 0.21F, 1.0F);
        float sweepDistance = Math.min(sweepPhase, 1.0F - sweepPhase);
        float sweep = Mth.clamp((0.115F - sweepDistance) / 0.10F, 0.0F, 1.0F);
        sweep = sweep * sweep * (3.0F - 2.0F * sweep) * 0.38F;
        return colour(
                Mth.floor(Mth.lerp(sweep, red(film), 255.0F)),
                Mth.floor(Mth.lerp(sweep, green(film), 255.0F)),
                Mth.floor(Mth.lerp(sweep, blue(film), 255.0F)));
    }

    private static int colour(int red, int green, int blue) {
        return red << 16 | green << 8 | blue;
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

    private static VoxelMask mask() {
        VoxelMask current = voxelMask;
        if (current != null) {
            return current;
        }
        synchronized (InvariantObserverItemRenderer.class) {
            current = voxelMask;
            if (current == null) {
                current = loadMask();
                voxelMask = current;
            }
        }
        return current;
    }

    private static VoxelMask loadMask() {
        try (InputStream stream = Minecraft.getInstance().getResourceManager().open(MASK);
             NativeImage image = NativeImage.read(stream)) {
            byte[][] bands = new byte[image.getHeight()][image.getWidth()];
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    if (FastColor.ABGR32.alpha(image.getPixelRGBA(x, y)) < 26) {
                        bands[y][x] = -1;
                        continue;
                    }
                    bands[y][x] = (byte) radialBand(x, y,
                            image.getWidth(), image.getHeight());
                }
            }
            return new VoxelMask(image.getWidth(), image.getHeight(), bands);
        } catch (IOException error) {
            UltimatumMod.LOGGER.error("Could not build Invariant Observer's voxel mask", error);
            return VoxelMask.EMPTY;
        }
    }

    private static int radialBand(int x, int y, int width, int height) {
        float relativeX = (x + 0.5F) / width - 0.5F;
        float relativeY = (y + 0.5F) / height - 0.5F;
        float radius = Mth.sqrt(relativeX * relativeX + relativeY * relativeY);
        if (radius >= 0.395F) {
            return 0;
        }
        if (radius >= 0.285F) {
            return 1;
        }
        if (radius >= 0.165F) {
            return 2;
        }
        return 3;
    }

    private record VoxelMask(int width, int height, byte[][] bands) {
        private static final VoxelMask EMPTY = new VoxelMask(1, 1,
                new byte[][]{{-1}});

        private int bandAt(int x, int y) {
            return x >= 0 && y >= 0 && x < width && y < height ? bands[y][x] : -1;
        }
    }
}
