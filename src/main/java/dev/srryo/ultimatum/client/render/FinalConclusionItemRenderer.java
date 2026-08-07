package dev.srryo.ultimatum.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Draws Final Conclusion from a deliberately sparse monochrome texture. The
 * image supplies only the silhouette; movement, apparent depth and reflection
 * are produced here so the item stays achromatic without looking static.
 */
public final class FinalConclusionItemRenderer extends BlockEntityWithoutLevelRenderer {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation("ultimatum", "textures/item/final_conclusion.png");

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
        VertexConsumer texture = buffers.getBuffer(RenderType.entityTranslucent(TEXTURE));

        // Two very faint displaced impressions create depth without adding colour.
        float driftX = Mth.sin(time * 1.31F) * 0.014F;
        float driftY = Mth.cos(time * 1.07F) * 0.011F;
        texturedPlane(poseStack, texture, driftX - 0.018F, driftY + 0.010F,
                0.478F, 24, LightTexture.FULL_BRIGHT, packedOverlay);
        texturedPlane(poseStack, texture, -driftX + 0.016F, -driftY - 0.008F,
                0.486F, 17, LightTexture.FULL_BRIGHT, packedOverlay);

        // Stable material layer: opaque graphite core and translucent white edge.
        texturedPlane(poseStack, texture, 0.0F, 0.0F, 0.500F,
                255, packedLight, packedOverlay);

        // A barely shifted full-bright reprint reads as reflection. It flares when
        // the player commits an attack, but never introduces an enchantment colour.
        int reflectionAlpha = 29 + Mth.floor(attack * 108.0F);
        float reflectionShift = 0.006F + attack * 0.012F;
        texturedPlane(poseStack, texture, reflectionShift, reflectionShift,
                0.522F, reflectionAlpha, LightTexture.FULL_BRIGHT, packedOverlay);

        renderScan(poseStack, buffers.getBuffer(ObserverRenderTypes.FILM), time, attack);
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

    private static void renderScan(PoseStack poseStack, VertexConsumer consumer,
                                   float time, float attack) {
        // The highlight travels along the blade's lower-left to upper-right axis.
        float progress = Mth.positiveModulo(time / 2.7F, 1.0F);
        float centerX = Mth.lerp(progress, 0.285F, 0.885F);
        float centerY = Mth.lerp(progress, 0.235F, 0.935F);
        float halfLength = 0.030F + attack * 0.014F;
        float halfWidth = 0.006F + attack * 0.006F;
        int alpha = 42 + Mth.floor(attack * 136.0F);
        Matrix4f matrix = poseStack.last().pose();

        // Narrow diamond aligned with the diagonal blade.
        consumer.vertex(matrix, centerX - halfLength, centerY - halfLength, 0.540F)
                .color(255, 255, 255, 0).endVertex();
        consumer.vertex(matrix, centerX - halfWidth, centerY + halfWidth, 0.540F)
                .color(255, 255, 255, alpha).endVertex();
        consumer.vertex(matrix, centerX + halfLength, centerY + halfLength, 0.540F)
                .color(255, 255, 255, 0).endVertex();
        consumer.vertex(matrix, centerX + halfWidth, centerY - halfWidth, 0.540F)
                .color(255, 255, 255, alpha).endVertex();
    }

    private static void texturedPlane(PoseStack poseStack, VertexConsumer consumer,
                                      float offsetX, float offsetY, float z,
                                      int alpha, int packedLight, int packedOverlay) {
        Matrix4f pose = poseStack.last().pose();
        Matrix3f normal = poseStack.last().normal();
        float minX = offsetX;
        float minY = offsetY;
        float maxX = 1.0F + offsetX;
        float maxY = 1.0F + offsetY;

        vertex(consumer, pose, normal, minX, minY, z, 0.0F, 1.0F,
                alpha, packedLight, packedOverlay, 1.0F);
        vertex(consumer, pose, normal, maxX, minY, z, 1.0F, 1.0F,
                alpha, packedLight, packedOverlay, 1.0F);
        vertex(consumer, pose, normal, maxX, maxY, z, 1.0F, 0.0F,
                alpha, packedLight, packedOverlay, 1.0F);
        vertex(consumer, pose, normal, minX, maxY, z, 0.0F, 0.0F,
                alpha, packedLight, packedOverlay, 1.0F);

        // Render the reverse face for third-person hands and item frames.
        vertex(consumer, pose, normal, minX, maxY, z - 0.001F, 0.0F, 0.0F,
                alpha, packedLight, packedOverlay, -1.0F);
        vertex(consumer, pose, normal, maxX, maxY, z - 0.001F, 1.0F, 0.0F,
                alpha, packedLight, packedOverlay, -1.0F);
        vertex(consumer, pose, normal, maxX, minY, z - 0.001F, 1.0F, 1.0F,
                alpha, packedLight, packedOverlay, -1.0F);
        vertex(consumer, pose, normal, minX, minY, z - 0.001F, 0.0F, 1.0F,
                alpha, packedLight, packedOverlay, -1.0F);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f pose,
                               Matrix3f normal, float x, float y, float z,
                               float u, float v, int alpha, int packedLight,
                               int packedOverlay, float normalZ) {
        consumer.vertex(pose, x, y, z)
                .color(255, 255, 255, alpha)
                .uv(u, v)
                .overlayCoords(packedOverlay)
                .uv2(packedLight)
                .normal(normal, 0.0F, 0.0F, normalZ)
                .endVertex();
    }
}
