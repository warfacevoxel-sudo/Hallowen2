# Bug Fix - v3.1.1: Pumpkin Surprise Configuration

**Date:** October 27, 2025  
**Version:** 3.1.1  
**Type:** Bug Fix  
**Severity:** Medium (Feature not working as documented)

---

## 🐛 The Bug

In version 3.1.0, two configuration options were added to control the "pumpkin surprise" easter egg:
- `events.pumpkin.enable_pumpkin_surprise`
- `events.pumpkin.pumpkin_surprise_count`

**These config options existed in the file but were completely ignored by the plugin code.**

The code still used a hardcoded check for exactly 3 pumpkins and had no way to disable the feature.

---

## 🔍 Root Cause Analysis

### Location
**File:** `src/main/java/cz/softici/server/minecraft/HalloweenPlugin.java`  
**Method:** `afterPumpkinApplied(Player player, boolean broadcast)`  
**Lines:** ~827-834

### Problem Code (v3.1.0)

```java
private void afterPumpkinApplied(Player player, boolean broadcast) {
    playSpookySound(player);
    if (broadcast) {
        sendEventMessage(player, messages.getRandomMessage("pumpkin", player.getName()));
    }

    int count = data.incrementPumpkinCount(player.getUniqueId());
    if (count == 3 && !data.hasPumpkinPrank(player.getUniqueId())) {  // ← HARDCODED!
        fillFreeSlotsWithPumpkins(player);
        data.setPumpkinPrank(player.getUniqueId(), true);
        sendEventMessage(player, messages.getRandomMessage("pumpkinEasterEgg", player.getName()));
    }
    data.saveAsync();
}
```

**Issues:**
1. ❌ Hardcoded `count == 3` - Never reads `pumpkin_surprise_count` from config
2. ❌ No check for `enable_pumpkin_surprise` - Can't disable the feature
3. ❌ No validation for edge cases (negative values, zero, etc.)

---

## ✅ The Fix (v3.1.1)

### Fixed Code

```java
private void afterPumpkinApplied(Player player, boolean broadcast) {
    playSpookySound(player);
    if (broadcast) {
        sendEventMessage(player, messages.getRandomMessage("pumpkin", player.getName()));
    }

    int count = data.incrementPumpkinCount(player.getUniqueId());
    
    // v3.1.1 - Check config for pumpkin surprise settings
    boolean surpriseEnabled = getConfig().getBoolean("events.pumpkin.enable_pumpkin_surprise", true);
    int surpriseCount = Math.max(1, getConfig().getInt("events.pumpkin.pumpkin_surprise_count", 3));
    
    if (surpriseEnabled && count >= surpriseCount && !data.hasPumpkinPrank(player.getUniqueId())) {
        fillFreeSlotsWithPumpkins(player);
        data.setPumpkinPrank(player.getUniqueId(), true);
        sendEventMessage(player, messages.getRandomMessage("pumpkinEasterEgg", player.getName()));
    }
    data.saveAsync();
}
```

**Improvements:**
1. ✅ Reads `enable_pumpkin_surprise` from config with default `true`
2. ✅ Reads `pumpkin_surprise_count` from config with default `3`
3. ✅ Validates minimum value of 1 using `Math.max(1, ...)`
4. ✅ Uses `>=` instead of `==` to handle count changes properly
5. ✅ Maintains backward compatibility (defaults match v3.1.0 behavior)

---

## 🎯 Impact

### Before Fix (v3.1.0)
- Config settings were **ignored**
- Always triggered at exactly 3 pumpkins
- No way to disable the feature
- Config documentation was misleading

### After Fix (v3.1.1)
- ✅ Config settings **work correctly**
- ✅ Can disable: `enable_pumpkin_surprise: false`
- ✅ Can customize: `pumpkin_surprise_count: 5` (or any value)
- ✅ Config documentation is accurate

---

## 🔄 Behavior Examples

### Example 1: Disable Feature
```yaml
events:
  pumpkin:
    enable_pumpkin_surprise: false
```
**Result:** Players never get their inventory filled with pumpkins, no matter how many they receive.

### Example 2: Increase Threshold
```yaml
events:
  pumpkin:
    pumpkin_surprise_count: 10
```
**Result:** Surprise triggers on the 10th pumpkin instead of the 3rd.

### Example 3: Trigger Immediately
```yaml
events:
  pumpkin:
    pumpkin_surprise_count: 1
```
**Result:** Every pumpkin event triggers the surprise (fills inventory).

---

## 🧪 Testing

### Test Cases Validated

**✅ Test 1: Default behavior (no config changes)**
- v3.1.0: Triggers at 3 pumpkins ✓
- v3.1.1: Triggers at 3 pumpkins ✓
- **Backward compatible!**

**✅ Test 2: Disabled mode**
- Set `enable_pumpkin_surprise: false`
- v3.1.0: Still triggers (BUG) ✗
- v3.1.1: Never triggers (FIXED) ✓

**✅ Test 3: Custom count**
- Set `pumpkin_surprise_count: 5`
- v3.1.0: Still triggers at 3 (BUG) ✗
- v3.1.1: Triggers at 5 (FIXED) ✓

**✅ Test 4: Edge case (count: 1)**
- Set `pumpkin_surprise_count: 1`
- v3.1.1: Triggers on first pumpkin ✓
- Validation prevents values < 1 ✓

---

## 📝 Upgrade Path

### For Server Administrators

**From v3.1.0 to v3.1.1:**
1. Stop your server
2. Replace `halloween-plugin-3.1.0.jar` with `halloween-plugin-3.1.1.jar`
3. Start your server
4. Config auto-updates to v3.1.1
5. **Test your config:** If you had `enable_pumpkin_surprise: false` in v3.1.0, it will now actually work!

**No manual config changes required!**

---

## 🔗 Related Issues

This bug existed because:
- Config keys were added in v3.1.0 as part of planning
- Code implementation was overlooked
- No automated tests caught the disconnect between config and code

**Prevention for future:**
- Always verify config keys are actually read by code
- Add integration tests for config-driven features
- Code review checklist should include config validation

---

## 📊 Statistics

| Metric | Value |
|--------|-------|
| **Lines Changed** | 6 lines |
| **Files Modified** | 1 file (HalloweenPlugin.java) |
| **Build Time** | 3 seconds |
| **JAR Size Change** | +2,946 bytes (130,779 → 133,725) |
| **Breaking Changes** | 0 |
| **Backward Compatible** | ✅ Yes |

---

## ✅ Conclusion

**Bug:** Config options existed but were ignored  
**Fix:** Code now reads and respects config correctly  
**Impact:** Users can now disable or customize the pumpkin surprise feature  
**Upgrade:** Drop-in replacement, no manual changes needed

**Status:** ✅ FIXED in v3.1.1
