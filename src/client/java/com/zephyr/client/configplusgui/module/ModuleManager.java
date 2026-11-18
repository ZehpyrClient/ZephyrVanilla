package com.zephyr.client.configplusgui.module;

import com.zephyr.client.api.ZephyrEvents;

import com.zephyr.client.configplusgui.config.ConfigManager;
import com.zephyr.client.configplusgui.config.GrimHidingManager;
import com.zephyr.client.configplusgui.config.ProfileManager;

import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Registry of every module singleton. {@link #init()} instantiates the movement, disable,
 * restores their persisted state via {@link ConfigManager#load}. The registry drives the
 * click-gui listing, per-tick dispatch of enabled modules, and the enable-count shown in
 * the HUD; the "Default" profile's initial snapshot is built from it via
 * {@link ProfileManager}.
 */
@SuppressWarnings ("all")
public final class ModuleManager {
    private static final List<Module> MODULES = new ArrayList<>();
    /** Modules registered by addons before {@link #init()} has run; drained at the end of init. */
    private static final List<Module> PENDING = new ArrayList<>();
    /** Whether the built-in registration pass and the initial config load have completed. */
    private static boolean initialized = false;

    private ModuleManager() {
    }

    /** Registers every module singleton and loads their persisted state from disk. */
    public static void init() {
        // No built-in modules: ZephyrVanilla ships only the API/systems.
        ConfigManager.load(MODULES);
        // If "Hide Detectable Modules" persisted as ON, snapshot loaded states and force-disable detectables.
        GrimHidingManager.reapplyAfterLoad();

        // Addons may register modules from their own client initializers, which can run
        // before or after Zephyr's; queue early ones and drain them now that config is loaded.
        initialized = true;
        if (!PENDING.isEmpty()) {
            List<Module> pending = new ArrayList<>(PENDING);
            PENDING.clear();
            for (Module module : pending) {
                registerModule(module);
            }
        }
    }

    /**
     * Adds a module to the registry; called from {@link #init()} in display order.
     * Unlike {@link #registerModule(Module)} this never queues or loads config - it
     * is purely for the built-in registration pass.
     */
    private static void register(Module module) {
        MODULES.add(module);
    }

    /**
     * Registers a module (typically from an addon) so it shows up in the click-gui,
     * gets persisted, receives ticks and gets a keybind slot.
     * <p>
     * Safe to call at any point during client initialization: calls made before
     * Zephyr's initial config load are queued and applied at the end of
     * {@link #init()}; calls made afterwards take effect immediately and have the
     * module's persisted state restored from {@code modules.json}. Registering the
     * same instance twice is a no-op.
     *
     * @param module the module singleton to register
     */
    public static void registerModule(Module module) {
        if (module == null || MODULES.contains(module)) {
            return;
        }
        if (!initialized) {
            PENDING.add(module);
            return;
        }
        MODULES.add(module);
        ZephyrEvents.fireModuleRegistered(module);
        ConfigManager.loadFor(module);
        if (GrimHidingManager.isActive() && module.isGrimDetectable()) {
            module.setEnabledSilently(false);
        }
    }

    /** Removes a registered module. No-op if not registered. */
    public static void unregisterModule(Module module) {
        if (module == null) return;
        MODULES.remove(module);
        PENDING.remove(module);
    }

    /** Returns all registered modules in registration order, read-only. */
    public static List<Module> getModules() {
        return Collections.unmodifiableList(MODULES);
    }

    /**
     * Returns only the modules visible to the current player/mode (hidden modules
     * filtered when not allowed, Grim-detectable modules filtered while
     * {@code Hide Detectable Modules} is active).
     */
    public static List<Module> getVisibleModules() {
        List<Module> visible = HiddenModules.filterVisible(MODULES);
        if (!GrimHidingManager.isActive()) {
            return visible;
        }
        List<Module> out = new ArrayList<>();
        for (Module module : visible) {
            if (!module.isGrimDetectable()) {
                out.add(module);
            }
        }
        return Collections.unmodifiableList(out);
    }

    /**
     * Returns the modules rendered in the main HUD. Hidden modules are never included here,
     * even when they are unlocked - they are listed on the secret screen instead.
     * Grim-detectable modules are also excluded while {@code Hide Detectable Modules}
     * is active (they are force-disabled, so this mostly matters for consistency).
     */
    public static List<Module> getHudModules() {
        List<Module> hud = new ArrayList<>();
        boolean grimActive = GrimHidingManager.isActive();
        for (Module module : MODULES) {
            if (module.isHidden()) continue;
            if (grimActive && module.isGrimDetectable()) continue;
            hud.add(module);
        }
        return Collections.unmodifiableList(hud);
    }

    /**
     * Returns every hidden module. Callers must gate rendering on
     * {@link HiddenModules#shouldShowHidden()}; the secret screen does this.
     */
    public static List<Module> getHiddenModules() {
        List<Module> hidden = new ArrayList<>();
        for (Module module : MODULES) {
            if (module.isHidden()) {
                hidden.add(module);
            }
        }
        return Collections.unmodifiableList(hidden);
    }

    /**
     * Looks up a module by name, case-insensitively.
     *
     * @return the matching module, or {@code null} if none is registered
     */
    public static Module get(String name) {
        for (Module module : MODULES) {
            if (module.getName().equalsIgnoreCase(name)) {
                return module;
            }
        }
        return null;
    }

    /** The number of currently enabled non-hidden modules, matching what the main HUD lists. */
    public static int enabledCount() {
        int count = 0;
        for (Module module : getHudModules()) {
            if (module.isEnabled()) count++;
        }
        return count;
    }

    /** Returns every Grim-detectable module, regardless of the hiding toggle. */
    public static List<Module> getGrimDetectableModules() {
        List<Module> out = new ArrayList<>();
        for (Module module : MODULES) {
            if (module.isGrimDetectable()) {
                out.add(module);
            }
        }
        return Collections.unmodifiableList(out);
    }

    /** Whether the given module is currently suppressed by Hide Detectable Modules. */
    public static boolean isGrimHidden(Module module) {
        return GrimHidingManager.isHidden(module);
    }

    /** Forwards the client tick to every enabled module (skipping Grim-suppressed ones as a safety net). */
    public static void tick(Minecraft client) {
        boolean grimActive = GrimHidingManager.isActive();
        for (Module module : MODULES) {
            if (!module.isEnabled()) continue;
            if (grimActive && module.isGrimDetectable()) continue;
            module.tick(client);
        }
    }

    /** Persists all module state to {@code modules.json} and refreshes the active profile snapshot. */
    public static void saveAll() {
        ConfigManager.save(MODULES);
        ProfileManager.captureActiveProfile();
    }
}
