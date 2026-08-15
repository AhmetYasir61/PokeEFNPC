# Epic Fight combat patch

This turns `pokeefnpc:npc` into a real Epic Fight fighter: Epic Fight's own idle,
walk, run and swim animations instead of the vanilla arm-swing, its hit and
knockdown reactions when the NPC is struck, and actual sword and fist combos
when it fights.

It is **not** bundled in the jar. An earlier version was, and it broke logins —
Epic Fight syncs mob patches to the client at join time, and a patch it cannot
read takes the whole handshake down with an "invalid player data" error. Shipping
it separately means a bad patch costs you a datapack, not your server.

## Installing

Drop `pokeefnpc-ef.zip` into your world's `datapacks` folder:

    <world>/datapacks/pokeefnpc-ef.zip

Then `/reload`, or restart. Check it took with `/datapack list` — it should
appear as enabled.

To remove it, delete the zip and `/reload`. NPCs fall straight back to vanilla
combat; nothing else in the mod depends on it.

## What is in it

`npc.json` is the mob patch, and the format is Epic Fight's own — the same one
its in-game datapack editor writes.

- `default_livingmotions` — the idle/walk/run/sneak/swim/fall/sit/sleep set, as
  a plain map of `LivingMotion` name to animation key.
- `stun_animations` — what the NPC does when hit: `SHORT`, `LONG`, `KNOCKDOWN`,
  `FALL`.
- `humanoid_weapon_motions` — how the NPC stands and moves per weapon. A guard
  holding a longsword carries it like a longsword.
- `combat_behavior` — the attacks themselves, weighted, with `within_distance`
  conditions so a swing is only chosen at a range where it would land.

Every animation key in the file was checked against the animation table inside
Epic Fight 20.14.17 for 1.20.1. A key Epic Fight does not have is not a
warning — it is a hard deserialisation failure, so none are guessed.

## Emotes are a separate thing

The waving, bowing, laughing and so on do **not** come from here, and do not need
this datapack. Those are played directly by the mod, and the animations come from
the Epic Fight Dancing addon (`efdancing`) when it is installed, because base
Epic Fight ships combat and locomotion but almost no social gestures. Without
Dancing the mod animates the same gestures procedurally, so nothing disappears —
they are simply less polished.
