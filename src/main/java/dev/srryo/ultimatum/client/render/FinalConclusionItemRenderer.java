package dev.srryo.ultimatum.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Draws Final Conclusion as a transparent silhouette traced by the same kind
 * of thin additive line used by the observer array's halo.
 */
public final class FinalConclusionItemRenderer extends BlockEntityWithoutLevelRenderer {
    private static final float CENTRE_Z = 0.500F;
    private static final float HALF_DEPTH = 0.055F;
    private static final int[] DEPTH_ALPHA = {76, 24, 20, 18, 20, 24, 76};

    public FinalConclusionItemRenderer() {
        this(Minecraft.getInstance().getBlockEntityRenderDispatcher(),
                Minecraft.getInstance().getEntityModels());
    }

    private FinalConclusionItemRenderer(BlockEntityRenderDispatcher dispatcher,
                                        EntityModelSet modelSet) {
        super(dispatcher, modelSet);
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context,
                             PoseStack poseStack, MultiBufferSource buffers,
                             int packedLight, int packedOverlay) {
        float time = (Util.getMillis() % 600_000L) / 1000.0F;
        // The contour is permanently at its full emissive state. Attack animation no
        // longer changes brightness; only the trace and film colours keep moving.
        FinalConclusionShaders.prepare(time, 1.0F);
        VertexConsumer trace = buffers.getBuffer(FinalConclusionRenderTypes.TRACE);

        // Seven contour slices occupy roughly the depth of a generated vanilla item.
        // Their combined face-on opacity matches the old single line, while an oblique
        // view separates them into a transparent wire-like volume.
        for (int layer = 0; layer < DEPTH_ALPHA.length; layer++) {
            float progress = layer / (float) (DEPTH_ALPHA.length - 1);
            float phase = layer * 2.431F + 0.73F;
            float driftX = layeredDrift(time, phase, 0.43F, 0.19F, 0.0140F);
            float driftY = layeredDrift(time, phase + 1.67F,
                    0.37F, 0.23F, 0.0115F);
            float depthDrift = layeredDrift(time, phase + 3.11F,
                    0.29F, 0.17F, 0.0065F);
            float depth = Mth.lerp(progress, CENTRE_Z - HALF_DEPTH,
                    CENTRE_Z + HALF_DEPTH) + depthDrift;
            int colour = filmColor(time, layer * 0.13F);
            texturedPlane(poseStack, trace, driftX, driftY, depth,
                    red(colour), green(colour), blue(colour), DEPTH_ALPHA[layer],
                    LightTexture.FULL_BRIGHT, packedOverlay);
        }

        // The two delayed refraction echoes sit just outside the front and back faces,
        // making their separation visible without filling the transparent centre.
        float driftX = Mth.sin(time * 1.13F) * 0.006F;
        float driftY = Mth.cos(time * 0.97F) * 0.005F;
        int rearColour = filmColor(time, 0.81F);
        int frontColour = filmColor(time, 0.37F);
        texturedPlane(poseStack, trace, driftX - 0.006F, driftY + 0.004F,
                CENTRE_Z - HALF_DEPTH - 0.008F,
                red(rearColour), green(rearColour), blue(rearColour), 42,
                LightTexture.FULL_BRIGHT, packedOverlay);
        texturedPlane(poseStack, trace, -driftX + 0.005F, -driftY - 0.003F,
                CENTRE_Z + HALF_DEPTH + 0.008F,
                red(frontColour), green(frontColour), blue(frontColour), 27,
                LightTexture.FULL_BRIGHT, packedOverlay);
    }

    private static float layeredDrift(float time, float phase, float primarySpeed,
                                      float secondarySpeed, float amplitude) {
        float primary = Mth.sin(time * primarySpeed + phase);
        float secondary = Mth.sin(time * secondarySpeed + phase * 1.783F + 0.91F);
        return (primary * 0.68F + secondary * 0.32F) * amplitude;
    }

    private static int filmColor(float time, float offset) {
        // ObserverArrayRenderer advances the same palette by 0.0018 hue per tick.
        // Item rendering uses seconds, so 20 ticks per second gives 0.036 here.
        float hue = Mth.positiveModulo(offset + time * 0.036F, 1.0F);
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

    private static void texturedPlane(PoseStack poseStack, VertexConsumer consumer,
                                      float offsetX, float offsetY, float depth,
                                      int red, int green, int blue, int alpha,
                                      int packedLight, int packedOverlay) {
        Matrix4f pose = poseStack.last().pose();
        Matrix3f normal = poseStack.last().normal();
        float minX = offsetX;
        float minY = offsetY;
        float maxX = 1.0F + offsetX;
        float maxY = 1.0F + offsetY;

        vertex(consumer, pose, normal, minX, minY, depth, 0.0F, 1.0F,
                red, green, blue, alpha, packedLight, packedOverlay, 1.0F);
        vertex(consumer, pose, normal, maxX, minY, depth, 1.0F, 1.0F,
                red, green, blue, alpha, packedLight, packedOverlay, 1.0F);
        vertex(consumer, pose, normal, maxX, maxY, depth, 1.0F, 0.0F,
                red, green, blue, alpha, packedLight, packedOverlay, 1.0F);
        vertex(consumer, pose, normal, minX, maxY, depth, 0.0F, 0.0F,
                red, green, blue, alpha, packedLight, packedOverlay, 1.0F);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f pose,
                               Matrix3f normal, float x, float y, float z,
                               float u, float v, int red, int green, int blue,
                               int alpha, int packedLight, int packedOverlay,
                               float normalZ) {
        consumer.vertex(pose, x, y, z)
                .color(red, green, blue, alpha)
                .uv(u, v)
                .overlayCoords(packedOverlay)
                .uv2(packedLight)
                .normal(normal, 0.0F, 0.0F, normalZ)
                .endVertex();
    }
}
