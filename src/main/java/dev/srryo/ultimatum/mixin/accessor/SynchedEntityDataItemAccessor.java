package dev.srryo.ultimatum.mixin.accessor;

import net.minecraft.network.syncher.SynchedEntityData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SynchedEntityData.DataItem.class)
public interface SynchedEntityDataItemAccessor<T> {
    @Accessor("value")
    void ultimatum$setValue(T value);

    @Accessor("dirty")
    void ultimatum$setDirty(boolean dirty);
}
