# 🎃 Halloween Plugin v3.4.0

**Halloween events for Paper, Spigot & Purpur 1.21.3 – 26.x.** Random scares, summonable pumpkin bosses, an
animated grave ritual with three undead waves, Herobrine encounters, pumpkin buffs, a candy economy, statistics,
a Discord webhook and a season scheduler – one lightweight, fully configurable plugin. Free and open source (MIT).

Developed by **Softici s.r.o.**

[![Version](https://img.shields.io/badge/version-3.4.0-orange.svg)](https://gitlab.com/softici/halloween/-/releases)
[![Server](https://img.shields.io/badge/server-1.21.3%2B%20%7C%2026.x-green.svg)](https://softici.gitlab.io/halloween/compatibility/)
[![Java](https://img.shields.io/badge/java-21%2B-blue.svg)](https://adoptium.net/)
[![API](https://img.shields.io/badge/API-Spigot%20%7C%20Paper%20%7C%20Purpur-blue.svg)](https://www.spigotmc.org/resources/129197/)
[![Spigot downloads](https://img.shields.io/spiget/downloads/129197?label=spigot%20downloads&color=orange)](https://www.spigotmc.org/resources/129197/)
[![Spigot rating](https://img.shields.io/spiget/rating/129197?label=rating&color=orange)](https://www.spigotmc.org/resources/129197/reviews)
[![Docs](https://img.shields.io/badge/docs-softici.gitlab.io%2Fhalloween-ff6f00.svg)](https://softici.gitlab.io/halloween/)
[![License](https://img.shields.io/badge/license-MIT-lightgrey.svg)](LICENSE)

**📖 [Documentation](https://softici.gitlab.io/halloween/)** ·
[FAQ](https://softici.gitlab.io/halloween/faq/) ·
[Commands](https://softici.gitlab.io/halloween/commands/) ·
[config.yml](https://softici.gitlab.io/halloween/config/) ·
[Changelog](https://softici.gitlab.io/halloween/changelog/)

**⬇️ Download:** [SpigotMC](https://www.spigotmc.org/resources/129197/) ·
[Modrinth](https://modrinth.com/plugin/halloween-plugin) ·
[Hangar](https://hangar.papermc.io/softici/Halloween) ·
[GitLab Releases](https://gitlab.com/softici/halloween/-/releases) ·
[GitHub Releases](https://github.com/softici/halloween/releases)

Source: [gitlab.com/softici/halloween](https://gitlab.com/softici/halloween) (primary) · [github.com/softici/halloween](https://github.com/softici/halloween) (mirror)

<p align="center">
  <img src="https://softici.gitlab.io/halloween/img/skeleton_boss_fight.webp" alt="Halloween Boss fight with health bar and cursed bat minions" width="800">
</p>

| | | |
|---|---|---|
| ![Grave event wave](https://softici.gitlab.io/halloween/img/grave_event_fight.webp) | ![Pumpkin Monster summoned](https://softici.gitlab.io/halloween/img/pumpkin_monster_summoned.webp) | ![Pumpkin Trader](https://softici.gitlab.io/halloween/img/pumpkin_trader.webp) |
| Grave event – wave bar with countdown | Pumpkin Zombie Boss summoned | Pumpkin Trader with config-driven trades |

Showcase videos: [Boss](https://www.youtube.com/watch?v=IGhz5KSODT8) · [Grave event](https://www.youtube.com/watch?v=SUoFE0Cjh34) · [Pumpkin Trader](https://www.youtube.com/watch?v=L97l9WvlNXA)

## 📦 Installation

### Requirements

| | Minimum |
|---|---|
| Server | Spigot / Paper / Purpur **1.21.3 or newer** — including the year-based versions **26.1 and 26.2**. **26.3:** compiles against the 26.3 API and runs on Paper 26.3 alpha builds in our tests, but Paper 26.3 is still alpha and has not been used in production yet — expect it to work, no guarantees until Paper marks 26.3 stable. |
| Java | **21+** (Paper 26.x itself requires Java 25) |

Running 1.21.1 / 1.21.2? Stay on **v3.1.1** — the `Attribute` API names used since v3.2.0 do not exist there.

### Fresh Installation (v3.4.0)

1. Download `halloween-plugin-3.4.0.jar` from [GitLab Releases](https://gitlab.com/softici/halloween/-/releases) (direct: [halloween-plugin-3.4.0.jar](https://gitlab.com/softici/halloween/-/releases/v3.4.0/downloads/halloween-plugin-3.4.0.jar)) or [SpigotMC](https://www.spigotmc.org/resources/129197/)
2. *Optional:* [Vault](https://www.spigotmc.org/resources/vault.34315/) (+ an economy plugin) for `money:` rewards, [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) for `%halloween_*%` placeholders, [WorldGuard](https://enginehub.org/worldguard) for the `halloween-events` region flag
2. Place the `.jar` file in your server's `plugins/` folder
3. Restart your server (plugin will auto-generate `Halloween_Softici/` folder)
4. Edit `plugins/Halloween_Softici/config.yml` and `messages.yml` to customize features
5. Run `/halloween reload` to apply changes

### Upgrading from v3.0.5 or newer (drop-in)

Stop the server, swap the JAR, start. Your `config.yml` is **smart-merged**: existing values and
comments are kept, only missing sections/keys are added and `config_version` is bumped. Your
`messages.yml` keeps every category you customized; only categories that do not exist yet are
appended. `data.yml` (player opt-outs, global on/off) is never touched.

If `messages.yml` contains a YAML typo (e.g. an unclosed quote), the plugin logs the line/column,
leaves the file untouched and uses default messages until you fix it and run `/halloween reload`.

### Upgrading from v3.0.0 – v3.0.4

Config is replaced with fresh defaults; your old file is kept as `config.old_<timestamp>.yml` in the
plugin folder — copy your values back manually. `messages.yml` and `data.yml` are preserved.

### Migrating from v1.x/v2.x

⚠️ **Breaking Changes in v3.0.0:**
- Plugin folder name changed from `Halloween/` to `Halloween_Softici/`
- Config structure changed: `intervals:` → `events:`, boss sections renamed

**Migration Steps:**

1. **Backup your old config:**
   ```powershell
   copy plugins\Halloween\config.yml plugins\Halloween\config.yml.backup
   copy plugins\Halloween\data.yml plugins\Halloween\data.yml.backup
   ```

2. **Stop your server**

3. **Replace the plugin JAR:**
   ```powershell
   del plugins\halloween-plugin-1.7.2.jar
   # Place halloween-plugin-3.4.0.jar in plugins/
   ```

4. **Start your server:**
   - Plugin will create new `Halloween_Softici/` folder with a fresh v3.x config
   - Shows warning about old `Halloween/` folder
   - **Your old config.yml is not converted** (the structure changed: `intervals:` → `events:`, boss sections renamed) —
     re-apply your custom values in the new `config.yml` by hand. Do not copy the old file over the new one:
     a v1.x/v2.x `config_version` is replaced with fresh defaults on the next start (backup `config.old_<timestamp>.yml`).

5. **Migrate player data (optional):**
   ```powershell
   copy plugins\Halloween\data.yml plugins\Halloween_Softici\data.yml
   ```

6. **Verify migration:**
   - Check console for folder conflict warnings
   - Run `/halloween reload` to confirm no errors
   - Test events with `/halloween set pumpkin 1 1`

7. **Clean up old folder (after testing):**
   ```powershell
   # Only after verifying v3.x works correctly!
   rmdir /s plugins\Halloween
   ```

**Config Migration Notes (how `config_version` is handled):**
- `3.0.5` … `3.2.x` → smart merge, all custom values and comments preserved
- `3.0.0` – `3.0.4`, any v1.x/v2.x version, or a missing/unreadable marker → replaced with fresh defaults, backup `config.old_<timestamp>.yml`
- `messages.yml` is only ever *extended* (new categories appended), never replaced — see [WHY_CONFIG_COMMENTS_CANT_MERGE.md](release_notes/WHY_CONFIG_COMMENTS_CANT_MERGE.md) and [v3.1.0_SMART_CONFIG_MERGE.md](release_notes/v3.1.0_SMART_CONFIG_MERGE.md) for the background

---

## 🚨 IMPORTANT: v3.0.0 Folder Name Changed

**OLD:** `plugins/Halloween/`  
**NEW:** `plugins/Halloween_Softici/`

This change prevents conflicts with other Halloween plugins. See [FOLDER_NAME_CHANGE_v3.0.0.md](release_notes/FOLDER_NAME_CHANGE_v3.0.0.md) for migration guide.

---

## 🆕 Recent Updates

### v3.4.0 (September 2026) - Admin Toolkit, Statistics, Candy Economy 🎃📊🍬
- **NEW**: Season scheduler (`season`) - Halloween turns itself on/off by date, countdown on join
- **NEW**: `/halloween trigger <event> [player|@a]` - fire any scare event or boss on demand
- **NEW**: Witching hour (`witching_hour`) - a daily window with more frequent events, announcements and ambient sounds
- **NEW**: Discord webhook (`discord`) - boss/grave/season events and the season-end leaderboard
- **NEW**: Statistics (`statistics`, `stats.yml`) - `/halloween stats [player]`, `/halloween top <stat>`, configurable player visibility, PlaceholderAPI expansion `halloween`
- **NEW**: Boss health bars, grave wave bar with countdown, titles (`visuals`)
- **NEW**: WorldGuard region flag `halloween-events` (deny = no events, bosses, graves or traders in the region)
- **NEW**: Rewards `command:`, `money:` (Vault) and `candy:` in every `rewards:` list; `chance:` now works for boss rewards too
- **NEW**: Halloween Candy (`candy`) drops from Halloween mobs/bosses/graves; the Pumpkin Trader's trades are configurable (`pumpkin_villager.trades`, `item: CANDY`)
- **FIXED**: `grave.pumpkin_drops_per_mob` is honoured, boss/grave cooldowns are saved immediately, data.yml is written from a snapshot

### v3.3.0 (September 16, 2026) - Community Requests 🎨👻
- **NEW**: `&` colour codes and `&#RRGGBB` hex colours in `messages.yml` (and config messages); placeholders are case-insensitive (`%player%` = `%PLAYER%`)
- **NEW**: *Every* text is in `messages.yml` now - help menu, on/off broadcasts, permission message, all admin command feedback (84 categories, appended automatically)
- **NEW**: `player_filters` - vanished players (standard `vanished` metadata: SuperVanish, PremiumVanish, Essentials, CMI) and spectators get no events/buffs/rewards and are never named in broadcasts
- **NEW**: `login_messages.enabled` / `.sound` - silence the join messages and sound
- **CHANGED**: pumpkin buff `level: 1` now means Effect I (was Effect II). Existing configs are converted automatically, effects stay the same
- **FIXED**: `/halloween buffs add|level` validate the level; grave announcements respect `/halloween disable`

### v3.2.2 (September 15, 2026) - messages.yml Typo Protection 🛡️
- **FIXED**: A YAML syntax error in `messages.yml` (e.g. unclosed quote) no longer silently overwrites the file with defaults — the error (line/column) is logged, the file stays untouched, defaults are used until you fix it and `/halloween reload`
- **FIXED**: Bogus `Could not save messages.yml ... already exists` warning on every start

### v3.2.1 (September 15, 2026) - Grave Event Fix ⚰️
- **FIXED**: `/halloween off` during a grave event no longer spawns the next wave later — all scheduled wave/timeout/warning/animation tasks are cancelled with the event

### v3.2.0 (September 15, 2026) - Minecraft 26.x / Paper 26.2 Support 🚀
- **NEW**: Runs on the year-based Minecraft versions (26.1 / 26.2 / 26.3) — uses the current `Attribute` API, built against Paper API 26.2 (Java 21 bytecode, still Spigot-compatible)
- **CHANGED**: Requires server 1.21.3+ and Java 21+; `api-version: 1.21`; `halloween.admin` permission declared
- **IMPROVED**: v3.1.x configs are smart-merged too (3.1.0 configs finally receive the `pumpkin_surprise` keys)

### v3.1.1 (October 27, 2025) - Pumpkin Surprise Configuration
- **FIXED**: `events.pumpkin.enable_pumpkin_surprise` / `pumpkin_surprise_count` are now actually used (were hardcoded to the 3rd pumpkin)

### v3.1.0 (October 21, 2025) - Smart Config Merge & World Blacklist
- **NEW**: Smart config merge — upgrades add missing sections without touching your values
- **NEW**: `worlds_blacklist` (nether/end excluded from events by default)
- **NEW**: Last 9 hardcoded messages moved to `messages.yml`

### v3.0.5 – v3.0.9 (October 2025) - Hotfixes & Message Customization
- v3.0.9: boss messages configurable; v3.0.8: all grave messages configurable + JAR-default fallback; v3.0.7: bosses no longer pick up player gear; v3.0.6: rapid-click boss spawn exploit fixed; v3.0.5: boss/grave cooldowns, message merge fix

### v3.0.4 (October 16, 2025) - Configurable Welcome Messages ✨
- **NEW**: Welcome messages are now configurable in `messages.yml`
- **NEW**: 8 random welcome message variations with `%PLAYER%` placeholder
- **IMPROVED**: Auto-merges new `welcomeMessage:` section to existing files
- **IMPROVED**: Cleaner join messages with better user experience

### v3.0.3 (October 15, 2025) - Reward System Hotfix
- **FIXED**: Min/max 0,0 configuration now properly disables rewards (no more errors)
- **FIXED**: Random pumpkin mob drops respect `min: 0, max: 0` setting
- **FIXED**: Bat transformation pumpkin rewards respect `min: 0, max: 0` setting
- **IMPROVED**: Admins can now safely disable specific rewards without plugin errors

### v3.0.2 (October 14, 2025) - Pumpkin Trader Hotfix  
- **FIXED**: Pumpkin Trader creation now respects Halloween enable/disable settings
- **FIXED**: Players with Halloween disabled can no longer create Pumpkin Traders
- **FIXED**: Pumpkin Trader creation respects global Halloween on/off state

## 🆕 What's New in v3.0.0 (October 10, 2025)

### 🐛 Critical Bug Fixes
- **FIXED**: Event `enabled: false` flag now properly disables events
- **FIXED**: XP error (IllegalArgumentException) in grave events and boss deaths
- **FIXED**: Config version no longer downgrades from v3.0.0 to v1.7.2 on restart
- **FIXED**: Deprecated `pumpkin_protector` and standalone `herobrine` sections removed from auto-migration

### ✨ Major New Features
- **🦇 Bat Transformation Event**: 33% chance for killed bats to transform into buffed witches
  - Drops 3-8 pumpkins + random useful potion (Strength/Swiftness/Regen/Healing/Fire Resistance)
  - 1.5x health, pumpkin helmet, glowing effect
  - Fully configurable spawn chance and rewards

### 🔧 Config Structure Changes
- **BREAKING**: `intervals:` renamed to `events:` (cleaner structure)
- **BREAKING**: `pumpkin_monster:` renamed to `pumpkin_zombie_boss:`
- **BREAKING**: `pumpkin_witch:` renamed to `pumpkin_skeleton_boss:`
- **REMOVED**: `pumpkin_protector:` section (feature was non-functional)
- **REMOVED**: Standalone `herobrine:` section (now uses `events.herobrine` only)
- **NEW**: All events now have `enabled: true/false` flag that actually works!

### 📂 Folder & Naming
- Plugin name: `Halloween_Softici` (no conflicts with other Halloween plugins)
- Folder: `plugins/Halloween_Softici/`
- Old folder detection with migration warnings

---

## ✨ Features

### 🦇 Bat Transformation Event ⭐ *NEW in v3.0.0*
Kill bats and watch them transform into cursed witches!

- **33% transformation chance** (configurable)
- Transformed witches have:
  - Pumpkin helmet + glowing effect
  - 1.5x health multiplier
  - Custom drops: 3-8 pumpkins + random useful potion
- **Random Potions**: Strength, Swiftness, Regeneration, Healing, or Fire Resistance
- Smart XP rewards (5 levels base, scales with player level)
- Respects player opt-out settings
- Auto-cleanup on `/halloween off`

---

### 👻 Herobrine Scare Events ⭐ *NEW in v1.7.0* ✅ *Fixed in v1.7.2*
Progressive 4-stage encounter system with increasing intensity!

- **Stage 1**: Silent observation (Herobrine watches from 20-30 blocks away)
- **Stage 2**: Closer encounter (15-25 blocks, cave ambience)
- **Stage 3**: Vanishing act (appears then teleports away with enderman scream)
- **Stage 4**: The chase (aggressive pursuit with wither sounds)
- **✅ Displays Galthorius skin** (white glowing eyes) correctly
- **✅ Can be disabled** with `events.herobrine.enabled: false` *v3.0.0*
- Fully configurable spawn rates and stage durations
- Atmospheric sound effects for each stage
- Automatic cleanup on plugin disable

---

### 🎃 Spooky Login Greeting
When players join, they are greeted with a **chilling message** reminding them that the Halloween event is active.  
**v1.5.4**: 30-minute cooldown prevents pumpkin helmet farming on join!  
**v3.0.0**: Fully configurable cooldown and can be disabled

---

### 🎃 Random Pumpkin Curse
- Every 1–10 minutes (configurable), a carved pumpkin is placed on players’ heads.  
- Items are safe — the plugin checks inventory space before swapping helmets.  
- If a player already has 3 pumpkin curses, their free slots fill with stacks of pumpkins *(Easter egg)*.  

---

### 👀 Slenderman Prank
- Every 10–15 minutes (configurable), an Enderman spawns 1 block in front of a random player.  
- It disappears after 0.5 seconds, leaving behind a creepy broadcast message and spooky sounds.  

---

### 🔥 Fire Prank
- Every 30–60 minutes (configurable), a random player is set on fire harmlessly *(no damage)*.  
- Fire lasts ~2 seconds with funny messages like:  
  > "I fell into the burning ring of fire…"

---

### ⚡ Lightning Prank
- Random harmless lightning strikes with thunder sounds and funny broadcast messages.  

---

### 🌑 Darkness Event ⭐ *NEW in v1.4.0*
- Every 10–50 minutes (configurable), a player’s screen briefly darkens for 1 second.  
- Accompanied by scary sound effects (trapdoor closing + cave ambience).  
- Creates atmospheric jump scares without disrupting gameplay.  

---

### ⚰️ Grave System ⭐ *NEW in v1.6.0* ✅ *Enhanced in v1.7.2*
Build elaborate graves and summon an **undead army** to test your combat skills!

#### How to Build:
1. Place **3 wood planks** underground (coffin)
2. Cover with **3 dirt blocks** in a row
3. Add **RIP sign** on middle dirt block
4. Build **cross** with stone walls (4 vertical + 2 horizontal arms at 3rd level)
5. Place **candles** on opposite side
6. Light candle with **flint & steel**!

#### What Happens:
- **Dramatic 18-second animation**: Cross sinks gradually → Dirt vanishes → Coffin transforms to nether bricks
- **Wave timeout system** ✅ *v1.7.2*: 2/3/5 minutes per wave (prevents stalling)
- **30-second warnings** before timeout ✅ *v1.7.2*
- **Three progressive waves**:
  - Wave 1: 3 Skeletons, 2 Zombies, 2 Skeleton Riders, 3 Zombie Riders
  - Wave 2: 4 Skeletons, 3 Zombies, 3 Skeleton Riders, 4 Zombie Riders
  - Wave 3: 5 Skeletons, 4 Zombies, **3 Blazes** ✅ *v1.7.2*, 4 Skeleton Riders
- **Buffed mobs**: +30% health, +50% damage, +20% speed (all with pumpkin helmets and glow!)
- **Enhanced Rewards** ✅ *v1.7.2*: **Ancient Debris (2-4)**, 10-32 pumpkins, cakes, skulls (50%), treasure map, 5-10 diamonds
- **Scaled rewards**: Based on completion percentage ✅ *v1.7.2*
- **Team XP**: Distributed based on kill contribution
- **✅ Works underground** and when surrounded by other blocks!

---

### 💀 Epic Boss Battles ⭐ *NEW in v1.5.0* ✅ *Enhanced in v1.7.2*
#### Pumpkin Monster (Zombie Boss)
- **Summon**: Place fence + carved pumpkin on top, ignite with flint & steel.  
- **Stats**: 100 HP, Diamond Armor, Mace weapon, +30% speed, +2.0 damage.  
- **Rewards** ✅ *v1.7.2*: **Dynamic configurable loot** - 10–14 carved pumpkins, cakes, zombie head (30% chance), glowstone dust, smart XP.

#### Halloween Boss (Skeleton Boss with Minions)
- **Summon**: Place lapis lazuli block + carved pumpkin on top, ignite with flint & steel.  
- **Stats**: 100 HP, Netherite Armor + Pumpkin Head, Bow, +30% speed, +3.0 damage.  
- **Minions**: Spawns *Cursed Bat* (every 10s) and *Haunting Phantom* (every 60s).  
- **Rewards** ✅ *v1.7.2*: **Dynamic configurable loot** - Ancient Debris, phantom membrane, beneficial potions, 15–30 pumpkins, +40% more XP.  
- **v1.5.3 Update**: Changed from Witch to Skeleton for balanced combat and reliable minion spawning.

---

### 🔊 Horror Sound Command ⭐ *NEW in v1.6.2* ✅ *Enhanced in v1.7.2*
- Play spooky horror sounds to specific players or everyone!
- Command: `/halloween sound <player|@a> <sound>`
- **16 unique horror sounds** ✅ *v1.7.2*: Including new **horse death scream**!
- Tab completion for players and sounds
- Perfect for events, pranks, and atmosphere!

---

### 💎 Pumpkin Villager Trading ⭐ *NEW in v1.5.0*
- **Convert villager**: Right-click any villager with a carved pumpkin.  
- Becomes **Pumpkin Trader** (Toolsmith profession, wears pumpkin helmet).  
- **Trading**: 64 carved pumpkins ↔ 1 emerald (both directions).  
- Daily limit: 64 trades per villager.  
- Reverts to normal when Halloween is disabled globally.  

---

### ⚡ Smart XP System ⭐ *NEW in v1.5.4*
- Intelligent XP scaling prevents high-level player exploits.  
- **Low-level players (< 30)**: Gain direct levels (3–7 per boss).  
- **High-level players (≥ 30)**: Gain raw XP that scales naturally.  
- Halloween Boss gives **+40% more XP** than Pumpkin Monster.  

---

### 🎁 Configurable Pumpkin Buffs ⭐ *NEW in v1.3.0* ✅ *Enhanced in v1.7.2*
- Wearing a **carved pumpkin** grants customizable potion effects.  
- **Configurable duration** ✅ *v1.7.2*: Default 15 seconds (prevents night vision blinking).
- **Instant removal** ✅ *v1.7.2*: Buffs clear immediately when helmet is removed.
- **Default buffs**: Haste II, Speed II, Strength II, Resistance II, Jump Boost II, Regeneration II.  
- Player also **glows with a spooky yellow aura** (toggleable).  
- Fully configurable via `config.yml` or admin commands.  
- See [PUMPKIN_BUFFS_GUIDE.md](release_notes/PUMPKIN_BUFFS_GUIDE.md) for details.  

---

### 🛡️ Player Protection ⭐ *Enhanced in v1.5.4*
- Players can `/halloween disable` to opt out completely.  
- Disabled players cannot spawn bosses.  
- `/halloween off` despawns all bosses, reverts villagers, and stops tasks.  
- Private mode: `broadcast_events: false` sends personal messages only.  

---

### 📝 Customizable Messages
- All prank messages are stored in `messages.yml`.  
- Add, remove, or translate them freely.  
- Use `%PLAYER%` to insert the victim’s name.  

---

### 🔊 Spooky Sounds
- Thunder and creepy cave noises play during pranks for immersive scares.  

---

### 📢 Server-Wide Announcements
- Everyone sees who was pranked (unless `broadcast_events: false`).  

---

## ⚙️ Configuration

### `config.yml` (v3.3.x Structure, excerpt)
```yaml
# DO NOT EDIT THIS LINE - used for auto-migration detection
config_version: "3.4.0"

# Worlds where no Halloween events run (v3.1.0+)
worlds_blacklist:
  - "nether"
  - "world_nether"
  - "the_end"
  - "world_the_end"

# Player filters (v3.3.0+): vanished players & spectators get no events, buffs, rewards or name mentions
player_filters:
  respect_vanish: true
  skip_spectators: true

# Join messages (v3.3.0+)
login_messages:
  enabled: true   # welcome, enable/disable hint, "globally disabled" notice, login-pumpkin cooldown
  sound: true     # spooky join sound

# v3.4.0+ (see config.yml for all keys)
season:        { enabled: false, start: "10-01", end: "11-05", countdown_days: 7 }
witching_hour: { enabled: false, mode: real, start: "20:00", end: "22:00", interval_multiplier: 0.5 }
discord:       { enabled: false, webhook_url: "", username: "Halloween" }
statistics:    { enabled: true, top_public: true, season_end_top: 3 }
visuals:       { boss_bar: { enabled: true, radius: 40 }, titles: { enabled: true } }
worldguard:    { enabled: true }
candy:         { enabled: true, material: COOKIE, name: "&6&lHalloween Candy", edible: false }

# Global control via commands (/halloween on/off)
broadcast_events: false  # If false, only affected player sees messages

# Custom command prefix (v1.6.1+)
command_prefix: "halloween"  # Change to "scary", "spooky", etc.

# Login Pumpkin (v3.0.0+)
login_pumpkin:
  enabled: true
  cooldown_minutes: 30  # Prevents farming on rejoin

# Helmet Rewards (v3.0.0+)
helmet_rewards:
  enabled: true
  first_reward_minutes: 3  # First cake reward
  increment_minutes: 3  # Increases each time (3, 6, 9, 12...)
  max_minutes: 30  # Maximum interval

# Bat Transformation (v3.0.0+) ⭐ NEW!
bat_transformation:
  enabled: true
  chance: 0.33  # 33% chance
  health_multiplier: 1.5
  message: "§5§l✦ §dThe bat transforms into a witch! §5§l✦"
  xp_reward: 5
  rewards:
    pumpkins_min: 3
    pumpkins_max: 8
    potion_drop: true  # Random useful potion

# Boss Battles (v3.0.0: renamed sections)
pumpkin_zombie_boss:  # Formerly pumpkin_monster
  enabled: true
  health: 100.0
  damage_boost: 2.0
  speed_boost: 0.3
  xp_reward: 10

pumpkin_skeleton_boss:  # Formerly pumpkin_witch
  enabled: true
  health: 100.0
  damage_boost: 3.0
  speed_boost: 0.3
  xp_reward: 15
  bat_spawn_interval: 10
  phantom_spawn_interval: 60

# Grave System (v1.6.0+)
grave:
  enabled: true
  wave_timeouts:
    wave1: 120
    wave2: 180
    wave3: 300
  rewards:
    - material: CARVED_PUMPKIN
      min: 10
      max: 32
    - material: ANCIENT_DEBRIS
      min: 2
      max: 4

# Scare Events (v3.0.0: renamed from "intervals")
events:
  pumpkin:
    enabled: true
    min: 1
    max: 10
    enable_pumpkin_surprise: true  # Fill inventory with pumpkins after the Nth pumpkin (v3.1.1+)
    pumpkin_surprise_count: 3
  
  slenderman:
    enabled: true
    min: 10
    max: 15
  
  fire:
    enabled: true
    min: 30
    max: 60
    damage_enabled: false
  
  lightning:
    enabled: true
    min: 20
    max: 40
    damage_enabled: false
  
  darkness:
    enabled: true
    min: 10
    max: 50
    duration_seconds: 2
  
  herobrine:
    enabled: true
    min: 40
    max: 80
    render_distance: 3

# Pumpkin Buffs
pumpkin_buffs:
  enabled: true
  duration: 15  # Seconds
  glowing: true
  effects:
    - type: HASTE
      level: 2      # 1 = Effect I, 2 = Effect II (v3.3.0+; older configs are converted automatically)
      enabled: true
    # ... (more effects)

# Update Checker (v1.7.1+)
update_checker:
  enabled: true
  message: "§6[Halloween] §eNew update available!\n§7Current: §c%current_version% §7→ Latest: §a%new_version%\n§eDownload: %link%"
```

---

### `messages.yml`
Every text the plugin shows lives here (since v3.3.0 including the help menu and admin feedback). Each category
is a list and one entry is picked at random. Colours: `&c`, `&l`, `&#RRGGBB` or `§`. Placeholders are
case-insensitive (`%player%` = `%PLAYER%`); `%COMMAND%` is the configured command name.
```yaml
pumpkin:
  - "&c👻 A spooky spirit has cursed &e%player% &cwith a pumpkin!"
  - "👻 A spooky spirit has cursed %PLAYER% with a pumpkin!"

slenderman:
  - "👻 Slenderman almost got %PLAYER%… but he vanished in shame."
  - "🎃 Something spooky appeared before %PLAYER%, but it chickened out."

fire:
  - "🔥 I fell into the burning ring of fire..."
  - "🔥 Looks like %PLAYER% played with matches again!"

lightning:
  - "⚡ Somebody was messing with lightning and accidentally hit %PLAYER%"
  - "⚡ Zeus was aiming for someone else, sorry %PLAYER%"

darkness:
  - "🌑 %PLAYER% felt the darkness creeping in..."
  - "👁️ Something lurks in the shadows around %PLAYER%..."

herobrine:
  stage1:
    - "👁️ %PLAYER% feels like they're being watched..."
  stage2:
    - "😨 %PLAYER% swears they saw something in the distance..."
  stage3:
    - "😱 %PLAYER% just saw Herobrine... then he vanished!"
  stage4:
    - "🏃 RUN %PLAYER%! Herobrine is hunting you!"

graveAwaken:
  - "⚰️ %PLAYER% has disturbed an ancient grave... something evil awakens!"

graveCurseBroken:
  - "✨ %PLAYER% has broken the curse of the grave and defeated the undead army!"

helmetReward:
  - "🍰 %PLAYER% was rewarded for trick-or-treat!"

pumpkinEasterEgg:
  - "🎃 Well... %PLAYER% seems to really like pumpkins."
```

---

## 📜 Commands

### 👤 Player Commands
- `/halloween help` — Show all available commands
- `/halloween enable` — Enable Halloween events for yourself
- `/halloween disable` — Disable all Halloween events for yourself
- `/halloween stats` — Your Halloween statistics *(v3.4.0+, `halloween.stats`)*
- `/halloween top <stat> [count]` — Leaderboard *(v3.4.0+, public unless `statistics.top_public: false`)*

### 🛠️ Admin Commands *(require `halloween.use` or OP)*
- `/halloween on` — Enable Halloween globally
- `/halloween off` — Disable all Halloween events globally (despawns ALL entities)
- `/halloween reload` — Reload config and player data (restarts all tasks)
- `/halloween set <event> <min> <max>` — Adjust event intervals  
  Example: `/halloween set pumpkin 2 6` or `/halloween set fire 0 0` (to disable)
- `/halloween sound <player|@a> <sound>` — Play horror sounds *(v1.6.2+)*
- `/halloween buffs <list|toggle|glow|add|remove|enable|disable|level>` — Manage pumpkin buffs
- `/halloween reset <pumpkin|helmet|cooldowns|all> [player]` — Reset one-time rewards / boss & grave cooldowns *(cooldowns: v3.4.0+)*
- `/halloween cleanup [all]` — Remove Halloween entities (use "all" to include traders) *(v3.0.0+)*
- `/halloween trigger <pumpkin|slenderman|fire|lightning|darkness|herobrine|zombieboss|skeletonboss|batwitch> [player|@a]` — Fire an event or boss now; ignores cooldowns and the global state but not the world blacklist / WorldGuard flag *(v3.4.0+, `halloween.trigger`)*
- `/halloween stats <player>` — Any player's full statistics *(v3.4.0+, `halloween.stats.admin`)*
- `/halloween candy give <player> <amount>` — Give Halloween Candy *(v3.4.0+)*
- `/halloween debug eligible [player]` — Show why a player does / does not receive events, boss bars and titles *(v3.4.0+)*

**Note:** Custom command prefix can be changed in config.yml (e.g., `/scary`, `/spooky`)  

---

## 🔑 Permissions

| Permission | Default | Description |
|-------------|----------|-------------|
| `halloween.base` | ✅ true | Allows `/halloween help`, `/halloween enable`, `/halloween disable`. |
| `halloween.use` | ❌ OP | Admin commands: `on|off`, `reload`, `set`, `reset`, `buffs`, `sound`, `cleanup`. |
| `halloween.admin` | ❌ OP | Receives the in-game "update available" notice on join. |
| `halloween.stats` | ✅ true | `/halloween stats` (own statistics) and the public leaderboard. *(v3.4.0+)* |
| `halloween.stats.admin` | ❌ OP | Any player's statistics, all stats. *(v3.4.0+)* |
| `halloween.trigger` | ❌ OP | `/halloween trigger`. *(v3.4.0+)* |

---

## 📦 Installation

1. Place the plugin `.jar` in your server’s `plugins/` folder.  
2. Start your server once to generate configuration files.  
3. Edit `config.yml` and `messages.yml` as desired.  
4. Reload with `/halloween reload` or restart your server.  

---

## 📚 Documentation

- **[softici.gitlab.io/halloween](https://softici.gitlab.io/halloween/)** — the documentation site (built from [`docs/`](docs/) with mkdocs-material on GitLab Pages). [`llms.txt`](llms.txt) is a machine-readable summary for AI assistants.  
- [release_notes/CHANGELOG_v3.4.0.md](release_notes/CHANGELOG_v3.4.0.md), [v3.3.0](release_notes/CHANGELOG_v3.3.0.md), [v3.2.2](release_notes/CHANGELOG_v3.2.2.md), [v3.2.1](release_notes/CHANGELOG_v3.2.1.md), [v3.2.0](release_notes/CHANGELOG_v3.2.0.md) — Latest changes.  
- [release_notes/COMMUNITY_REQUESTS_ANALYSIS_2026-09.md](release_notes/COMMUNITY_REQUESTS_ANALYSIS_2026-09.md) — What the community asked for and what was done.  
- [PAPER_26.3_EXPERIMENTAL.md](release_notes/PAPER_26.3_EXPERIMENTAL.md) — Status of Paper 26.3 (alpha) support.  
- [release_notes/EVENTS_REFERENCE.md](release_notes/EVENTS_REFERENCE.md) — All Halloween events.  
- [release_notes/PUMPKIN_BUFFS_GUIDE.md](release_notes/PUMPKIN_BUFFS_GUIDE.md) — Pumpkin buff details.  
- [release_notes/BUFFS_QUICK_REFERENCE.md](release_notes/BUFFS_QUICK_REFERENCE.md) — Command reference.  
- [release_notes/VERSION_UPDATE_CHECKLIST.md](release_notes/VERSION_UPDATE_CHECKLIST.md) — Release process (5 files to bump).  
- [DOCUMENTATION_INDEX.md](DOCUMENTATION_INDEX.md) — Index of all documents.  

---

## 🆕 Version History

### v3.4.0 (September 2026) - Admin toolkit, statistics, candy economy
- Season scheduler, `/halloween trigger`, witching hour, Discord webhook, statistics + PlaceholderAPI, boss bars & titles, WorldGuard flag, `command:`/`money:`/`candy:` rewards, Halloween Candy + configurable Pumpkin Trader

### v3.3.0 (September 2026) - Community requests
- `&`/hex colours, case-insensitive placeholders, everything in messages.yml, vanish/spectator filters, join-message toggle, 1-based buff levels (auto-converted)

### v3.2.x (September 2026) - Minecraft 26.x
- v3.2.0: Paper 26.2 / Minecraft 26.x support (new `Attribute` API, Paper API build, server 1.21.3+ / Java 21+)
- v3.2.1: grave event no longer continues after `/halloween off`
- v3.2.2: broken `messages.yml` is never overwritten; startup warning removed

### v3.1.x (October 2025) - Smart Config Merge
- v3.1.0: smart config merge, `worlds_blacklist`, remaining messages externalized
- v3.1.1: pumpkin surprise configurable (`enable_pumpkin_surprise`, `pumpkin_surprise_count`)

### v3.0.5 – v3.0.9 (October 2025) - Hotfixes
- Boss/grave cooldowns, full-replace config migration below 3.0.5, rapid-click fix, bosses don't pick up items, all grave and boss messages in `messages.yml` with JAR-default fallback

### v3.0.0 (October 2025) - MAJOR UPDATE 🎃
**Configuration Breaking Changes:**
- ⚠️ **Folder name changed**: `Halloween` → `Halloween_Softici` (prevents conflicts)
- ⚠️ **Config restructure**: `intervals:` → `events:`, all events now have `enabled:` flag
- ⚠️ **Boss renames**: `pumpkin_monster:` → `pumpkin_zombie_boss:`, `pumpkin_witch:` → `pumpkin_skeleton_boss:`
- ⚠️ **Deprecated sections removed**: `pumpkin_protector:`, standalone `herobrine:` (use `events.herobrine:`)

**New Features:**
- ✨ NEW: Bat Transformation Event - Bats have 33% chance to transform into witches with rewards
- ✨ NEW: Configurable event enabled/disabled flags (per-event control)
- ✨ NEW: Old folder detection with migration warnings
- ✨ NEW: Random useful potion drops from bat witches (Strength/Swiftness/Regeneration/Healing/Fire Resistance)

**Critical Bug Fixes:**
- 🐛 FIXED: Event `enabled: false` now properly stops events (6 task files updated)
- 🐛 FIXED: Bat witch potion drops now craftable (were previously broken items)
- 🐛 FIXED: XP errors in 3 locations (GraveManager, PumpkinMonsterManager, PumpkinWitchManager)
- 🐛 FIXED: Config version overwrite during migration
- 🐛 FIXED: Deprecated sections no longer added to fresh installs
- 🐛 FIXED: Dead migration code removed (cleanup)

**Migration Notes:**
- A fresh v3 config is generated in `plugins/Halloween_Softici/`; v1.x/v2.x values are not converted automatically
- Old folder detection shows warning on first startup
- Manually copy data.yml from old folder if migrating from v1.x

### v1.7.2 (October 9, 2025)
- 🐛 FIXED: Grave wave timeout system (waves no longer stall)
- 🐛 FIXED: Cross arms now sink gradually block-by-block
- 🐛 FIXED: Wave 3 blazes replace ineffective phantoms
- 🐛 FIXED: Herobrine skin displays Galthorius (white eyes) correctly
- 🐛 FIXED: Pumpkin buff duration increased to 15 seconds (no blinking)
- 🐛 FIXED: Buffs remove instantly when helmet is removed
- ✨ NEW: Dynamic boss reward configuration system
- ✨ NEW: Ancient debris in grave rewards (2-4)
- ✨ NEW: Per-wave mob spawn configuration
- ✨ NEW: Horse death sound effect (16 horror sounds total)
- ✨ NEW: Wave timeout with 30-second warnings
- ✨ NEW: Scaled rewards based on completion percentage

### v1.7.1 (October 8, 2025)
- 🔧 FIXED: Grave detection now works underground and when surrounded by blocks
- 🔧 FIXED: Cross arms now sink completely during animation (layer 3→0)
- ✨ NEW: Automatic update checker with admin notifications
- ✨ NEW: Clickable download links in update notifications
- ✨ NEW: Fully configurable update messages with placeholders

### v1.7.0 (October 7, 2025)
- ✨ NEW: Herobrine Scare Events (4-stage progressive encounter system)
- ✨ NEW: Stage-based atmospheric encounters with sounds
- ✨ NEW: Fully configurable spawn rates and stage durations
- 🔧 FIXED: Herobrine cleanup on plugin disable

### v1.6.2 (October 7, 2025)
- NEW: Horror sound command
- NEW: 15 unique horror sounds with tab completion
- NEW: Broadcast sounds to all players

### v1.6.1 (October 7, 2025)
- NEW: Custom command prefix system
- NEW: Player data separated to data.yml
- IMPROVED: Cross-server compatibility

### v1.6.0 (October 7, 2025)
- NEW: Grave System - Build graves and summon undead armies
- NEW: Dramatic 18-second grave animation
- NEW: Team-based XP distribution for grave battles

### v1.5.4 (October 6, 2025)
- Smart XP scaling system  
- 30-minute join cooldown  
- Global disable cleanup  
- Boss spawn safety checks  

### v1.5.3 (October 5, 2025)
- Changed boss from Witch → Skeleton  
- Improved minion spawn reliability  

### v1.5.0 (October 5, 2025)
- Added epic boss battles & trading  
- Introduced random pumpkin mobs  

### v1.4.0 (October 3, 2025)
- Added darkness event and cake rewards  

### v1.3.0 (October 3, 2025)
- Configurable pumpkin buffs system  

### v1.2.0 and earlier
- Core events, player data, and messages  

---

## 🐛 Bug Reports & Suggestions
Found a bug or have a feature request?  
➡️ Open an issue on [GitLab](https://gitlab.com/softici/halloween/-/issues) (or the [GitHub mirror](https://github.com/softici/halloween/issues)), post in the [SpigotMC discussion](https://www.spigotmc.org/resources/129197/), or contact **Softici s.r.o.**

---

## 📜 License
Open-source under the **MIT License**.  
You are free to use, modify, and share it.

---

**Made with 🎃 by Softici s.r.o.**  
**Stay spooky! 👻🕷️🦇**
