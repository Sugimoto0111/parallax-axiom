package dev.srryo.ultimatum.item;

import dev.srryo.ultimatum.UltimatumMod;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class AbsoluteEndItem extends SwordItem {
    public AbsoluteEndItem(Tier tier, int attackDamage, float attackSpeed, Properties properties) {
        super(tier, attackDamage, attackSpeed, properties);
    }

    @Override
    public boolean onLeftClickEntity(ItemStack stack, Player player, Entity entity) {
        if (player.level().isClientSide) {
            return false;
        }
        UltimatumMod.KILL_SERVICE.enqueue(player, entity);
        return true;
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (!attacker.level().isClientSide && attacker instanceof Player player) {
            UltimatumMod.KILL_SERVICE.enqueue(player, target);
        }
        return true;
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        super.inventoryTick(stack, level, entity, slot, selected);
        if (!level.isClientSide && entity instanceof Player player
                && player.swinging
                && (player.getMainHandItem() == stack || player.getOffhandItem() == stack)) {
            // This vanilla item hook still runs after Omni-Mobs replaces Forge's EVENT_BUS.
            UltimatumMod.KILL_SERVICE.onAbsoluteEndSwing(player);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip,
                                TooltipFlag flag) {
        tooltip.add(Component.translatable("item.ultimatum.absolute_end.desc")
                .withStyle(ChatFormatting.DARK_RED));
        tooltip.add(Component.translatable("item.ultimatum.absolute_end.invincibility")
                .withStyle(ChatFormatting.GOLD));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }
}
