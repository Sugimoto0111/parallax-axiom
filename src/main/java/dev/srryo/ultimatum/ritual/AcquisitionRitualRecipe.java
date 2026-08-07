package dev.srryo.ultimatum.ritual;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/** A data-driven list of offerings accepted by the End exit-podium altar. */
public final class AcquisitionRitualRecipe implements Recipe<Container> {
    private final ResourceLocation id;
    private final List<Requirement> requirements;
    private final ItemStack result;
    private final boolean requiresWornObserver;
    private final int duration;
    private final int priority;

    public AcquisitionRitualRecipe(ResourceLocation id, List<Requirement> requirements,
                                   ItemStack result, boolean requiresWornObserver,
                                   int duration, int priority) {
        this.id = id;
        this.requirements = List.copyOf(requirements);
        this.result = result.copy();
        this.requiresWornObserver = requiresWornObserver;
        this.duration = duration;
        this.priority = priority;
    }

    public List<Requirement> requirements() {
        return requirements;
    }

    public boolean requiresWornObserver() {
        return requiresWornObserver;
    }

    public int duration() {
        return duration;
    }

    public int priority() {
        return priority;
    }

    @Override
    public boolean matches(Container container, Level level) {
        return false;
    }

    @Override
    public ItemStack assemble(Container container, RegistryAccess registryAccess) {
        return result.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return false;
    }

    @Override
    public ItemStack getResultItem(RegistryAccess registryAccess) {
        return result.copy();
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return RitualRegistries.ACQUISITION_SERIALIZER.get();
    }

    @Override
    public net.minecraft.world.item.crafting.RecipeType<?> getType() {
        return RitualRegistries.ACQUISITION_TYPE.get();
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    public record Requirement(Ingredient ingredient, int count) {
        public Requirement {
            if (count <= 0) {
                throw new IllegalArgumentException("Ritual ingredient count must be positive");
            }
        }
    }

    public static final class Serializer implements RecipeSerializer<AcquisitionRitualRecipe> {
        @Override
        public AcquisitionRitualRecipe fromJson(ResourceLocation id, JsonObject json) {
            JsonArray ingredientArray = GsonHelper.getAsJsonArray(json, "ingredients");
            List<Requirement> requirements = new ArrayList<>(ingredientArray.size());
            for (int index = 0; index < ingredientArray.size(); index++) {
                JsonObject entry = GsonHelper.convertToJsonObject(
                        ingredientArray.get(index), "ingredients[" + index + "]");
                Ingredient ingredient = Ingredient.fromJson(
                        GsonHelper.getAsJsonObject(entry, "ingredient"));
                int count = GsonHelper.getAsInt(entry, "count", 1);
                requirements.add(new Requirement(ingredient, count));
            }
            if (requirements.isEmpty()) {
                throw new IllegalArgumentException("Acquisition ritual requires an offering");
            }

            ItemStack result = ShapedRecipe.itemStackFromJson(
                    GsonHelper.getAsJsonObject(json, "result"));
            boolean requiresObserver = GsonHelper.getAsBoolean(
                    json, "requires_worn_observer", false);
            int duration = Math.max(20, GsonHelper.getAsInt(json, "duration", 160));
            int priority = GsonHelper.getAsInt(json, "priority", 0);
            return new AcquisitionRitualRecipe(id, requirements, result,
                    requiresObserver, duration, priority);
        }

        @Override
        public AcquisitionRitualRecipe fromNetwork(ResourceLocation id,
                                                    FriendlyByteBuf buffer) {
            int size = buffer.readVarInt();
            List<Requirement> requirements = new ArrayList<>(size);
            for (int index = 0; index < size; index++) {
                requirements.add(new Requirement(Ingredient.fromNetwork(buffer),
                        buffer.readVarInt()));
            }
            ItemStack result = buffer.readItem();
            boolean requiresObserver = buffer.readBoolean();
            int duration = buffer.readVarInt();
            int priority = buffer.readVarInt();
            return new AcquisitionRitualRecipe(id, requirements, result,
                    requiresObserver, duration, priority);
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer,
                              AcquisitionRitualRecipe recipe) {
            buffer.writeVarInt(recipe.requirements.size());
            for (Requirement requirement : recipe.requirements) {
                requirement.ingredient.toNetwork(buffer);
                buffer.writeVarInt(requirement.count);
            }
            buffer.writeItem(recipe.result);
            buffer.writeBoolean(recipe.requiresWornObserver);
            buffer.writeVarInt(recipe.duration);
            buffer.writeVarInt(recipe.priority);
        }
    }
}
