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
    private static final int[][] DEPTH_COLOUR = {
            {118, 224, 255},
            {146, 198, 255},
            {190, 172, 255},
            {238, 232, 255},
            {208, 178, 255},
            {154, 206, 255},
            {126, 234, 255}
    };

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
        float attack = attackPulse(stack);
        FinalConclusionShaders.prepare(time, attack);
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
            float whiteBlend = 0.16F
                    + (Mth.sin(time * 0.31F + phase * 0.83F) * 0.5F + 0.5F) * 0.22F;
            int[] baseColour = DEPTH_COLOUR[layer];
            int red = Mth.floor(Mth.lerp(whiteBlend, baseColour[0], 255.0F));
            int green = Mth.floor(Mth.lerp(whiteBlend, baseColour[1], 255.0F));
            int blue = Mth.floor(Mth.lerp(whiteBlend, baseColour[2], 255.0F));
            texturedPlane(poseStack, trace, driftX, driftY, depth,
                    red, green, blue, DEPTH_ALPHA[layer],
                    LightTexture.FULL_BRIGHT, packedOverlay);
        }

        // The two delayed refraction echoes sit just outside the front and back faces,
        // making their separation visible without filling the transparent centre.
        float driftX = Mth.sin(time * 1.13F) * 0.006F;
        float driftY = Mth.cos(time * 0.97F) * 0.005F;
        texturedPlane(poseStack, trace, driftX - 0.006F, driftY + 0.004F,
                CENTRE_Z - HALF_DEPTH - 0.008F,
                190, 190, 190, 42, LightTexture.FULL_BRIGHT, packedOverlay);
        texturedPlane(poseStack, trace, -driftX + 0.005F, -driftY - 0.003F,
                CENTRE_Z + HALF_DEPTH + 0.008F,
                112, 112, 112, 27, LightTexture.FULL_BRIGHT, packedOverlay);
    }

    private static float layeredDrift(float time, float phase, float primarySpeed,
                                      float secondarySpeed, float amplitude) {
        float primary = Mth.sin(time * primarySpeed + phase);
        float secondary = Mth.sin(time * secondarySpeed + phase * 1.783F + 0.91F);
        return (primary * 0.68F + secondary * 0.32F) * amplitude;
    }

    private static float attackPulse(ItemStack renderedStack) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return 0.0F;
        }
        boolean held = ItemStack.isSameItemSameTags(
                minecraft.player.getMainHandItem(), renderedStack)
                || ItemStack.isSameItemSameTags(
                minecraft.player.getOffhandItem(), renderedStack);
        if (!held) {
            return 0.0F;
        }
        return Mth.sin(minecraft.player.getAttackAnim(minecraft.getFrameTime()) * Mth.PI);
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
