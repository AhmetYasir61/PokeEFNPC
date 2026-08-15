# Optional: Epic Fight combat for the NPCs

Epic Fight only builds a combat patch for entity types it has been told about.
For a mod's own mob it is told through a datapack: a file at
`data/<namespace>/epicfight_mobpatch/<entity path>.json`, resolved against
`ForgeRegistries.ENTITY_TYPES`. So the file's **location** is the binding — for
this mod's NPC that is `data/pokeefnpc/epicfight_mobpatch/npc.json`.

## Why this is not shipped inside the jar

It was, briefly, and it broke logins. Epic Fight serialises every mob-patch
entry to NBT and sends it to each joining client; a field it cannot convert
becomes a null tag, `SPDatapackSync.toBytes` throws, and the player is kicked
with **"Invalid player data"** before they ever reach the world.

A bad entry therefore does not degrade the feature — it locks everyone out of
the server. That is not something a mod should be able to do to you by default,
so this stays opt-in: you install it deliberately, and if it misbehaves you
delete one file rather than rebuilding the mod.

**Without it the NPCs still fight**, using vanilla melee. What you gain by
adding it is Epic Fight's animated combat.

## Installing

Copy `npc.json` to:

```
<world>/datapacks/pokeefnpc-epicfight/data/pokeefnpc/epicfight_mobpatch/npc.json
```

with a `pack.mcmeta` beside `data/`:

```json
{ "pack": { "pack_format": 15, "description": "PokeEFNPC Epic Fight patch" } }
```

Then `/reload`, and **log in on a test account first**. If you are kicked with
"Invalid player data", delete the datapack folder and reload — the server is
fine again immediately.

## The schema

Read out of `MobPatchReloadListener` in Epic Fight 20.14.17, not from
documentation. The field names are right; the values below are a starting point
and are the part most likely to need adjusting.

Recognised keys: `disabled`, `isHumanoid`, `faction`, `preset`, `model`,
`armature`, `renderer`, `attributes` (`impact`, `armor_negation`, `max_strikes`,
`attack_damage`, `chasing_speed`, `stun_armor`, `scale`), `default_livingmotions`,
`humanoid_weapon_motions`, `combat_behavior`, `stun_animations`.

If a key produces a null NBT tag, that is the one that kicks players. Bisect by
removing keys until logins work again.
