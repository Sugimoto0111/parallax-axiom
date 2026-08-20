package dev.srryo.ultimatum.mixin.accessor;

import net.minecraft.client.gui.Gui;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Gui.class)
public interface GuiSelectedItemAccessor {
    @Accessor("toolHighlightTimer")
    int ultimatum$getToolHighlightTimer();

    @Accessor("lastToolHighlight")
    ItemStack ultimatum$getLastToolHighlight();
}
