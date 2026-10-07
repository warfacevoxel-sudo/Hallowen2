# Distribuce a viditelnost pluginu – průvodce (září 2026)

Stav k 20. 9. 2026 a co zbývá udělat ručně. Vše technické (web, GitHub mirror, Modrinth projekt, CI) je hotové
nebo připravené; níže je seznam kroků, které vyžadují váš účet/heslo, a doporučený rytmus údržby.

## 1. Co už je hotové

| Kanál | Stav | Odkaz |
|---|---|---|
| **Dokumentační web** (GitLab Pages, mkdocs-material) | staví se automaticky z `docs/` při každém pushi do `master` (job `pages`) | https://softici.gitlab.io/halloween/ |
| `sitemap.xml`, `robots.txt`, `llms.txt`, Open Graph + JSON-LD `SoftwareApplication` | součást webu | https://softici.gitlab.io/halloween/llms.txt |
| **GitHub mirror** `softici/halloween` | repo vytvořeno přes API, topics + About nastaveny, `master` + všechny tagy pushnuty, Release v3.4.0 s jarem (sha256 `a6efe2ca…`). ⚠️ **Účet je aktuálně GitHubem „flagged“ (veřejně 404)** – viz 2.0. **GitLab push mirror zatím není** – viz 2.1 | https://github.com/softici/halloween |
| **Modrinth** projekt `halloween-plugin` (id `wskTmuSO`) | vytvořen přes API: popis = `MODRINTH_DESCRIPTION.md`, ikona, odkazy, kategorie *Game Mechanics / Mobs / Adventure* (+ Economy, Decoration), 7 screenshotů v galerii, verze **3.4.0** (loadery Paper/Spigot/Purpur/Bukkit, MC 1.21.3–1.21.11, 26.1–26.3), **odesláno ke schválení** (status `processing`). Slug `halloween` drží cizí skrytý projekt, proto `halloween-plugin` (pro vyhledávání i lepší). | https://modrinth.com/plugin/halloween-plugin |
| **GitLab projekt** | description + topics doplněny, Pages přepnuto na *Everyone* a vypnuta „unique domain“ (web běží na softici.gitlab.io/halloween). První pipeline: `build` ✅ 66 s, `pages` ✅ 17 s | https://gitlab.com/softici/halloween |
| **CI** (`.gitlab-ci.yml`) | `build` (Maven, JDK 25) při každém pushi, `pages` na masteru, `release` (manuální, jen s tagem `vX.Y.Z` a proměnnými `MODRINTH_TOKEN`/`GITHUB_TOKEN`) | Build → Pipelines |
| **Měření** | `/opt/minecraft/server/marketing/stats.sh` každé pondělí 9:00 zapisuje řádek do `stats.csv` (Spigot dl/rating, Hangar views/dl, Modrinth dl/followers, GitLab/GitHub stars, HTTP kód webu). Baseline 20. 9.: Spigot 918 dl / 4,9★ (7 hodnocení), Hangar 3013 zobrazení / 107 dl, Modrinth 0. | `cat /opt/minecraft/server/marketing/stats.csv` |

## 2. Co udělat vy (v tomto pořadí)

### 2.0 GitHub účet je „flagged“ – odblokovat (hned, 5 min + čekání na support)

Krátce po vytvoření repa přes API GitHub nový účet `softici` **skryl** (anonymně vrací
https://github.com/softici i repo 404, přihlášený přes API vše funguje). To je standardní antispamová
kontrola u čerstvých účtů s rychlou aktivitou. Postup:

1. Přihlaste se na https://github.com – nahoře bývá žlutý banner *„Your account has been flagged“* s odkazem
   *Contact support*. Pokud banner není, ověřte e-mail (Settings → Emails) a zapněte 2FA (Settings → Password
   and authentication) – někdy to flag zruší samo do několika hodin.
2. Jinak https://support.github.com/contact → *Account* → text (anglicky, stačí):
   *„My new account `softici` was flagged shortly after I created the public repository `halloween` (a mirror of
   my open-source Minecraft plugin at https://gitlab.com/softici/halloween) and pushed the code and a release via
   the API. Please review and unflag the account.“* Support obvykle odpoví do 1–2 pracovních dnů.
3. Ověření: `curl -I https://github.com/softici/halloween` vrátí 200 (nebo prostě otevřít odhlášený).
   Do té doby odkazy na GitHub v README/webu vedou na 404 – GitLab je primární, takže nic nefunkčního pro uživatele
   pluginu; jen mirror není vidět.

### 2.1 GitLab → GitHub push mirror + rotace tokenů (hned, ~5 min)

Ukládání vašeho GitHub tokenu do GitLabu jsem nechal na vás (bezpečnostní politika mi nedovolí zapisovat
cizí přihlašovací údaje do služeb třetích stran). Udělejte to rovnou s **novým** tokenem, starý pak smažte:

1. **Nový GitHub token:** https://github.com/settings/personal-access-tokens/new → Token name `gitlab-mirror`,
   Expiration 1 year (GitHub max.), Repository access → *Only select repositories* → `softici/halloween`,
   Permissions → Repository permissions → **Contents: Read and write** (Metadata se přidá samo) → *Generate token* → zkopírovat.
2. **Mirror v GitLabu:** https://gitlab.com/softici/halloween/-/settings/repository → *Mirroring repositories* →
   *Add new*: Git repository URL `https://softici@github.com/softici/halloween.git`, Mirror direction **Push**,
   Authentication method *Password*, Password = nový token, ☐ *Only mirror protected branches* (vypnuto),
   ☐ *Keep divergent refs* (vypnuto) → *Mirror repository* → u nového řádku klikněte 🔄 *Update now*.
   Za minutu má být status zelený a GitHub ukazuje stejný commit jako GitLab. Od té chvíle se každý push do
   GitLabu (i tagy) sám propíše na GitHub.
3. **Rotace tokenů z chatu:**
   - GitHub: https://github.com/settings/tokens → starý `ghp_…` (classic) → *Delete*.
   - GitLab: https://gitlab.com/-/user_settings/personal_access_tokens → `rottemp` → *Revoke*
     (na push jsem ho použil jednorázově, nikde uložen není).
   - Modrinth: https://modrinth.com/settings/pats → *Revoke*. Pro budoucí automatický upload z CI vytvořte nový
     PAT jen se scopy **Create versions** + **Write versions** a uložte ho jako CI proměnnou (viz 2.5); bez toho
     nahráváte verze ručně v UI – také v pořádku.

### 2.2 Modrinth – po schválení (e-mail od Modrinthu, obvykle 1–3 dny)

1. Zkontrolujte stránku https://modrinth.com/plugin/halloween-plugin (popis, galerie, odkazy). Dokud není
   schváleno, vidíte ji jen přihlášený jako `softici` (https://modrinth.com/dashboard/projects).
2. Pokud moderátor něco vytkne (nejčastěji: chybějící licence/odkaz na zdroj – máme; „nefunkční“ odkazy – web
   musí být již nasazený), odpovězte v *Moderation* záložce projektu a znovu odešlete (*Resubmit*).
3. Volitelně: *Settings → Links* doplňte Discord, pokud server nějaký má; *Settings → Members* – nic.
4. Do budoucích update postů na Spigotu přidejte větu „Also available on Modrinth“.

### 2.3 Hangar (30 min, doporučeno – stránka je zastaralá, z verze 1.4.0)

https://hangar.papermc.io/softici/Halloween

1. **Settings → General:** Category → *Gameplay*; Description → tagline
   „Halloween events for Paper, Spigot & Purpur 1.21.3 – 26.x: scares, pumpkin bosses, grave waves, Herobrine, candy economy, stats, Discord.“;
   Links → *Homepage* = https://softici.gitlab.io/halloween/, *Source* = https://gitlab.com/softici/halloween,
   *Issues* = https://gitlab.com/softici/halloween/-/issues, *Wiki* = https://softici.gitlab.io/halloween/.
2. **Settings → Tags:** odeberte `ADDON`, nechte/přidejte nic dalšího (Hangar tagy jsou omezené).
3. **Pages → Resource page (Edit):** smažte starý text a vložte obsah `publication_notes/HANGAR_PAGE.md`.
4. **Versions → New version:** nahrajte `halloween-plugin-3.4.0.jar` (nebo vložte URL
   https://gitlab.com/softici/halloween/-/releases/v3.4.0/downloads/halloween-plugin-3.4.0.jar), Platform **Paper**,
   verze **1.21.3 – 1.21.11, 26.1, 26.2, 26.3**, Channel *Release*, changelog = `release_notes/CHANGELOG_v3.4.0.md`.
   Starší verze (1.x) nechte být.
5. Volitelně nahrajte ikonu (`icon.jpg`) v Settings → Avatar.

### 2.4 SpigotMC (10 min)

https://www.spigotmc.org/resources/129197/ → *Edit resource*

1. Na začátek popisu (pod tagline, nad videa) vložte blok z `publication_notes/SPIGOT_LINKS_BLOCK_v3.4.0.bbcode`.
2. Pole **Documentation** (záložka Documentation) – na začátek přidejte řádek:
   `[B]Full documentation: [URL='https://softici.gitlab.io/halloween/']softici.gitlab.io/halloween[/URL][/B]`
3. Pole **Source code URL** = `https://gitlab.com/softici/halloween` (pokud není), **Donation** nechte.
4. V příštím update postu poproste o recenzi jednou větou („If the plugin made your Halloween, a review helps others find it 🎃“).
5. Odpovídejte v diskusním vlákně do 24 h – aktivita ve vlákně zvyšuje pozici v Spigot vyhledávání.

### 2.5 GitLab CI proměnné pro automatický release (volitelné, 5 min)

GitLab → Settings → CI/CD → Variables → *Add variable*:

| Key | Value | Flags |
|---|---|---|
| `MODRINTH_TOKEN` | nový Modrinth PAT (Create + Write versions) | **Masked**, **Protected** |
| `GITHUB_TOKEN` | nový GitHub fine-grained token (Contents RW) | **Masked**, **Protected** |

Pak při dalším releasu stačí: `git tag v3.5.0 && git push origin v3.5.0` → v pipeline tagu ručně spustit job
`release` (nahraje jar na Modrinth i GitHub). Aby *Protected* proměnné fungovaly, musí být tagy `v*` chráněné
(Settings → Repository → Protected tags → `v*`).

### 2.6 GitHub – drobnosti (5 min)

https://github.com/softici/halloween

1. *Settings → General → Social preview → Upload an image*: `docs/img/og-cover.jpg` (1200×630, připraveno).
2. Nic dalšího – About, topics, homepage i Release v3.4.0 jsou nastaveny přes API. Issues jsou zapnuté; když
   někdo založí issue na GitHubu, řešte ho tam (mirror kód nepřenáší issues).

### 2.7 Google Search Console a Bing (15 min, jednou)

1. https://search.google.com/search-console → *Add property* → **URL prefix** `https://softici.gitlab.io/halloween/`
   → ověření **HTML tag**: zkopírujte `<meta name="google-site-verification" content="…">`, pošlete mi ho
   (nebo vložte do `overrides/main.html` do bloku `extrahead`) a po nasazení klikněte *Verify*.
2. Po ověření: *Sitemaps* → přidat `sitemap.xml`.
3. https://www.bing.com/webmasters → *Import from Google Search Console* (jedním klikem převezme ověření i sitemap).
4. Za 2–4 týdny zkontrolujte *Performance* – dotazy typu „minecraft halloween plugin“, „spigot halloween plugin 26.2“.

### 2.8 Komunita – kolem 1. října (1–2 h celkem)

- **r/admincraft** (https://www.reddit.com/r/admincraft/): post typu *„Free open-source Halloween plugin for Paper/Spigot 1.21–26.x – bosses, grave waves, candy economy (v3.4.0)“*
  s 2 screenshoty a odkazem na web; pravidla subredditu povolují self-promo pluginů jednou za čas, buďte v komentářích.
- **PaperMC Discord** (#plugin-releases / #showcase kanál dle pravidel), **SpigotMC fórum** (Resource updates se propíše samo).
- **YouTube:** do popisků tří existujících videí doplňte odkaz na web a Modrinth.
- Krátký klip (≤ 10 s) hrobu/bossa se hodí jako GIF na Reddit i Modrinth galerii – stačí OBS/ShareX.

## 3. Rytmus údržby

| Kdy | Co |
|---|---|
| po každém releasu | tag `vX.Y.Z` → GitLab Release (checklist krok 6) → job `release` nebo ruční upload na Modrinth/Hangar/Spigot → aktualizovat `docs/changelog.md`, `docs/llms.txt`, `mkdocs.yml: plugin_version` (checklist krok 8) |
| týdně (automaticky) | `stats.csv` – jednou za měsíc se podívat na trend |
| do 24 h | odpovědi ve Spigot vlákně, GitLab/GitHub issues, Modrinth komentáře |
| říjen | 1–2 „showcase“ posty (Reddit, Discord), prosba o recenze |

## 4. Kde co je v repu

- `docs/` – zdroj webu (Markdown), `mkdocs.yml` – konfigurace webu, `overrides/main.html` – OG meta + JSON-LD
- `docs/llms.txt` = `llms.txt` – shrnutí pro AI asistenty (udržovat stejné)
- `publication_notes/MODRINTH_DESCRIPTION.md` – popis na Modrinthu (Markdown)
- `publication_notes/HANGAR_PAGE.md` – text pro Hangar
- `publication_notes/SPIGOT_LINKS_BLOCK_v3.4.0.bbcode` – blok odkazů do Spigot popisu
- `.gitlab-ci.yml` – build / pages / release
- `/opt/minecraft/server/marketing/stats.sh`, `/etc/cron.d/halloween-marketing-stats` – měření (na serveru, ne v repu)
