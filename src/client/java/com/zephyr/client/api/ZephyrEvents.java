package com.zephyr.client.api;

import com.zephyr.client.commands.Command;
import com.zephyr.client.configplusgui.module.Module;
import com.zephyr.client.configplusgui.setting.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Lightweight event bus for addons.
 *
 * <p>Listeners can be registered at any time and stay active for the life of the
 * client. Dispatch happens on the main client thread, so listeners may safely
 * access the world, player, and Zephyr registries.
 *
 * <p>Available events:
 * <ul>
 *   <li>Module toggles: {@link #onModuleToggled(java.util.function.Consumer)}</li>
 *   <li>Setting changes: {@link #onSettingChanged(java.util.function.Consumer)}</li>
 *   <li>Client tick: {@link #onClientTick(java.util.function.Consumer)}</li>
 *   <li>World join/disconnect: {@link #onWorldJoin(java.util.function.Consumer)},
 *       {@link #onWorldDisconnect(java.util.function.Consumer)}</li>
 *   <li>Module/command registration: {@link #onModuleRegistered(java.util.function.Consumer)},
 *       {@link #onCommandRegistered(java.util.function.Consumer)}</li>
 *   <li>Profile change: {@link #onProfileChanged(java.util.function.Consumer)}</li>
 *   <li>Stealth/hide-detectable toggles: {@link #onStealthModeChanged(java.util.function.Consumer)},
 *       {@link #onHideDetectableChanged(java.util.function.Consumer)}</li>
 *   <li>Screen open/close: {@link #onScreenOpened(java.util.function.Consumer)},
 *       {@link #onScreenClosed(java.util.function.Consumer)}</li>
 *   <li>Config save: {@link #onConfigSaved(Runnable)}</li>
 * </ul>
 */
public final class ZephyrEvents {
    private static final List<Consumer<ModuleToggleEvent>> MODULE_TOGGLED = new CopyOnWriteArrayList<>();
    private static final List<Consumer<SettingChangedEvent>> SETTING_CHANGED = new CopyOnWriteArrayList<>();
    private static final List<Consumer<Minecraft>> CLIENT_TICK = new CopyOnWriteArrayList<>();
    private static final List<Consumer<Minecraft>> WORLD_JOIN = new CopyOnWriteArrayList<>();
    private static final List<Consumer<Minecraft>> WORLD_DISCONNECT = new CopyOnWriteArrayList<>();
    private static final List<Consumer<Module>> MODULE_REGISTERED = new CopyOnWriteArrayList<>();
    private static final List<Consumer<Command>> COMMAND_REGISTERED = new CopyOnWriteArrayList<>();
    private static final List<Consumer<String>> PROFILE_CHANGED = new CopyOnWriteArrayList<>();
    private static final List<Consumer<Boolean>> STEALTH_MODE_CHANGED = new CopyOnWriteArrayList<>();
    private static final List<Consumer<Boolean>> HIDE_DETECTABLE_CHANGED = new CopyOnWriteArrayList<>();
    private static final List<Consumer<Screen>> SCREEN_OPENED = new CopyOnWriteArrayList<>();
    private static final List<Consumer<Screen>> SCREEN_CLOSED = new CopyOnWriteArrayList<>();
    private static final List<Runnable> CONFIG_SAVED = new CopyOnWriteArrayList<>();

    private ZephyrEvents() {
    }

    /** Fired whenever any module (built-in or addon) transitions between enabled and disabled. */
    public record ModuleToggleEvent(Module module, boolean enabled) {
    }

    /** Fired whenever any setting's value is written. The setting doesn't know its owner module. */
    public record SettingChangedEvent(Setting<?> setting) {
    }

    /** Registers a listener for {@link ModuleToggleEvent}s. */
    public static void onModuleToggled(Consumer<ModuleToggleEvent> listener) {
        MODULE_TOGGLED.add(listener);
    }

    /** Registers a listener for {@link SettingChangedEvent}s. */
    public static void onSettingChanged(Consumer<SettingChangedEvent> listener) {
        SETTING_CHANGED.add(listener);
    }

    /** Registers a listener fired at the end of every client tick, screen open or not. */
    public static void onClientTick(Consumer<Minecraft> listener) {
        CLIENT_TICK.add(listener);
    }

    /** Registers a listener fired when the client joins a world/server. */
    public static void onWorldJoin(Consumer<Minecraft> listener) {
        WORLD_JOIN.add(listener);
    }

    /** Registers a listener fired when the client leaves a world/server. */
    public static void onWorldDisconnect(Consumer<Minecraft> listener) {
        WORLD_DISCONNECT.add(listener);
    }

    /** Registers a listener fired whenever a module is registered. */
    public static void onModuleRegistered(Consumer<Module> listener) {
        MODULE_REGISTERED.add(listener);
    }

    /** Registers a listener fired whenever a command is registered. */
    public static void onCommandRegistered(Consumer<Command> listener) {
        COMMAND_REGISTERED.add(listener);
    }

    /** Registers a listener fired when the active profile changes. */
    public static void onProfileChanged(Consumer<String> listener) {
        PROFILE_CHANGED.add(listener);
    }

    /** Registers a listener fired when Stealth Mode toggles. */
    public static void onStealthModeChanged(Consumer<Boolean> listener) {
        STEALTH_MODE_CHANGED.add(listener);
    }

    /** Registers a listener fired when Hide Detectable Modules toggles. */
    public static void onHideDetectableChanged(Consumer<Boolean> listener) {
        HIDE_DETECTABLE_CHANGED.add(listener);
    }

    /** Registers a listener fired when a screen is opened via ZephyrApi. */
    public static void onScreenOpened(Consumer<Screen> listener) {
        SCREEN_OPENED.add(listener);
    }

    /** Registers a listener fired when a screen is closed via ZephyrApi. */
    public static void onScreenClosed(Consumer<Screen> listener) {
        SCREEN_CLOSED.add(listener);
    }

    /** Registers a listener fired after config is saved via ZephyrApi. */
    public static void onConfigSaved(Runnable listener) {
        CONFIG_SAVED.add(listener);
    }

    /** Dispatches a module toggle; called by {@link Module#setEnabled}. */
    public static void fireModuleToggled(Module module, boolean enabled) {
        for (Consumer<ModuleToggleEvent> listener : MODULE_TOGGLED) {
            listener.accept(new ModuleToggleEvent(module, enabled));
        }
    }

    /** Dispatches a setting change; called by {@link Setting#setValue}. */
    public static void fireSettingChanged(Setting<?> setting) {
        for (Consumer<SettingChangedEvent> listener : SETTING_CHANGED) {
            listener.accept(new SettingChangedEvent(setting));
        }
    }

    /** Dispatches a client tick; called by Zephyr's client initializer. */
    public static void fireClientTick(Minecraft client) {
        for (Consumer<Minecraft> listener : CLIENT_TICK) {
            listener.accept(client);
        }
    }

    /** Dispatches a world join; called by Zephyr's client initializer. */
    public static void fireWorldJoin(Minecraft client) {
        for (Consumer<Minecraft> listener : WORLD_JOIN) {
            listener.accept(client);
        }
    }

    /** Dispatches a world disconnect; called by Zephyr's client initializer. */
    public static void fireWorldDisconnect(Minecraft client) {
        for (Consumer<Minecraft> listener : WORLD_DISCONNECT) {
            listener.accept(client);
        }
    }

    /** Dispatches a module registration. */
    public static void fireModuleRegistered(Module module) {
        for (Consumer<Module> listener : MODULE_REGISTERED) {
            listener.accept(module);
        }
    }

    /** Dispatches a command registration. */
    public static void fireCommandRegistered(Command command) {
        for (Consumer<Command> listener : COMMAND_REGISTERED) {
            listener.accept(command);
        }
    }

    /** Dispatches a profile change. */
    public static void fireProfileChanged(String profileName) {
        for (Consumer<String> listener : PROFILE_CHANGED) {
            listener.accept(profileName);
        }
    }

    /** Dispatches a Stealth Mode change. */
    public static void fireStealthModeChanged(boolean enabled) {
        for (Consumer<Boolean> listener : STEALTH_MODE_CHANGED) {
            listener.accept(enabled);
        }
    }

    /** Dispatches a Hide Detectable change. */
    public static void fireHideDetectableChanged(boolean enabled) {
        for (Consumer<Boolean> listener : HIDE_DETECTABLE_CHANGED) {
            listener.accept(enabled);
        }
    }

    /** Dispatches a screen open. */
    public static void fireScreenOpened(Screen screen) {
        for (Consumer<Screen> listener : SCREEN_OPENED) {
            listener.accept(screen);
        }
    }

    /** Dispatches a screen close. */
    public static void fireScreenClosed(Screen screen) {
        for (Consumer<Screen> listener : SCREEN_CLOSED) {
            listener.accept(screen);
        }
    }

    /** Dispatches a config save. */
    public static void fireConfigSaved() {
        for (Runnable listener : CONFIG_SAVED) {
            listener.run();
        }
    }
}
