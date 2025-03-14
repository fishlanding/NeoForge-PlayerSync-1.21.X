package net.doodlechaos.playersync;

import com.mojang.brigadier.CommandDispatcher;
import net.doodlechaos.playersync.command.*;
import net.doodlechaos.playersync.input.InputsManager;
import net.doodlechaos.playersync.sync.AudioSync;
import net.doodlechaos.playersync.sync.SyncKeyframe;
import net.doodlechaos.playersync.sync.SyncTimeline;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.ClientChatEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(PlayerSync.MOD_ID)
public class PlayerSync
{
    public static final String MOD_ID = "playersync";
    public static final Logger SLOGGER = LogUtils.getLogger();

    public static boolean overrideTickDelta;
    public static float myTickDelta = 0;
    public static boolean OpenKeyCommandsEditScreen = false;

    public PlayerSync(IEventBus modEventBus, ModContainer modContainer)
    {
        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);

        // Register ourselves for server and other game events we are interested in.
        // Note that this is necessary if and only if we want *this* class (ExampleMod) to respond directly to events.
        // Do not add this line if there are no @SubscribeEvent-annotated functions in this class, like onServerStarting() below.
        NeoForge.EVENT_BUS.register(this);
        // Register the item to a creative tab
        modEventBus.addListener(this::addCreative);

        // Register our mod's ModConfigSpec so that FML can create and load the config file for us
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    // Register commands using RegisterCommandsEvent
    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        TickDeltaCommand.registerTickDeltaCommand(dispatcher);
        TimelineCommands.registerTimelineCommands(dispatcher);
        RecordCommands.registerRecordCommands(dispatcher);
        RenderCommands.registerRenderCommands(dispatcher);
        TestCommands.registerTestCommands(dispatcher);

        SLOGGER.info("Done registering commands");
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event){

        // Your code here – this runs after the server tick has finished.
        //SLOGGER.info("Detected end of server tick");
    }

    @SubscribeEvent
    public void onPlayerJoinWorld(PlayerEvent.PlayerLoggedInEvent event){
        String audioPath = "C:\\Users\\marky\\Downloads\\mainThemeRemix.ogg";
        AudioSync.loadAudio(audioPath);
        event.getEntity().sendSystemMessage(Component.literal("loaded audio: " + audioPath));
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent.Post event){
        Minecraft mc = Minecraft.getInstance();

        if(mc.screen == null && OpenKeyCommandsEditScreen)
        {
            SyncKeyframe key = SyncTimeline.getCurrKeyframe();
            if(key != null)
                mc.setScreen(new CommandListScreen(mc, (int)key.frame, key.cmds));
        }
    }

    private void commonSetup(final FMLCommonSetupEvent event)
    {
        // Some common setup code
        SLOGGER.info("HELLO FROM COMMON SETUP");

    }

    private void addCreative(BuildCreativeModeTabContentsEvent event)
    {

    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event)
    {
        // Do something when the server starts
        SLOGGER.info("HELLO from server starting");
    }

    // You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
    @EventBusSubscriber(modid = MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents
    {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event)
        {
            // Some client setup code
            SLOGGER.info("HELLO FROM CLIENT SETUP");
            SLOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
        }
    }
}
