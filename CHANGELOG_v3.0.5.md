# Halloween Plugin v3.0.5 Changelog

**Release Date:** October 17, 2025  
**Build Status:** ✅ PRODUCTION READY

## 🎯 Major Features

### 1. Anti-Stacking Boss Teleportation System
- **Problem Solved:** Players can no longer trap bosses in blocks to avoid fair combat
- **Implementation:** 
  - Bosses teleport to closest player every 5-15 seconds (random intervals)
  - Smart safe location detection algorithm prevents teleporting into solid blocks
  - Visual effects (particles) and audio feedback (enderman teleport sound) on teleportation
  - Both Zombie Boss and Skeleton Boss protected against stacking exploits
- **Technical:** Added `teleportTasks` Map, `startBossTeleportation()`, `getSafeLocation()`, and `stopBossTeleportation()` methods

### 2. Configurable Cooldown System (Anti-Farming)
- **Problem Solved:** Prevents excessive farming of boss rewards and grave loot
- **Configuration:**
  - `pumpkin_zombie_boss.cooldown_minutes: 10` (default)
  - `pumpkin_skeleton_boss.cooldown_minutes: 10` (default) 
  - `grave.cooldown_minutes: 15` (default)
- **Features:**
  - Per-player cooldown tracking with persistent storage in `data.yml`
  - Detailed cooldown messages showing remaining time (minutes and seconds)
  - Cooldown only applies to the player who spawned the boss/activated the grave
- **Technical:** Added cooldown tracking methods in `PlayerDataManager` and enforcement in all boss/grave managers

### 3. Fixed Reload Command
- **Problem Solved:** `/halloween reload` now properly updates `messages.yml` with new messages from JAR
- **Implementation:** `MessageManager.reload()` now calls `mergeDefaults()` to load new message categories
- **Benefit:** Server admins can get new message content without restart

### 4. Smart Config Migration for 3.0+ Versions
- **Problem Solved:** Existing configs automatically get new v3.0.5 features without losing custom settings
- **Implementation:** Intelligent detection and addition of missing cooldown parameters
- **Preserves:** All existing user customizations while adding new functionality
- **Logs:** Clear feedback showing which new config options were added

## 🔧 Technical Changes

### Version Updates
- Updated `pom.xml` version: `3.0.4` → `3.0.5`
- Updated `plugin.yml` version: `3.0.4` → `3.0.5`  
- Updated `config.yml` header and `config_version`: `3.0.4` → `3.0.5`
- Updated version check logic in `HalloweenPlugin.java`
- Updated logging statements to reflect v3.0.5

### New Configuration Parameters
```yaml
pumpkin_zombie_boss:
  cooldown_minutes: 10  # NEW - Cooldown before same player can spawn another boss

pumpkin_skeleton_boss:
  cooldown_minutes: 10  # NEW - Cooldown before same player can spawn another boss

grave:
  cooldown_minutes: 15  # NEW - Cooldown before same player can activate another grave
```

### New PlayerDataManager Methods
- `canSpawnZombieBoss(UUID, int)` / `setLastZombieBossSpawn(UUID)` / `getRemainingZombieBossCooldown(UUID, int)`
- `canSpawnSkeletonBoss(UUID, int)` / `setLastSkeletonBossSpawn(UUID)` / `getRemainingSkeletonBossCooldown(UUID, int)`
- `canActivateGrave(UUID, int)` / `setLastGraveActivation(UUID)` / `getRemainingGraveCooldown(UUID, int)`

### Boss Manager Enhancements
- **PumpkinMonsterManager:** Added teleportation system and cooldown enforcement
- **PumpkinWitchManager:** Added teleportation system and cooldown enforcement  
- **GraveManager:** Added cooldown enforcement for grave activation

## 🚀 Performance & Balance Improvements

### Boss Balance
- **Anti-Exploitation:** Bosses can no longer be trapped/stacked to avoid combat
- **Fair Rewards:** Cooldowns prevent rapid-fire boss spawning for infinite loot
- **Dynamic Combat:** Teleportation keeps boss fights engaging and mobile

### Resource Management
- **Controlled Farming:** Cooldowns prevent server-side lag from excessive mob spawning
- **Balanced Economy:** Limited boss/grave rewards maintain server progression balance
- **Memory Efficient:** Proper cleanup of teleportation tasks prevents memory leaks

## 🔄 Migration & Compatibility

### Automatic Migration
- **From v3.0.0-3.0.4:** Seamless upgrade with automatic cooldown parameter addition
- **From v2.x:** Full config regeneration with timestamped backup
- **From v1.x:** Complete migration with feature updates

### Backward Compatibility
- **Existing Configs:** All user customizations preserved during upgrade
- **Data Persistence:** Player data (disabled status, intervals) maintained
- **Command Structure:** No breaking changes to existing commands

## 📋 Admin Notes

### New Commands
- No new commands added - cooldowns managed automatically
- Existing `/halloween reload` now properly updates messages

### Testing Checklist
- [x] Boss teleportation prevents stacking exploits
- [x] Cooldown system prevents farming
- [x] Config migration preserves user settings
- [x] Reload command updates messages.yml
- [x] All existing features still functional
- [x] Clean compilation and packaging

### Server Requirements
- **Minecraft Version:** 1.21.1+ (Paper/Purpur recommended)
- **Java Version:** 17+
- **Dependencies:** Spigot/Paper API 1.21.1

## 🎃 Version Summary

Halloween Plugin v3.0.5 represents a significant balance and anti-exploit update focused on fair gameplay and server stability. The addition of boss teleportation and cooldown systems addresses the most common admin complaints about boss farming while maintaining the plugin's core spooky experience.

**Key Achievements:**
- ✅ **Boss Exploit Prevention:** No more block-stacking cheese strategies
- ✅ **Farming Mitigation:** Configurable cooldowns for balanced progression  
- ✅ **Enhanced Admin Tools:** Improved reload functionality
- ✅ **Seamless Upgrades:** Smart config migration preserves customizations
- ✅ **Production Ready:** Successful compilation and comprehensive testing

**Total Development Time:** ~2 hours  
**Lines Changed:** ~200+ across 6 core files  
**New Features:** 4 major systems  
**Breaking Changes:** None - fully backward compatible

*This version is ready for production deployment on Halloween 2025 servers.*