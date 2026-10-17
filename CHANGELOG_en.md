# Evil Curse Fix — Changelog (English)

Covers every release from **1.0.0** to **1.6.7**.

---

## 1.6.7 — Fork Merge Release

Merged the three fixes that existed only in the external **1.6.6** fork (which was
branched from our 1.6.3 — verified by comparing class sets: it contains our
`EzfixReentrySlot`, `EzfixNeutralizeModernFixSpinWaitMixin` and the rewritten
`CuriosTagKeyCacheMixin`, but **not** our 1.6.4 AttributeMap fix).

### Merged fixes
- **TCC "真我" (ZhenWo) death-protection recursion** — two complementary guards:
  - `TccZhenWoStackGuardMixin` → `com.xlxyvergil.tcc.util.ZhenWoGuard#canPreventDeath`
  - `TccZhenWoInternalGuardMixin` → `com.xlxyvergil.tcc.items.curios.bound.ZhenWo#canPreventDeath`
  - Both make non-player entities return `false` immediately. "真我" is a
    player-only curio, but the original code ran a Curios inventory lookup for
    *any* `LivingEntity`; combined with Ice & Fire dragon tick callbacks that
    re-enter the hurt chain, this produced a `StackOverflowError`.
- **PickablePets `pets.dat` save crash** — `PickablePetsSaveGuardMixin` redirects
  `CachedPets.save(path)` to retry up to 8 times with increasing back-off
  (10→80 ms), covering transient Windows file locks (antivirus, backups,
  unflushed previous write). If all retries fail it logs once and skips the save
  (the data stays in memory and the next tick retries) instead of letting an
  `IOException` escape into the `ServerLevel` tick chain.

### Hardening over the fork version
- The fork declared all three injections with `require = 1`, which turns any
  target mismatch into a **startup crash**. All are now `require = 0`.
- The fork's TCC guards do not compile against tcc **1.3.0** (the version in this
  pack) because `ZhenWo` / `ZhenWoGuard` only exist in tcc **1.4.16+**. They are
  now marked `@Pseudo` (target may be absent) and gated by class-existence checks
  in `EzfixMixinPlugin`, so the mod builds and runs correctly on **both** tcc
  versions — applying the guard on 1.4.16+ and skipping silently on 1.3.0.

## 1.6.4
- **Fixed `ConcurrentModificationException` in `AttributeMap.getInstance`** (client
  crash "Ticking entity", 2026-10-17):
  - **Crash chain**: `ParCoolStamina.tick` → `LivingEntity.getAttribute` →
    `AttributeMap.m_22146_` → `HashMap.computeIfAbsent` → CME.
  - **Root cause (bytecode-verified against forge-1.20.1-47.4.0-srg.jar)**:
    `m_22146_` is literally `f_22139_.computeIfAbsent(attribute, this::m_22187_)`,
    where `f_22139_` is a plain `HashMap` (from `Maps.newHashMap()`) and `m_22187_`
    calls `AttributeSupplier.m_22250_(consumer, attribute)` to build the
    `AttributeInstance`. Because `HashMap.computeIfAbsent` bumps `modCount` while
    resolving the mapping function, any re-entrant read/write of the same map from
    within that callback chain (attributeslib / revelationfix / tcc mixin hooks)
    throws CME. Players carrying dozens of additional attributes hit this path
    frequently when an attribute is first accessed.
  - **Fix**: `EzfixAttributeMapConcurrentModificationMixin` redirects that
    `computeIfAbsent` call with an equivalent "get → create outside the map → put"
    sequence. The mapping function now runs outside `computeIfAbsent`'s internal
    computation state, so re-entrant map access can no longer corrupt its
    bookkeeping. Semantics preserved: first-write-wins, returns the instance the
    map finally holds. Recovered CMEs (if any remain deeper in the callback chain)
    are logged once per call site instead of crashing.

## 1.6.3
- **Fixed the Curios TagKey storm that freezes the render thread** (the 1.4.11 fix
  was ineffective — proven by a 2026-09-19 watchdog thread dump):
  - **Symptom**: render thread stuck RUNNABLE at
    `CuriosImplMixinHooks.lambda$static$9` → `new ResourceLocation` →
    `ItemTags.create` → `TagKey.create` → Guava `Interner.intern`, reached from
    `CuriosApi.getItemStackSlots` ← `ApothicCurios` ← `LootCategory.forItem`.
    FPS collapses and the game stops responding.
  - **Why 1.4.11–1.6.2 didn't help**: the cache existed, but its key was the
    freshly-constructed `ResourceLocation` instance. `ConcurrentHashMap.get` with a
    brand-new key object hashes to a different lookup and always missed — the cache
    was effectively dead code. The redirect was also declared with `require = 0`,
    so a non-match was skipped silently (zero log entries for that mixin).
  - **Fix (two layers)**:
    - **A** — `ItemTags.create` redirect now keys on `"namespace:path"` **strings**
      instead of `ResourceLocation` instances, so lookups actually hit.
    - **B** — new: `getItemStackSlots(ItemStack, boolean)` is memoised per item ID
      (`HEAD` returns the cached map, `RETURN` stores it). On a hit, the whole
      `lambda$static$9` path is skipped — no `ResourceLocation` allocation and no
      Guava interning at all. Items are cached by registry name; results depend only
      on item type and slot definitions (fixed after registration), so this is safe.
- **Full injection-point re-verification** against the live instance's 332 mods
  (37 target classes checked): 29 confirmed present, 4 are Forge/Minecraft core
  classes (expected), `yakumoblade registry.Highfrequency` absent while the `starSE`
  variant is present (the mutually-exclusive guards handle this by design), and
  `VirtualWitchArrangement` / `time_slow` are simply not installed — both are
  optional dependencies whose guards skip silently, matching the runtime log.
  No further version mismatches found.

## 1.6.2
- **Fixed the "save and quit hangs forever" deadlock** (runtime-log confirmed on the
  F:\MC instance, 2026-09-13):
  - **Symptom**: after clicking "Save and Quit to Title" (often right after pausing),
    the client kept rendering normally while the integrated server thread stayed parked
    forever in `runServer → waitUntilNextTick → managedBlock → waitForTasks (m_5667_)`;
    the server tick froze, `stopServer` was never called, and the client's
    `clearLevel` wait-for-stop loop never finished — the quit became permanent,
    with `ModernFix integrated server watchdog` reporting the same stall
    (40 s → 164 s and counting).
  - **Root cause**: ModernFix's `perf.fix_loop_spin_waiting` mixin
    (`@Mixin(priority = 500)`) overwrites `MinecraftServer.m_5667_` (waitForTasks) and
    replaces the wait with `LockSupport.parkNanos("waiting for tasks",
    nextTickTime * 1e6 - now)` — "sleep until the next tick deadline". Once
    `nextTickTime` stops advancing (pause/quit state combination), the park becomes
    permanent. This was the only mod in the pack touching that call path
    (verified by scanning all candidate mod jars for `m_18701_`/`waitUntilNextTick`
    references — ModernFix alone).
  - **Workaround**: `EzfixNeutralizeModernFixSpinWaitMixin` (`@Mixin(priority = 1500)`,
    applied after ModernFix) cancels the ModernFix implementation at `HEAD` of
    `m_5667_` and substitutes a **bounded 1 ms park**. A bounded wait can never
    sleep forever; wake-up frequency (1000/s) is far above the 20/s tick rate and
    costs the same order of CPU as the original vanilla path.
  - Applied only when ModernFix is present (mixin-plugin class check); when absent,
    `require = 0` skips it and the vanilla inherited implementation stays in use.
    ModernFix's other optimizations are unaffected.

## 1.6.1
- **Fixed `GoetyWandStatsCompatMixin` never applying** (runtime-log confirmed):
  the `@Redirect` handler was declared `static` while the target method
  `GoetyProvider.getSpellStats` is an instance method, so Mixin rejected the injection
  with `InvalidInjectionException: 'static' modifier of handler method does not match
  target` and — because of `defaultRequire: 0` — silently skipped it. The 1.5.2
  Touhou-Little-Maid-Spell crash fix had therefore never actually been active.
  - Handler changed to an instance method (javap-verified:
    `getSpellStats(EntityMaid, ISpell)` is non-static; the redirect target
    `WandUtil.getStats(LivingEntity, ISpell)` call site matches exactly).
- Known-log note: the watchdog stall warning seen on world load (tick=1, ~35 s) is the
  diagnostics working as designed during long loading spikes — the game continued
  normally afterwards; no action needed.

## 1.6.0 — Code Audit Release

**Overview.** A full audit of all 39 mixins was performed (including bytecode
verification of every injection target via `javap`). All medium- and low-severity
findings were fixed. Behavior for the supported modpack (终恶重置 1.3.1,
Forge 1.20.1 / 47.4.0) is unchanged; robustness and compatibility across version
drift are improved.

### Medium-severity fixes

- **Reentrancy guards hardened (9 mixins).** All `ThreadLocal<Boolean>` flags were
  replaced with the new `EzfixReentrySlot` (nesting depth + 5 s TTL self-healing).
  This fixes two latent bugs in `RankHandlerRecursionGuardMixin`,
  `SpudiversityInvisibleWaitingGuardMixin`, the four Spud reflect guards
  (BloodFox / AuspiciousPotato / BuildPotato / BlazeEnd), `EzfixSlashRecursionGuardMixin`,
  and both `EzfixHighfrequency*RecursionGuardMixin`:
  - a cancelled reentrant call previously cleared the outer call's flag (breaking
    the nesting protection), and
  - an exception thrown mid-method left the flag stuck forever — silently disabling
    slash effects or damage reflection on the server thread until restart.
  Stale flags now heal automatically within 5 seconds.

- **Traveloptics rapid laser: constructor-level redirect.** `TravelopticsRapidLaserMixin`
  no longer takes over the whole `spawnLaserBeam` method. It now redirects only the
  `Laser_Beam_Entity` constructor call (javap-verified: a single `NEW` instruction,
  `(EntityType, LivingEntity, 6×double, float, Level)` argument order). The original
  method body — rotation setup, entity registration, and any other side effects —
  is fully preserved. Old cataclysm builds are forwarded unchanged; new builds are
  adapted to `(EntityType, LivingEntity, x, y, z, Vec3, float, Level)`. Constructors
  are resolved once and cached.

- **Virtual Witch learn-spell: per-frame resend fixed.** javap showed that
  `ci.cancel()` skipped the original `heldSpellTime = -1` reset after the removed
  `ServerboundLearnSpell` allocation, which caused the modern learn packet to be
  re-sent every frame while holding. The handler now resets `heldSpellTime` itself
  (restoring the original semantics), replays the learn-success sound
  (`LEARN_ELDRITCH_SPELL`) reflectively, and caches all `Field`/`Method` lookups.

- **Watchdog: pause no longer looks like a stall.** `EzfixMonitor` now raises the
  server-stall threshold from 30 s to 60 s while the render heartbeat is alive
  (single-player ESC pause stops the server tick but not rendering). Dedicated
  servers (no render heartbeat) keep the original 30 s threshold.

- **Nightwarden ring particle: cached constructor.** `TravelopticsNightwardenRingMixin`
  caches the `CompatRingData` constructor; a lookup failure logs once instead of
  reflecting on every tick.

- **Core crash fix now fails loud.** `EventBusHandleExceptionMixin` (the EventBus
  log4j `MessageSupplier` linkage crash fix) now uses `require = 1`: if a future
  eventbus version changes the method signature, the mismatch is reported at
  startup instead of being silently skipped by `defaultRequire: 0`.

### Low-severity fixes

- `GunsmithlibSpawnBoundMixin` and `SocketingRecipeMixin`: explicit `ordinal = 0`
  on the `List.get` / `List.set` redirects (javap-verified single call sites),
  so future added call sites cannot be accidentally guarded.
- `EzfixMixinPlugin` now gates the starSE-package `Highfrequency` guard as well.
- `FCAEffectSyncGuardMixin`: non-CME causes are rethrown with their original type
  (no more `RuntimeException` wrapping that broke the exception chain);
  `IllegalAccessException` logs once instead of being silently swallowed.
- yakumoblade compile-only stubs moved to a dedicated `src/stub` source set:
  excluded from the built jar and from the dev run classpath (no more
  dev-environment collision with the real classes).
- 11 mixin targets switched from `targets` strings to `value = Class`, reducing
  annotation-processor warnings from 13 to 2. The remaining 2 are intentional:
  `time_slow` and `touhou_little_maid_spell` are optional dependencies whose jars
  are not required for compilation.

### Verification

- `spawnImpactParticles(Vec3)` handler signature confirmed correct via javap —
  no change needed.
- `gradlew build` passes: 0 errors, 2 intentional warnings.
- Built jar verified: no `com/yakumosakura` entries, `EzfixReentrySlot` present,
  all 39 mixins packaged.

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
