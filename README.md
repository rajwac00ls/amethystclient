# Birowo Client

A modular Minecraft client for 1.21.11 (Fabric).

## Building

```
./gradlew build
```

The mod jar is written to `build/libs/`.

## Testing

```
./gradlew test
```

The tests cover the parts of the client that do not need a running game: the arithmetic of the
aim, the wind of the mouse, the quantiser of the mouse steps, the event bus, the configuration
file and the lifecycle of the modules.

## Using the client

| Key | Action |
| --- | --- |
| `R` | Open and close the interface |
| `H` | Toggle ESP |
| `B` | Toggle BlockESP |
| `Y` | Toggle TargetESP |
| `K` | Toggle KillAura |
| `V` | Toggle AimAssist |
| `T` | Toggle TriggerBot |
| `G` | Toggle the HUD readout |

In the interface:

- Click a header to fold a panel, drag it to move it.
- Click a module to toggle it, right click it to change its settings.
- Type in the search panel to look for a module.

Positions of the panels are remembered in `config/birowo_gui.json`, the settings of the modules in
`config/birowo.json`.

## The modules

| Module | What it does |
| --- | --- |
| ESP | Draws a box around the entities around the player. |
| BlockESP | Draws a box around the ores, the chests and the other blocks that are worth looking for. |
| TargetESP | Marks the entity that is being attacked with a floating ring, a translucent body and a cone hat. |
| KillAura | Attacks the closest valid target once the aim has arrived at it. |
| AimAssist | Turns the camera towards a target without attacking. |
| TriggerBot | Attacks the entity the crosshair is on. |
| ClickGUI | The interface of the client. |
| HUD | A small readout of the state of the client. |

### How the aim is moved

Both aiming modules hand their target to one rotation engine
(`platform/rotation/RotationEngine.java`). The engine moves the aim once per frame, before the
camera of the game is updated, so the movement is smooth and never one frame behind.

The engine offers three ways to move the aim:

- `REGULAR` — the aim moves towards the target at a fixed speed, in degrees per second.
- `WIND_MOUSE` — the aim is pushed by a force that behaves like a hand on a mouse.
- `BLATANT` — the aim jumps onto the target. This is not the default, because a jump is the one
  movement that looks like a jump.

An aim is either visible or silent:

- **visible** — the camera of the player is pulled towards the target, and the mouse keeps the
  finer movement. KillAura and AimAssist both have a `silent` setting that decides which of the
  two they use.
- **silent** — the camera stays where the player left it, and the angle is written into the
  movement packets instead, so that the server is told about an angle the player does not see.

When a visible aim is let go, the camera walks back to the angle it had before the aim took it
over, at a limited speed, and gives up as soon as the mouse moves. The aim is not left standing
where the target was.

## The movement of the aim

The aim of KillAura does not stand still on one spot of the target. The point it is pointed at
wanders slowly over the body of the target, inside a band that is derived from how wide the
target looks from where the player is standing, so that the point never leaves the target and
never stands still either. The band moves to the other side of the middle of the target every
few ticks, and the height of the point drifts slowly above and below it.

KillAura also keeps the target that is aimed at for a moment (`switchDelay`) before it takes
another one, so that two entities that are close to each other do not make the aim jump back
and forth. AimAssist does the same with `switchTicks`, and leaves the aim alone altogether
inside its `safeZone`.

TriggerBot spreads the moment of the hit around a middle of fifty milliseconds, so that two
hits in a row are never exactly the same distance apart.

## The blocks that are shown

BlockESP looks through the chunks around the player for the blocks of the group that is chosen
(`preset`): the ores, the ores and the chests, or the rare blocks. A chunk holds thousands of
blocks, so the result of a chunk is kept until it grows old, and only a few chunks are looked
through in one tick. The result grows old after a hundred ticks, because a block that was mined
and a block that was placed are not noticed before that.

## The PvP version

The rules of 1.8.9 and of 1.21 differ in the two places that decide how fast a player may hit:
1.8.9 has no attack cooldown, and a hit does not interrupt a sprint.

The client reads the version the server announces and adapts to it. A proxy may rewrite the
announcement, so the version can also be set by hand:

```
.birowo pvp 1.8.9
.birowo pvp 1.21
.birowo pvp auto
```

The version that is in use is shown in the readout of the client, next to the frame rate.
