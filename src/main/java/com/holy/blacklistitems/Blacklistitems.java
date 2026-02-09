package com.holy.blacklistitems;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;

import java.io.*;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.Set;

@Mod(Blacklistitems.MODID)
public class Blacklistitems {
    public static final String MODID = "blacklistitems";
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Gson gson = new Gson();
    private static Set<String> blacklist = new HashSet<>();
    private static File blacklistFile;

    public Blacklistitems() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("starting");
        blacklistFile = event.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.PLAYER_DATA_DIR).resolveSibling("blacklist.json").toFile();

        loadBlacklist();
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("bli")
                        .requires(cs -> cs.hasPermission(2))
                        .then(Commands.literal("add")
                                .executes(this::addItemToBlacklist)
                        )
        );
    }

    private int addItemToBlacklist(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();

        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) {
            source.sendFailure(net.minecraft.network.chat.Component.literal("Your main hand is empty"));
            return 0;
        }

        String id = stack.getItem().toString();

        if (blacklist.add(id)) {
            saveBlacklist();
            source.sendSuccess(() -> Component.literal("Added to the blacklist: " + id), true);
        } else {
            source.sendFailure(Component.literal("This item is already on the blacklist."));
        }
        return 1;
    }

    private void loadBlacklist() {
        if (blacklistFile.exists()) {
            try (Reader reader = new FileReader(blacklistFile)) {
                Type type = new TypeToken<Set<String>>() {}.getType();
                blacklist = gson.fromJson(reader, type);
                if (blacklist == null) blacklist = new HashSet<>();
            } catch (IOException e) {
                e.printStackTrace();
                blacklist = new HashSet<>();
            }
        }
    }

    private void saveBlacklist() {
        try (Writer writer = new FileWriter(blacklistFile)) {
            gson.toJson(blacklist, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        MinecraftServer server = event.getServer();
        if (server == null) return;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player == null) continue;
            checkInventory(player);
        }
    }

    private void checkInventory(ServerPlayer serverPlayer) {
        for (ItemStack stack : serverPlayer.getInventory().items) {
            if (stack.isEmpty()) continue;
            String id = stack.getItem().toString();
            if (blacklist.contains(id)) {
                serverPlayer.sendSystemMessage(net.minecraft.network.chat.Component.literal("The " + stack.getDisplayName().getString() + " item is banned on this server!")
                        .withStyle(ChatFormatting.RED));
                serverPlayer.drop(stack.copy(), false, true);
                stack.shrink(stack.getCount());
            }
        }
    }
}
