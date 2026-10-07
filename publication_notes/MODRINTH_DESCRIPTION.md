<p align="center">
  <img src="https://softici.gitlab.io/halloween/img/skeleton_boss_fight.webp" alt="Halloween Plugin – boss fight with health bar" width="800">
</p>

# 🎃 Halloween Plugin

**Turn your server into a haunted house for the season.** Random scares, summonable pumpkin bosses, an animated
grave ritual with three undead waves, Herobrine encounters, pumpkin buffs, a candy economy, statistics and a
Discord webhook – one lightweight plugin for **Paper, Spigot and Purpur 1.21.3 – 26.x**. Free and open source (MIT).

📖 **[Documentation](https://softici.gitlab.io/halloween/)** · ❓ [FAQ](https://softici.gitlab.io/halloween/faq/) ·
⌨️ [Commands](https://softici.gitlab.io/halloween/commands/) · ⚙️ [config.yml](https://softici.gitlab.io/halloween/config/) ·
🐛 [Issues](https://gitlab.com/softici/halloween/-/issues) · 💬 [SpigotMC thread](https://www.spigotmc.org/resources/129197/)

## ✨ What it does

- **Scare events** – a carved pumpkin lands on a player's head, Slenderman flickers in front of them, harmless
  fire and lightning, a flash of darkness, and a four-stage **Herobrine** encounter at night. Every event has its
  own interval and on/off switch.
- **Boss battles** – build a small altar (fence or lapis block + carved pumpkin), light it with flint & steel and
  fight the **Pumpkin Zombie Boss** or the **Halloween Skeleton Boss** with cursed-bat and phantom minions.
  Health bars, titles, configurable loot.
- **Grave system** – coffin, RIP sign, stone cross, candles. Light the candle, watch an 18-second ritual, then
  survive **three waves of buffed undead**. Wave bar with countdown, team XP, a glowing reward chest.
- **Candy economy & Pumpkin Trader** – **Halloween Candy** drops from Halloween mobs, bosses and graves; turn any
  villager into a **Pumpkin Trader** with trades you define (pumpkins, candy, emeralds, golden apples…).
- **Pumpkin buffs, bat witches, pumpkin mobs** – potion effects while wearing a carved pumpkin, bats that turn
  into glowing witches, hostile mobs spawning with pumpkin heads.
- **Statistics, leaderboards, Discord** – `/halloween stats`, `/halloween top <stat>`, a **PlaceholderAPI**
  expansion and a **Discord webhook** for boss kills, grave completions and the season-end top 3.
- **Runs itself** – a **season scheduler** switches Halloween on/off by date, a daily **witching hour** doubles
  the scares, a **WorldGuard** flag and a world blacklist keep spawn and the Nether quiet, every player can opt out.
- **Rewards your way** – items, random potions, candy, console **commands** and **Vault money** in every
  reward list, with per-entry chance.
- **Every text is yours** – 170+ message categories in `messages.yml` with `&` and hex colours, random
  variations and case-insensitive placeholders. Translate freely; updates never overwrite your changes.

## 🚀 Install in 30 seconds

1. Drop `halloween-plugin-3.4.0.jar` into `plugins/` and restart.
2. `plugins/Halloween_Softici/config.yml` and `messages.yml` appear – tweak what you like, `/halloween reload`.
3. Halloween is on. `/halloween off` pauses everything; or set `season.enabled: true` and let it run 1 Oct – 5 Nov.

Optional: **Vault** (money rewards), **PlaceholderAPI** (`%halloween_*%`), **WorldGuard** (`halloween-events` flag).

## ✅ Compatibility

| | |
|---|---|
| Server | **Paper, Spigot, Purpur** – Bukkit API only, no NMS |
| Minecraft | **1.21.3 – 1.21.11, 26.1, 26.2** – one jar. **26.3**: compiled against the 26.3 API and tested on Paper 26.3 alpha; marked supported once Paper 26.3 is stable |
| Java | 21+ |
| Not supported | Folia; 1.21.1/1.21.2 (use v3.1.1 from SpigotMC) |

Upgrading from any 3.0.5+ version is a drop-in: `config.yml` is smart-merged (values *and comments* kept),
`messages.yml` is only extended, and a YAML typo never wipes your file.

## 🎬 Videos

<iframe width="560" height="315" src="https://www.youtube-nocookie.com/embed/IGhz5KSODT8" title="Halloween Boss showcase" frameborder="0" allowfullscreen></iframe>

<iframe width="560" height="315" src="https://www.youtube-nocookie.com/embed/SUoFE0Cjh34" title="Grave event showcase" frameborder="0" allowfullscreen></iframe>

<iframe width="560" height="315" src="https://www.youtube-nocookie.com/embed/L97l9WvlNXA" title="Pumpkin Trader showcase" frameborder="0" allowfullscreen></iframe>

## ⌨️ Commands & permissions (short)

| Command | Who |
|---|---|
| `/halloween enable` / `disable` | players – opt in / out |
| `/halloween stats`, `/halloween top <stat>` | players (`halloween.stats`) |
| `/halloween on` / `off` / `reload` | admins (`halloween.use`) |
| `/halloween trigger <event> [player\|@a]` | admins (`halloween.trigger`) – fire any event or boss now |
| `/halloween set <event> <min> <max>` | admins – intervals in minutes, `0 0` disables |
| `/halloween buffs …`, `sound`, `reset`, `cleanup`, `candy give`, `debug eligible` | admins |

Alias `/hlw`; rename the command with `command_prefix` if another plugin uses `/halloween`.
Full reference: [Commands](https://softici.gitlab.io/halloween/commands/) · [Permissions](https://softici.gitlab.io/halloween/permissions/).

## 🔗 Links

- 📖 Documentation: https://softici.gitlab.io/halloween/
- 💾 Source (GitLab): https://gitlab.com/softici/halloween — mirror: https://github.com/softici/halloween
- 🐛 Bug reports & ideas: https://gitlab.com/softici/halloween/-/issues
- 💬 Discussion & reviews: https://www.spigotmc.org/resources/129197/
- 📜 Changelog: https://softici.gitlab.io/halloween/changelog/

Made with 🎃 by **Softici s.r.o.** – stay spooky! 👻
