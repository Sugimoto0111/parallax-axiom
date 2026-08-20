package dev.srryo.parallaxaxiom;

import com.mojang.logging.LogUtils;
import dev.srryo.parallaxaxiom.item.FinalConclusionItem;
import dev.srryo.parallaxaxiom.item.InvariantObserverItem;
import dev.srryo.parallaxaxiom.item.ObservationMirrorItem;
import dev.srryo.parallaxaxiom.invincibility.InvincibilityService;
import dev.srryo.parallaxaxiom.mobility.ArtifactMobilityService;
import dev.srryo.parallaxaxiom.mobility.ArtifactReachService;
import dev.srryo.parallaxaxiom.mobility.ArtifactUtilityService;
import dev.srryo.parallaxaxiom.kill.KillService;
import dev.srryo.parallaxaxiom.network.ParallaxAxiomNetwork;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.Tiers;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.RegisterGameTestsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

@Mod(ParallaxAxiomMod.MOD_ID)
public final class ParallaxAxiomMod {
    public static final String MOD_ID = "parallax_axiom";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final KillService KILL_SERVICE = new KillService();
    public static final InvincibilityService INVINCIBILITY_SERVICE = new InvincibilityService();
    public static final ArtifactMobilityService ARTIFACT_MOBILITY_SERVICE =
            new ArtifactMobilityService();
    public static final ArtifactReachService ARTIFACT_REACH_SERVICE =
            new ArtifactReachService();
    public static final ArtifactUtilityService ARTIFACT_UTILITY_SERVICE =
            new ArtifactUtilityService();

    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);

    public static final RegistryObject<Item> FINAL_CONCLUSION = ITEMS.register("final_conclusion",
            () -> new FinalConclusionItem(Tiers.NETHERITE, 7, -2.4F,
                    new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));
    public static final RegistryObject<Item> INVARIANT_OBSERVER = ITEMS.register("invariant_observer",
            () -> new InvariantObserverItem(
                    new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));
    public static final RegistryObject<Item> ZERO_FOCUS = ITEMS.register("zero_focus",
            () -> new Item(new Item.Properties().stacksTo(1)
                    .rarity(Rarity.EPIC).fireResistant()));
    public static final RegistryObject<Item> ORIGINAL_IMAGE_MIRROR = ITEMS.register(
            "original_image_mirror", () -> new ObservationMirrorItem(
                    new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant(),
                    "item.parallax_axiom.original_image_mirror.lore"));
    public static final RegistryObject<Item> TERMINAL_IMAGE_MIRROR = ITEMS.register(
            "terminal_image_mirror", () -> new ObservationMirrorItem(
                    new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant(),
                    "item.parallax_axiom.terminal_image_mirror.lore"));

    public ParallaxAxiomMod(FMLJavaModLoadingContext context) {
        ParallaxAxiomNetwork.register();
        IEventBus modBus = context.getModEventBus();
        ITEMS.register(modBus);
        modBus.addListener(this::addCreativeTabContents);
        modBus.addListener(this::registerGameTests);
        MinecraftForge.EVENT_BUS.register(KILL_SERVICE);
        MinecraftForge.EVENT_BUS.register(INVINCIBILITY_SERVICE);
        MinecraftForge.EVENT_BUS.register(ARTIFACT_MOBILITY_SERVICE);
        MinecraftForge.EVENT_BUS.register(ARTIFACT_REACH_SERVICE);
        MinecraftForge.EVENT_BUS.register(ARTIFACT_UTILITY_SERVICE);
    }

    private void addCreativeTabContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            event.accept(FINAL_CONCLUSION);
            event.accept(INVARIANT_OBSERVER);
            event.accept(ZERO_FOCUS);
            event.accept(ORIGINAL_IMAGE_MIRROR);
            event.accept(TERMINAL_IMAGE_MIRROR);
        }
    }

    private void registerGameTests(RegisterGameTestsEvent event) {
        event.register(ParallaxAxiomGameTests.class);
    }
}
