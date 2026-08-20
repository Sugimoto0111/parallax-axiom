package dev.srryo.parallaxaxiom.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.datafixers.util.Either;
import dev.srryo.parallaxaxiom.ParallaxAxiomMod;
import dev.srryo.parallaxaxiom.mixin.accessor.ClientTextTooltipAccessor;
import dev.srryo.parallaxaxiom.mixin.accessor.GuiSelectedItemAccessor;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTextTooltip;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector2ic;

import java.util.List;
import java.util.ListIterator;

/**
 * Textureless tooltip presentation for the two Parallax Axiom endgame items.
 * The displaced contours, exterior optical structures and thin-film border
 * deliberately mirror the observer array without depending on FE, NoSugar or
 * a bundled GUI texture.
 */
@Mod.EventBusSubscriber(modid = ParallaxAxiomMod.MOD_ID, value = Dist.CLIENT)
public final class ClientParallaxTooltip {
    private static final int CONTENT_PADDING = 7;
    private static final int FRAME_DEPTH = 500;
    private static boolean reportedGlyphRenderer;

    private ClientParallaxTooltip() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onGatherTooltip(RenderTooltipEvent.GatherComponents event) {
        if (!isParallaxItem(event.getItemStack())) {
            return;
        }

        long now = System.currentTimeMillis();
        ListIterator<Either<FormattedText, TooltipComponent>> iterator =
                event.getTooltipElements().listIterator();
        while (iterator.hasNext()) {
            Either<FormattedText, TooltipComponent> element = iterator.next();
            if (element.left().isEmpty()) {
                continue;
            }
            FormattedText text = element.left().orElseThrow();
            TextRole role = classifyText(event.getItemStack(), text.getString());
            if (role != null) {
                iterator.set(Either.left(animateText(text, role, now)));
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onTooltip(RenderTooltipEvent.Pre event) {
        ItemStack stack = event.getItemStack();
        if (!isParallaxItem(stack) || event.getComponents().isEmpty()) {
            return;
        }
        event.setCanceled(true);
        render(event);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onSelectedItemName(RenderGuiOverlayEvent.Pre event) {
        if (event.getOverlay() != VanillaGuiOverlay.ITEM_NAME.type()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        Gui gui = minecraft.gui;
        GuiSelectedItemAccessor access = (GuiSelectedItemAccessor) (Object) gui;
        ItemStack stack = access.parallaxAxiom$getLastToolHighlight();
        int timer = access.parallaxAxiom$getToolHighlightTimer();
        if (timer <= 0 || !isParallaxItem(stack)) {
            return;
        }

        event.setCanceled(true);
        renderSelectedItemName(event.getGuiGraphics(), minecraft, gui, stack,
                timer, event.getWindow().getGuiScaledWidth(),
                event.getWindow().getGuiScaledHeight());
    }

    private static boolean isParallaxItem(ItemStack stack) {
        return stack.is(ParallaxAxiomMod.FINAL_CONCLUSION.get())
                || stack.is(ParallaxAxiomMod.INVARIANT_OBSERVER.get());
    }

    private static void render(RenderTooltipEvent.Pre event) {
        GuiGraphics graphics = event.getGraphics();
        Font font = event.getFont();
        ItemStack stack = event.getItemStack();
        List<ClientTooltipComponent> components = event.getComponents();
        int contentWidth = 0;
        int contentHeight = components.size() == 1 ? -2 : 0;
        for (ClientTooltipComponent component : components) {
            contentWidth = Math.max(contentWidth, component.getWidth(font));
            contentHeight += component.getHeight();
        }

        int decoratedWidth = contentWidth + CONTENT_PADDING * 2;
        int decoratedHeight = contentHeight + CONTENT_PADDING * 2;
        Vector2ic position = event.getTooltipPositioner().positionTooltip(
                event.getScreenWidth(), event.getScreenHeight(), event.getX(), event.getY(),
                decoratedWidth, decoratedHeight);
        int left = position.x();
        int top = position.y();
        int right = left + decoratedWidth;
        int bottom = top + decoratedHeight;
        int textX = left + CONTENT_PADDING;
        int textY = top + CONTENT_PADDING;

        long now = System.currentTimeMillis();
        float timeSeconds = animationSeconds(now);
        float cursorPhase = Mth.positiveModulo(event.getX() * 0.0021F
                + event.getY() * 0.0013F, 1.0F);
        float phase = Mth.positiveModulo(timeSeconds / 9.0F + cursorPhase, 1.0F);
        int filmTop = withAlpha(Mth.hsvToRgb(phase, 0.34F, 1.0F), 205);
        int filmBottom = withAlpha(Mth.hsvToRgb(
                Mth.positiveModulo(phase + 0.24F, 1.0F), 0.42F, 0.92F), 175);
        int ghostA = withAlpha(Mth.hsvToRgb(
                Mth.positiveModulo(phase + 0.48F, 1.0F), 0.30F, 0.88F), 45);
        int ghostB = withAlpha(Mth.hsvToRgb(
                Mth.positiveModulo(phase + 0.72F, 1.0F), 0.38F, 0.96F), 36);
        int drift = Math.round(Mth.sin(timeSeconds * 1.3F) * 1.5F);
        int cursorDriftX = Mth.clamp((event.getX() - event.getScreenWidth() / 2) / 90,
                -2, 2);
        int cursorDriftY = Mth.clamp((event.getY() - event.getScreenHeight() / 2) / 80,
                -2, 2);

        PoseStack poseStack = graphics.pose();
        poseStack.pushPose();
        poseStack.translate(0.0F, 0.0F, FRAME_DEPTH);

        // FE's strongest tooltip device is its animation outside the text box.
        // Here that visual weight becomes lenses, skewed glass panes and film
        // fragments rather than FE's end-portal triangles and star imagery.
        drawExteriorOptics(graphics, left, top, right, bottom, filmTop,
                filmBottom, now, cursorDriftX, cursorDriftY);

        // Soft depth shadow and two displaced contours establish the parallax
        // before the opaque-enough black mirror is drawn over their centers.
        graphics.fill(left - 6, top - 5, right + 7, bottom + 7, 0x48000000);
        drawFrame(graphics, left - 3 + drift, top - 2, right - 2 + drift,
                bottom - 1, ghostA, ghostB);
        drawFrame(graphics, left + 3 - drift, top + 2, right + 4 - drift,
                bottom + 3, ghostB, ghostA);

        graphics.fillGradient(left, top, right, bottom, 0xEC0A0E14, 0xF0040609);
        graphics.fillGradient(left + 2, top + 2, right - 2, bottom - 2,
                0x241A2630, 0x08000000);

        // Camera/cursor-dependent glass seams: subtle enough to leave the text
        // legible, but visibly displaced when the tooltip moves around the screen.
        int seam = left + 8 + Mth.positiveModulo(event.getX() + drift, 17);
        graphics.fillGradient(seam, top + 3, seam + 1, bottom - 3,
                withAlpha(filmTop, 27), withAlpha(filmBottom, 5));
        int secondSeam = right - 11 - Mth.positiveModulo(event.getY() - drift, 13);
        graphics.fillGradient(secondSeam, top + 5, secondSeam + 1, bottom - 5,
                withAlpha(filmBottom, 8), withAlpha(filmTop, 22));

        drawFrame(graphics, left, top, right, bottom, filmTop, filmBottom);
        drawCornerBrackets(graphics, left, top, right, bottom, filmTop, filmBottom);
        drawIncompleteLens(graphics, right - 10, top + 9, 7, 7,
                withAlpha(filmTop, 70), now, false, 20);

        if (!components.isEmpty()) {
            int dividerY = textY + components.get(0).getHeight() + 1;
            graphics.fillGradient(textX, dividerY, right - CONTENT_PADDING,
                    dividerY + 1, withAlpha(filmTop, 92), withAlpha(filmBottom, 18));
        }

        int componentY = textY;
        for (int index = 0; index < components.size(); index++) {
            ClientTooltipComponent component = components.get(index);
            if (component instanceof ClientTextTooltip textTooltip) {
                FormattedCharSequence text =
                        ((ClientTextTooltipAccessor) (Object) textTooltip)
                                .parallaxAxiom$getText();
                TextRole role = classifyText(stack, plainText(text));
                if (role != null) {
                    renderAnimatedGlyphs(font, text, textX, componentY, role,
                            timeSeconds, 1.0F, 255, poseStack,
                            graphics.bufferSource());
                } else {
                    component.renderText(font, textX, componentY,
                            poseStack.last().pose(), graphics.bufferSource());
                }
            } else {
                component.renderText(font, textX, componentY,
                        poseStack.last().pose(), graphics.bufferSource());
            }
            componentY += component.getHeight() + (index == 0 ? 2 : 0);
        }
        graphics.flush();

        componentY = textY;
        for (int index = 0; index < components.size(); index++) {
            ClientTooltipComponent component = components.get(index);
            component.renderImage(font, textX, componentY, graphics);
            componentY += component.getHeight() + (index == 0 ? 2 : 0);
        }
        poseStack.popPose();
    }

    private static void renderSelectedItemName(GuiGraphics graphics,
                                               Minecraft minecraft, Gui gui,
                                               ItemStack stack, int timer,
                                               int screenWidth,
                                               int screenHeight) {
        MutableComponent vanillaName = Component.empty()
                .append(stack.getHoverName())
                .withStyle(stack.getRarity().getStyleModifier());
        if (stack.hasCustomHoverName()) {
            vanillaName.withStyle(ChatFormatting.ITALIC);
        }
        Component highlightName = stack.getHighlightTip(vanillaName);
        Component animatedName = animateText(highlightName, TextRole.TITLE,
                System.currentTimeMillis());
        FormattedCharSequence sequence = animatedName.getVisualOrderText();
        Font font = minecraft.font;
        int width = font.width(sequence);
        int x = (screenWidth - width) / 2;
        int occupiedHeight = gui instanceof ForgeGui forgeGui
                ? Math.max(forgeGui.leftHeight, forgeGui.rightHeight) : 0;
        int y = screenHeight - Math.max(occupiedHeight, 59);
        if (minecraft.gameMode != null && !minecraft.gameMode.canHurtPlayer()) {
            y += 14;
        }

        int alpha = Mth.clamp(timer * 256 / 10, 0, 255);
        if (alpha <= 0) {
            return;
        }
        float timeSeconds = animationSeconds(System.currentTimeMillis());
        float hue = Mth.positiveModulo(timeSeconds / 7.2F, 1.0F);
        int filmA = Mth.hsvToRgb(hue, 0.40F, 1.0F);
        int filmB = Mth.hsvToRgb(Mth.positiveModulo(hue + 0.28F, 1.0F),
                0.44F, 0.92F);
        int left = x - 7;
        int right = x + width + 7;
        int top = y - 5;
        int bottom = y + 13;

        PoseStack poseStack = graphics.pose();
        poseStack.pushPose();
        poseStack.translate(0.0F, 0.0F, FRAME_DEPTH);
        graphics.fill(left - 3, top - 2, right + 3, bottom + 2,
                withAlpha(0x000000, alpha * 45 / 255));
        graphics.fillGradient(left, top, right, bottom,
                withAlpha(0x091019, alpha * 184 / 255),
                withAlpha(0x020406, alpha * 205 / 255));
        graphics.fillGradient(left, top, right, top + 1,
                withAlpha(filmA, alpha * 190 / 255),
                withAlpha(filmB, alpha * 115 / 255));
        graphics.fillGradient(left, bottom - 1, right, bottom,
                withAlpha(filmB, alpha * 80 / 255),
                withAlpha(filmA, alpha * 160 / 255));
        drawHudBrackets(graphics, left, top, right, bottom,
                withAlpha(filmA, alpha * 170 / 255),
                withAlpha(filmB, alpha * 135 / 255));

        renderAnimatedGlyphs(font, sequence, x, y, TextRole.TITLE, timeSeconds,
                0.82F, alpha, poseStack, graphics.bufferSource());
        graphics.flush();
        poseStack.popPose();
    }

    private static void drawHudBrackets(GuiGraphics graphics, int left, int top,
                                        int right, int bottom, int leftColor,
                                        int rightColor) {
        graphics.fill(left - 3, top + 2, left + 2, top + 3, leftColor);
        graphics.fill(left - 3, top + 2, left - 2, bottom - 2, leftColor);
        graphics.fill(right - 2, bottom - 3, right + 3, bottom - 2, rightColor);
        graphics.fill(right + 2, top + 2, right + 3, bottom - 2, rightColor);
    }

    private static void renderAnimatedGlyphs(Font font,
                                             FormattedCharSequence sequence,
                                             float startX, float startY,
                                             TextRole role, float timeSeconds,
                                             float motionScale, int alpha,
                                             PoseStack poseStack,
                                             MultiBufferSource.BufferSource buffers) {
        if (!reportedGlyphRenderer) {
            reportedGlyphRenderer = true;
            ParallaxAxiomMod.LOGGER.info("Parallax glyph animation renderer is active");
        }
        int line = role.animationLine;
        float[] cursorX = {startX};
        int[] glyphIndex = {0};
        float amplitude = switch (line) {
            case 0 -> 3.0F;
            case 1, 2 -> 1.85F;
            default -> 1.35F;
        } * motionScale;
        float speed = line == 0 ? 4.4F : 3.7F;
        float phaseStep = line == 0 ? 0.72F : 0.58F;

        sequence.accept((ignoredIndex, style, codePoint) -> {
            int currentGlyph = glyphIndex[0]++;
            float phase = timeSeconds * speed + currentGlyph * phaseStep
                    + line * 1.37F;
            float waveY = Mth.sin(phase) * amplitude;
            float refractX = Mth.cos(phase * 0.71F) *
                    (line == 0 ? 0.95F : 0.38F) * motionScale;
            String character = new String(Character.toChars(codePoint));
            FormattedCharSequence glyph = FormattedCharSequence.forward(character, style);
            int glyphWidth = font.width(glyph);
            float verticalScale = 1.0F + Mth.cos(phase - 0.55F) *
                    (line == 0 ? 0.15F : 0.07F) * motionScale;
            Matrix4f glyphPose = scaledGlyphPose(poseStack, cursorX[0], startY,
                    glyphWidth, verticalScale);

            if (line == 0 && !Character.isWhitespace(codePoint)) {
                int sourceColor = style.getColor() == null
                        ? 0xDDE7F0 : style.getColor().getValue();
                Style cyanEchoStyle = style.withColor(TextColor.fromRgb(
                        mixRgb(0x031016, sourceColor, 0.38F)));
                Style magentaEchoStyle = style.withColor(TextColor.fromRgb(
                        mixRgb(0x140713, sourceColor, 0.32F)));
                FormattedCharSequence cyanEcho = FormattedCharSequence.forward(
                        character, cyanEchoStyle);
                FormattedCharSequence magentaEcho = FormattedCharSequence.forward(
                        character, magentaEchoStyle);
                float echoY = startY - waveY * 0.60F + 0.7F;
                font.drawInBatch(cyanEcho, cursorX[0] - 1.25F - refractX,
                        echoY, textDrawColor(alpha * 58 / 100), false,
                        glyphPose, buffers,
                        Font.DisplayMode.NORMAL, 0, 15728880);
                font.drawInBatch(magentaEcho, cursorX[0] + 1.25F - refractX,
                        echoY + 0.35F, textDrawColor(alpha * 52 / 100),
                        false, glyphPose, buffers,
                        Font.DisplayMode.NORMAL,
                        0, 15728880);
            }

            font.drawInBatch(glyph, cursorX[0] + refractX, startY + waveY,
                    textDrawColor(alpha), true, glyphPose, buffers,
                    Font.DisplayMode.NORMAL, 0, 15728880);
            cursorX[0] += glyphWidth;
            return true;
        });
    }

    private static Matrix4f scaledGlyphPose(PoseStack poseStack, float glyphX,
                                            float glyphY, float glyphWidth,
                                            float verticalScale) {
        float pivotX = glyphX + glyphWidth * 0.5F;
        float pivotY = glyphY + 4.0F;
        return new Matrix4f(poseStack.last().pose())
                .translate(pivotX, pivotY, 0.0F)
                .scale(1.0F, verticalScale, 1.0F)
                .translate(-pivotX, -pivotY, 0.0F);
    }

    private static Component animateText(FormattedText source, TextRole role,
                                         long now) {
        MutableComponent animated = Component.empty();
        float timeSeconds = animationSeconds(now);
        int length = Math.max(1, source.getString().codePointCount(
                0, source.getString().length()));
        int[] characterIndex = {0};
        source.visit((style, segment) -> {
            segment.codePoints().forEach(codePoint -> {
                int color = animatedTextColor(role, characterIndex[0], length,
                        timeSeconds);
                Style coloredStyle = style.withColor(TextColor.fromRgb(color));
                animated.append(Component.literal(new String(Character.toChars(codePoint)))
                        .setStyle(coloredStyle));
                characterIndex[0]++;
            });
            return FormattedText.STOP_ITERATION.empty();
        }, Style.EMPTY);
        return animated;
    }

    private static int animatedTextColor(TextRole role, int character, int length,
                                         float timeSeconds) {
        int line = role.animationLine;
        float position = character / (float) Math.max(1, length - 1);
        if (line == 0) {
            float hue = Mth.positiveModulo(timeSeconds / 7.2F
                    + position * 0.42F, 1.0F);
            float brightness = 0.91F + 0.09F * Mth.sin(
                    timeSeconds * 3.1F - character * 0.58F);
            return Mth.hsvToRgb(hue, 0.43F, brightness);
        }

        if (line == 1 || line == 2) {
            float sweep = Mth.positiveModulo(timeSeconds / 3.1F
                    + line * 0.19F, 1.0F);
            float distance = Math.abs(position - sweep);
            distance = Math.min(distance, 1.0F - distance);
            float highlight = Mth.clamp(1.0F - distance * 8.5F, 0.0F, 1.0F);
            highlight *= highlight;
            int film = Mth.hsvToRgb(Mth.positiveModulo(timeSeconds / 8.5F
                    + line * 0.17F, 1.0F), 0.34F, 1.0F);
            return mixRgb(0x98A3AE, film, 0.78F * highlight);
        }

        float pulse = 0.5F + 0.5F * Mth.sin(timeSeconds * 2.4F
                - character * 0.24F);
        int film = Mth.hsvToRgb(Mth.positiveModulo(timeSeconds / 9.0F
                + position * 0.18F, 1.0F), 0.42F, 0.92F);
        return mixRgb(0x3B7380, film, 0.25F + pulse * 0.48F);
    }

    private static TextRole classifyText(ItemStack stack, String text) {
        if (text.equals(stack.getHoverName().getString())) {
            return TextRole.TITLE;
        }

        String translationRoot;
        if (stack.is(ParallaxAxiomMod.FINAL_CONCLUSION.get())) {
            translationRoot = "item.parallax_axiom.final_conclusion";
        } else if (stack.is(ParallaxAxiomMod.INVARIANT_OBSERVER.get())) {
            translationRoot = "item.parallax_axiom.invariant_observer";
        } else {
            return null;
        }

        if (text.equals(Component.translatable(translationRoot + ".lore.1").getString())) {
            return TextRole.LORE_ONE;
        }
        if (text.equals(Component.translatable(translationRoot + ".lore.2").getString())) {
            return TextRole.LORE_TWO;
        }
        if (text.equals(Component.translatable(translationRoot + ".detail").getString())
                || text.equals(Component.translatable("tooltip.parallax_axiom.hold_shift")
                        .getString())) {
            return TextRole.FUNCTION;
        }
        return null;
    }

    private static String plainText(FormattedCharSequence sequence) {
        StringBuilder text = new StringBuilder();
        sequence.accept((ignoredIndex, ignoredStyle, codePoint) -> {
            text.appendCodePoint(codePoint);
            return true;
        });
        return text.toString();
    }

    private enum TextRole {
        TITLE(0),
        LORE_ONE(1),
        LORE_TWO(2),
        FUNCTION(3);

        private final int animationLine;

        TextRole(int animationLine) {
            this.animationLine = animationLine;
        }
    }

    private static int mixRgb(int from, int to, float amount) {
        float clamped = Mth.clamp(amount, 0.0F, 1.0F);
        int red = Math.round(Mth.lerp(clamped, from >> 16 & 0xFF, to >> 16 & 0xFF));
        int green = Math.round(Mth.lerp(clamped, from >> 8 & 0xFF, to >> 8 & 0xFF));
        int blue = Math.round(Mth.lerp(clamped, from & 0xFF, to & 0xFF));
        return red << 16 | green << 8 | blue;
    }

    private static float animationSeconds(long now) {
        // Epoch milliseconds cannot retain frame-sized changes after conversion
        // to float. Keep the value in a short repeating window before doing any
        // float animation math so every rendered frame advances its phase.
        return now % 3_600_000L / 1000.0F;
    }

    private static int textDrawColor(int alpha) {
        return Mth.clamp(alpha, 0, 255) << 24 | 0x00FFFFFF;
    }

    private static void drawFrame(GuiGraphics graphics, int left, int top,
                                  int right, int bottom, int topColor,
                                  int bottomColor) {
        graphics.fill(left, top, right, top + 1, topColor);
        graphics.fill(left, bottom - 1, right, bottom, bottomColor);
        graphics.fillGradient(left, top, left + 1, bottom, topColor, bottomColor);
        graphics.fillGradient(right - 1, top, right, bottom, topColor, bottomColor);
    }

    private static void drawCornerBrackets(GuiGraphics graphics, int left, int top,
                                           int right, int bottom, int topColor,
                                           int bottomColor) {
        graphics.fill(left - 2, top - 2, left + 8, top - 1, topColor);
        graphics.fill(left - 2, top - 2, left - 1, top + 8, topColor);
        graphics.fill(right - 8, top - 2, right + 2, top - 1, topColor);
        graphics.fill(right + 1, top - 2, right + 2, top + 8, topColor);
        graphics.fill(left - 2, bottom + 1, left + 8, bottom + 2, bottomColor);
        graphics.fill(left - 2, bottom - 8, left - 1, bottom + 2, bottomColor);
        graphics.fill(right - 8, bottom + 1, right + 2, bottom + 2, bottomColor);
        graphics.fill(right + 1, bottom - 8, right + 2, bottom + 2, bottomColor);
    }

    private static void drawExteriorOptics(GuiGraphics graphics, int left, int top,
                                           int right, int bottom, int topColor,
                                           int bottomColor, long time,
                                           int cursorDriftX, int cursorDriftY) {
        int centerY = (top + bottom) / 2;
        int leftFocusX = left - 8 + cursorDriftX;
        int rightFocusX = right + 8 - cursorDriftX;

        drawIncompleteLens(graphics, leftFocusX, centerY + cursorDriftY,
                22, 15, withAlpha(topColor, 78), time, false, 64);
        drawIncompleteLens(graphics, leftFocusX + 2, centerY - 1,
                15, 10, withAlpha(bottomColor, 48), time + 1700L, true, 52);
        drawIncompleteLens(graphics, rightFocusX, centerY - cursorDriftY,
                20, 14, withAlpha(bottomColor, 72), time + 900L, true, 60);
        drawIncompleteLens(graphics, rightFocusX - 2, centerY + 1,
                13, 9, withAlpha(topColor, 45), time + 2600L, false, 48);

        double slowAngle = time * 0.00012D;
        drawRotatedPane(graphics, left - 12 + cursorDriftX, top + 7 + cursorDriftY,
                15, 5, slowAngle - 0.22D, withAlpha(topColor, 53));
        drawRotatedPane(graphics, left - 5 - cursorDriftX, bottom - 5,
                11, 4, -slowAngle * 0.8D + 0.31D, withAlpha(bottomColor, 42));
        drawRotatedPane(graphics, right + 11 - cursorDriftX, top + 10,
                14, 5, -slowAngle - 0.16D, withAlpha(bottomColor, 50));
        drawRotatedPane(graphics, right + 5 + cursorDriftX, bottom - 4 - cursorDriftY,
                10, 4, slowAngle * 0.75D + 0.25D, withAlpha(topColor, 40));

        drawFilmShard(graphics, left - 28 + cursorDriftX, top + 3,
                slowAngle * 1.7D, withAlpha(bottomColor, 92));
        drawFilmShard(graphics, left - 23, bottom + 7 - cursorDriftY,
                -slowAngle * 1.4D, withAlpha(topColor, 64));
        drawFilmShard(graphics, right + 27 - cursorDriftX, top + 1,
                -slowAngle * 1.6D, withAlpha(topColor, 86));
        drawFilmShard(graphics, right + 22, bottom + 6 + cursorDriftY,
                slowAngle * 1.3D, withAlpha(bottomColor, 60));
    }

    private static void drawIncompleteLens(GuiGraphics graphics, int centerX,
                                           int centerY, int radiusX, int radiusY,
                                           int color, long time, boolean reverse,
                                           int segmentCount) {
        double rotation = time * (reverse ? -0.00021D : 0.00027D);
        for (int segment = 0; segment < segmentCount; segment++) {
            int gapCycle = segment % 15;
            if (gapCycle >= 9 && gapCycle <= 11) {
                continue;
            }
            double angle = rotation + Mth.TWO_PI * segment / segmentCount;
            int x = centerX + (int) Math.round(Math.cos(angle) * radiusX);
            int y = centerY + (int) Math.round(Math.sin(angle) * radiusY);
            graphics.fill(x, y, x + 1, y + 1, color);
        }
    }

    private static void drawRotatedPane(GuiGraphics graphics, int centerX,
                                        int centerY, int halfWidth, int halfHeight,
                                        double angle, int color) {
        int[] xs = new int[4];
        int[] ys = new int[4];
        int[] localX = {-halfWidth, halfWidth, halfWidth, -halfWidth};
        int[] localY = {-halfHeight, -halfHeight, halfHeight, halfHeight};
        double cosine = Math.cos(angle);
        double sine = Math.sin(angle);
        for (int corner = 0; corner < 4; corner++) {
            xs[corner] = centerX + (int) Math.round(localX[corner] * cosine
                    - localY[corner] * sine);
            ys[corner] = centerY + (int) Math.round(localX[corner] * sine
                    + localY[corner] * cosine);
        }
        for (int corner = 0; corner < 4; corner++) {
            int next = (corner + 1) % 4;
            drawLine(graphics, xs[corner], ys[corner], xs[next], ys[next], color);
        }
    }

    private static void drawFilmShard(GuiGraphics graphics, int centerX,
                                      int centerY, double angle, int color) {
        double cosine = Math.cos(angle);
        double sine = Math.sin(angle);
        int tipX = centerX + (int) Math.round(cosine * 4.0D);
        int tipY = centerY + (int) Math.round(sine * 4.0D);
        int tailX = centerX - (int) Math.round(cosine * 3.0D);
        int tailY = centerY - (int) Math.round(sine * 3.0D);
        int sideX = centerX + (int) Math.round(-sine * 2.0D);
        int sideY = centerY + (int) Math.round(cosine * 2.0D);
        drawLine(graphics, tipX, tipY, tailX, tailY, color);
        drawLine(graphics, tipX, tipY, sideX, sideY, withAlpha(color, 38));
    }

    private static void drawLine(GuiGraphics graphics, int startX, int startY,
                                 int endX, int endY, int color) {
        int deltaX = endX - startX;
        int deltaY = endY - startY;
        int steps = Math.max(Math.abs(deltaX), Math.abs(deltaY));
        if (steps == 0) {
            graphics.fill(startX, startY, startX + 1, startY + 1, color);
            return;
        }
        for (int step = 0; step <= steps; step++) {
            int x = startX + Math.round(deltaX * (step / (float) steps));
            int y = startY + Math.round(deltaY * (step / (float) steps));
            graphics.fill(x, y, x + 1, y + 1, color);
        }
    }

    private static int withAlpha(int rgb, int alpha) {
        return Mth.clamp(alpha, 0, 255) << 24 | rgb & 0x00FFFFFF;
    }
}
