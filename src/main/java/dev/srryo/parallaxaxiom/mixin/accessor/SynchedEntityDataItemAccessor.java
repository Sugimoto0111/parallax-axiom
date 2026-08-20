package dev.srryo.parallaxaxiom.mixin.accessor;

import net.minecraft.network.syncher.SynchedEntityData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SynchedEntityData.DataItem.class)
public interface SynchedEntityDataItemAccessor<T> {
    @Accessor("value")
    void parallaxAxiom$setValue(T value);

    @Accessor("dirty")
    void parallaxAxiom$setDirty(boolean dirty);
}
