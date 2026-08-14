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
| `voice.enabled` | `true` | Hear players and answer aloud (needs Simple Voice Chat). |
| `voice.coneDot` | `0.6` | How directly you must face an NPC to be talking to it. |
| `speech_to_text.endpoint` | whisper.cpp | Local transcription URL. |
| `language_model.enabled` | `false` | Off = written dialogue only. |
| `language_model.endpoint` | Ollama | Local model URL. |
| `language_model.extraPrompt` | `""` | Appended to every prompt — set a language here. |
| `text_to_speech.endpoint` | Piper | Local synthesis URL. |
| `skins.pool` | `[]` | Account names NPCs draw skins from. |
| `skins.fromCustomName` | `true` | A name tag chooses the skin. |

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

**Simple Voice Chat** — optional. Unlike the two above this is a compile-time
dependency, because a plugin has to *implement* SVC's interfaces and reflection
cannot satisfy an interface contract. It stays safe because the only class that
names a voice chat type is the plugin, and the plugin is loaded by Simple Voice
Chat itself — on an install without it, nothing ever classloads that file, and
every other class goes through a facade whose signatures mention no voice chat
types at all. The microphone event is never cancelled: players hear each other
exactly as they always did, and the NPCs are simply listening in.

All three bridges absorb every failure and log once. None is load-bearing.

---

---

## Talking to them out loud

With **Simple Voice Chat** installed, you can just speak to an NPC. No key to
hold, no command, no menu: stand in front of somebody and talk.

**How it decides you are talking to it.** Who is being addressed is fixed at the
*first* frame of the sentence — nearest NPC inside the cone you are facing, in
line of sight — so turning your head mid-sentence does not deliver the back half
of it to a different villager. The cone is what stops a crowded square from all
answering at once.

**How it knows you finished.** Simple Voice Chat sends packets while you
transmit and stops when you do not, so an utterance is the run of packets bounded
by silence. Anything under 400 ms, or too quiet, is discarded as a cough.

**How it answers.** The reply is played on an audio channel anchored to the NPC
itself, so it comes out of that villager's mouth with normal distance falloff and
directionality, and only the person being answered hears it. Each character is
pitched from its role and its own id — captains and executioners low, bards and
menders high, ±4% per individual — so one voice model still produces a village of
distinct people, and the same NPC always sounds the same.

The mouth stops moving on the last syllable, because the stop is driven by the
audio actually ending rather than by a guess at how long the line was.

Typed chat goes down the exact same path: if you happen to be looking at somebody
while you type, they hear it. Your message still goes to chat as normal.

---

## Thinking, on your own machine

Free and offline. Three programs, all local, all optional independently:

| Job | Free option | Config section |
|---|---|---|
| Hearing | `whisper.cpp` server, faster-whisper | `[speech_to_text]` |
| Thinking | Ollama, llama.cpp server, LM Studio | `[language_model]` |
| Speaking | Piper | `[text_to_speech]` |

```
# hearing
./server -m models/ggml-base.bin --port 8080

# thinking
ollama serve && ollama pull llama3.2:3b

# speaking
piper --model en_GB-alan-medium.onnx --http --port 5000
```

The recogniser and the model are addressed through the shapes everything already
speaks — multipart upload for transcription, OpenAI chat-completions for the
model — so any of the runners above work by changing one URL. Nothing needs an
account, a key, or a network connection.

### The model is given a character, not a role

It is never asked "what would a villager say?". It is told who *this particular
person* is: its trade, the traits it was rolled with, the mood the simulation has
it in right now, what it thinks of you, the hour, and how its village is faring.
So the same question put to a contented baker and to a frightened one during a
raid gets genuinely different answers, because the facts in the prompt are
different facts.

### The simulation stays in charge

The model can **say** things and **express** a mood, and nothing else. It cannot
set a price, hand out an item, accept a contract, or change a reputation — every
one of those still goes through the normal checked path in `NpcActionPacket`. A
reply may carry at most one `[emote:wave]` and one `[mood:angry]` tag, which are
mapped onto the mod's own enums and **stripped from the text**; an unknown tag is
discarded. That is what makes the model part of the character rather than a chat
window bolted onto one — the same reply that says *get away from my stall* also
folds the shopkeeper's arms and puts the scowl on its face.

Set `extraPrompt` for a language: `Always answer in Turkish.`

### It never costs you a tick

Every network call — recognition, model, synthesis — runs on bounded pools at
minimum thread priority, and results hop back to the server thread before
touching anything in the world. When a queue is full the request is **dropped**
and the NPC uses its written line. After three failures the client backs off for
a minute rather than letting a village full of NPCs each discover the same dead
socket. A slow model makes the world quieter, never laggier.

---

## Skins

NPCs use the vanilla player model, so any player skin fits them.

**On NameMC:** NameMC has no public API, and scraping its pages is fragile and
against its terms. What it *displays* is Mojang's own profile data — so that is
read directly instead: `api.mojang.com` for the UUID, `sessionserver.mojang.com`
for the skin URL and the slim-arm flag. Same skin, from the source, no scraping.
If you would rather point somewhere else — an offline-mode server, a mirror, a
proxy in front of NameMC — `urlTemplate` takes a URL with `{name}` in it and is
used instead, with no lookup at all.

Two ways to give an NPC a face:

- **A name tag.** Rename an NPC to an account name and it wears that account's
  skin. That is the whole feature, with no interface at all.
- **The pool.** Put account names in `skins.pool` and NPCs draw from it — stably,
  from their own id, so a character keeps its face for life rather than
  reshuffling on every reload.

Resolution and download happen **entirely on the client**, exactly as vanilla
does for players: the server syncs a username and nothing else, so no skin data
passes through it. Downloads are cached to `.minecraft/pokeefnpc-skins` and to a
registered texture, legacy 64×32 skins are converted, and a name that is still
downloading or has no account behind it simply falls back to the role skin.

Role skins shipped with the mod remain the middle tier: ordinary 64×64 player
skins at `assets/pokeefnpc/textures/entity/npc/<role>.png`, all optional.

## Building

```
./gradlew build
```

Forge 47.3.0 / Minecraft 1.20.1 / Java 17.

## Licence

MIT.
