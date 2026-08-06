package dev.srryo.ultimatum;

import com.mojang.logging.LogUtils;
import dev.srryo.ultimatum.item.AbsoluteEndItem;
import dev.srryo.ultimatum.invincibility.InvincibilityService;
import dev.srryo.ultimatum.kill.KillService;
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

@Mod(UltimatumMod.MOD_ID)
public final class UltimatumMod {
    public static final String MOD_ID = "ultimatum";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final KillService KILL_SERVICE = new KillService();
    public static final InvincibilityService INVINCIBILITY_SERVICE = new InvincibilityService();

    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);

    public static final RegistryObject<Item> ABSOLUTE_END = ITEMS.register("absolute_end",
            () -> new AbsoluteEndItem(Tiers.NETHERITE, 7, -2.4F,
                    new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));

    public UltimatumMod(FMLJavaModLoadingContext context) {
        IEventBus modBus = context.getModEventBus();
        ITEMS.register(modBus);
        modBus.addListener(this::addCreativeTabContents);
        modBus.addListener(this::registerGameTests);
        MinecraftForge.EVENT_BUS.register(KILL_SERVICE);
        MinecraftForge.EVENT_BUS.register(INVINCIBILITY_SERVICE);
    }

    private void addCreativeTabContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            event.accept(ABSOLUTE_END);
        }
    }

    private void registerGameTests(RegisterGameTestsEvent event) {
        event.register(UltimatumGameTests.class);
    }
}
