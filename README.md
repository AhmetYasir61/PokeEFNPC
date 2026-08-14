# PokeEFNPC

A living medieval/fantasy population for **Minecraft 1.20.1 (Forge)**.

Not a mob that stands in a doorway waiting to be right-clicked. These are people:
they are born into a village, claim a bed and a workbench, keep a daily routine,
work a trade, run shops and guilds, raise defences, and fight for their lives when
the monsters come. They have moods, and the mood shows on the face.

**Epic Fight** and **PokeFace** are both optional. Both are reached through
reflection, so the mod loads and plays with neither installed — and cannot be
broken by a point release of either.

---

## The two ways people get into the world

This is the core of the design, and it is deliberate: the world runs itself, and
an operator can still say exactly who stands where.

### 1. Villages populate themselves

A settlement is founded automatically the first time an NPC stands in something
Minecraft already considers a village. From then on it grows its own people. What
kind of people depends on what the place is short of:

- Keeps getting raided → it starts producing **guards and soldiers** instead of bakers.
- Larder running dry → it starts producing **farmers and shepherds**.
- Otherwise → an ordinary demographic mix, weighted per role.

Growth is paid for. A village only produces a new resident if it has the food to
feed one, and feeding a newcomer costs the stores up front. A well-defended,
well-fed settlement visibly grows; a besieged one visibly shrinks.

### 2. The operator places them by hand

The **NPC Editor** (creative/op only) does everything without touching a command:

| Action | Effect |
|---|---|
| Right-click air | Cycle the tool's mode |
| Sneak + right-click an NPC | Cycle which role the tool assigns |
| Right-click an NPC (ROLE mode) | Set its role and **lock** it — nothing will ever re-roll it |
| Right-click a block (landmark mode) | Mark the meeting point, guard post, shrine, training ground or hideout |
| Right-click a block (FOUND mode) | Found a settlement there, so a hand-built town becomes a real place |

The NPC spawn egg places someone who rolls whatever role the local settlement is
currently short of.

---

## The people

41 roles, each of which fixes its own statistics, personality, routine, gestures,
shop stock and interface.

- **Commoners** — villager, farmer, shepherd, fisherman, lumberjack, miner,
  builder, mender, baker, innkeeper, beggar
- **Martial** — soldier, guard, night watch, watch captain, knight, archer,
  mercenary, hunter, executioner
- **Guild** — traveller, adventurer, adventurers' chief, guild clerk, guild master
- **Trades** — merchant, blacksmith, alchemist, money-changer, scribe
- **Arcane & faith** — mage, priest, healer, seer, bard
- **Criminal** — thief, smuggler, **black market dealer**, bandit
- **Gentry** — noble, elder

### Every role gets its own menu

There is one container type and one packet for all of them. What makes a fence's
window different from a guild master's is the set of **pages** the role declares,
which the screen assembles into a tab strip — different tabs, different title,
different trim colour. Adding a role gives it an interface for free.

Pages: Talk, Shop, Contracts, Guild, Black Market, Smithing, Bank, Training, Hire,
Rumours, Lodging, Infirmary, Settlement.

The **black market** is genuinely secret. Off-book stock is filtered out
**server-side** for players the fence does not trust, so it never reaches an
untrusted client at all — it is not merely hidden in the interface.

---

## Moods, and reading a face across a courtyard

Mood is simulated on the server, where the events that cause it happen. Two
continuous channels do the work — **valence** (pleasant↔unpleasant) and **arousal**
(calm↔agitated) — and the discrete expression is *derived* from where the two are.

The asymmetry between the two unhappy quadrants is the whole point:

| | Calm | Agitated |
|---|---|---|
| **Unhappy** | **Sad** — brows raised at the inner ends, mouth corners down | **Angry** — brows drawn down and inward |
| **Content** | Neutral | Focused |

So a robbed shopkeeper glares and a bereaved one droops, out of one piece of
arithmetic. **Brows down and in = angry. Brows up = sad.** That is the contract
with PokeFace, and nothing is remapped across the bridge.

Each character settles back to its own **baseline** at its own rate, set by
personality — a hot-tempered guard spikes and recovers fast, a phlegmatic elder
takes its time. A settlement under attack is felt by *everyone in it*, not only by
whoever can see the monsters, so a raid reads on every face in the village at once.

---

## Emotes, including looping ones

43 gestures. Each carries three things at once:

1. an **Epic Fight animation** to use when Epic Fight has one,
2. a **procedural pose** the client animates itself otherwise — which is why the
   emotes still read on a vanilla install,
3. the **face** that goes with it.

**Looping** emotes are the ones an NPC settles into for a whole shift: the smith
hammers, the clerk writes, the guard holds its stance, the beggar pleads, the bard
plays. One-shots are reactions it fires and returns from. Gestures are offset per
entity so a row of guards is never in step.

---

## A day, and a settlement

Eleven daily routines (farmhand, artisan, shopkeeper, day watch, night watch,
adventuring, clergy, nocturnal, vagrant, wanderer, noble leisure). The schedule is
a plain lookup on the hour, not a state machine — so an NPC dragged out of its
routine simply rejoins wherever the clock has got to.

Work is productive: a working NPC adds to the settlement's shared larder or
materials pile, which pays for defences and keeps prosperity up, which in turn
stocks the shops and funds the contracts. Builders and guards walk the perimeter,
notice where it is dark or open, and close it with a torch or a fence out of the
shared stores.

When the monsters come, courage decides — not role. A brave baker holds a doorway;
a cowardly soldier runs for its bed. The watch converges on whatever has got
*furthest into the village*, rather than each guard peeling off after its own zombie.

---

## Configuration

`config/pokeefnpc-server.toml`:

| Setting | Default | Effect |
|---|---|---|
| `naturalPopulation` | `true` | Villages grow their own people. Off = operator-placed only. |
| `maxSettlementPopulation` | `24` | Cap on self-grown population. |
| `populationIntervalTicks` | `6000` | How often growth is considered. |
| `outlawSpawnChance` | `0.08` | Chance a newcomer is a thief/smuggler/fence. |
| `allowNpcBuilding` | `true` | Let NPCs place torches and fences. Off = they still fight and flee, they just stop building. |
| `stealFromPlayers` | `false` | Off = thieves steal from settlement stores instead of player inventories. |
| `replaceVanillaVillagers` | `false` | Convert vanilla villagers into NPCs. |

---

## Compatibility

**Epic Fight** — optional. When present, NPCs are registered for combat patches
and emotes play as Epic Fight animations where one exists. When absent, vanilla
melee and procedural gestures. Reached by reflection with no mixins and no patch
of Epic Fight's renderer.

**PokeFace** — optional. NPC faces are drawn by calling PokeFace's own
`FaceRenderer.renderInHeadSpace` from the NPC's render layer, so the NPCs get
exactly the eyes, brows and mouth the player has, animated by the same code, with
no duplicated drawing logic and no changes needed on the PokeFace side. Without
it, NPCs have plain skin faces and the mood system still drives behaviour,
emotes and dialogue.

Both bridges absorb every failure and log once. Neither is load-bearing.

---

## Skins

NPCs use the vanilla player model, so role skins are ordinary 64×64 player skins
at `assets/pokeefnpc/textures/entity/npc/<role>.png`. They are **optional** — a
role with no art falls back to the default skin rather than rendering a
missing-texture NPC, so a resource pack can add art for any role with no code
change.

## Building

```
./gradlew build
```

Forge 47.3.0 / Minecraft 1.20.1 / Java 17.

## Licence

MIT.
