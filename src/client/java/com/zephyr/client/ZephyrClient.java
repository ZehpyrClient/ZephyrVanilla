package com.zephyr.client;

import com.zephyr.Zephyr;
import com.zephyr.client.configplusgui.config.GlobalConfig;
import com.zephyr.client.configplusgui.config.ProfileManager;
import com.zephyr.client.configplusgui.hud.HudRenderer;
import com.zephyr.client.configplusgui.hud.NotificationManager;
import com.zephyr.client.configplusgui.hud.PartyManager;
import com.zephyr.client.configplusgui.keybind.GuiKeybindHandler;
import com.zephyr.client.configplusgui.keybind.KeybindManager;
import com.zephyr.client.configplusgui.module.ModuleManager;
import com.zephyr.client.commands.CommandPrefixHandler;
import com.zephyr.client.discord.DiscordPresenceManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import com.zephyr.client.api.ZephyrAddon;
import com.zephyr.client.api.ZephyrEvents;

/**
 * Zephyr's client-side entry point (Fabric {@link ClientModInitializer}).
 * Wires up the whole client: initializes configuration, modules, profiles and
 * keybinds, registers the {@code .z} command, hooks Discord Rich Presence to
 * client lifecycle and play connection events, registers tooltip providers, and
 * attaches per-tick handling plus HUD renderers (notifications and the HUD
 * overlay). Modules are periodically autosaved when the configured interval
 * elapses, and always saved on client stop.
 */
public class
ZephyrClient implements ClientModInitializer {
	private final GuiKeybindHandler guiKeybindHandler = new GuiKeybindHandler();

	private long tickCount = 0;

	/**
	 * Initializes every client-side subsystem and registers Fabric event
	 * handlers for lifecycle, tick, play connection, tooltip and HUD events.
	 */
	@Override
	public void onInitializeClient() {
		ClientLifecycleEvents.CLIENT_STARTED.register(client -> DiscordPresenceManager.initialize());
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> DiscordPresenceManager.shutdown());
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			DiscordPresenceManager.onWorldTransition();
			ZephyrEvents.fireWorldJoin(client);
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			DiscordPresenceManager.onWorldTransition();
			ZephyrEvents.fireWorldDisconnect(client);
		});


		GlobalConfig.init();
		ModuleManager.init();
		ProfileManager.init();
		KeybindManager.init();

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			TickScheduler.tick();
			ZephyrEvents.fireClientTick(client);
			guiKeybindHandler.tick(client);
			CommandPrefixHandler.tick(client);
			ModuleManager.tick(client);
			PartyManager.tick();

			tickCount++;
			double interval = GlobalConfig.autosaveIntervalSeconds();
			if (interval > 0 && tickCount % Math.max(1, (long) (interval * 20)) == 0) {
				ModuleManager.saveAll();
			}
		});

		HudElementRegistry.attachElementBefore(net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements.CHAT,
				Identifier.fromNamespaceAndPath("zephyr", "notifications"),
				(graphics, tickCounter) -> {
					Minecraft client = Minecraft.getInstance();
					NotificationManager.render(graphics, client.font,
							client.getWindow().getGuiScaledWidth(),
							client.getWindow().getGuiScaledHeight());
				});

		HudElementRegistry.attachElementBefore(net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements.CHAT,
				Identifier.fromNamespaceAndPath("zephyr", "hud_overlay"),
				(graphics, tickCounter) -> {
					Minecraft client = Minecraft.getInstance();
					HudRenderer.render(graphics, client.font,
							client.getWindow().getGuiScaledWidth(),
							client.getWindow().getGuiScaledHeight());
				});



		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			ModuleManager.saveAll();
		});

		invokeAddonEntrypoints();
	}


	/**
	 * Runs every {@link ZephyrAddon} declared under the {@code "zephyr"} entrypoint, after
	 * all of the subystems are initialized, so that any addons can register whatever they want. 
	 * Any failing addons are logged and skipped.
	 */
	private static void invokeAddonEntrypoints() {
		for (var container : FabricLoader.getInstance().getEntrypointContainers("zephyr", ZephyrAddon.class)) {
			String modId = container.getProvider().getMetadata().getId();
			try {
				container.getEntrypoint().onInitializeZephyr();
			} catch (Throwable t) {
				Zephyr.LOGGER.error("[Zephyr] Addon '{}' failed to initialize.", modId, t);
			}
		}
	}
}