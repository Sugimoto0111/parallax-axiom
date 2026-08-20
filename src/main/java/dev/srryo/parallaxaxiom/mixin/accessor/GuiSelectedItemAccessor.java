package dev.srryo.parallaxaxiom.mixin.accessor;

import net.minecraft.client.gui.Gui;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Gui.class)
public interface GuiSelectedItemAccessor {
    @Accessor("toolHighlightTimer")
    int parallaxAxiom$getToolHighlightTimer();

    @Accessor("lastToolHighlight")
    ItemStack parallaxAxiom$getLastToolHighlight();
}
