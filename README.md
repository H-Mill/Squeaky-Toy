# Custom Weapon SFX

Play custom sound effects when you attack or get hit in Old School RuneScape.

## Features

- Assign sounds to specific weapons
- Play different sounds for misses, regular hits, and max hits — on both regular and special attacks
- Dedicated **Player kill** trigger that fires when your attack kills another player
- Separate sounds for when *you* take damage (Received Attacks)
- Multiple sound groups per weapon, each with its own triggers, sounds, volume, and activation chance
- Multiple sounds per group — one is picked at random each time the group fires
- Per-group activation chance (0–100%) for randomized playback
- Enable or disable individual weapons without losing their configuration
- Use built-in sounds or drop in your own `.wav` files
- Per-sound volume control and in-panel preview

## Setup

1. Open the **Custom Weapon SFX** panel from the sidebar.
2. Add a weapon — click **Add (Equipped)** while holding a weapon, or click **Add (Search)** to find one by name (requires being logged in).
3. Expand the weapon and configure its sound group(s).
4. To play a sound when you take damage, expand the **Received Attacks** section at the top.

## Sound Groups

Each weapon (and the Received Attacks section) contains one or more **sound groups**. A sound group fires when its selected triggers match the outcome of an attack (see [Triggers](#triggers) for exact matching rules).

Click **+ Add Sound Group** inside an expanded weapon to add another group. Each group has:

- **Chance** — probability the group plays when its trigger fires (100% = always, 0% = never). Useful for adding occasional variation.
- **Sound(s)** — one or more sound files; if multiple are added, one is chosen at random each time the group fires. Click **▶** to preview a sound.
- **Volume** — playback volume per sound (0–100).
- **Triggers** — which hit outcomes cause this group to fire (see below).

Click **+ Add Sound** inside a group to add more sounds to the random pool.

## Triggers

| Trigger | When it fires |
|---|---|
| Regular attack zero | Regular attack that deals 0 damage |
| Regular attack max | Regular attack that is a max hit |
| Special attack zero | Special attack that deals 0 damage |
| Special attack hit | Special attack that deals non-max damage |
| Special attack max | Special attack that is a max hit |
| All attacks | Any attack that deals damage |
| Player kill | Your attack kills another player |

The **Received Attacks** section supports a subset: Regular attack zero, Regular attack hit, and All attacks.

### Player kill trigger

The **Player kill** trigger fires when your hit reduces another player's health to zero. It only works in PvP (wilderness, PvP worlds, etc.) — NPC kills do not activate it.

**Priority:** when a weapon has at least one group with Player kill enabled, that group takes exclusive priority on kills. All other groups on that weapon are suppressed for that attack, even if their own triggers would have matched.

**Combining with damage triggers:** if you add Player kill to a group alongside one or more damage triggers (Regular attack max, Special attack hit, Special attack max, etc.), **both conditions must be met** for the group to fire. For example:

- `Player kill` + `Special attack max` → fires only when you kill a player with a special attack max hit
- `Player kill` + `Regular attack max` + `Special attack max` → fires when you kill a player with either a regular or special max hit
- `Player kill` alone → fires on any killing blow, regardless of hit type

This lets you set up a generic kill sound alongside a more specific one for, say, a one-shot special — only the most specific matching group fires.

## Enabling and Disabling Weapons

Each weapon row has a checkbox in its header. Unchecking it disables that weapon — its sound groups will not fire — without removing any configuration. Check it again to re-enable.

## Using Your Own Sounds

1. Place `.wav` files in `.runelite/customweaponsfx/`.
2. Click **Refresh Sounds** in the panel.
3. Your files will appear in the sound dropdowns alongside the built-in sounds.

## Global Toggles

Two toggles appear at the top of the panel and apply globally:

**Ignore max hits ≤ 3** — when enabled, max hit SFX will not play if the hit deals 3 or fewer damage. This prevents thrall max hits from triggering your *Regular/Special attack max* sounds. Enabled by default.

**Ignore zeroes with prayer** — when enabled, the Received Attacks *Regular attack zero* trigger will not fire while Protect from Melee, Protect from Ranged, or Protect from Magic is active. Useful if you don't want a "blocked" sound every time a prayer absorbs a hit. Enabled by default.

## Resetting

Click **Reset All Data** to clear all weapons and sound groups and restore defaults. A confirmation dialog will appear first.

## Known Issues

**Thrall hits** — if you have a thrall active and a *Regular attack zero* or *All attacks* trigger set, thrall zeros and damage will cause the sound to play. Thrall hits are indistinguishable from player hits in the data provided by the game client, so this cannot be fully filtered out. The **Ignore max hits ≤ 3** toggle mitigates thrall max hits specifically.

**Splashing** - Your players splashing, like thrall zeros, cannot be distinguished from other players splashes, so I opted to not include them in the plugin (everyones splashes around you would cause a SFX).