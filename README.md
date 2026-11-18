# Zephyr Vanilla

Zephyr Vanilla is the API-only base of Zephyr. It ships none of Zephyr's built-in modules or feature systems. Instead, it provides the core framework that addon mods build on: the module/setting/category system, config and profiles, keybinds, HUD, screens, events, and the public `ZephyrApi`.

Addon developers depend on this mod and register their own modules, commands, categories, and screens through the API.

- Minecraft: `26.3`
- Loader: Fabric Loader `>= 0.19.5`
- Java: `25`
- Requires: Fabric API

---

## Building from source

```bash
./gradlew build
```

Requires JDK 25.


# Zephyr Addon API

Zephyr exposes a small, stable API so other mods (addons) can register their own
modules, settings, categories, commands and screens, control built-in modules,
and react to Zephyr events, without touching Zephyr's internal packages.

Everything lives in `com.zephyr.client.api`:

| Class | Purpose |
|---|---|
| `ZephyrAddon` | Entrypoint interface your addon implements |
| `ZephyrApi`   | Static methods for registering and controlling features |
| `ZephyrEvents`| Lightweight event bus for module/setting/tick/world events |

The only Zephyr classes you need beyond the API are the base classes you extend:
`Module`, `Command` and the `Setting` types.

---

## Contents

- [Gradle setup](#gradle-setup)
- [Getting started](#getting-started)
  - [fabric.mod.json](#fabricmodjson)
  - [Writing the entrypoint class](#writing-the-entrypoint-class)
  - [Early vs. late registration](#early-vs-late-registration)
- [Registering features](#registering-features)
  - [Modules](#modules)
  - [Settings](#settings)
  - [Custom setting types](#custom-setting-types)
  - [Categories](#categories)
  - [Commands](#commands)
  - [Screens in the Tab cycle](#screens-in-the-tab-cycle)
- [Controlling built-in modules](#controlling-builtin-modules)
- [Events](#events)
- [Feedback and utilities](#feedback-and-utilities)
- [Threading rules](#threading-rules)
- [Persistence](#persistence)

---

## Gradle setup

Zephyr is published to your local Maven repository. In the Zephyr repo:

```bash
./gradlew publishToMavenLocal
```

Then, in your addon's `build.gradle`:

```gradle
repositories {
    mavenLocal()
}

dependencies {
    minecraft "com.mojang:minecraft:${project.minecraft_version}"
    implementation "net.fabricmc:fabric-loader:${project.loader_version}"
    implementation "net.fabricmc.fabric-api:fabric-api:${project.fabric_api_version}"

    // Use the same Minecraft version as your addon targets.
    implementation "com.zephyr:zephyr:${project.zephyr_version}"
}
```

With matching `gradle.properties` entries:

```properties
minecraft_version=26.3
loader_version=0.19.5
fabric_api_version=0.161.0+26.3
zephyr_version=1.0
```

> Use the same Minecraft / loader / Fabric API versions as the Zephyr build you
> published. the addon compiles against Zephyr's remapped sources.

In Gradle terms the dependency is `implementation`-scoped; you do not need to
Jar-in-Jar Zephyr. Instead, declare a dependency in your `fabric.mod.json` (see
below) so the loader refuses to start your addon without Zephyr present.

---

## Getting started

### fabric.mod.json

Add a `"zephyr"` entrypoint and a dependency:

```json
{
    "id": "myaddon",
    "environment": "client",
    "entrypoints": {
        "client": [ "com.example.myaddon.MyAddonClient" ],
        "zephyr": [ "com.example.myaddon.MyAddon" ]
    },
    "depends": {
        "zephyr": "*"
    }
}
```

- The `"zephyr"` entrypoint is invoked **after Zephyr has fully initialized**
  (config, modules, profiles and keybinds are all loaded).
- The `"client"` entrypoint is optional - you can also call `ZephyrApi` from
  there. Registration is safe at any point during startup.

### Writing the entrypoint class

```java
public final class MyAddon implements ZephyrAddon {
    @Override
    public void onInitializeZephyr() {
       ZephyrApi.registerCategory("MyAddon");
        ZephyrApi.registerModule(AutoGreet.INSTANCE);
        ZephyrApi.registerCommand(new GreetCommand());

        ZephyrEvents.onWorldJoin(client ->
                ZephyrApi.notify("MyAddon ready!", 0xFF7B4DFF));
    }
}
```

### Early vs. late registration

You have two options for when to register:

1. **Early** - from your own `ClientModInitializer#onInitializeClient`.
   Zephyr queues registrations made before its config loads and applies them
   (with persisted state restored) at the end of its own initialization.
2. **Late** - from `ZephyrAddon#onInitializeZephyr`. Registration takes effect
   immediately and persisted state is restored from `modules.json` right away.

Both are fully supported; late registration is recommended because everything
Zephyr-side is guaranteed to be ready.

---

## Registering features

### Modules

Modules are the main unit: a toggleable feature with settings that appears in
the click-GUI, persists to `modules.json`, receives ticks while enabled, and
gets a keybind slot.

```java
public final class AutoGreet extends Module {
    public static final AutoGreet INSTANCE = new AutoGreet();

    private final BooleanSetting enabled = new BooleanSetting("Greet", true);

    private AutoGreet() {
        super("Auto Greet", "Greets players when you join", TestAddonUtil.testCategory());
        addSetting(enabled);
    }

    @Override
    protected void onEnable() {
        // Called by setEnabled(true); also called once on startup when persisted ON.
    }

    @Override
    protected void onDisable() {
        // Called by setEnabled(false).
    }

    @Override
    public void tick(Minecraft client) {
        // Called every client tick while the module is enabled.
        // client.player may be null in menus.
    }
}
```

Key points:

- Register a **singleton instance** (registering the same instance twice is a
  no-op), via `ZephyrApi.registerModule(module)`.
- Constructors: `Module(name, description, category)`, plus overloads with
  `enabledByDefault`, `hidden` and `grimDetectable`.
- `addSetting(...)` populates the settings list; order defines GUI order.
- `onEnable` / `onDisable` are protected hooks; `tick(Minecraft)` runs only
  while enabled.

### Settings

Each setting type lives in `com.zephyr.client.configplusgui.setting`:

| Type | Constructor | Value |
|---|---|---|
| `BooleanSetting` | `(name, boolean defaultValue)` | `Boolean` |
| `NumberSetting` | `(name, double default, min, max, step)` | `Double`, clamped & snapped |
| `EnumSetting<T>` | `(name, T defaultValue)` | the enum constant |
| `StringSetting` | `(name, String defaultValue)` | `String` |
| `ListSetting` | `(name)` | `List<ListSetting.ListEntry>` (`block,color` pairs) |
| `KeybindSetting` | `(name)`, `(name, Keybind)`, `(name, int... keys)` | `Keybind` |
| `ButtonSetting` | `(name, String label, Runnable action)` | none - runs `action` on click |
| `ClickableSetting<T>` | abstract, `(name, T defaultValue)` | your own type |

`Setting<T>` exposes `get()`, and each concrete type adds typed `set(...)`
methods. Values written through the API or the GUI are persisted automatically.

#### Custom setting types

Extend `ClickableSetting<T>` to add a brand-new setting type with no Zephyr GUI
code - it renders as a standard row (name on the left, `getDisplayValue()`
right-aligned) and receives left clicks:

```java
public static final class CounterSetting extends ClickableSetting<Integer> {
    private int clicks;

    public CounterSetting() {
        super("Click Counter", 0);
    }

    @Override
    public String getDisplayValue() {
        return "clicks: " + clicks;
    }

    @Override
    public boolean onClick(double mouseX, double mouseY) {
        clicks++;
        return true; // true = the click was handled
    }
}
```

`ButtonSetting` is the ready-made version of this pattern for "click to run an
action" rows. Custom settings are not persisted by default - persisting your
own state is your job (e.g. write it inside your module's config lifecycle).

### Categories

Custom categories group your modules in the click-GUI's category dropdown and
tag module rows. `registerCategory` is idempotent - the same display name always
returns the same singleton, so it is safe to call once per module constructor:

```java
Category myCategory = ZephyrApi.registerCategory("MyAddon");
```

### Commands

Commands are matched case-insensitively after Zephyr's command prefix (default
`.`), appear in tab-completion, and receive double-quoted spans as single args:

```java
public final class GreetCommand extends Command {
    public GreetCommand() {
        super("greet", "Greets a player by name");
    }

    @Override
    public void execute(String[] args) {
        ZephyrApi.sendChatMessage("Hello, " + (args.length > 0 ? args[0] : "world") + "!");
    }

    @Override
    public List<String> suggest(String[] args) {
        // The last element of args is the token being completed.
        return args.length == 1 ? List.of("alice", "bob") : List.of();
    }
}

ZephyrApi.registerCommand(new GreetCommand()); // -> ".greet alice"
```

### Screens in the Tab cycle

Addon screens join the Tab screen cycle after Zephyr's built-ins (Menu →
Keybinds → Config → Profiles → *your screens*). Provide a factory; it is called
fresh each time the cycle reaches your entry:

```java
ZephyrApi.registerCycleScreen("myaddon", MyAddonScreen::new);
```

The screen is a plain Minecraft `Screen`. To keep the cycle going, handle the
Tab key yourself and call:

```java
ZephyrApi.cycleNext();     // or cyclePrevious() for Shift+Tab
```

Mirror Zephyr's behavior for a seamless feel: `isPauseScreen() → false`.

---

## Controlling built-in modules

Any registered module - built-in or addon - is addressable by name
(case-insensitive):

```java
Module zoom = ZephyrApi.getModule("Zoom");      // null if not found
ZephyrApi.isEnabled("Zoom");                    // boolean
ZephyrApi.setEnabled("Zoom", true);             // fires onEnable + toggle event
ZephyrApi.toggle("Zoom");                       // flips; no-op for unknown names

ZephyrApi.getModules();                         // everything, registration order (read-only)
ZephyrApi.getVisibleModules();                  // respects hidden modules and Hide Detectable
```

Settings on any module (names are the config keys, matched **case-sensitively**):

```java
Setting<?> setting = ZephyrApi.getSetting(zoom, "Zoom FOV"); // null if absent
Object value      = ZephyrApi.getSettingValue(zoom, "Zoom FOV"); // Boolean/Double/String/enum/List

ZephyrApi.setSettingValue(zoom, "Zoom FOV", 45.0);  // typed coercion:
//   BooleanSetting  <- Boolean
//   NumberSetting   <- any Number (clamped/snapped to the slider range)
//   EnumSetting     <- the enum instance, or its constant name() as a String (case-insensitive)
//   StringSetting   <- String
// returns true when the value was applied

ZephyrApi.addListEntry(module, "Blocks", "minecraft:diamond_ore", "#5BDDFF");
ZephyrApi.clearList(module, "Blocks");
```

---

## Events

`ZephyrEvents` is a lightweight listener bus. Register listeners at any time
(usually from `onInitializeZephyr`); they stay active for the whole session and
may be registered repeatedly. **All dispatch happens on the main client thread**,
so listeners can safely touch the world, the player and Zephyr's registries.

```java
// Any module (built-in or addon) toggles on/off.
ZephyrEvents.onModuleToggled(event -> {
    Module module = event.module();   // the module that changed
    boolean enabled = event.enabled();
});

// Any setting is written (the setting does not know its owner module).
ZephyrEvents.onSettingChanged(event -> {
    Setting<?> setting = event.setting();
});

// End of every client tick, screen open or not.
ZephyrEvents.onClientTick(client -> { ... });

// Client joins / leaves a world or server.
ZephyrEvents.onWorldJoin(client -> { ... });
ZephyrEvents.onWorldDisconnect(client -> { ... });
```

For per-module tick logic prefer `Module#tick` (it only runs while enabled);
use `onClientTick` for always-on background work.

---

## Feedback and utilities

```java
ZephyrApi.notify("Saved!", 0xFF7B4DFF); // HUD toast (accent color; hidden in Stealth Mode)
ZephyrApi.sendChatMessage("[MyAddon] hi"); // client-side chat message, never sent to the server
ZephyrApi.saveConfig();  // persist all modules to modules.json + refresh the active profile

ZephyrApi.scheduleTicks(20, () -> { ... });   // run after N client ticks
ZephyrApi.scheduleMillis(100, () -> { ... }); // run after N ms, on the client thread

ZephyrApi.openScreen(myScreen);  // opens a screen (thread-safe; hops to the client thread)
ZephyrApi.openClickGui();        // opens Zephyr's module list
ZephyrApi.closeScreen();         // closes whatever screen is open
ZephyrApi.isClickGuiOpen();      // whether Zephyr's GUI is open
ZephyrApi.cycleNext();           // advance the Tab screen cycle
ZephyrApi.cyclePrevious();       // step back in the Tab screen cycle

Minecraft client = ZephyrApi.getClient(); // the running Minecraft instance
```

---

## Threading rules

- `ZephyrApi` methods are static and safe to call from the client thread.
- Event dispatch, `Module#tick`, command execution and scheduled actions all
  run on the main client thread - you do not need synchronization there.
- If you must touch the API from another thread (e.g. a worker), hop to the
  client thread first with `Minecraft.getInstance().execute(...)` (or schedule
  via `scheduleMillis` / `scheduleTicks`).

---

## Persistence

- Registered modules and their settings persist automatically to
  `config/zephyr/modules.json`, including across profile switches.
- Persisted state is restored when your module registers, whether registration
  happens early (queued) or late.
- Call `ZephyrApi.saveConfig()` after changing module state programmatically if
  you want the change written out immediately; otherwise it is saved on Zephyr's
  autosave interval and on client stop.

---

## Expanded API (keybinds, profiles, global config)

### Keybinds

```java
ZephyrApi.getKeybind(module);               // Keybind (or Keybind.NONE)
ZephyrApi.getKeybind("Zoom");
ZephyrApi.setKeybind(module, new Keybind(InputConstants.KEY_X, Keybind.UNSET, Keybind.UNSET));
ZephyrApi.clearKeybind(module);

ZephyrApi.getSystemKeybind(KeybindManager.SystemAction.STEALTH_MODE);
ZephyrApi.setSystemKeybind(KeybindManager.SystemAction.STEALTH_MODE, bind);
ZephyrApi.resetSystemKeybind(KeybindManager.SystemAction.STEALTH_MODE);
ZephyrApi.getSystemActions(); // SystemAction[]
ZephyrApi.getCommandPrefix();
```

### Profiles

```java
ZephyrApi.getProfileNames();
ZephyrApi.getActiveProfile();
ZephyrApi.createProfile("MyProfile");
ZephyrApi.applyProfile("Default");
ZephyrApi.renameProfile("Old", "New");
ZephyrApi.deleteProfile("MyProfile");
ZephyrApi.captureActiveProfile();
```

### Global config / stealth

```java
ZephyrApi.isStealthMode(); ZephyrApi.setStealthMode(true); ZephyrApi.toggleStealthMode();
ZephyrApi.isHideDetectableModules(); ZephyrApi.setHideDetectableModules(true);
ZephyrApi.isTestMode(); ZephyrApi.setTestMode(true);
ZephyrApi.isHiddenModulesUnlocked();
ZephyrApi.getThemeColor(); ZephyrApi.setThemeColor(ThemeColor.LAVENDER); ZephyrApi.cycleThemeColor();
ZephyrApi.useCustomColor(); ZephyrApi.setUseCustomColor(false);
ZephyrApi.getHudMode(); ZephyrApi.setHudMode(HudMode.MODERN); ZephyrApi.cycleHudMode();
ZephyrApi.getNotificationCorner(); ZephyrApi.setNotificationCorner(Corner.BOTTOM_RIGHT);
ZephyrApi.getNotificationHoldSeconds(); ZephyrApi.setNotificationHoldSeconds(2.0);
ZephyrApi.getAnimationSpeed(); ZephyrApi.setAnimationSpeed(1.0);
ZephyrApi.getAutosaveInterval(); ZephyrApi.setAutosaveInterval(60);
ZephyrApi.keybindConflictWarningsEnabled(); ZephyrApi.setKeybindConflictWarningsEnabled(true);
ZephyrApi.discordPresence(); ZephyrApi.setDiscordPresence(true);
ZephyrApi.hotkeyPopupsEnabled(); ZephyrApi.setHotkeyPopupsEnabled(true);
ZephyrApi.getAccentColor();
```

### Additional events

```java
ZephyrEvents.onModuleRegistered(module -> { ... });
ZephyrEvents.onCommandRegistered(command -> { ... });
ZephyrEvents.onProfileChanged(name -> { ... });
ZephyrEvents.onStealthModeChanged(enabled -> { ... });
ZephyrEvents.onHideDetectableChanged(enabled -> { ... });
ZephyrEvents.onScreenOpened(screen -> { ... });
ZephyrEvents.onScreenClosed(screen -> { ... });
ZephyrEvents.onConfigSaved(() -> { ... });
```

### Utilities

```java
ZephyrApi.execute(() -> { ... });          // run on client thread
ZephyrApi.getCommands();                   // all registered commands
ZephyrApi.commandPrefix();                 // resolved prefix text
ZephyrApi.unregisterModule(module);
ZephyrApi.unregisterCommand("name");
ZephyrApi.unregisterCycleScreen("id");
```
