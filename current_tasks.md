# Current Tasks

## ✅ v3.1.1 - DONE (shipped inside v3.2.0)
## ✅ v3.2.0 - Paper 26.2 compatibility - DONE (September 15, 2026)

See `release_notes/CHANGELOG_v3.2.0.md`. Original v3.1.1 task list kept below for reference.

---

# Current Tasks - v3.1.1 Update (October 27, 2025)

## 🎯 Version 3.1.1 - Pumpkin Surprise Configuration

**Type:** Minor Feature Update (Bug Fix + Feature)  
**Current Version:** v3.1.0  
**Target Version:** v3.1.1  
**Estimated Time:** 2-3 hours

---

## 📋 Background

The plugin has an easter egg feature that fills a player's inventory with pumpkins when they receive their 3rd pumpkin (from either events or login). Currently:
- ✅ Config settings **already exist** in `config.yml` (added in v3.1.0):
  - `events.pumpkin.enable_pumpkin_surprise: true`
  - `events.pumpkin.pumpkin_surprise_count: 3`
- ❌ **Code doesn't use these settings** - still hardcoded to check `count == 3`
- ❌ No way to disable this feature or change the trigger count

**Goal:** Make the code actually use the config settings that already exist.

---

## ✅ Task List

### Task 1: Update `afterPumpkinApplied()` Method
**File:** `src/main/java/cz/softici/server/minecraft/HalloweenPlugin.java`  
**Location:** Lines ~825-833 (method `afterPumpkinApplied`)

**Current Code:**
```java
int count = data.incrementPumpkinCount(player.getUniqueId());
if (count == 3 && !data.hasPumpkinPrank(player.getUniqueId())) {
    fillFreeSlotsWithPumpkins(player);
    data.setPumpkinPrank(player.getUniqueId(), true);
    sendEventMessage(player, messages.getRandomMessage("pumpkinEasterEgg", player.getName()));
}
```

**Required Changes:**
- Read `enable_pumpkin_surprise` from config
- Read `pumpkin_surprise_count` from config
- Replace hardcoded `count == 3` with config value
- Add config check before triggering surprise

**New Logic:**
```java
int count = data.incrementPumpkinCount(player.getUniqueId());
boolean surpriseEnabled = getConfig().getBoolean("events.pumpkin.enable_pumpkin_surprise", true);
int surpriseCount = getConfig().getInt("events.pumpkin.pumpkin_surprise_count", 3);

if (surpriseEnabled && count >= surpriseCount && !data.hasPumpkinPrank(player.getUniqueId())) {
    fillFreeSlotsWithPumpkins(player);
    data.setPumpkinPrank(player.getUniqueId(), true);
    sendEventMessage(player, messages.getRandomMessage("pumpkinEasterEgg", player.getName()));
}
```

---

### Task 2: Verify Config Settings Exist
**File:** `src/main/resources/config.yml`  
**Location:** Lines ~172-175 (events.pumpkin section)

**Status:** ✅ Already exists! Just verify it's correct:
```yaml
events:
  pumpkin:
    enabled: true
    min: 5
    max: 20
    enable_pumpkin_surprise: true  # ✅ Already present
    pumpkin_surprise_count: 3      # ✅ Already present
```

**Action:** No changes needed - just verify during testing.

---

### Task 3: Update Smart Config Merge Logic
**File:** `src/main/java/cz/softici/server/minecraft/HalloweenPlugin.java`  
**Location:** `smartMergeConfigSections()` method (around line ~500-600)

**Check If Needed:**
- Verify if `smartMergeConfigSections()` handles `events.pumpkin` section
- Ensure it adds missing `enable_pumpkin_surprise` and `pumpkin_surprise_count` keys
- Test with old config file (v3.0.x) to ensure auto-migration works

**Expected Behavior:**
- Users upgrading from v3.0.x should get new config keys added automatically
- No manual config editing required

---

### Task 4: Version Bump (5 Files)
**Following VERSION_UPDATE_CHECKLIST.md pattern**

#### 4.1 Update `pom.xml`
**Line 9:** `<version>3.1.0</version>` → `<version>3.1.1</version>`

#### 4.2 Update `plugin.yml`
**Line 3:** `version: 3.1.0` → `version: 3.1.1`

#### 4.3 Update `config.yml`
**Line 2:** Comment header `# Halloween Plugin v3.1.0 Config`  
**Line 5:** `config_version: "3.1.0"` → `config_version: "3.1.1"`

#### 4.4 Update `HalloweenPlugin.java` Version Checks
**Search for:** Version comparison logic in config migration  
**Update:** Accept both v3.1.0 and v3.1.1 configs without regeneration  
**Lines to check:** ~410-416 (config version validation logic)

#### 4.5 Update `HalloweenPlugin.java` Version Logs
**Search for:** Console output messages mentioning version  
**Example locations:** Lines ~157, ~166 (plugin startup messages)

---

### Task 5: Testing Checklist

#### Test 1: Fresh Install (New Config)
- ✅ Delete existing `plugins/HalloweenPlugin/` folder
- ✅ Start server with v3.1.1
- ✅ Verify config has correct default values
- ✅ Trigger pumpkin event 3 times → inventory fills on 3rd
- ✅ Check console for version "v3.1.1" messages

#### Test 2: Config Disabled (`enable_pumpkin_surprise: false`)
- ✅ Set `enable_pumpkin_surprise: false`
- ✅ Reload config: `/halloween reload`
- ✅ Trigger pumpkin event 5+ times
- ✅ Verify inventory does NOT fill with pumpkins

#### Test 3: Custom Count (`pumpkin_surprise_count: 5`)
- ✅ Set `pumpkin_surprise_count: 5`
- ✅ Reset player data or test with new player
- ✅ Trigger pumpkin event 5 times
- ✅ Verify inventory fills on 5th pumpkin (not 3rd)

#### Test 4: Upgrade from v3.1.0 (Config Migration)
- ✅ Use existing v3.1.0 config (without new keys)
- ✅ Start server with v3.1.1
- ✅ Verify smart merge adds missing keys
- ✅ Check config has `enable_pumpkin_surprise` and `pumpkin_surprise_count`

#### Test 5: Login Pumpkin Trigger
- ✅ Enable login pumpkin: `login_pumpkin.enabled: true`
- ✅ Join server to get pumpkin #1
- ✅ Trigger event for pumpkin #2
- ✅ Join again (after cooldown) for pumpkin #3
- ✅ Verify surprise triggers correctly

---

### Task 6: Build & Deploy

#### 6.1 Build JAR
```powershell
mvn clean package
```
**Expected Output:** `target/halloween-plugin-3.1.1.jar`

#### 6.2 Test on Paper Server
- Copy JAR to Paper server `plugins/` folder
- Test all scenarios from Task 5
- Check console logs for errors

#### 6.3 Verify File Size
- Expected: ~130-131 KB (minimal change from v3.1.0)
- Compare with v3.1.0: 130,779 bytes

---

### Task 7: Documentation Updates

#### 7.1 Create Release Notes
**Files to create:**
- `release_notes/CHANGELOG_v3.1.1.md` - Detailed changelog
- `release_notes/BUGFIX_v3.1.1.md` - Bug fix explanation
- `BUGFIXES_v3.1.1.md` - Root-level bugfix summary

**Content Outline:**
- **Bug Description:** Config settings existed but were ignored by code
- **Root Cause:** Hardcoded `count == 3` check in `afterPumpkinApplied()`
- **Fix:** Code now reads from config correctly
- **Impact:** Users can now disable/customize pumpkin surprise
- **Upgrade Path:** Existing configs work, smart merge adds missing keys

#### 7.2 Update Project Status Documents
- Update `AT_A_GLANCE.md` → Current version v3.1.1
- Update `EXECUTIVE_SUMMARY_v3.1.0.md` → Create new v3.1.1 version

#### 7.3 Create SpigotMC Publication Notes
**File:** `publication_notes/SPIGOT_UPDATE_v3.1.1.bbcode`

**Template:**
```bbcode
[SIZE=5][B]v3.1.1 - Pumpkin Surprise Configuration Fix[/B][/SIZE]

[B]Bug Fix:[/B]
✅ Pumpkin surprise easter egg now respects config settings
✅ Can disable inventory fill: enable_pumpkin_surprise: false
✅ Can customize trigger count: pumpkin_surprise_count: 3 (default)

[B]How It Works:[/B]
When players receive pumpkins (from events or login), the plugin tracks the count.
Previously hardcoded to fill inventory on 3rd pumpkin - now fully configurable!

[B]Upgrade Notes:[/B]
Drop-in replacement for v3.1.0 - existing configs work perfectly.
New config keys auto-added via smart merge.
```

---

## 🚀 Implementation Order

1. **Code Changes** (30 min)
   - Task 1: Update `afterPumpkinApplied()` method
   - Task 2: Verify config (already done)
   - Task 3: Check smart merge logic

2. **Version Bump** (15 min)
   - Task 4: Update all 5 files

3. **Build & Test** (60 min)
   - Task 6: Build JAR
   - Task 5: Run all test scenarios

4. **Documentation** (45 min)
   - Task 7: Create release notes
   - Update project docs

**Total Time:** ~2.5 hours

---

## ⚠️ Critical Notes

1. **Config Already Exists:** The config keys are already in v3.1.0 - this is a bug fix to make the code use them
2. **Backward Compatible:** Users with v3.1.0 configs should see no behavior change (default values match current hardcoded behavior)
3. **Smart Merge:** Users upgrading from v3.0.x will get new keys added automatically
4. **No Breaking Changes:** This is purely additive functionality

---

## 📝 Additional Considerations

### Optional Enhancement (Future v3.1.2?)
Consider adding a message when surprise is disabled:
- If `enable_pumpkin_surprise: false` and player reaches count
- Show subtle message: "The pumpkin spirits are being gentle today..."

### Testing Edge Cases
- What if `pumpkin_surprise_count: 0`? (Instant trigger?)
- What if `pumpkin_surprise_count: 999`? (Never triggers?)
- Negative values? (Should validate in code)

**Recommendation:** Add validation:
```java
int surpriseCount = Math.max(1, getConfig().getInt("events.pumpkin.pumpkin_surprise_count", 3));
```
enable_pumpkin_surprise: true would do that. I am not sure whether or not login pumpkin can trigger this event but we should handle that as well.
If server has this feature disabled, players will not be getting inventory filled with pumpkins either after getting 3rd pumpkin after login OR from event pumpkin (every Nth min)

Also as I think about that, we should make it even more configurable.
We can enable/disable, but we should probably let admins configure how many pumpkins should players obtain from event or login in order to this :pumpkin surprise: be triggerd. I think now it is 3rd by default, but it would be nice to add another config parameter something like "
pumpkin_surprise_count: 3
" or treshold or something like that.

Do not forget to add this to automerge aswell. This should be under the pumpkin as well in config.yml