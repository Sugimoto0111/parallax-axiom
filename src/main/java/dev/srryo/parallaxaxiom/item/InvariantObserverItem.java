package dev.srryo.parallaxaxiom.item;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import dev.srryo.parallaxaxiom.ParallaxAxiomMod;
import dev.srryo.parallaxaxiom.client.render.InvariantObserverItemRenderer;
import dev.srryo.parallaxaxiom.invincibility.InvincibilityService;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurio;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/** The Curios anchor for player invariance and the observer-array manifestation. */
public final class InvariantObserverItem extends Item implements ICurioItem {
    public static final String SLOT_IDENTIFIER = "artifact";
    public static final UUID MOVEMENT_SPEED_ID =
            UUID.fromString("97d99eb5-2602-4ca2-9d37-adbff403a311");
    private static final UUID SWIM_SPEED_ID =
            UUID.fromString("0fb4d74c-3240-42c3-af13-df6685ef3fb0");

    public InvariantObserverItem(Properties properties) {
        super(properties);
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private final BlockEntityWithoutLevelRenderer renderer =
                    new InvariantObserverItemRenderer();

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return renderer;
            }
        });
    }

    @Override
    public boolean canEquip(SlotContext context, ItemStack stack) {
        return SLOT_IDENTIFIER.equals(context.identifier()) && !context.cosmetic();
    }

    @Override
    public void onEquip(SlotContext context, ItemStack previousStack, ItemStack stack) {
        if (!context.entity().level().isClientSide && context.entity() instanceof Player player) {
            InvincibilityService.anchor(player);
            InvincibilityService.restoreNow(player);
            ParallaxAxiomMod.ARTIFACT_MOBILITY_SERVICE.updateNow(player);
            ParallaxAxiomMod.ARTIFACT_REACH_SERVICE.updateNow(player);
            ParallaxAxiomMod.ARTIFACT_UTILITY_SERVICE.updateNow(player);
        }
    }

    @Override
    public void onUnequip(SlotContext context, ItemStack newStack, ItemStack stack) {
        // Curios can report an NBT/configuration update as an old-stack/new-stack swap.
        // That is still the same equipped Artifact and must not cancel active flight.
        if (newStack.is(ParallaxAxiomMod.INVARIANT_OBSERVER.get())) {
            return;
        }
        if (!context.entity().level().isClientSide && context.entity() instanceof Player player) {
            InvincibilityService.release(player);
            ParallaxAxiomMod.ARTIFACT_MOBILITY_SERVICE.disableNow(player);
            ParallaxAxiomMod.ARTIFACT_REACH_SERVICE.disableNow(player);
            ParallaxAxiomMod.ARTIFACT_UTILITY_SERVICE.disableNow(player);
        }
    }

    @Override
    public void curioTick(SlotContext context, ItemStack stack) {
        if (!context.entity().level().isClientSide && context.entity() instanceof Player player) {
            InvincibilityService.anchor(player);
            InvincibilityService.restoreNow(player);
            ParallaxAxiomMod.ARTIFACT_MOBILITY_SERVICE.updateNow(player);
            ParallaxAxiomMod.ARTIFACT_REACH_SERVICE.updateNow(player);
        }
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(
            SlotContext context, UUID slotUuid, ItemStack stack) {
        ImmutableMultimap.Builder<Attribute, AttributeModifier> modifiers =
                ImmutableMultimap.builder();
        modifiers.put(Attributes.MOVEMENT_SPEED, new AttributeModifier(
                MOVEMENT_SPEED_ID, "Invariant Observer ground speed", 0.1D,
                AttributeModifier.Operation.ADDITION));
        modifiers.put(ForgeMod.SWIM_SPEED.get(), new AttributeModifier(
                SWIM_SPEED_ID, "Invariant Observer swim speed", 2.0D,
                AttributeModifier.Operation.ADDITION));
        return modifiers.build();
    }

    @Override
    public ICurio.DropRule getDropRule(SlotContext context, net.minecraft.world.damagesource.DamageSource source,
                                       int lootingLevel, boolean recentlyHit, ItemStack stack) {
        return ICurio.DropRule.ALWAYS_KEEP;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip,
                                TooltipFlag flag) {
        tooltip.add(Component.translatable("item.parallax_axiom.invariant_observer.lore.1")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("item.parallax_axiom.invariant_observer.lore.2")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        if (Screen.hasShiftDown()) {
            tooltip.add(Component.translatable("item.parallax_axiom.invariant_observer.detail")
                    .withStyle(ChatFormatting.DARK_AQUA));
        } else {
            tooltip.add(Component.translatable("tooltip.parallax_axiom.hold_shift")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return false;
    }
}
