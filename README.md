# 恶咒专用修复 (ezfix)

针对整合包「终恶重置1.3.1」(Minecraft 1.20.1 / Forge 47.4.0) 错误报告的专用修复模组。
全部修复通过 Mixin 运行时注入，不改动任何第三方模组文件，移除本模组即可完全回退。

## 修复内容

### 1. 致命崩溃：EventBus 异常日志路径故障（核心）
**现象**：`ReportedException: Ticking entity` → `LinkageError: loader constraint violation ... org.apache.logging.log4j.util.MessageSupplier`

**链路**：拔刀剑 SlashEffect 攻击 → LivingEntity.hurt → 神化:现代诸神黄昏 BloodBulletBonus → LivingHurtEvent 分发，某监听器抛异常后，
`EventBus.handleException` 用 log4j 的 `Logger.error(Marker, MessageSupplier)` 记录日志时，
因整合包存在 MessageSupplier 接口双重类定义（类加载冲突）抛出 LinkageError，顶替原始异常传播回 tick 线程导致服务器崩溃。

**修复**：Mixin 接管 `EventBus.handleException`，改用 log4j 字符串重载（不触碰 MessageSupplier 接口），
外层 `catch (Throwable)` 兜底：日志系统发生任何故障都只输出到 System.err，绝不抛回调用方。
事件分发语义不变，原始监听器异常照常记录（出现 `[ezfix]` 标记即为本模组在工作）。

### 2. 诡厄巫法（Goety）流浪商人交易空指针（恶咒核心）
**现象**：`NullPointerException ... "f_19853_" because "entity" is null at ModTradeUtil.java:88`

**修复**：`ModTradeUtil$TreasureMapForEmeralds.m_213663_` 入口判空（与原代码"非 ServerLevel 返回 null"语义一致）。
JEI 中诡厄巫法流浪商人宝藏地图交易恢复正常显示。

### 3. 同类村民交易空指针（一并修复）
- 原版 `VillagerTrades$TreasureMapForEmeralds`（VillagerTrades.java:378）
- 山樱之刃 `VillagerSlashBladeListing`（yamazakura）
- 食物附魔 `FoodWithRandomEffectsForEmerald`（add_potion）

### 4. 神化（Apotheosis）打孔配方越界
**现象**：`IndexOutOfBoundsException at ArrayList.set(SocketingRecipe.java:63)`（JEI 计算输出时）

**修复**：拦截 `List.set`，下标越界时静默跳过（仅影响 JEI 假容器场景，正常游戏流程不受影响）。

### 5. 诡厄遗物 × 精妙核心关闭容器无限递归（1.1.0）
**现象**：关闭精妙背包/存储容器时发生栈溢出，堆栈重复：
`onContainerClose → syncPlayerBlueprints → StorageContainerMenuBase.broadcastChanges → ServerPlayer.closeContainer → onContainerClose`。

**修复**：对 `PrefabJobLifecycleEvents.onContainerClose` 调用蓝图同步的路径按玩家 UUID 添加线程内重入保护。首次同步保持完整；同步过程中同一玩家再次触发关闭事件时跳过嵌套同步，让容器关闭流程正常完成。

### 6. 四份历史崩溃报告兼容修复（1.2.0）
- `Virtual Witch Arrangement`：将已删除的 `ServerboundLearnSpell` 调用替换为新版 `LearnSpellPacket`。
- `Fallen Gems & Affixes`：在延迟词缀施法入口捕获 Iron's Spells API 漂移产生的 `LinkageError/NoClassDefFoundError`，仅跳过本次施法。
- `Peyro's Scythe`：在罪火拥抱真正添加属性修饰符的位置二次检查 UUID，消除重复修饰符异常。

### 7. 神化无可用词缀掉落崩溃（1.3.0）
**现象**：`Failed to locate any affixes for heart_of_revenge with category LootCategory[curios:heart]`，异常由死亡掉落转换链传播到拔刀剑斩击实体 Tick。

**修复**：当神化确认最终词缀列表为空时，取消本次随机词缀转换并保留原物品。其他拥有合法词缀池的装备不受影响。

### 8. 历史报告批量修复（1.4.0 → 1.4.2）
- `gunsmithlib_std_ammo`：商人刷新器对空玩家列表调用 `nextInt(0)` 崩溃 → 边界钳制 + 安全取值。
- `traveloptics`：极速激光旧版灾变构造器已删除（NoSuchMethodError）→ 在方法入口按新版构造器重写整个生成逻辑。
- `traveloptics`：服务端引用客户端粒子类（NoClassDefFoundError）→ 服务端直接跳过粒子。
- `SlashBlade` EntityDrive：跨维度存档时 `getKnockBack()` 为空 → 写入默认枚举序数。
- `goety` RobeEvents：长袍火伤反射无限递归 → 线程级重入保护。
- `tcc` MetaMorph/applyImaginaryDamage：虚化伤害互相触发无限递归 → 共享重入守卫（辅助类置于 `com.example.ezfix.util`，避开 Mixin 包引用限制）。

### 9. 健壮性修正（1.4.1 / 1.4.2）
- `defaultRequire: 0`：任何注入点在玩家实例上匹配失败只被跳过，绝不导致启动崩溃。
- 辅助类移出 mixin 包：修复 `IllegalClassLoadError: ... is in a defined mixin package` 启动崩溃。
- 构造器 `@Redirect` 改为 `@Inject` 方法体重写：消除对旧构造器调用点字节码匹配的依赖。

### 10. 旧版整合包（1.0.x）兼容修复（1.4.3）
- `slashblade_useful_addon` 旧版（1.1.x）：`RankHandler.OnHurtEntity` 在伤害事件内对实体再造成伤害导致无限递归（新版 1.2.5 已自愈，本守卫对两个版本均无害）→ 线程级重入保护（HEAD 拦截 + RETURN 清除）。

### 11. 跨版本反射化加固与新增修复（1.4.4 → 1.4.6）
- `VirtualWitchLearnSpellMixin`：网络包类名/字段全部改反射探测，兼容 ISS 3.4.x（无 LearnSpellPacket）与 3.15.x。
- `TravelopticsNightwardenRingMixin`：暗夜典狱长环形粒子构造器适配（coremod 替换后的 10 参数旧调用 → 9 参数新构造器），反射构造 + Object 返回。
- `TravelopticsRapidLaserMixin`：激光构造器按灾变版本反射探测（新版 8 参数 / 旧版 6-double）。
- `FCAEffectSyncGuardMixin`：flame_chase_artifacts 药水效果同步触发的 AttributeMap 遍历竞态（ConcurrentModificationException）→ 拦截属性重算调用并吞掉 CME，覆盖 3 参数/无参两个重载。

## 已知未修复问题（非本模组可干预范围）

| 问题 | 建议 |
|------|------|
| traveloptics coremod（`cataclysm_to_magic_fix` 内 `traveloptics_cataclysm_compat.js`）大量 `Nashorn Undefined → ClassNode` 报错 | 该 coremod 是第三方模组资源，Mixin 无法干预；建议更新或移除 `cataclysm_to_magic_fix` |
| `irons_spells_enchantments-1.0.2-all` 数据包 `global_loot_modifiers.json` 缺字段 | 数据包内容损坏；建议更新该模组或删除数据包内损坏的 loot modifier 文件 |

## 兼容性

- 所需：Minecraft 1.20.1、Forge 47+（已在 47.4.0 整合包验证）
- goety / yamazakura / add_potion / apotheosis 均为可选依赖：未安装时对应修复自动跳过（仅启动日志 WARN）

## 构建

构建前需将第三方依赖模组 jar 放入 `libs/`（编译期解析 Mixin 目标用，不打包进产物）。
所需清单见 `build.gradle` 的 `compileOnly files(...)` 列表，例如：
goety、yamazakura、add_potion、apotheosis、goeticlegacy、irons_spellbooks、
fallen_gems_affixes、PeyroScythe、ISS_VirtualWitchArrangement、L_Enders_Cataclysm、
SlashBladeResharped、traveloptics、gunsmithlib_std_ammo、tcc、
slashblade_useful_addon、cataclysm-to-magic-fix、flame_chase_artifacts、
spudiversity、curios、sophisticatedcore。可选依赖（yakumoblade、GuardianMod、
touhou_little_maid_spell）缺失时构建自动跳过对应编译校验。

```bash
JAVA_HOME=<jdk17> gradle build   # 产物: build/libs/evilcursefix-1.6.0.jar
```

## 部署

将 `ezfix-1.0.0.jar` 放入实例 `mods/` 目录即可。
