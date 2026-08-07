package dev.srryo.ultimatum.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** Renders the supplied white mask as four independently drifting optical depths. */
public final class InvariantObserverItemRenderer extends BlockEntityWithoutLevelRenderer {
    private static final int BAND_COUNT = 4;
    private static final float MASK_MIN = 0.045F;
    private static final float MASK_MAX = 0.955F;

    public InvariantObserverItemRenderer() {
        this(Minecraft.getInstance().getBlockEntityRenderDispatcher(),
                Minecraft.getInstance().getEntityModels());
    }

    private InvariantObserverItemRenderer(BlockEntityRenderDispatcher dispatcher,
                                          EntityModelSet modelSet) {
        super(dispatcher, modelSet);
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context,
                             PoseStack poseStack, MultiBufferSource buffers,
                             int packedLight, int packedOverlay) {
        float time = (Util.getMillis() % 600_000L) / 1000.0F;
        InvariantObserverShaders.prepare(time);
        VertexConsumer trace = buffers.getBuffer(InvariantObserverRenderTypes.TRACE);

        // Each radial part of the mask occupies its own plane. Their offsets are small
        // enough to preserve the icon, but make its rings search for a common focus.
        for (int band = 0; band < BAND_COUNT; band++) {
            float phase = band * 1.917F + 0.41F;
            renderBand(poseStack, trace, time, band, phase,
                    44, 1.55F, packedOverlay);
            renderBand(poseStack, trace, time, band, phase,
                    224, 1.0F, packedOverlay);
        }
    }

    private static void renderBand(PoseStack poseStack, VertexConsumer consumer,
                                   float time, int band, float phase,
                                   int alpha, float echoScale, int packedOverlay) {
        float horizontal = layeredDrift(time, phase,
                0.31F + band * 0.019F, 0.14F, 0.0085F) * echoScale;
        float vertical = layeredDrift(time, phase + 1.73F,
                0.27F + band * 0.015F, 0.12F, 0.0070F) * echoScale;
        float depth = 0.465F + band * 0.023F
                + Mth.sin(time * (0.19F + band * 0.011F) + phase) * 0.0035F;
        float rotation = Mth.sin(time * (0.23F + band * 0.014F) + phase * 0.77F)
                * (1.2F + band * 0.35F) * echoScale;

        poseStack.pushPose();
        poseStack.translate(0.5F + horizontal, 0.5F + vertical, depth);
        poseStack.mulPose(Axis.ZP.rotationDegrees(rotation));
        poseStack.translate(-0.5F, -0.5F, 0.0F);
        texturedPlane(poseStack, consumer, band, alpha,
                LightTexture.FULL_BRIGHT, packedOverlay);
        poseStack.popPose();
    }

    private static float layeredDrift(float time, float phase, float primarySpeed,
                                      float secondarySpeed, float amplitude) {
        float primary = Mth.sin(time * primarySpeed + phase);
        float secondary = Mth.sin(time * secondarySpeed + phase * 1.611F + 0.83F);
        return (primary * 0.7F + secondary * 0.3F) * amplitude;
    }

    private static void texturedPlane(PoseStack poseStack, VertexConsumer consumer,
                                      int band, int alpha,
                                      int packedLight, int packedOverlay) {
        Matrix4f pose = poseStack.last().pose();
        Matrix3f normal = poseStack.last().normal();
        int bandCode = Mth.floor(band * (255.0F / (BAND_COUNT - 1)));

        vertex(consumer, pose, normal, MASK_MIN, MASK_MIN, 0.0F,
                0.0F, 1.0F, bandCode, alpha, packedLight, packedOverlay);
        vertex(consumer, pose, normal, MASK_MAX, MASK_MIN, 0.0F,
                1.0F, 1.0F, bandCode, alpha, packedLight, packedOverlay);
        vertex(consumer, pose, normal, MASK_MAX, MASK_MAX, 0.0F,
                1.0F, 0.0F, bandCode, alpha, packedLight, packedOverlay);
        vertex(consumer, pose, normal, MASK_MIN, MASK_MAX, 0.0F,
                0.0F, 0.0F, bandCode, alpha, packedLight, packedOverlay);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f pose, Matrix3f normal,
                               float x, float y, float z, float u, float v,
                               int bandCode, int alpha, int packedLight, int packedOverlay) {
        consumer.vertex(pose, x, y, z)
                .color(bandCode, 255, 255, alpha)
                .uv(u, v)
                .overlayCoords(packedOverlay)
                .uv2(packedLight)
                .normal(normal, 0.0F, 0.0F, 1.0F)
                .endVertex();
    }
}
