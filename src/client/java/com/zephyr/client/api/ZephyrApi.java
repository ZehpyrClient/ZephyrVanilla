package com.zephyr.client.api;

import com.zephyr.client.MillisScheduler;
import com.zephyr.client.TickScheduler;
import com.zephyr.client.commands.Command;
import com.zephyr.client.commands.CommandManager;
import com.zephyr.client.commands.CommandPrefixHandler;
import com.zephyr.client.configplusgui.config.GlobalConfig;
import com.zephyr.client.configplusgui.config.ProfileManager;
import com.zephyr.client.configplusgui.hud.Corner;
import com.zephyr.client.configplusgui.hud.HudMode;
import com.zephyr.client.configplusgui.hud.NotificationManager;
import com.zephyr.client.configplusgui.hud.ThemeColor;
import com.zephyr.client.configplusgui.keybind.Keybind;
import com.zephyr.client.configplusgui.keybind.KeybindManager;
import com.zephyr.client.configplusgui.module.Category;
import com.zephyr.client.configplusgui.module.HiddenModules;
import com.zephyr.client.configplusgui.module.Module;
import com.zephyr.client.configplusgui.module.ModuleManager;
import com.zephyr.client.configplusgui.screen.ClickGuiScreen;
import com.zephyr.client.configplusgui.screen.ZephyrScreen;
import com.zephyr.client.configplusgui.setting.BooleanSetting;
import com.zephyr.client.configplusgui.setting.EnumSetting;
import com.zephyr.client.configplusgui.setting.ListSetting;
import com.zephyr.client.configplusgui.setting.NumberSetting;
import com.zephyr.client.configplusgui.setting.Setting;
import com.zephyr.client.configplusgui.setting.KeybindSetting;
import com.zephyr.client.configplusgui.setting.StringSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.util.List;
import java.util.function.Supplier;

/**
 * Public entry point for Zephyr addons.
 *
 * <p>An addon implements {@link ZephyrAddon} and declares it under the {@code "zephyr"}
 * entrypoint in {@code fabric.mod.json}:
 * <pre>{@code
 * "entrypoints": {
 *   "zephyr": [ "com.example.myaddon.MyAddon" ]
 * },
 * "depends": { "zephyr": "*" }
 * }</pre>
 *
 * <p>Registration:
 * <ul>
 *   <li>Modules: {@link #registerModule(Module)}, {@link #registerCategory(String)}</li>
 *   <li>Commands: {@link #registerCommand(Command)}</li>
 *   <li>Screens in the Tab cycle: {@link #registerCycleScreen(String, java.util.function.Supplier)}</li>
 * </ul>
 *
 * <p>Timing: registration is safe at any time during client startup. Calls made before
 * Zephyr loads config are queued and applied (with persisted state restored) at the
 * end of initialization; calls from {@link ZephyrAddon#onInitializeZephyr()} take
 * effect immediately. Late registration is recommended.
 *
 * <p>Control of registered modules: {@link #getModule(String)}, {@link #isEnabled(String)},
 * {@link #setEnabled(String, boolean)}, {@link #toggle(String)}.
 *
 * <p>Settings: {@link #getSetting(Module, String)}, {@link #getSettingValue(Module, String)},
 * {@link #setSettingValue(Module, String, Object)}, {@link #addListEntry(Module, String, String, String)},
 * {@link #clearList(Module, String)}.
 *
 * <p>Keybinds: {@link #getKeybind(Module)}, {@link #setKeybind(Module, Keybind)},
 * {@link #getSystemKeybind(KeybindManager.SystemAction)}, system actions via
 * {@link #getSystemActions()}.
 *
 * <p>Profiles: {@link #getProfileNames()}, {@link #getActiveProfile()}, {@link #applyProfile(String)},
 * {@link #createProfile(String)}, and related methods.
 *
 * <p>Global config: stealth mode, theme, HUD mode, notifications, animation speed,
 * autosave interval, Discord presence, and more getters/setters.
 *
 * <p>Screens: {@link #openScreen(Screen)}, {@link #openClickGui()}, {@link #closeScreen()},
 * {@link #cycleNext()}, {@link #cyclePrevious()}.
 *
 * <p>Feedback and utilities: {@link #notify(String, int)}, {@link #sendChatMessage(String)},
 * {@link #saveConfig()}, {@link #scheduleTicks(int, Runnable)}, {@link #scheduleMillis(long, Runnable)},
 * {@link #execute(Runnable)}, {@link #getClient()}.
 *
 * <p>Events: see {@link ZephyrEvents} for module/setting/tick/world/screen/profile hooks.
 *
 * <p>Threading: all methods are static and intended for the client thread. Use
 * {@link #execute(Runnable)} to hop to the client thread from elsewhere.
 *
 * <p>Persistence: module state is stored in {@code modules.json}; profiles snapshot
 * module and setting state. {@link #saveConfig()} persists immediately.
 */
@SuppressWarnings ("all")
public final class ZephyrApi {
    private ZephyrApi() {
    }

    // ---------------------------------------------------------------- registration

    /**
     * Registers an addon module so it appears in the click-gui, gets persisted to
     * {@code modules.json}, receives ticks while enabled and gets a keybind slot on
     * the keybinds screen. Safe to call before or after Zephyr finishes loading;
     * persisted state is applied automatically either way.
     *
     * @param module the module singleton to register
     */
    public static void registerModule(Module module) {
        ModuleManager.registerModule(module);
    }

    /**
     * Unregisters a previously registered module, removing it from the click-gui and
     * tick dispatch. No-op for unknown modules.
     *
     * @param module the module to remove
     */
    public static void unregisterModule(Module module) {
        if (module == null) return;
        ModuleManager.unregisterModule(module);
    }

    /**
     * Registers (or returns the already-registered) module category with the given
     * display name. Custom categories appear in the click-gui's category dropdown and
     * as tags on module rows. Pass the returned category to your module constructors.
     *
     * @param displayName the human-readable label, e.g. "MyAddon"
     * @return the category singleton for that name
     */
    public static Category registerCategory(String displayName) {
        return Category.of(displayName);
    }

    /**
     * Registers a chat command, invocable with Zephyr's command prefix and listed in
     * tab-completion.
     *
     * @param command the command to register
     */
    public static void registerCommand(Command command) {
        CommandManager.register(command);
    }

    /**
     * Unregisters a chat command by name.
     *
     * @param name the command name
     */
    public static void unregisterCommand(String name) {
        CommandManager.unregister(name);
    }

    /**
     * Registers an addon screen into the Tab screen cycle, after the built-in screens.
     * The factory creates a fresh instance each time the cycle reaches the entry; the
     * screen is a plain {@link Screen} and should call {@link #cycleNext()} or
     * {@link #cyclePrevious()} when it wants to continue cycling.
     *
     * @param id      a unique id for the entry, e.g. your mod id
     * @param factory creates the screen instance
     */
    public static void registerCycleScreen(String id, Supplier<Screen> factory) {
        ZephyrScreen.registerCycleScreen(id, factory);
    }

    /**
     * Unregisters an addon screen from the Tab cycle by id.
     *
     * @param id the entry id
     */
    public static void unregisterCycleScreen(String id) {
        ZephyrScreen.unregisterCycleScreen(id);
    }

    // ---------------------------------------------------------------- module access

    /**
     * Looks up a registered module by name, case-insensitively.
     *
     * @return the module, or {@code null} if none is registered
     */
    public static Module getModule(String name) {
        return ModuleManager.get(name);
    }

    /** All registered modules (built-in and addon) in registration order, read-only. */
    public static List<Module> getModules() {
        return ModuleManager.getModules();
    }

    /** All modules currently visible in the UI (respects hidden modules and Hide Detectable). */
    public static List<Module> getVisibleModules() {
        return ModuleManager.getVisibleModules();
    }

    /** Whether the named module is currently enabled; {@code false} for unknown names. */
    public static boolean isEnabled(String name) {
        Module module = ModuleManager.get(name);
        return module != null && module.isEnabled();
    }

    /** Enables or disables the named module (firing its onEnable/onDisable). No-op for unknown names. */
    public static void setEnabled(String name, boolean enabled) {
        Module module = ModuleManager.get(name);
        if (module != null) {
            module.setEnabled(enabled);
        }
    }

    /** Flips the named module between enabled and disabled. No-op for unknown names. */
    public static void toggle(String name) {
        Module module = ModuleManager.get(name);
        if (module != null) {
            module.toggle();
        }
    }

    // ---------------------------------------------------------------- settings

    /**
     * Finds a module's setting by name, case-sensitively (setting names are config keys).
     *
     * @return the setting, or {@code null} if the module or setting doesn't exist
     */
    public static Setting<?> getSetting(Module module, String settingName) {
        if (module == null) return null;
        for (Setting<?> setting : module.getSettings()) {
            if (setting.getName().equals(settingName)) {
                return setting;
            }
        }
        return null;
    }

    /**
     * Reads a setting's current value as an Object ({@code Boolean}, {@code Double},
     * {@code String}, the enum constant, or a {@code List<ListSetting.ListEntry>}).
     *
     * @return the value, or {@code null} if the module or setting doesn't exist
     */
    public static Object getSettingValue(Module module, String settingName) {
        Setting<?> setting = getSetting(module, settingName);
        return setting == null ? null : setting.get();
    }

    /**
     * Writes a setting's value with type coercion: {@code Boolean} for boolean settings,
     * any {@code Number} for number settings (clamped/snapped to the slider range), and
     * a constant {@code name()} string or enum instance for enum settings.
     *
     * @return {@code true} if the value was applied
     */
    public static boolean setSettingValue(Module module, String settingName, Object value) {
        Setting<?> setting = getSetting(module, settingName);
        if (setting == null || value == null) {
            return false;
        }
        if (setting instanceof BooleanSetting booleanSetting && value instanceof Boolean bool) {
            booleanSetting.set(bool);
            return true;
        }
        if (setting instanceof NumberSetting numberSetting && value instanceof Number number) {
            numberSetting.set(number.doubleValue());
            return true;
        }
        if (setting instanceof EnumSetting<?> enumSetting) {
            Object constant = value instanceof Enum<?> enumValue && enumSetting.getEnumType().isInstance(enumValue)
                    ? enumValue
                    : value instanceof String name ? findEnumConstant(enumSetting, name) : null;
            if (constant != null) {
                setEnumValue(enumSetting, (Enum) constant);
                return true;
            }
        }
        if (setting instanceof StringSetting stringSetting && value instanceof String string) {
            stringSetting.set(string);
            return true;
        }
        if (setting instanceof KeybindSetting keybindSetting && value instanceof Keybind keybind) {
            keybindSetting.set(keybind);
            return true;
        }
        return false;
    }

    /** Appends a {@code block,color} entry to a list setting (e.g. an Xray block list). */
    public static void addListEntry(Module module, String settingName, String blockId, String color) {
        Setting<?> setting = getSetting(module, settingName);
        if (setting instanceof ListSetting listSetting) {
            listSetting.add(blockId, color);
        }
    }

    /** Removes every entry from a list setting. */
    public static void clearList(Module module, String settingName) {
        Setting<?> setting = getSetting(module, settingName);
        if (setting instanceof ListSetting listSetting) {
            listSetting.clear();
        }
    }

    /** Looks up an enum constant by name against a setting's enum type. */
    private static Object findEnumConstant(EnumSetting<?> setting, String name) {
        for (Object constant : setting.getEnumType().getEnumConstants()) {
            if (((Enum<?>) constant).name().equalsIgnoreCase(name)) {
                return constant;
            }
        }
        return null;
    }

    /** Writes an enum setting's value via a raw-typed call into the setting. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void setEnumValue(EnumSetting setting, Enum constant) {
        setting.set(constant);
    }

    // ---------------------------------------------------------------- keybinds

    /** Returns the keybind bound to a module (or {@link Keybind#NONE}). */
    public static Keybind getKeybind(Module module) {
        return module == null ? Keybind.NONE : KeybindManager.get(module);
    }

    /** Returns the keybind bound to a module by name. */
    public static Keybind getKeybind(String moduleName) {
        Module module = ModuleManager.get(moduleName);
        return module == null ? Keybind.NONE : KeybindManager.get(module);
    }

    /** Sets a module's keybind. */
    public static void setKeybind(Module module, Keybind bind) {
        if (module != null) KeybindManager.set(module, bind);
    }

    /** Sets a module's keybind by name. */
    public static void setKeybind(String moduleName, Keybind bind) {
        Module module = ModuleManager.get(moduleName);
        if (module != null) KeybindManager.set(module, bind);
    }

    /** Clears a module's keybind. */
    public static void clearKeybind(Module module) {
        if (module != null) KeybindManager.clear(module);
    }

    /** Returns a system action's keybind. */
    public static Keybind getSystemKeybind(KeybindManager.SystemAction action) {
        return action == null ? Keybind.NONE : KeybindManager.get(action);
    }

    /** Sets a system action's keybind. */
    public static void setSystemKeybind(KeybindManager.SystemAction action, Keybind bind) {
        if (action != null) KeybindManager.set(action, bind);
    }

    /** Resets a system action's keybind to its default. */
    public static void resetSystemKeybind(KeybindManager.SystemAction action) {
        if (action != null) KeybindManager.resetToDefault(action);
    }

    /** Returns the resolved command prefix text (or null). */
    public static String getCommandPrefix() {
        return CommandPrefixHandler.currentPrefix();
    }

    /** All system actions. */
    public static KeybindManager.SystemAction[] getSystemActions() {
        return KeybindManager.SystemAction.values();
    }

    // ---------------------------------------------------------------- profiles

    /** All profile names. */
    public static List<String> getProfileNames() {
        return ProfileManager.getProfileNames();
    }

    /** The active profile name. */
    public static String getActiveProfile() {
        return ProfileManager.getActiveProfile();
    }

    /** Creates a profile snapshotting current state and makes it active. */
    public static boolean createProfile(String name) {
        return ProfileManager.createProfile(name);
    }

    /** Applies a named profile. */
    public static boolean applyProfile(String name) {
        return ProfileManager.applyProfile(name);
    }

    /** Renames a profile. */
    public static boolean renameProfile(String oldName, String newName) {
        return ProfileManager.renameProfile(oldName, newName);
    }

    /** Deletes a profile. */
    public static boolean deleteProfile(String name) {
        return ProfileManager.deleteProfile(name);
    }

    /** Re-snapshots the active profile from current state. */
    public static void captureActiveProfile() {
        ProfileManager.captureActiveProfile();
    }

    // ---------------------------------------------------------------- global config

    public static boolean isStealthMode() {
        return GlobalConfig.stealthMode();
    }

    public static void setStealthMode(boolean enabled) {
        GlobalConfig.setStealthMode(enabled);
    }

    public static void toggleStealthMode() {
        GlobalConfig.toggleStealthMode();
    }

    public static boolean isHideDetectableModules() {
        return GlobalConfig.hideDetectableModules();
    }

    public static void setHideDetectableModules(boolean enabled) {
        GlobalConfig.setHideDetectableModules(enabled);
    }

    public static void toggleHideDetectableModules() {
        GlobalConfig.toggleHideDetectableModules();
    }

    public static boolean isTestMode() {
        return GlobalConfig.isTestMode();
    }

    public static void setTestMode(boolean enabled) {
        GlobalConfig.setTestMode(enabled);
    }

    public static boolean isHiddenModulesUnlocked() {
        return HiddenModules.shouldShowHidden();
    }

    public static ThemeColor getThemeColor() {
        return GlobalConfig.themeColor();
    }

    public static void setThemeColor(ThemeColor color) {
        GlobalConfig.setThemeColor(color);
    }

    public static void cycleThemeColor() {
        GlobalConfig.cycleThemeColor();
    }

    public static boolean useCustomColor() {
        return GlobalConfig.useCustomColor();
    }

    public static void setUseCustomColor(boolean enabled) {
        GlobalConfig.setUseCustomColor(enabled);
    }

    public static double getAnimationSpeed() {
        return GlobalConfig.animationSpeed();
    }

    public static void setAnimationSpeed(double speed) {
        GlobalConfig.setAnimationSpeed(speed);
    }

    public static Corner getNotificationCorner() {
        return GlobalConfig.notificationCorner();
    }

    public static void setNotificationCorner(Corner corner) {
        GlobalConfig.setNotificationCorner(corner);
    }

    public static void cycleNotificationCorner() {
        GlobalConfig.cycleNotificationCorner();
    }

    public static double getNotificationHoldSeconds() {
        return GlobalConfig.notificationHoldSeconds();
    }

    public static void setNotificationHoldSeconds(double seconds) {
        GlobalConfig.setNotificationHoldSeconds(seconds);
    }

    public static HudMode getHudMode() {
        return GlobalConfig.hudMode();
    }

    public static void setHudMode(HudMode mode) {
        GlobalConfig.setHudMode(mode);
    }

    public static void cycleHudMode() {
        GlobalConfig.cycleHudMode();
    }

    public static double getAutosaveInterval() {
        return GlobalConfig.autosaveIntervalSeconds();
    }

    public static void setAutosaveInterval(double seconds) {
        GlobalConfig.setAutosaveInterval(seconds);
    }

    public static boolean keybindConflictWarningsEnabled() {
        return GlobalConfig.keybindConflictWarningsEnabled();
    }

    public static void setKeybindConflictWarningsEnabled(boolean enabled) {
        GlobalConfig.setKeybindConflictWarningsEnabled(enabled);
    }

    public static void toggleKeybindConflictWarnings() {
        GlobalConfig.toggleKeybindConflictWarnings();
    }

    public static boolean discordPresence() {
        return GlobalConfig.discordPresence();
    }

    public static void setDiscordPresence(boolean enabled) {
        GlobalConfig.setDiscordPresence(enabled);
    }

    public static void toggleDiscordPresence() {
        GlobalConfig.toggleDiscordPresence();
    }

    public static boolean hotkeyPopupsEnabled() {
        return GlobalConfig.hotkeyPopupsEnabled();
    }

    public static void setHotkeyPopupsEnabled(boolean enabled) {
        GlobalConfig.setHotkeyPopupsEnabled(enabled);
    }

    public static void toggleHotkeyPopups() {
        GlobalConfig.toggleHotkeyPopups();
    }

    public static int getAccentColor() {
        return GlobalConfig.accent();
    }

    // ---------------------------------------------------------------- screens

    /**
     * Opens any screen, replacing whatever is currently open. Runs through the client
     * thread executor, so it is safe from any thread.
     *
     * @param screen the screen to open
     */
    public static void openScreen(Screen screen) {
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> {
            Screen previous = client.gui.screen();
            client.gui.setScreen(screen);
            if (previous != null) ZephyrEvents.fireScreenClosed(previous);
            if (screen != null) ZephyrEvents.fireScreenOpened(screen);
        });
    }

    /** Opens Zephyr's click-gui module list. */
    public static void openClickGui() {
        openScreen(new ClickGuiScreen());
    }

    /** Closes the current screen (if any). */
    public static void closeScreen() {
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> {
            Screen previous = client.gui.screen();
            client.gui.setScreen(null);
            if (previous != null) ZephyrEvents.fireScreenClosed(previous);
        });
    }

    /** Whether Zephyr's click-gui is currently open. */
    public static boolean isClickGuiOpen() {
        return Minecraft.getInstance().gui.screen() instanceof ClickGuiScreen;
    }

    /** Advances to the next screen in the Tab cycle (built-ins, then addon screens). */
    public static void cycleNext() {
        Minecraft client = Minecraft.getInstance();
        Screen current = client.gui.screen();
        Screen next = ZephyrScreen.cycleNeighbor(current, 1);
        if (next != null) {
            client.gui.setScreen(next);
            if (current != null) ZephyrEvents.fireScreenClosed(current);
            ZephyrEvents.fireScreenOpened(next);
        }
    }

    /** Goes back one screen in the Tab cycle (wrapping through the built-ins). */
    public static void cyclePrevious() {
        Minecraft client = Minecraft.getInstance();
        Screen current = client.gui.screen();
        Screen previous = ZephyrScreen.cycleNeighbor(current, -1);
        if (previous != null) {
            client.gui.setScreen(previous);
            if (current != null) ZephyrEvents.fireScreenClosed(current);
            ZephyrEvents.fireScreenOpened(previous);
        }
    }

    // ---------------------------------------------------------------- feedback & utilities

    /**
     * Shows a toast notification on the HUD (hidden while Stealth Mode is active).
     *
     * @param text  the message to display
     * @param color the accent color for the toast's top strip, e.g. {@code 0xFF7B4DFF}
     */
    public static void notify(String text, int color) {
        NotificationManager.notifyText(text, color);
    }

    /** Adds a client-side system chat message (never sent to the server). */
    public static void sendChatMessage(String text) {
        CommandManager.sendMessage(text);
    }

    /** Persists every module's state to {@code modules.json} and refreshes the active profile. */
    public static void saveConfig() {
        ModuleManager.saveAll();
        ZephyrEvents.fireConfigSaved();
    }

    /** Runs the action after the given number of client ticks. */
    public static void scheduleTicks(int delayTicks, Runnable action) {
        TickScheduler.schedule(delayTicks, action);
    }

    /** Runs the action after the given number of milliseconds (on the client thread). */
    public static void scheduleMillis(long delayMillis, Runnable action) {
        MillisScheduler.schedule(delayMillis, action);
    }

    /** Runs an action on the client thread. */
    public static void execute(Runnable action) {
        Minecraft.getInstance().execute(action);
    }

    /** The running Minecraft client instance. */
    public static Minecraft getClient() {
        return Minecraft.getInstance();
    }

    /** All registered commands. */
    public static List<Command> getCommands() {
        return CommandManager.getCommands();
    }

    /** The current command prefix (derived from keybind). */
    public static String commandPrefix() {
        return CommandPrefixHandler.currentPrefix();
    }
}
