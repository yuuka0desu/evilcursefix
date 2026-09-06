# Evil Curse Fix — Changelog (English)

Covers every release from **1.0.0** to **1.6.0**.

---

## 1.6.0
- **Audit fixes (medium severity)**:
  - Replaced all 9 `ThreadLocal<Boolean>` reentrancy guards with `EzfixReentrySlot`
    (nesting depth + 5 s TTL self-healing). Fixes two latent bugs: a cancelled reentrant
    call used to clear the outer call's flag, and an exception mid-method left the flag
    stuck forever (silently disabling slash effects / damage reflection until restart).
  - `TravelopticsRapidLaserMixin` rewritten from whole-method takeover to a
    constructor-level `@Redirect` (verified via javap: single `NEW` instruction,
    `(EntityType, LivingEntity, 6×double, float, Level)` argument order) — the original
    method body (rotation, spawn, side effects) is now fully preserved.
  - `VirtualWitchLearnSpellMixin`: reset `heldSpellTime` after sending the modern packet
    (the original `-1` reset was skipped by `ci.cancel()`, causing per-frame resends),
    replayed the learn-success sound reflectively, cached all `Field` lookups.
  - `EzfixMonitor`: server-stall threshold raised to 60 s while the render heartbeat is
    alive (single-player ESC pause no longer triggers false stall alarms).
  - `TravelopticsNightwardenRingMixin`: cached the constructor; lookup failure now logs
    once instead of reflecting every frame.
  - `EventBusHandleExceptionMixin` now uses `require = 1` — version drift fails loud
    instead of silently disabling the core crash fix.
- **Audit fixes (low severity)**:
  - Explicit `ordinal = 0` on the `List.get`/`List.set` redirects (javap-verified single
    call sites) in `GunsmithlibSpawnBoundMixin` and `SocketingRecipeMixin`.
  - `EzfixMixinPlugin` now gates the starSE-package `Highfrequency` guard as well.
  - `FCAEffectSyncGuardMixin` rethrows non-CME causes with their original type (no more
    `RuntimeException` wrapping); `IllegalAccessException` logs once.
  - yakumoblade compile-only stubs moved to a dedicated `src/stub` source set — excluded
    from the jar and from dev run classpath, no more dev-environment class collision.
  - 11 mixin targets switched from `targets` strings to `value = Class` (13 → 2 AP
    warnings; the remaining 2 are intentional for optional jar-less dependencies).
- javap-verified `spawnImpactParticles(Vec3)` handler signature — no change needed.

## 1.5.3
- **Performance**: cached the reflective `Method` handles in `FCAEffectSyncGuardMixin`
  and `GoetyWandStatsCompatMixin` (they previously ran `Class.getMethod` on every damage
  event — a hot-path cost added by our own guards).
- **Diagnostics**: added `DamagePerfProbeMixin` — times every `ForgeHooks.onLivingHurt`
  dispatch, prints per-30 s stats (count / avg / max / ≥10 ms slow count) and dumps the
  thread stack for any single event slower than 25 ms (60 s cooldown).

## 1.5.2
- Fixed `NoSuchMethodError` in Touhou Little Maid Spell (万法皆通) calling the removed
  Goety 2.5.35.x signature `WandUtil.getStats(LivingEntity, ISpell)` — reflective
  overload detection with cached fallback (`GoetyWandStatsCompatMixin`).

## 1.5.1
- Fixed NPE in Goety `Owned.isAlliedTo(Entity)` when a servant's revenge target died
  and `HurtByTargetGoal` passed a null target (`GoetyOwnedNullTargetGuardMixin`).

## 1.5.0
- **Merged the standalone `npefix` mod**:
  - `EzfixAttributeMapNullGuardMixin` — null-tolerant AttributeMap accessors when the
    attribute supplier was never registered (generic NPE class).
  - `EzfixGuiNullPlayerGuardMixin`, `EzfixLightTextureNullGuardMixin` — skip HUD/light
    rendering while the local player is missing (client).
- **Merged the standalone `anti_overflow` mod**:
  - `EzfixArmorPiercingGuardMixin` — time_slow (GuardianMod) armor-piercing re-hurt guard.
  - `EzfixSlashRecursionGuardMixin` — SlashBlade `AttackManager.doSlash` recursion guard.
  - `EzfixHighfrequencyRecursionGuardMixin` (+ `Star` variant) — YakumoBlade
    `Highfrequency.onDoingSlash` recursion guard for both package layouts
    (`registry.specialeffects` and `specialeffects.starSE`).
  - `EzfixMixinPlugin` — conditionally enables the optional slash guards by class
    presence. (The duplicated EventBus logger fix was not merged; ezfix 1.2.0 already
    covers it.)
- **Regression fix (1.4.11)**: replaced the `ConcurrentHashMap.computeIfAbsent`
  implementation in the curios TagKey cache with a lock-free get / create-outside /
  `putIfAbsent` pattern — the old version could spin-lock the render thread when the
  mapping function re-entered the same bin.

## 1.4.11
- **Performance**: added `CuriosTagKeyCacheMixin` — caches curios slot `TagKey`s
  (previously every `testCurioPredicates` call created a new `ResourceLocation` and
  interned a new TagKey, hammering Guava's weak intern table and stalling the render
  thread at 1–2 FPS).
- **Diagnostics**: the watchdog now logs through log4j (`ezfix-monitor` logger) so its
  output reliably lands in `latest.log`.

## 1.4.10
- Fixed invalid `@Redirect` factory signature in `TravelopticsNightwardenRingMixin`
  (Mixin requires the target type, not `Object`, as return type for constructor
  redirects). Restored `CompatRingData` return type; construction stays reflective
  and fail-safe.

## 1.4.9
- Added the independent watchdog thread (`EzfixMonitor` + server/render heartbeat
  mixins): 60 s status summaries, 30 s stall detection with automatic full thread-stack
  dumps — turns "game not responding" reports into actionable stack traces.

## 1.4.8
- Spudiversity audit follow-up — added 4 more reflective-damage recursion guards:
  Blood Fox Potato, Auspicious Potato, Build Potato, BlazeEnd effect.

## 1.4.7
- Added `SpudiversityInvisibleWaitingGuardMixin` — breaks the InvisibleWaiting
  self-reflect loop (combined with the tcc guard this fully stops the
  TargetDummy/EnderMan three-way reflect cycle).

## 1.4.6
- Added `FCAEffectSyncGuardMixin` — swallows the `ConcurrentModificationException`
  thrown inside `AttributeMap.setAttributesUpdating` when flame_chase_artifacts syncs
  potion effects during Apotheosis' charm tick (both `m_141973_` overloads).

## 1.4.5
- Cross-version hardening via reflection (fixes crashes on the 1.0.x modpack):
  - `VirtualWitchLearnSpellMixin` — no direct reference to ISS network classes
    (`LearnSpellPacket` absent in ISS 3.4.x); packet class and sender probed by name;
    fields read reflectively.
  - `TravelopticsRapidLaserMixin` — constructor probed: Cataclysm 3.31 8-arg first,
    legacy 6-double fallback.
  - `TravelopticsNightwardenRingMixin` — reflective construction, `Object` return
    (later corrected in 1.4.10).

## 1.4.4
- Added `TravelopticsNightwardenRingMixin` — adapts the 10-arg legacy
  `CompatRingData` constructor call (after cataclysm-to-magic-fix coremod rewrite) to
  the current 9-arg constructor (drops the obsolete size argument).

## 1.4.3
- Added `RankHandlerRecursionGuardMixin` for the 1.0.x modpack's old
  slashblade_useful_addon (1.1.x) `OnHurtEntity` re-hurt loop (harmless on 1.2.5+).

## 1.4.2
- Fixed `IllegalClassLoadError: ... is in a defined mixin package` startup crash —
  moved the shared guard helper (`EzfixRecursionGuard`) out of the mixin package into
  `com.example.ezfix.util`.

## 1.4.1
- Fixed a startup crash introduced by 1.4.0: the laser constructor `@Redirect` could
  fail its injection check on player instances (signature mismatch), and
  `defaultRequire: 1` promoted it to a fatal `MixinTransformerError`.
  - Reworked `TravelopticsRapidLaserMixin` to a HEAD `@Inject` method rewrite.
  - Globally lowered `defaultRequire` to **0**: any unmatched injection point is now
    skipped with a WARN instead of crashing the launch.

## 1.4.0
- Batch fixes from historical reports:
  - `gunsmithlib_std_ammo` merchant spawner `nextInt(0)` bound clamp.
  - traveloptics rapid-laser legacy Cataclysm constructor adaptation.
  - traveloptics sandstorm projectile client-particle reference on server.
  - SlashBlade `EntityDrive` knockback-enum NPE on dimension change.
  - Goety robe fire-reflect recursion guard.
  - tcc MetaMorph ↔ applyImaginaryDamage recursion guard (shared ThreadLocal).
  *(Superseded by 1.4.1/1.4.2; listed for completeness.)*

## 1.3.0
- Fixed Apotheosis loot-conversion crash: `Failed to locate any affixes for
  <curios:heart item>` — keep the original drop when the affix pool is empty
  (`ApotheosisEmptyAffixMixin`).

## 1.2.0
- Four historical crash-report fixes:
  - Virtual Witch Arrangement learn-spell packet → modern `LearnSpellPacket`.
  - Fallen Gems & Affixes spell-cast `LinkageError` guard.
  - Peyro's Scythe sinfire duplicate attribute-modifier guard.

## 1.1.0
- Fixed Goetic Legacy × Sophisticated Core container-close infinite recursion
  (per-player re-entrancy guard around blueprint sync).

## 1.0.0
- Initial release:
  - EventBus `handleException` log4j `MessageSupplier` LinkageError fix (the core
    fatal crash).
  - Null-guards for vanilla / Goety / yamazakura / add_potion villager trades.
  - Apotheosis socketing recipe `List.set` out-of-bounds guard.
