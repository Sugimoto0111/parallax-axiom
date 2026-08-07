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

        // Delayed, low-opacity copies give the contour the halo's refracted-line
        // character without turning the transparent centre back into a filled blade.
        float driftX = Mth.sin(time * 1.13F) * 0.006F;
        float driftY = Mth.cos(time * 0.97F) * 0.005F;
        texturedPlane(poseStack, trace, driftX - 0.006F, driftY + 0.004F,
                190, 190, 190, 42, LightTexture.FULL_BRIGHT, packedOverlay);
        texturedPlane(poseStack, trace, -driftX + 0.005F, -driftY - 0.003F,
                112, 112, 112, 27, LightTexture.FULL_BRIGHT, packedOverlay);
        texturedPlane(poseStack, trace, 0.0F, 0.0F,
                255, 255, 255, 255, LightTexture.FULL_BRIGHT, packedOverlay);
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
                                      float offsetX, float offsetY,
                                      int red, int green, int blue, int alpha,
                                      int packedLight, int packedOverlay) {
        Matrix4f pose = poseStack.last().pose();
        Matrix3f normal = poseStack.last().normal();
        float minX = offsetX;
        float minY = offsetY;
        float maxX = 1.0F + offsetX;
        float maxY = 1.0F + offsetY;

        vertex(consumer, pose, normal, minX, minY, 0.500F, 0.0F, 1.0F,
                red, green, blue, alpha, packedLight, packedOverlay, 1.0F);
        vertex(consumer, pose, normal, maxX, minY, 0.500F, 1.0F, 1.0F,
                red, green, blue, alpha, packedLight, packedOverlay, 1.0F);
        vertex(consumer, pose, normal, maxX, maxY, 0.500F, 1.0F, 0.0F,
                red, green, blue, alpha, packedLight, packedOverlay, 1.0F);
        vertex(consumer, pose, normal, minX, maxY, 0.500F, 0.0F, 0.0F,
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
