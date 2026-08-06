package dev.srryo.ultimatum.item;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import dev.srryo.ultimatum.UltimatumMod;
import dev.srryo.ultimatum.invincibility.InvincibilityService;
import net.minecraft.ChatFormatting;
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
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurio;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import java.util.List;
import java.util.UUID;

/** The invisible Curios anchor for player invincibility and future visual manifestations. */
public final class AbsoluteArtifactItem extends Item implements ICurioItem {
    public static final String SLOT_IDENTIFIER = "artifact";
    public static final UUID MOVEMENT_SPEED_ID =
            UUID.fromString("97d99eb5-2602-4ca2-9d37-adbff403a311");
    private static final UUID SWIM_SPEED_ID =
            UUID.fromString("0fb4d74c-3240-42c3-af13-df6685ef3fb0");

    public AbsoluteArtifactItem(Properties properties) {
        super(properties);
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
            UltimatumMod.ARTIFACT_MOBILITY_SERVICE.updateNow(player);
            UltimatumMod.ARTIFACT_REACH_SERVICE.updateNow(player);
            UltimatumMod.ARTIFACT_UTILITY_SERVICE.updateNow(player);
        }
    }

    @Override
    public void onUnequip(SlotContext context, ItemStack newStack, ItemStack stack) {
        // Curios can report an NBT/configuration update as an old-stack/new-stack swap.
        // That is still the same equipped Artifact and must not cancel active flight.
        if (newStack.is(UltimatumMod.ABSOLUTE_ARTIFACT.get())) {
            return;
        }
        if (!context.entity().level().isClientSide && context.entity() instanceof Player player) {
            InvincibilityService.release(player);
            UltimatumMod.ARTIFACT_MOBILITY_SERVICE.disableNow(player);
            UltimatumMod.ARTIFACT_REACH_SERVICE.disableNow(player);
            UltimatumMod.ARTIFACT_UTILITY_SERVICE.disableNow(player);
        }
    }

    @Override
    public void curioTick(SlotContext context, ItemStack stack) {
        if (!context.entity().level().isClientSide && context.entity() instanceof Player player) {
            InvincibilityService.anchor(player);
            InvincibilityService.restoreNow(player);
            UltimatumMod.ARTIFACT_MOBILITY_SERVICE.updateNow(player);
            UltimatumMod.ARTIFACT_REACH_SERVICE.updateNow(player);
        }
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(
            SlotContext context, UUID slotUuid, ItemStack stack) {
        ImmutableMultimap.Builder<Attribute, AttributeModifier> modifiers =
                ImmutableMultimap.builder();
        modifiers.put(Attributes.MOVEMENT_SPEED, new AttributeModifier(
                MOVEMENT_SPEED_ID, "Absolute Artifact ground speed", 0.1D,
                AttributeModifier.Operation.ADDITION));
        modifiers.put(ForgeMod.SWIM_SPEED.get(), new AttributeModifier(
                SWIM_SPEED_ID, "Absolute Artifact swim speed", 2.0D,
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
        tooltip.add(Component.translatable("item.ultimatum.absolute_artifact.desc")
                .withStyle(ChatFormatting.GOLD));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }
}
