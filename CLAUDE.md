# AmethystClient — Engineering Constitution

**Status:** binding. Every rule here is mandatory for any agent or human writing code in this repository.
**Scope:** all files under this repository, excluding `yarn-1.21.11/` (read-only upstream reference).

---

## 0. The Truth Hierarchy

When sources conflict, the higher entry wins. Never blend them.

| Rank | Source | Authority |
|---|---|---|
| 1 | The compiled named jar (`~/.gradle/caches/fabric-loom/minecraftMaven/.../minecraft-merged-*.jar`) | Ground truth. Existence, exact signature, visibility, `final`/`static` — everything the compiler sees. |
| 2 | `yarn-1.21.11/mappings/**/*.mapping` | Ground truth for **names**, and the only searchable index of the API. **Not a completeness guarantee** — see §2.2a. |
| 3 | `yarn-1.21.11/CONVENTIONS.md` | Ground truth for **how to name** things. |
| 4 | `yarn-1.21.11/build.gradle` | Ground truth for **target version** (`minecraft_version = "1.21.11"`). |
| 5 | This document | Process and quality bar. |
| 6 | Your own recollection of the API | **Worthless. Discard it.** See §1.1. |

A name that exists in your memory but not in `mappings/` **does not exist**. A signature you "remember" is not evidence.

---

## 1. The Non-Negotiables

### 1.1 Never Guess — Verify Before You Type

This is the single most important rule in this repository.

**Before writing any line that touches the Minecraft API**, resolve the exact class, method, and descriptor from `yarn-1.21.11/mappings/`. A wrong method name is not a compile error you can shrug at — in a Fabric mod it is a *silent* wrong-behavior bug, or a crash in production for a user.

The verification loop:

```bash
cd yarn-1.21.11/mappings

# 1. Find a class by its obfuscated id (when you only know the intermediary name)
grep -rhE "^[[:space:]]*CLASS net/minecraft/class_310 " --include="*.mapping" .

# 2. Inspect a class's full API surface (declared members only — see §2.2)
cat net/minecraft/client/option/KeyBinding.mapping

# 3. Search every occurrence of a symbol across the whole mapping set
grep -rn "matchesKey" --include="*.mapping" .

# 4. Resolve a nested class
grep -nE "^[[:space:]]+CLASS " net/minecraft/client/util/InputUtil.mapping
```

**If a mapping file does not contain the symbol you need**, the symbol is either unnamed, inherited, or missing from the mappings. All three mean: stop, and find out which. Do not invent a name — and do not conclude it does not exist. Check the compiled jar (§2.2b), because the mapping files are demonstrably incomplete (§2.2a).

**Unnamed ≠ absent.** Yarn leaves some members unmapped (e.g. `MinecraftClient.mapping` has 207 `METHOD` lines of which 28 are unnamed). An unmapped member must be referenced by its intermediary name (`method_53524`) — never by a guessed semantic name. If you need a semantic name for one, you name it *locally* and comment it.

**Never guess a descriptor.** `method_1753 (Lnet/minecraft/class_332;Lnet/minecraft/class_9779;)V` is `(DrawContext, RenderTickCounter)`. If you write `(DrawContext, float)` because that is what it was in 1.8, the code will not compile and you will have burned a cycle. If you write it from memory and it *happens* to compile, you have a latent bug.

### 1.2 Only Verified APIs — No Hallucinated Overloads

Do not invent convenience overloads that "should" exist. If `DrawContext` has exactly one `drawItem(ItemStack, int, int)` variant, use that one. If you need a different signature, implement the difference in your own code — do not assume an overload exists.

### 1.3 Evidence or Silence

Every non-obvious claim in a commit message, code comment, or design note must be traceable to a file path or a mapping line. If you cannot cite it, you do not know it — say so, or go verify it.

Comments explain **why**. The code already says what. A comment restating the code is noise.

### 1.4 Ask Before Assuming — but Do Not Stall

When a decision genuinely belongs to the project owner (product intent, visual direction, scope, trade-offs), ask — one focused question, with options and a recommendation. Do not ask about things the repository can answer, and do not block delivery on a question you can decide with a clearly-stated, reversible default.

---

## 2. Known Traps in Yarn 1.21.11

These are empirically verified against `yarn-1.21.11/mappings/`. Re-verify if the target version changes.

### 2.1 The input system was refactored in 1.21.9

There is a dedicated package `net.minecraft.client.input`:

`AbstractInput` · `CharInput` · `CursorMovement` · `Input` · `KeyInput` · `KeyboardInput` · `MouseInput` · `Scroller` · `SystemKeycodes`

Consequences:

- `KeyBinding.matchesKey` takes **`net.minecraft.client.input.KeyInput`**, *not* an `int` keycode and *not* `InputUtil.Key`.
  `METHOD method_1417 matchesKey (Lnet/minecraft/class_11908;)Z` — `class_11908` = `net/minecraft/client/input/KeyInput`.
- `KeyBinding.setKeyPressed` takes `InputUtil.Key` (`class_3675$class_306`) — the two types coexist. Do not mix them up.
- `AbstractInput` (`class_11907`) exposes `getKeycode()`, `hasCtrl()`, `hasShift()`, `hasAlt()`, `hasCtrlOrCmd()`, `isUp/isDown/isLeft/isRight`, `isEnter()`, `isEscape()`.
- The old `InputUtil.Type` still exists (`class_307`) and is used by the `KeyBinding` constructor — but see §2.1a: its constants are **unnamed** in this mapping set.

Any code that reads raw keys must go through the 1.21.11 `client.input` API. Pre-1.21.9 idioms are wrong here.

### 2.1a `InputUtil.Type`'s constants are unnamed in this mapping set

`InputUtil.Type` (`class_307`) declares only `name`, `map`, `textTranslator`, and `UNKNOWN_TRANSLATION_KEY`. A search across all 6,606 mapping files for `KEYSYM`, `SCANCODE`, and `KEYBOARD` returns **zero** hits.

The enum constants exist in the game, but they are not named here — so `InputUtil.Type.KEYSYM` is **not** a name you can rely on.

**Practical rule:** prefer the `KeyBinding(String id, int code, KeyBinding.Category category)` constructor, which needs no `InputUtil.Type` at all. If you genuinely need a typed key, verify the constant against the compiled named jar before using it — never from memory.

This is the general shape of the trap: *a class exists, its type exists, but a member of it is unnamed.* "The class is there" does not imply "the member you want is there."

### 2.2 Mapping files contain only *declared* members

The Enigma mapping format does **not** record superclasses, and each `.mapping` file lists only the members **declared** in that class.

Verified fact: `MinecraftClient.mapping` contains **zero** occurrences of `runTasks`, `isOnThread`, or `getThread`. All three are declared in `net/minecraft/util/thread/ThreadExecutor.mapping` (`method_5383`, `method_18854`, `method_3777`). The format simply does not surface inherited members under the subclass.

**Therefore:** to resolve a full API surface you must walk the superclass chain manually, and you must confirm the chain itself from the compiled jar — the mappings will not tell you that class A extends class B.

**Known limitation of this repository:** no Minecraft jar is checked in, and no jar is present in the local Gradle cache. So *inheritance* relationships cannot be verified from what is on disk here. What *is* verifiable offline is every declared member and every descriptor. When you need a superclass relationship, confirm it against the named jar (or the decompiled source) and say which you did.

Practical consequence: if a method you expected is not in the class's mapping file, look for it in a plausible superclass's file before concluding it does not exist.

### 2.2a The mapping files are an index, not a completeness guarantee

**Verified defect.** `Entity.getBoundingBox()` exists in the shipped named jar:

```
public final net.minecraft.util.math.Box getBoundingBox();
```

It has **no entry** in `yarn-1.21.11/mappings/net/minecraft/entity/Entity.mapping`, nor anywhere else in the 6,606 mapping files. A repository-wide `grep -rn getBoundingBox --include="*.mapping"` returns only `VoxelShape`, `LivingEntity.getBoundingBox(Pose)`, `VehicleInventory` and `GameTestState`.

The intermediary jar confirms the gap is real rather than a naming difference: `net.minecraft.class_1297` declares no zero-argument `class_238`-returning method other than `method_33332` (`calculateBoundingBox`).

**Consequences — two rules:**

1. **Never conclude "this API does not exist" from the mapping files alone.** Absence of evidence in the mappings is not evidence of absence. The mapping files omit some members.
2. **When a member matters, confirm it in the compiled jar.** This is the only way to learn visibility, `final`/`static`, and whether a method is declared or inherited — none of which the mapping format records.

### 2.2b Verifying against the compiled jar

```bash
NJAR=$(find ~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged \
       -name '*.jar' ! -name '*sources*' | head -1)

# Full declared API of a class, including private and inherited-from-interface members
javap -p -cp "$NJAR" net.minecraft.entity.Entity

# A single class's public surface
javap -cp "$NJAR" net.minecraft.client.MinecraftClient

# The actual implementation, when behaviour matters
javap -p -c -cp "$NJAR" net.minecraft.client.MinecraftClient
```

`javap -c` is how the vanilla attack path was established for this client: `MinecraftClient.doAttack()` is `private boolean` and ultimately calls `interactionManager.attackEntity(player, ((EntityHitResult) crosshairTarget).getEntity())` — which is why a combat module should call `ClientPlayerInteractionManager.attackEntity` rather than `PlayerEntity.attack` directly.

**Reading bytecode is not a last resort.** For any behaviour you are about to replicate, `javap -c` on the vanilla method is the cheapest way to be certain. It takes one command and removes an entire class of silent bugs.

### 2.2c `Box` has public fields, not corner getters

`net.minecraft.util.math.Box` (`class_238`) exposes its corners as **public final fields**:

```
FIELD field_1320 maxX D
FIELD field_1321 minZ D
FIELD field_1322 minY D
FIELD field_1323 minX D
FIELD field_1324 maxZ D
FIELD field_1325 maxY D
```

There is **no** `getMinX()`, `getMaxX()`, `getMinY()`, `getMaxY()`, `getMinZ()` or `getMaxZ()` in
1.21.11 — not in the mappings and not in the compiled named jar. The only accessors are
`getMin(Direction.Axis)`, `getMax(Direction.Axis)`, `getMinPos()` and `getMaxPos()`.

Write `box.minX`, not `box.getMinX()`. Code that uses the getters does not compile, which is at
least a loud failure — but the same habit carried over from an older Yarn will waste a build
cycle every time.

### 2.3 Nested classes use the short obfuscated id
Nested classes appear **indented** inside their owner's file, keyed by the short form — not `owner$child`:

```
	CLASS class_306 Key              # inside util/InputUtil.mapping
	CLASS class_307 Type
	CLASS class_11900 Category       # inside option/KeyBinding.mapping
```

Use `grep -nE "^[[:space:]]+CLASS " <file>` to enumerate them. Searching for `class_3675$class_306` returns nothing and will make you think the class is missing.

### 2.4 `ClientWorld`, not `World`

`MinecraftClient.world` is `net.minecraft.client.world.ClientWorld` (`class_638`). Code written against `net.minecraft.world.World` will not compile.

### 2.5 Yarn 1.21.11 exposes no convenience getters on `MinecraftClient`

Verified: `MinecraftClient.mapping` contains **no** `getCurrentScreen`, `getOptions`, `getPlayer`, `getWorld`, or `getInGameHud`. Those are not "renamed" — they do not exist in this version's named surface.

Access the **public fields** directly:

```java
MinecraftClient client = MinecraftClient.getInstance();
client.currentScreen   // Screen        (field_1755)
client.player          // ClientPlayerEntity (field_1724)
client.world           // ClientWorld   (field_1687)
client.options         // GameOptions   (field_1690)
client.inGameHud       // InGameHud     (field_1705)
client.window          // Window        (field_1704)
client.targetedEntity  // Entity        (field_1692)
```

Writing `client.getCurrentScreen()` because every tutorial and every pre-1.21 snippet does is the single most common failure mode in this codebase. The mappings are the arbiter, not the tutorials.

### 2.6 `RenderTickCounter` replaced float tick deltas

`InGameHud.render` is `method_1753 (Lnet/minecraft/class_332;Lnet/minecraft/class_9779;)V` → `(DrawContext, RenderTickCounter)`. `class_9779` = `net.minecraft.client.render.RenderTickCounter`.

---

## 3. Naming

Alibaba **must** follow `yarn-1.21.11/CONVENTIONS.md` for anything that mirrors or extends a vanilla member. That document is authoritative; this section is a summary of the parts most often violated.

- Classes: `UpperCamelCase`. Fields/methods/params: `lowerCamelCase`. `static final`: `UPPER_SNAKE_CASE`.
- Methods are verb phrases (`tick`, `getCarversForStep`), except `withX`, `toX`, `fromX`, `of`, and builders.
- Classes and non-boolean fields are noun phrases (`ChunkRegion`, `color`).
- Booleans are adjectives or present-tense verbs — **no `is`/`has` prefix** (`colored`, not `isColored`).
- Natural language order: `ChestBlockEntity`, never `BlockEntityChest`.
- Abbreviations only when universal and already used by Yarn: `id`, `pos`, `nbt`, `init`, `min`/`max`, `json`. Acronyms are single words: `JsonObject`, `NbtIo` — never `JSONObject`.
- Packages are singular.
- `get` = non-boolean getter with no side effect. `is` = boolean getter. `set` = setter (param named after the property: `setColor(color)`). `with` = returns a modified copy. `create` = makes a new instance. `from` = static factory of the owner's type. `to` = converts to another type. `read` = loads into `this`. `write` = saves into a passed object.
- "tick" = once per tick. "update" = everything else. "last" = the value it had last tick (`lastX`).
- American English.

**When you name a non-vanilla concept, you are establishing a convention.** Name it as if the next ten contributors will live with it — because they will.

---

## 4. Architecture

### 4.1 Layering

```
              Features (modules: KillAura, ESP, HUD, ...)
                        │  depend on
              Platform  (client↔MC integration, mixins, rendering)
                        │  depend on
                Core    (module registry, event bus, config, keybinds, utils)
                        │  depend on
                 Inf    (no dependency on Minecraft classes at all)
```

- **`core`** must never import `net.minecraft.*`. It holds the event bus, module lifecycle, config serialization, and the command framework. If it needs game state, it asks via an event.
- **`platform`** is the *only* layer that imports `net.minecraft.*` or touches mixins. It adapts MC to core interfaces.
- **`features`** may use platform APIs but must not reach into mixins directly.
- Dependency direction is strictly downward. A cycle is a design bug, not a technical challenge.

### 4.2 Module contract

Every feature module implements one interface and is registered exactly once.

- **Toggleable.** Enable/disable must be free of side effects that outlive the toggle. Enabling installs hooks; disabling removes them. Symmetric.
- **Configurable.** Every tunable is a declared, typed, serializable config key with a sane default. No magic numbers in logic bodies.
- **Independently disposable.** Disabling a module restores vanilla behavior *exactly*. If a module cannot be cleanly disabled, it is not a module — it is a patch, and it belongs in the platform layer.
- **No hidden global state.** A module's state lives in the module. Cross-module communication goes through the event bus.

### 4.3 Event bus

- Events are immutable value objects. No setters.
- Handlers are explicitly subscribed/unsubscribed; the bus must not leak subscribers.
- Handler order is deterministic and documented where it matters.
- An exception in one handler must not prevent the others from running, and must be reported rather than swallowed.

### 4.4 Configuration

- Single source of truth, one file, one format, one schema version.
- **Never lose a user's config across a version bump.** Unknown keys are preserved on write, not silently dropped.
- Fail loudly on a corrupt file: back it up, start from defaults, and tell the user.

---

## 5. Threading & Performance

Minecraft is single-threaded for logic and single-threaded for rendering, and they are **different** threads. Getting this wrong causes intermittent, unreproducible crashes.

- **Game logic** (entity, world, packet, tick) runs on the client thread. Read and write world/player state only there.
- **Rendering** (HUD, ESP, nametags) runs on the render thread. Touch *only* rendering state there.
- Anything crossing the boundary (a worker thread reading player position) must go through an explicit, thread-safe snapshot — never a raw field read.
- If you need to know which thread you are on, resolve the API from `net/minecraft/util/thread/ThreadExecutor.mapping` (§2.2) rather than guessing a method name.

Allocation discipline:

- No allocation in per-frame render code unless it is unavoidable. Reuse buffers, vectors, and matrices.
- No `for` loop over `client.world.getEntities()` every frame without a distance pre-filter. Entity iteration is the single most common cause of Minecraft client FPS collapse.
- Never call `world.getBlockState()`, `getEntities()`, or any chunk lookup from the render thread without an existing, tick-side cache.

---

## 6. Mixins

Mixins are the sharpest tool and the easiest to abuse. Rules:

- **One concern per mixin class.** Name it for the target method: `MinecraftClientMixin`, `InGameHudMixin`.
- `@Inject` with `at = @At("HEAD")` only. Tail/head injection shifts bytecode offsets and breaks on every version bump.
- `require = 1` on every inject, and `expect = 1` where the count is stable. A silent no-op mixin is worse than a crash.
- Never `@Overwrite` a vanilla method. Redirect or inject instead.
- Mixins must not change vanilla control flow. A mixin that makes vanilla code behave differently for non-client consumers is a bug.
- Every mixin carries a comment stating the target MC version it was verified against.

---

## 7. The Anti-Slop Standard

This section exists because the failure mode of AI-assisted work is *plausible-looking* code that is wrong, padded, or unmaintainable.

### 7.1 Banned outright

- **Placeholder code.** No `// TODO: implement`, no `throw new UnsupportedOperationException("Not implemented")`, no empty method bodies, no `return null; // later`.
- **Dead code.** No unused fields, unused imports, unreachable branches, or parameters that exist only "for future use".
- **Defensive noise.** No `try { ... } catch (Exception e) {}` swallowers, no `if (x != null)` on a value that cannot be null, no `Optional` wrapping of something already optional.
- **Comment noise.** No `// increment i`, no `// get the player`, no `// Loop through modules`. Comments explain *why*, never *what*.
- **Speculative generality.** No interface with one implementation "for extensibility", no abstract factory for a single product, no config schema for a feature that does not exist yet.
- **Wrapper pass-throughs.** A class whose only job is to forward to another with an identical signature is dead weight.
- **Duplicated logic.** If a block appears twice, extract it — unless the duplication is genuinely clearer inline, in which case say so in a comment.
- **Stubs with fabricated APIs.** Never write a call to a method you have not verified in `mappings/`. This is the cardinal sin and §1.1 exists to stop it.
- **Magic numbers.** Every literal gets a name or a config key.
- **God classes.** One class doing rendering, networking, config, and math.

### 7.2 Required quality

- **Every public member has a javadoc that states contract and units.** Not "returns the value" — states what the value *means*, what the units are, and what is true when it is absent.
- **Nullability is explicit.** Either `@Nullable`/`@NotNull` or an explicit "never null" contract.
- **Types carry meaning.** Prefer `record` for immutable value pairs. Avoid `Map<String, Object>` where a typed structure is possible.
- **Errors are actionable.** An exception message says what failed, with the offending value. `IllegalArgumentException("Invalid color: " + color)`, not `"bad input"`.
- **No swallowed exceptions.** Catch only what you can handle; log with context; rethrow if you cannot.
- **Comments explain decisions, not mechanics.** "Vanilla sends the rotation packet after the position update; hijacking it here avoids the server-side correction" — that earns its place. "This loops over the list" does not.

### 7.3 The self-review pass

Before presenting any code, re-read it once as if you were the maintainer receiving it in a pull request. Ask:

1. Does every Minecraft symbol exist in `mappings/`? → verify each.
2. Can I delete any line without breaking behavior? → delete it.
3. Would a reviewer ask "why?" about any line? → be ready to answer, or remove it.
4. Is there exactly one way to do this? → if not, why not?
5. What happens if this runs twice? → if it breaks, it is not idempotent and it must be.

---

## 8. Workflow

1. **Locate.** Find the target class in `yarn-1.21.11/mappings/`. Read the whole file if it is under ~150 lines.
2. **Resolve the superclass chain** if the member is not found (§2.2).
3. **Design.** Decide the layer (§4.1) and the module contract (§4.2) *before* writing.
4. **Implement.** Smallest correct change. Verify each API as you use it.
5. **Verify.** Compile. If the build cannot run here, say so explicitly rather than implying it was verified.
6. **Self-review** against §7.3.
7. **Report.** State what changed, what you verified, and what you could not verify.

**Communication rules**

- No filler. No "Great question!", no "Certainly!", no restating the request back.
- No progress narration of trivial steps.
- Report problems immediately and concretely, with the evidence.
- When blocked, present the options and your recommendation — then wait.
- Never claim a build passed, a test passed, or a bug is fixed unless you ran it and saw it.

---

## 9. Definition of Done

A change is complete when:

- [ ] Every Minecraft symbol used was verified against `yarn-1.21.11/mappings/`.
- [ ] It compiles.
- [ ] No banned pattern from §7.1 is present.
- [ ] Public members are documented with contract and units.
- [ ] Enabling/disabling the feature is symmetric and restores vanilla behavior.
- [ ] No allocation-in-hot-path or per-frame entity iteration was introduced.
- [ ] Threading is correct: game state on the client thread, rendering on the render thread.
- [ ] New mixins declare `require`/`expect` and name the verified MC version.
- [ ] The self-review pass (§7.3) was performed.
- [ ] The report distinguishes what was verified from what was not.

---

## 10. The Client as It Stands

This section describes the state of the implementation. It is the map of the code; the sections
above are the rules the code has to follow. Where the two disagree, the rules win — but then the
code is wrong and has to be fixed, not the rule.

### 10.1 Layers

```
core/     module registry, event bus, config, keybinds, GUI state      (no net.minecraft.*)
platform/ Minecraft integration: mixins, rendering, rotation, entities  (the only layer that
          may import net.minecraft.*)
features/ the modules themselves: combat, esp, gui, hud
```

The dependency direction is `features → platform → core`. `core` never imports `net.minecraft.*`.

### 10.2 Module registry and lifecycle

`core/module/ModuleManager` owns every module and ticks the enabled ones in registration order.
A module that throws while ticking is logged and skipped. The hooks of a module are
`onEnable()`, `onDisable()`, `onTick()` and `onRender()`; the last one runs once per frame for
every enabled module (`platform/render/RenderDispatcher`).

The modules that are registered in `BirowoClient`:

| Module | Id | Key | What it does |
| --- | --- | --- | --- |
| ESP | `esp` | `H` | A box around the entities around the player. |
| BlockESP | `blockesp` | `B` | A box around the blocks that are worth looking for. |
| TargetESP | `targetesp` | `Y` | Ring, body fill and cone hat on the entity that is attacked. |
| KillAura | `killaura` | `K` | Attacks the closest valid target once the aim has arrived. |
| AimAssist | `aimassist` | `V` | Turns the camera towards a target, without attacking. |
| TriggerBot | `triggerbot` | `T` | Attacks the entity the crosshair is on. |
| ClickGUI | `clickgui` | `R` | The interface. |
| HUD | `hud` | `G` | The readout. |

`KillAura` and `TriggerBot` share one `AttackLimiter`, so that they can never attack twice in
the same moment.

### 10.3 The rotation

The aiming modules do not turn the player themselves. They hand a `RotationAim` to the
`RotationEngine`, which is the only owner of the rotation of the player:

- `platform/rotation/RotationEngine` — moves the aim and applies it. Its state is static,
  because the mixin that steps it has no access to the objects of the mod.
- `platform/rotation/RotationRequest` — the claim of a module on the rotation. The request with
  the highest priority wins (`RotationPriority`).
- `platform/rotation/RotationAim` — the immutable snapshot of what a module wants.
- `core/util/AngleMath`, `core/util/WindMouse`, `core/util/StepQuantiser` — the arithmetic of
  the movement of the aim. They live in `core` because they do not need the game, which is also
  what makes them testable.

The engine is stepped by `platform/mixin/MinecraftClientMixin`, at the head of
`MinecraftClient.render(Z)V`. That is in front of `Mouse.tick()` and in front of the update of
the camera, so the angle of the frame is the angle the player sees. The mouse is read after the
step, which keeps the finer movement in the hands of the player.

An aim is applied in one of two modes, and the module says which one it wants:

- `RotationMode.VISIBLE` writes the yaw, the pitch, the head yaw and the body yaw of the player
  together — that is the aim the player sees.
- `RotationMode.SILENT_HEAD` leaves the camera of the player alone and turns only the head: the
  head yaw of the player follows the aim and the angle is handed to
  `platform/rotation/ServerRotation`, which `platform/mixin/ClientPlayerEntityMixin` swaps into
  the movement packets for the duration of the call. The angle of the movement packet is the
  angle of the head in the protocol of the game, so the server reads the aim as the head of the
  player while the body keeps facing the way the player looks. This is the default of the
  combat modules.

The ways to move the aim are `REGULAR` (a fixed speed in degrees per second), `WIND_MOUSE` (a
force that behaves like a hand on a mouse) and `BLATANT` (a jump). `BLATANT` is never the
default.

### 10.3a The movement of the aim

The aim of a module is not a fixed angle. The client owns three pieces of machinery that move it
around, and all three are about the movement of the camera — none of them changes a packet, and
none of them sends an angle that the aim is not at.

- `platform/rotation/AimPointTracker` keeps the point the aim is pointed at wandering over the
  body of the target. The band it moves in is derived from how wide the target looks from where
  the player is standing, so the point never leaves the target and never stands still on one spot
  of it. The band is crossed every few ticks, and the height of the point drifts slowly above
  and below the middle of the target. The tracker is one of the few pieces of the aim that is
  covered by a test.
- The engine eases the camera back to the angle it had before the aim took it over when a
  visible aim is let go. The walk is limited in speed and in time, and it is given up as soon as
  the mouse moves, so that the player is never fought over the camera.
- `TriggerBot` spreads the moment of the hit around a middle of fifty milliseconds.

Every movement the engine makes is rounded to the step of the mouse of the player: the factor of
the sensitivity raised to the third power and multiplied by one point two, which is what the
game itself uses. The rounding is applied to the movement and not to the angle, so that the sum
of many small movements lands on the step as well and no movement is lost. There is no setting
for it: a rotation that is not a multiple of the step of the mouse cannot have been made by the
mouse, and the server checks the angles it receives against that step.

### 10.4 The PvP version

`platform/combat/PvpVersionService` decides which PvP rules the server runs with. The rules of
1.8.9 have no attack cooldown and do not interrupt a sprint on a hit; the rules of 1.21 and of
everything between them do.

- The version is read from the announcement of the server (`ServerInfo.version`), which the
  client knows from the ping of the server list.
- A proxy may rewrite that announcement, so the version may be set by hand with the command
  `.birowo pvp <1.8.9|1.21|auto>`.
- `features/combat/AttackLimiter` lets the attacks through without a cooldown on 1.8.9, and
  `KillAura` leaves the sprint alone there.
- The version that is in use is shown in the readout of the client.

### 10.5 Rendering

- `platform/render/WorldLayers` builds the render layers the client draws with. There is no
  vanilla layer without a depth test, so `RenderLayer.of` is access-widened. A shape is drawn
  twice when it may be seen through walls: once with the depth test, once without.
- `platform/render/ShapeRenderer` writes the shapes: box edges, box fill with a vertical
  gradient, a ring and a cone. It touches no state of its own.
- The world pass is `WorldRenderEvents.AFTER_ENTITIES`; the overlay is attached after
  `VanillaHudElements.MISC_OVERLAYS`.

TargetESP draws three things around the entity that is attacked: a ring that floats up and down,
a translucent body that fades from the feet to the head, and a cone hat above the head. All of
them are drawn at the place the target is *drawn* at (`Entity.getLerpedPos`), not at the place
it was at during the last tick, so that they do not jump once per tick.

BlockESP draws a box around the blocks that are worth looking for. The blocks are looked for in
the chunks around the player:

- `platform/world/BlockPreset` names the groups of blocks — the ores, the ores and the chests,
  the rare blocks. The blocks of a group are resolved out of the registry of the game, and a
  block that is not in it is skipped.
- `platform/world/BlockScanner` looks through one chunk. A section that holds none of the
  blocks that are looked for is skipped without being read, because that check only walks
  through the few kinds of block the section is made of. The outline of a block is the outline
  shape of its state, moved to the place of the block.
- `features/esp/BlockEspModule` keeps the result of a chunk until it grows old, looks through
  only a few chunks in one tick, and draws the boxes that are nearest to the player first. The
  result of a chunk grows old after a hundred ticks, because a block that was mined and a block
  that was placed are not noticed before that.

### 10.6 The interface

`platform/gui/ClickGuiScreen` is a `Screen`. It draws a grid of panels, one per category, plus
a search panel. Each panel owns a `PanelState`, which keeps the place it is drawn at and the
place it rests at apart, so that a panel that is picked up while it is still moving does not
jump. The movement is eased with the time between two frames.

While the interface is open the game keeps running, so the screen lets go of the keys that move
the player when it opens and swallows them while it is open.

### 10.7 What is verified and what is not

**Verified here:** the build (`./gradlew build`) succeeds; the test suite
(`./gradlew test`) is green; every symbol of Minecraft that the client uses was read out of
`yarn-1.21.11/mappings/` and out of the compiled named jar (`javap`), including the rotation
model of the game (`Entity.setYaw`, `LivingEntity.tick`, `LivingEntityRenderer`,
`MinecraftClient.doAttack`, `Mouse.tick`, `GameRenderer.render`), the swap of the rotation in
the movement packets (`ClientPlayerEntity.sendMovementPackets`) and the version of the server
(`MinecraftClient.getCurrentServerEntry`, `ServerInfo.version`).

The symbols of the block overlay were read the same way: `Chunk.getSectionArray`,
`ChunkSection.getBlockState`, `ChunkSection.hasAny`, `ChunkSection.isEmpty`, `Chunk.getPos`,
`ChunkPos.getStartX`, `ChunkPos.toLong`, `ChunkPos.getPackedX`, `ChunkPos.getPackedZ`,
`ChunkManager.getChunk`, `ChunkStatus.FULL`, `Registry.getOptionalValue`, `Registries.BLOCK`,
`Identifier.tryParse`, `BlockState.getOutlineShape`, `BlockState.getBlock`, `VoxelShape.getBoundingBox`
and `Box.offset`.

**Not verified here:** the client has never been launched in this environment. Nothing about the
feel of the interface, the look of the shapes, the frame rate or the content of the log at
runtime has been observed. Those need a game and a human. In particular, the wind-down of the
camera, the wandering of the aim point and the block overlay have never been seen on a screen.

### 10.8 Known limitations

- The aim is written on the render thread. That is the thread vanilla's mouse writes the
  rotation on as well, so the race is the one vanilla has.
- The wind of the mouse keeps a speed between two frames, so its movement is only as exact as
  the frame rate allows.
- The wind-down of the camera holds the camera for a moment, and the movement of the mouse is
  thrown away while it lasts. The walk is given up as soon as the mouse moves by more than a
  tenth of a degree, which is one step of the mouse at the lowest sensitivity.
- The PvP version is read from the announcement of the server. A proxy that rewrites that
  announcement hides the version of the server behind it, and then the version has to be set by
  hand. A server that is joined without having been looked at in the list of servers announces
  nothing, and the modern rules are assumed. A version that is set by hand lasts for the
  session; it is not written to the configuration file.
- The result of a chunk of BlockESP grows old after a hundred ticks, so a block that was mined
  or placed is shown for up to five seconds. The client does not listen for the packets of the
  blocks, so the cache cannot be dropped earlier.
- The groups of blocks of BlockESP are fixed in the code. The interface has no field for a text,
  so a group cannot be changed from the interface.
- The tests cover the parts of the client that do not need the game: the arithmetic of the aim,
  the event bus, the configuration and the lifecycle of the modules. The modules that talk to
  the game — the rotation engine, the ESP and the interface — are not covered by them. The
  tracker of the aim point is covered, because it only needs a box and a point.
- The interface does not pause the game.

---

## Appendix A — Verified API Reference (MC 1.21.11 / Yarn)

Every entry below was read directly from `yarn-1.21.11/mappings/`. Use it as a starting point, then verify anything not listed.

### A.1 Class identifiers

| Intermediary | Named class |
|---|---|
| `class_310` | `net.minecraft.client.MinecraftClient` |
| `class_746` | `net.minecraft.client.network.ClientPlayerEntity` |
| `class_634` | `net.minecraft.client.network.ClientPlayNetworkHandler` |
| `class_332` | `net.minecraft.client.gui.DrawContext` |
| `class_327` | `net.minecraft.client.font.TextRenderer` |
| `class_329` | `net.minecraft.client.gui.hud.InGameHud` |
| `class_437` | `net.minecraft.client.gui.screen.Screen` |
| `class_9779` | `net.minecraft.client.render.RenderTickCounter` |
| `class_1297` | `net.minecraft.entity.Entity` |
| `class_638` | `net.minecraft.client.world.ClientWorld` |
| `class_304` | `net.minecraft.client.option.KeyBinding` |
| `class_11900` | `net.minecraft.client.option.KeyBinding.Category` (nested) |
| `class_3675` | `net.minecraft.client.util.InputUtil` |
| `class_306` | `net.minecraft.client.util.InputUtil.Key` (nested) |
| `class_307` | `net.minecraft.client.util.InputUtil.Type` (nested) |
| `class_11907` | `net.minecraft.client.input.AbstractInput` |
| `class_11908` | `net.minecraft.client.input.KeyInput` |
| `class_1255` | `net.minecraft.util.thread.ThreadExecutor` |
| `class_4093` | `net.minecraft.util.thread.ReentrantThreadExecutor` |
| `class_1799` | `net.minecraft.item.ItemStack` |
| `class_243` | `net.minecraft.util.math.Vec3d` |
| `class_2338` | `net.minecraft.util.math.BlockPos` |
| `class_2561` | `net.minecraft.text.Text` |

### A.2 `MinecraftClient` (`net/minecraft/client/MinecraftClient.mapping`)

- `getInstance()` → `MinecraftClient` — `method_1551`
- `getWindow()` → `Window` — `method_22683`
- `getCameraEntity()` → `Entity` — `method_1560`
- `getNetworkHandler()` → `ClientPlayNetworkHandler` — `method_1562`
- `getRenderTickCounter()` → `RenderTickCounter` — `method_61966`
- `isWindowFocused()` → `boolean` — `method_1569`
- `isShiftPressed()` → `boolean` — `method_74187`
- `isCtrlPressed()` → `boolean` — `method_74188`
- `isPaused()` → `boolean` — `method_1493`
- `shouldTick()` → `boolean` — `method_60647`
- **No `getCurrentScreen()` / `getOptions()` / `getPlayer()` / `getWorld()` / `getInGameHud()`** — use the public fields (§2.5)
- Fields: `world` (`class_638`), `player` (`class_746`), `options` (`class_315` `GameOptions`), `inGameHud` (`class_329`), `currentScreen` (`class_437` `Screen`), `window` (`class_1041` `Window`), `targetedEntity` (`class_1297`)

### A.3 `KeyBinding` (`net/minecraft/client/option/KeyBinding.mapping`)

```java
new KeyBinding(String id, int code, KeyBinding.Category category)                       // <init> (Ljava/lang/String;ILnet/minecraft/class_304$class_11900;)V
new KeyBinding(String id, InputUtil.Type type, int code, KeyBinding.Category cat, int mods)
KeyBinding.Category.create(Identifier id)      // method_74698
KeyBinding.Category.create(String name)        // method_74699
boolean wasPressed()      // method_1436 — consumes presses; while-loop idiom
boolean isPressed()       // method_1434 — held state; may miss quick taps
void     setPressed(boolean)                   // method_23481
boolean  matchesKey(KeyInput key)              // method_1417 — KeyInput, NOT int
boolean  matchesMouse(MouseInput click)        // method_1433
void     setKeyPressed(InputUtil.Key key, boolean pressed)  // method_1416
```

### A.4 `InGameHud` (`net/minecraft/client/gui/hud/InGameHud.mapping`)

- `render(DrawContext, RenderTickCounter)` — `method_1753` ← the standard HUD hook point
- `renderCrosshair(DrawContext, RenderTickCounter)` — `method_1736`
- `renderHotbar(DrawContext, RenderTickCounter)` — `method_1759`
- `renderStatusBars(DrawContext)` — `method_1760`
- `getTextRenderer()` — `method_1756`

### A.5 `DrawContext` (`net/minecraft/client/gui/DrawContext.mapping`)

```java
void fill(int x1, int y1, int x2, int y2, int color)                              // method_25294
void fillGradient(int x1, int y1, int x2, int y2, int c1, int c2)                  // method_25296
void drawTextWithShadow(TextRenderer, Text, int x, int y, int color)               // method_27535
void drawCenteredTextWithShadow(TextRenderer, Text, int x, int y, int color)       // method_27534
void drawText(TextRenderer, Text, int x, int y, int color, boolean shadow)          // method_51439
void drawItem(ItemStack, int x, int y)                                             // method_51427
void drawItem(ItemStack, int x, int y, int seed)                                   // method_51428
void drawItem(LivingEntity, ItemStack, int x, int y, int seed)                     // method_51423  (class_1309)
void drawItem(LivingEntity, World, ItemStack, int x, int y, int seed)              // method_51425  (class_1937)
void drawItemWithoutEntity(ItemStack, int x, int y)                                // method_51445
void drawItemTooltip(TextRenderer, ItemStack, int x, int y)                        // method_51446
int  getScaledWindowWidth()                                                       // method_51421
```

Note the first parameter of the entity-aware `drawItem` overloads is `LivingEntity` (`class_1309`), **not** `ItemRenderer`.

### A.6 `ClientPlayNetworkHandler` (`net/minecraft/client/network/ClientPlayNetworkHandler.mapping`)

- `sendChatMessage(String)` — `method_45729`
- `sendChatCommand(String)` — `method_45730`

### A.7 `ThreadExecutor` (`net/minecraft/util/thread/ThreadExecutor.mapping`)

- `getThread()` — `method_3777`
- `isOnThread()` — `method_18854`
- `runTasks()` — `method_5383`
- `executeTask(Runnable)` — `method_18859`
- `submit(Supplier)` / `submitAsync(Runnable)` → `CompletableFuture`
- `isMemoryError(Throwable)` — `method_61391`

### A.8 World / entity lookup

- `ClientWorld.getEntities()` → `Iterable` — `method_18112` (declared on `ClientWorld`)
- `World.getEntityById(int)` → `Entity` — `method_8469`
- `Entity.tick()` — `method_5773`; `Entity.getVelocity()` — `method_18798`; `Entity.isAlive()` — `method_5805`
- `ClientPlayerEntity.sendMovementPackets()` — `method_3136`

---

## Appendix B — Version Pin

`yarn-1.21.11/build.gradle`:

```
def minecraft_version = "1.21.11"
```

Toolchain in `yarn-1.21.11/gradle.properties`: Java **21** required by the buildscript (`settings.gradle` throws below 21); Fabric Loom `1.12.0-alpha.39`.

**Any code written here targets MC 1.21.11 with Yarn names.** If the target changes, every symbol in this document must be re-verified before reuse — the 1.21.9 input refactor (§2.1) is proof that names move between versions.
