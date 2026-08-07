package dev.srryo.ultimatum.ritual;

import dev.srryo.ultimatum.UltimatumMod;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class RitualRegistries {
    private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS,
                    UltimatumMod.MOD_ID);
    private static final DeferredRegister<RecipeType<?>> TYPES =
            DeferredRegister.create(ForgeRegistries.RECIPE_TYPES,
                    UltimatumMod.MOD_ID);

    public static final RegistryObject<RecipeSerializer<AcquisitionRitualRecipe>>
            ACQUISITION_SERIALIZER = SERIALIZERS.register("acquisition_ritual",
            AcquisitionRitualRecipe.Serializer::new);
    public static final RegistryObject<RecipeType<AcquisitionRitualRecipe>>
            ACQUISITION_TYPE = TYPES.register("acquisition_ritual",
            () -> new RecipeType<>() {
                @Override
                public String toString() {
                    return UltimatumMod.MOD_ID + ":acquisition_ritual";
                }
            });

    private RitualRegistries() {
    }

    public static void register(IEventBus modBus) {
        SERIALIZERS.register(modBus);
        TYPES.register(modBus);
    }
}
