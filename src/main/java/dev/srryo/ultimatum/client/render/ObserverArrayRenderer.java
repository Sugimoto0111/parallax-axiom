package dev.srryo.ultimatum.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.srryo.ultimatum.UltimatumMod;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.client.ICurioRenderer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * First visual prototype of the Artifact: an untextured parallax array made from
 * thin panes, incomplete lenses and film fragments. It intentionally relies only
 * on geometry so the silhouette can be tuned before final art or shaders exist.
 */
@Mod.EventBusSubscriber(modid = UltimatumMod.MOD_ID, value = Dist.CLIENT)
public final class ObserverArrayRenderer implements ICurioRenderer {
    private static final float DEG_TO_RAD = ((float) Math.PI / 180.0F);
    private static final int FOCUS_DURATION = 12;
    private static final Map<UUID, FocusState> FOCUS_STATES = new HashMap<>();
    private static final Map<UUID, ObserverFollowState> FOLLOW_STATES = new HashMap<>();

    @SubscribeEvent
    public static void onAttack(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isAttack()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null || !player.getMainHandItem().is(UltimatumMod.ABSOLUTE_END.get())
                || !(minecraft.hitResult instanceof EntityHitResult hit)) {
            return;
        }
        focus(player);
    }

    public static void focus(Player player) {
        FOCUS_STATES.put(player.getUUID(), new FocusState(player.tickCount));
    }

    @Override
    public <T extends LivingEntity, M extends EntityModel<T>> void render(
            ItemStack stack, SlotContext slotContext, PoseStack poseStack,
            RenderLayerParent<T, M> renderLayerParent, MultiBufferSource buffers,
            int packedLight, float limbSwing, float limbSwingAmount, float partialTicks,
            float ageInTicks, float netHeadYaw, float headPitch) {
        LivingEntity wearer = slotContext.entity();
        float time = wearer.tickCount + partialTicks;
        FocusState focusState = FOCUS_STATES.get(wearer.getUUID());
        float focus = focusAmount(wearer, focusState, partialTicks);
        if (focus <= 0.0F && focusState != null) {
            FOCUS_STATES.remove(wearer.getUUID());
            focusState = null;
        }

        ViewFactors view = viewFactors(wearer, partialTicks);
        FollowFrame follow = followFrame(wearer, partialTicks);
        VertexConsumer glass = buffers.getBuffer(ObserverRenderTypes.GLASS);
        VertexConsumer film = buffers.getBuffer(ObserverRenderTypes.FILM);

        poseStack.pushPose();
        ICurioRenderer.translateIfSneaking(poseStack, wearer);
        // Curios renderers share the humanoid model transform. Positive Z places
        // the structure just behind the torso without turning it into worn armor.
        poseStack.translate(0.0D, -0.45D, 0.34D);
        poseStack.scale(1.25F, 1.25F, 1.25F);

        renderPanels(poseStack, glass, film, time, view, follow, focus);
        renderFocusRings(poseStack, film, time, view, follow, focus);
        renderFragments(poseStack, glass, film, time, view, follow, focus);
        poseStack.popPose();
    }

    private static void renderPanels(PoseStack poseStack, VertexConsumer glass,
                                     VertexConsumer film, float time,
                                     ViewFactors view, FollowFrame follow, float focus) {
        renderPanel(poseStack, glass, film, time, view, follow, focus,
                0, 0.0F, -0.05F, 0.08F, 0.58F, 1.35F, 0.0F);
        renderPanel(poseStack, glass, film, time, view, follow, focus,
                1, -0.57F, -0.02F + Mth.sin(time * 0.055F) * 0.035F,
                0.0F, 0.38F, 1.08F, -18.0F);
        renderPanel(poseStack, glass, film, time, view, follow, focus,
                2, 0.57F, -0.02F + Mth.sin(time * 0.055F + 2.1F) * 0.035F,
                -0.01F, 0.38F, 1.08F, 18.0F);
        renderPanel(poseStack, glass, film, time, view, follow, focus,
                3, -0.86F, 0.18F + Mth.sin(time * 0.043F + 4.0F) * 0.045F,
                -0.08F, 0.22F, 0.72F, -31.0F);
        renderPanel(poseStack, glass, film, time, view, follow, focus,
                4, 0.86F, 0.18F + Mth.sin(time * 0.043F + 1.2F) * 0.045F,
                -0.08F, 0.22F, 0.72F, 31.0F);
    }

    private static void renderPanel(PoseStack poseStack, VertexConsumer glass,
                                    VertexConsumer film, float time, ViewFactors view,
                                    FollowFrame followFrame, float focus, int index,
                                    float x, float y, float z, float width,
                                    float height, float yaw) {
        FollowMotion follow = followFrame.panes[index];
        float phase = index * 1.37F;
        float horizontalDrift = Mth.sin(time * (0.026F + index * 0.0015F) + phase)
                * (index == 0 ? 0.025F : 0.042F);
        float yawDrift = Mth.sin(time * 0.021F + phase * 0.73F) * 2.8F;
        float rollDrift = Mth.cos(time * 0.018F + phase * 1.11F) * 1.6F;
        poseStack.pushPose();
        poseStack.translate(x + horizontalDrift + follow.x,
                y + follow.y, z + follow.z);
        poseStack.mulPose(Axis.YP.rotationDegrees(yaw + yawDrift + follow.yaw));
        poseStack.mulPose(Axis.ZP.rotationDegrees(rollDrift));

        float edgeVisibility = 0.16F + view.edge * 0.84F;
        for (int layer = 0; layer < 3; layer++) {
            float layerDepth = (layer - 1) * 0.032F;
            float parallaxX = view.side * (layer - 1) * 0.045F;
            float parallaxY = view.vertical * (layer - 1) * 0.025F;
            float layerScale = 1.0F - layer * 0.055F;
            int color = filmColor(time, index * 0.13F + layer * 0.07F + view.side * 0.08F);

            poseStack.pushPose();
            poseStack.translate(parallaxX, parallaxY, layerDepth);
            Matrix4f matrix = poseStack.last().pose();
            float halfWidth = width * layerScale * 0.5F;
            float halfHeight = height * layerScale * 0.5F;
            int fillAlpha = Mth.clamp((int) ((12 + layer * 3) * edgeVisibility
                    + focus * 7), 3, 30);
            quad(glass, matrix, -halfWidth, -halfHeight, 0.0F,
                    halfWidth, halfHeight, 0.0F, 4, 8, 11, fillAlpha);

            int borderAlpha = Mth.clamp((int) ((48 + layer * 13) * edgeVisibility
                    + focus * 65), 12, 150);
            rectangleBorder(film, matrix, halfWidth, halfHeight,
                    0.008F + layer * 0.002F, red(color), green(color), blue(color),
                    borderAlpha);

            if (layer == 1) {
                // A displaced interior contour makes the pane read as layered glass
                // rather than a single glowing rectangle.
                float insetX = view.side * 0.055F;
                float insetY = -view.vertical * 0.035F;
                poseStack.pushPose();
                poseStack.translate(insetX, insetY, 0.002F);
                rectangleBorder(film, poseStack.last().pose(), halfWidth * 0.68F,
                        halfHeight * 0.77F, 0.005F, blue(color), red(color),
                        green(color), borderAlpha / 2);
                poseStack.popPose();
            }
            poseStack.popPose();
        }
        poseStack.popPose();
    }

    private static void renderFocusRings(PoseStack poseStack, VertexConsumer film,
                                         float time, ViewFactors view,
                                         FollowFrame followFrame, float focus) {
        FollowMotion follow = followFrame.rings[0];
        poseStack.pushPose();
        poseStack.translate(view.side * 0.07F + follow.x,
                -0.07F + Mth.sin(time * 0.035F) * 0.025F + follow.y,
                0.13F + follow.z);
        poseStack.mulPose(Axis.YP.rotationDegrees(follow.yaw));
        poseStack.mulPose(Axis.ZP.rotationDegrees(time * 0.24F));
        int color = filmColor(time, 0.42F + view.side * 0.12F);
        segmentedRing(film, poseStack.last().pose(), 0.43F,
                0.014F + focus * 0.012F, 28, 5,
                red(color), green(color), blue(color), 54 + (int) (focus * 110));
        poseStack.popPose();

        poseStack.pushPose();
        follow = followFrame.rings[1];
        poseStack.translate(-view.side * 0.045F + follow.x,
                -0.07F + follow.y, 0.17F + follow.z);
        poseStack.mulPose(Axis.YP.rotationDegrees(follow.yaw));
        poseStack.mulPose(Axis.ZP.rotationDegrees(-time * 0.17F + 11.0F));
        int inverse = filmColor(time, 0.76F - view.side * 0.09F);
        segmentedRing(film, poseStack.last().pose(), 0.29F,
                0.009F + focus * 0.008F, 24, 4,
                red(inverse), green(inverse), blue(inverse), 42 + (int) (focus * 105));
        poseStack.popPose();
    }

    private static void renderFragments(PoseStack poseStack, VertexConsumer glass,
                                        VertexConsumer film, float time,
                                        ViewFactors view, FollowFrame followFrame,
                                        float focus) {
        for (int i = 0; i < 22; i++) {
            float x;
            float y;
            if (i < 18) {
                float side = (i % 2 == 0) ? -1.0F : 1.0F;
                float distance = 0.98F + ((i * 5) % 6) * 0.08F;
                x = side * distance;
                y = -0.66F + ((i * 7) % 12) * 0.12F;
            } else {
                int cap = i - 18;
                x = (cap % 2 == 0 ? -1.0F : 1.0F)
                        * (0.30F + (cap / 2) * 0.18F);
                y = cap < 2 ? -0.84F : 0.78F;
            }
            x += Mth.sin(time * (0.031F + i * 0.0015F) + i * 1.71F)
                    * (0.028F + (i % 4) * 0.008F);
            y += Mth.cos(time * (0.032F + (i % 5) * 0.0018F) + i * 0.83F)
                    * (0.024F + (i % 3) * 0.008F);
            float z = -0.07F + (i % 5) * 0.035F;
            int color = filmColor(time, i * 0.083F + view.side * 0.1F);
            FollowMotion follow = followFrame.fragments[i];

            poseStack.pushPose();
            poseStack.translate(x + follow.x, y + follow.y, z + follow.z);
            poseStack.mulPose(Axis.YP.rotationDegrees(follow.yaw));
            poseStack.mulPose(Axis.ZP.rotationDegrees(i * 37.0F
                    + time * (0.18F + (i % 6) * 0.055F)));
            float size = fragmentSize(i);
            float widthRatio = 0.46F + (i % 4) * 0.11F;
            float heightRatio = 1.25F + (i % 3) * 0.23F;
            diamond(glass, poseStack.last().pose(), size * heightRatio,
                    size * widthRatio,
                    3, 7, 10, 18 + (int) (focus * 8));
            diamond(film, poseStack.last().pose(), size * 0.72F,
                    size * widthRatio * 0.58F,
                    red(color), green(color), blue(color), 58 + (int) (focus * 95));
            poseStack.popPose();
        }
    }

    private static float fragmentSize(int index) {
        return switch (index % 8) {
            case 0 -> 0.110F;
            case 1 -> 0.084F;
            case 2 -> 0.066F;
            case 3 -> 0.052F;
            case 4 -> 0.039F;
            case 5 -> 0.029F;
            case 6 -> 0.021F;
            default -> 0.046F;
        };
    }

    private static float focusAmount(LivingEntity wearer, FocusState state, float partialTicks) {
        if (state == null) {
            return 0.0F;
        }
        float elapsed = wearer.tickCount + partialTicks - state.startTick;
        if (elapsed < 0.0F || elapsed >= FOCUS_DURATION) {
            return 0.0F;
        }
        float linear = 1.0F - elapsed / FOCUS_DURATION;
        return linear * linear * (3.0F - 2.0F * linear);
    }

    private static FollowFrame followFrame(LivingEntity wearer, float partialTicks) {
        double currentX = Mth.lerp(partialTicks, wearer.xOld, wearer.getX());
        double currentY = Mth.lerp(partialTicks, wearer.yOld, wearer.getY());
        double currentZ = Mth.lerp(partialTicks, wearer.zOld, wearer.getZ());
        Vec3 currentPosition = new Vec3(currentX, currentY, currentZ);
        float currentYaw = Mth.rotLerp(partialTicks, wearer.yBodyRotO, wearer.yBodyRot);
        float time = wearer.tickCount + partialTicks;

        ObserverFollowState state = FOLLOW_STATES.computeIfAbsent(wearer.getUUID(),
                ignored -> new ObserverFollowState(currentPosition, currentYaw, time));
        float deltaTicks = time - state.lastTime;
        if (deltaTicks < 0.0F || deltaTicks > 3.0F
                || state.referenceAnchor().distanceToSqr(currentPosition) > 64.0D) {
            state.snap(currentPosition, currentYaw, time);
        } else if (deltaTicks > 0.0F) {
            state.update(currentPosition, currentYaw, deltaTicks, time);
        }
        return state.frame(currentPosition, currentYaw);
    }

    private static FollowMotion localFollowMotion(Vec3 anchor, float anchorYaw,
                                                  Vec3 currentPosition,
                                                  float currentYaw) {
        Vec3 worldOffset = anchor.subtract(currentPosition);
        double distance = worldOffset.length();
        // The entire manifestation is rendered at 1.25 scale, so 1.2 local
        // blocks becomes a visible maximum follow distance of about 1.5 blocks.
        if (distance > 1.20D) {
            worldOffset = worldOffset.scale(1.20D / distance);
        }

        float bodyYaw = currentYaw * DEG_TO_RAD;
        double rightX = Mth.cos(bodyYaw);
        double rightZ = Mth.sin(bodyYaw);
        double forwardX = -Mth.sin(bodyYaw);
        double forwardZ = Mth.cos(bodyYaw);
        float localX = (float) (worldOffset.x * rightX + worldOffset.z * rightZ);
        float localY = (float) -worldOffset.y;
        float localZ = (float) -(worldOffset.x * forwardX + worldOffset.z * forwardZ);
        float localYaw = -Mth.clamp(Mth.wrapDegrees(anchorYaw - currentYaw),
                -18.0F, 18.0F);
        return new FollowMotion(localX, localY, localZ, localYaw);
    }

    private static ViewFactors viewFactors(LivingEntity wearer, float partialTicks) {
        Minecraft minecraft = Minecraft.getInstance();
        Camera camera = minecraft.gameRenderer.getMainCamera();
        double entityX = Mth.lerp(partialTicks, wearer.xOld, wearer.getX());
        double entityY = Mth.lerp(partialTicks, wearer.yOld, wearer.getY())
                + wearer.getBbHeight() * 0.52D;
        double entityZ = Mth.lerp(partialTicks, wearer.zOld, wearer.getZ());
        Vec3 toCamera = camera.getPosition().subtract(entityX, entityY, entityZ);
        double horizontalLength = Math.sqrt(toCamera.x * toCamera.x + toCamera.z * toCamera.z);
        if (horizontalLength < 1.0E-4D) {
            return new ViewFactors(0.0F, 0.0F, 0.0F);
        }
        float bodyYaw = Mth.rotLerp(partialTicks, wearer.yBodyRotO, wearer.yBodyRot)
                * DEG_TO_RAD;
        double rightX = Mth.cos(bodyYaw);
        double rightZ = Mth.sin(bodyYaw);
        double forwardX = -Mth.sin(bodyYaw);
        double forwardZ = Mth.cos(bodyYaw);
        float side = (float) ((toCamera.x * rightX + toCamera.z * rightZ)
                / horizontalLength);
        float facing = (float) ((toCamera.x * forwardX + toCamera.z * forwardZ)
                / horizontalLength);
        float edge = 1.0F - Math.abs(facing);
        float vertical = Mth.clamp((float) (toCamera.y / Math.max(1.0D, horizontalLength)),
                -1.0F, 1.0F);
        return new ViewFactors(side, vertical, edge);
    }

    private static int filmColor(float time, float offset) {
        float hue = Mth.positiveModulo(offset + time * 0.0018F, 1.0F);
        return Mth.hsvToRgb(hue, 0.42F, 0.96F);
    }

    private static void segmentedRing(VertexConsumer consumer, Matrix4f matrix,
                                      float radius, float thickness, int segments,
                                      int skipEvery, int red, int green, int blue,
                                      int alpha) {
        float inner = radius - thickness;
        for (int i = 0; i < segments; i++) {
            if (i % skipEvery == skipEvery - 1) {
                continue;
            }
            float a0 = Mth.TWO_PI * i / segments;
            float a1 = Mth.TWO_PI * (i + 0.72F) / segments;
            consumer.vertex(matrix, Mth.cos(a0) * inner, Mth.sin(a0) * inner, 0.0F)
                    .color(red, green, blue, alpha).endVertex();
            consumer.vertex(matrix, Mth.cos(a0) * radius, Mth.sin(a0) * radius, 0.0F)
                    .color(red, green, blue, alpha).endVertex();
            consumer.vertex(matrix, Mth.cos(a1) * radius, Mth.sin(a1) * radius, 0.0F)
                    .color(red, green, blue, alpha).endVertex();
            consumer.vertex(matrix, Mth.cos(a1) * inner, Mth.sin(a1) * inner, 0.0F)
                    .color(red, green, blue, alpha).endVertex();
        }
    }

    private static void rectangleBorder(VertexConsumer consumer, Matrix4f matrix,
                                        float halfWidth, float halfHeight, float thickness,
                                        int red, int green, int blue, int alpha) {
        quad(consumer, matrix, -halfWidth, halfHeight - thickness, 0.001F,
                halfWidth, halfHeight, 0.001F, red, green, blue, alpha);
        quad(consumer, matrix, -halfWidth, -halfHeight, 0.001F,
                halfWidth, -halfHeight + thickness, 0.001F, red, green, blue, alpha);
        quad(consumer, matrix, -halfWidth, -halfHeight, 0.001F,
                -halfWidth + thickness, halfHeight, 0.001F, red, green, blue, alpha);
        quad(consumer, matrix, halfWidth - thickness, -halfHeight, 0.001F,
                halfWidth, halfHeight, 0.001F, red, green, blue, alpha);
    }

    private static void diamond(VertexConsumer consumer, Matrix4f matrix,
                                float halfHeight, float halfWidth,
                                int red, int green, int blue, int alpha) {
        consumer.vertex(matrix, 0.0F, halfHeight, 0.0F)
                .color(red, green, blue, alpha).endVertex();
        consumer.vertex(matrix, halfWidth, 0.0F, 0.0F)
                .color(red, green, blue, alpha).endVertex();
        consumer.vertex(matrix, 0.0F, -halfHeight, 0.0F)
                .color(red, green, blue, alpha).endVertex();
        consumer.vertex(matrix, -halfWidth, 0.0F, 0.0F)
                .color(red, green, blue, alpha).endVertex();
    }

    private static void quad(VertexConsumer consumer, Matrix4f matrix,
                             float minX, float minY, float z1,
                             float maxX, float maxY, float z2,
                             int red, int green, int blue, int alpha) {
        consumer.vertex(matrix, minX, minY, z1).color(red, green, blue, alpha).endVertex();
        consumer.vertex(matrix, maxX, minY, z1).color(red, green, blue, alpha).endVertex();
        consumer.vertex(matrix, maxX, maxY, z2).color(red, green, blue, alpha).endVertex();
        consumer.vertex(matrix, minX, maxY, z2).color(red, green, blue, alpha).endVertex();
    }

    private static int red(int color) {
        return color >> 16 & 255;
    }

    private static int green(int color) {
        return color >> 8 & 255;
    }

    private static int blue(int color) {
        return color & 255;
    }

    private record FocusState(int startTick) {
    }

    private record ViewFactors(float side, float vertical, float edge) {
    }

    private record FollowMotion(float x, float y, float z, float yaw) {
    }

    private record FollowFrame(FollowMotion[] panes, FollowMotion[] rings,
                               FollowMotion[] fragments) {
    }

    private static final class ObserverFollowState {
        private final ElementFollower[] panes;
        private final ElementFollower[] rings;
        private final ElementFollower[] fragments;
        private float lastTime;

        private ObserverFollowState(Vec3 position, float bodyYaw, float time) {
            panes = createFollowers(5, 11, 0.69F, 0.87F,
                    0.66F, 0.85F, position, bodyYaw);
            rings = createFollowers(2, 101, 0.74F, 0.86F,
                    0.72F, 0.86F, position, bodyYaw);
            fragments = createFollowers(22, 211, 0.76F, 0.91F,
                    0.72F, 0.90F, position, bodyYaw);
            lastTime = time;
        }

        private Vec3 referenceAnchor() {
            return panes[0].anchor;
        }

        private void update(Vec3 position, float bodyYaw, float deltaTicks,
                            float time) {
            updateAll(panes, position, bodyYaw, deltaTicks);
            updateAll(rings, position, bodyYaw, deltaTicks);
            updateAll(fragments, position, bodyYaw, deltaTicks);
            lastTime = time;
        }

        private void snap(Vec3 position, float bodyYaw, float time) {
            snapAll(panes, position, bodyYaw);
            snapAll(rings, position, bodyYaw);
            snapAll(fragments, position, bodyYaw);
            lastTime = time;
        }

        private FollowFrame frame(Vec3 position, float bodyYaw) {
            return new FollowFrame(motions(panes, position, bodyYaw),
                    motions(rings, position, bodyYaw),
                    motions(fragments, position, bodyYaw));
        }

        private static ElementFollower[] createFollowers(
                int count, int seedOffset, float minimumPositionRetention,
                float maximumPositionRetention, float minimumYawRetention,
                float maximumYawRetention, Vec3 position, float bodyYaw) {
            ElementFollower[] followers = new ElementFollower[count];
            for (int i = 0; i < count; i++) {
                float positionRetention = Mth.lerp(randomUnit(i + seedOffset, 17),
                        minimumPositionRetention, maximumPositionRetention);
                float yawRetention = Mth.lerp(randomUnit(i + seedOffset, 43),
                        minimumYawRetention, maximumYawRetention);
                followers[i] = new ElementFollower(position, bodyYaw,
                        positionRetention, yawRetention);
            }
            return followers;
        }

        private static void updateAll(ElementFollower[] followers, Vec3 position,
                                      float bodyYaw, float deltaTicks) {
            for (ElementFollower follower : followers) {
                follower.update(position, bodyYaw, deltaTicks);
            }
        }

        private static void snapAll(ElementFollower[] followers, Vec3 position,
                                    float bodyYaw) {
            for (ElementFollower follower : followers) {
                follower.snap(position, bodyYaw);
            }
        }

        private static FollowMotion[] motions(ElementFollower[] followers,
                                              Vec3 position, float bodyYaw) {
            FollowMotion[] motions = new FollowMotion[followers.length];
            for (int i = 0; i < followers.length; i++) {
                motions[i] = localFollowMotion(followers[i].anchor,
                        followers[i].yaw, position, bodyYaw);
            }
            return motions;
        }

        private static float randomUnit(int index, int salt) {
            int value = index * 0x45D9F3B + salt * 0x27D4EB2D;
            value = (value ^ value >>> 16) * 0x45D9F3B;
            value ^= value >>> 16;
            return (value & Integer.MAX_VALUE) / (float) Integer.MAX_VALUE;
        }
    }

    private static final class ElementFollower {
        private Vec3 anchor;
        private float yaw;
        private final float positionRetention;
        private final float yawRetention;

        private ElementFollower(Vec3 anchor, float yaw, float positionRetention,
                                float yawRetention) {
            this.anchor = anchor;
            this.yaw = yaw;
            this.positionRetention = positionRetention;
            this.yawRetention = yawRetention;
        }

        private void update(Vec3 position, float bodyYaw, float deltaTicks) {
            double positionBlend = 1.0D - Math.pow(positionRetention, deltaTicks);
            anchor = anchor.lerp(position, positionBlend);
            float yawBlend = 1.0F - (float) Math.pow(yawRetention, deltaTicks);
            yaw += Mth.wrapDegrees(bodyYaw - yaw) * yawBlend;
        }

        private void snap(Vec3 position, float bodyYaw) {
            anchor = position;
            yaw = bodyYaw;
        }
    }
}
