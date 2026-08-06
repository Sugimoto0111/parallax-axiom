package dev.srryo.ultimatum.mixin.accessor;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(SynchedEntityData.class)
public interface SynchedEntityDataAccessor {
    @Invoker("getItem")
    <T> SynchedEntityData.DataItem<T> ultimatum$getItem(EntityDataAccessor<T> accessor);

    @Accessor("isDirty")
    void ultimatum$setDirty(boolean dirty);
}
