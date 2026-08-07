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
        // This no-cull, colour-only translucent pass preserves submission order
        // without writing per-layer depth into the scene.
        VertexConsumer texture = buffers.getBuffer(
                RenderType.entityTranslucentEmissive(TEXTURE));

        // Two very faint displaced impressions create depth without adding colour.
        float driftX = Mth.sin(time * 1.31F) * 0.014F;
        float driftY = Mth.cos(time * 1.07F) * 0.011F;
        texturedPlane(poseStack, texture, driftX - 0.018F, driftY + 0.010F,
                118, 128, 136, 18,
                LightTexture.FULL_BRIGHT, packedOverlay);
        texturedPlane(poseStack, texture, -driftX + 0.016F, -driftY - 0.008F,
                80, 88, 94, 13,
                LightTexture.FULL_BRIGHT, packedOverlay);

        // The source image is now only a solid white silhouette. Its border and
        // material are generated here, leaving no baked colour in the asset.
        renderOutline(poseStack, texture, time, attack, packedOverlay);
        texturedPlane(poseStack, texture, 0.0F, 0.0F,
                9, 12, 15, 238, packedLight, packedOverlay);
        texturedPlane(poseStack, texture, -0.002F, 0.003F,
                84, 92, 98, 38, LightTexture.FULL_BRIGHT, packedOverlay);

        // A barely shifted full-bright reprint reads as reflection. It flares when
        // the player commits an attack, but never introduces an enchantment colour.
        int reflectionAlpha = 24 + Mth.floor(attack * 116.0F);
        float reflectionShift = 0.006F + attack * 0.012F;
        texturedPlane(poseStack, texture, reflectionShift, reflectionShift,
                235, 241, 244, reflectionAlpha,
                LightTexture.FULL_BRIGHT, packedOverlay);

        // The same white silhouette is reused as a window into a procedural,
        // camera-ray-projected interior. It contains no baked scenery or colour.
        FinalConclusionShaders.prepare(time, attack, context);
        VertexConsumer interior = buffers.getBuffer(FinalConclusionRenderTypes.INTERIOR);
        texturedPlane(poseStack, interior, 0.0F, 0.0F,
                255, 255, 255, 255, LightTexture.FULL_BRIGHT, packedOverlay);

        renderScan(poseStack, buffers.getBuffer(ObserverRenderTypes.FILM), time, attack);
    }

    private static void renderOutline(PoseStack poseStack, VertexConsumer texture,
                                      float time, float attack, int packedOverlay) {
        float radius = 0.030F + Mth.sin(time * 1.43F) * 0.0025F
                + attack * 0.006F;
        int cardinalAlpha = 38 + Mth.floor(attack * 66.0F);
        int diagonalAlpha = 24 + Mth.floor(attack * 44.0F);
        float diagonal = radius * 0.72F;

        texturedPlane(poseStack, texture, -radius, 0.0F,
                232, 239, 242, cardinalAlpha, LightTexture.FULL_BRIGHT, packedOverlay);
        texturedPlane(poseStack, texture, radius, 0.0F,
                232, 239, 242, cardinalAlpha, LightTexture.FULL_BRIGHT, packedOverlay);
        texturedPlane(poseStack, texture, 0.0F, -radius,
                232, 239, 242, cardinalAlpha, LightTexture.FULL_BRIGHT, packedOverlay);
        texturedPlane(poseStack, texture, 0.0F, radius,
                232, 239, 242, cardinalAlpha, LightTexture.FULL_BRIGHT, packedOverlay);
        texturedPlane(poseStack, texture, -diagonal, -diagonal,
                198, 207, 212, diagonalAlpha, LightTexture.FULL_BRIGHT, packedOverlay);
        texturedPlane(poseStack, texture, diagonal, -diagonal,
                198, 207, 212, diagonalAlpha, LightTexture.FULL_BRIGHT, packedOverlay);
        texturedPlane(poseStack, texture, -diagonal, diagonal,
                198, 207, 212, diagonalAlpha, LightTexture.FULL_BRIGHT, packedOverlay);
        texturedPlane(poseStack, texture, diagonal, diagonal,
                198, 207, 212, diagonalAlpha, LightTexture.FULL_BRIGHT, packedOverlay);
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

        // The material is physically flat; NO_CULL makes this visible from either face.
        scanDiamond(consumer, matrix, centerX, centerY, halfLength, halfWidth,
                0.500F, alpha);
    }

    private static void scanDiamond(VertexConsumer consumer, Matrix4f matrix,
                                    float centerX, float centerY, float halfLength,
                                    float halfWidth, float z, int alpha) {
        consumer.vertex(matrix, centerX - halfLength, centerY - halfLength, z)
                .color(255, 255, 255, 0).endVertex();
        consumer.vertex(matrix, centerX - halfWidth, centerY + halfWidth, z)
                .color(255, 255, 255, alpha).endVertex();
        consumer.vertex(matrix, centerX + halfLength, centerY + halfLength, z)
                .color(255, 255, 255, 0).endVertex();
        consumer.vertex(matrix, centerX + halfWidth, centerY - halfWidth, z)
                .color(255, 255, 255, alpha).endVertex();
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
