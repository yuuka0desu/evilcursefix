package com.example.ezfix.mixin;

import net.minecraft.world.entity.ai.attributes.AttributeMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.ConcurrentModificationException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * 修复 AttributeMap.getInstance(m_22146_) 的 ConcurrentModificationException。
 *
 * 崩溃实证（2026-10-17，客户端 Ticking entity，玩家 Eclipse0712）：
 *   java.util.ConcurrentModificationException
 *   at java.util.HashMap.computeIfAbsent(HashMap.java:1221)
 *   at AttributeMap.m_22146_(AttributeMap.java:49)
 *   at LivingEntity.m_21051_(LivingEntity.java:1827)
 *   at ParCoolStamina.tick(ParCoolStamina.java:86)   ← 每 tick 查询耐力属性
 *
 * ⚠ @Pseudo 是必需的，不是可选项：
 * 本 mixin 发布于 1.6.7，首版未加 @Pseudo，导致 Android（Zalith Launcher /
 * Mio 包装器）启动失败。原因：Mixin 在 prepare 阶段解析本 mixin 的
 * @Mixin(value = AttributeMap.class) 时会预加载目标类；该解析链会顺带把
 * net.minecraft.world.item.Item 拉进类加载器，抢在 tact.mixins.json 的
 * ItemAccessor 之前，于是它抛
 *   MixinTargetAlreadyLoadedException: target net.minecraft.world.item.Item
 *   was loaded too early  → FATAL，启动终止。
 * 桌面端因 mixin 准备时序更早而侥幸未触发，移动端启动链路不同必然触发。
 * 加 @Pseudo 后 Mixin 不再于 prepare 阶段解析/加载目标类，改为类实际被
 * 加载时再处理，彻底消除提前加载。
 *
 * 字节码实证（forge-1.20.1-47.4.0-srg.jar）：
 *   m_22146_ 的实现就是
 *       f_22139_.computeIfAbsent(attribute, this::m_22187_)
 *   其中 f_22139_ 由 Maps.newHashMap() 创建（普通 HashMap，非并发容器），
 *   m_22187_ → AttributeSupplier.m_22250_(consumer, attribute) 创建
 *   AttributeInstance 并回调 consumer 注册该实例。
 *
 * 根因：HashMap.computeIfAbsent 在计算过程中会修改 modCount。只要 mapping
 * 函数（m_22187_ 经由 AttributeSupplier 回调，链上存在 attributeslib /
 * revelationfix / tcc 等模组的 mixin 钩子）在同一次调用内再次读写同一个
 * f_22139_，就会触发 "modCount != expectedModCount" 的 CME。玩家携带大量
 * 属性（该实例 additional_attributes 注册了数十个 innate spell/school 属性），
 * 首次访问某个属性时命中该路径的概率显著放大。
 *
 * 修复：把 computeIfAbsent 改写为「先查 → 在 map 之外创建 → 再插入」：
 *   1. 命中直接返回已有实例（与原来一致）；
 *   2. 未命中时先调用 mapping 函数创建实例 —— 此时不处于 computeIfAbsent
 *      的内部计算态，回调链重入读写本 map 也不会触发 CME；
 *   3. 创建完成后再插入，若期间已被其他路径插入则沿用已存在实例
 *      （等价于 computeIfAbsent 的「不覆盖」语义）。
 *
 * 语义一致性：computeIfAbsent 本就 first-write-wins 且返回 map 最终持有的
 * 实例；本实现保持这两点。
 */
/**
 * 双重防预加载：@Pseudo + 字符串 targets。
 *
 * @Pseudo：让 Mixin 不在 prepare 阶段解析/加载目标类。
 * 字符串 targets（而非 value = AttributeMap.class）：即便某种启动器实现
 * 在解析注解时仍会触碰 Class 字面量，字符串形式也不会促成类加载。
 * 两者叠加，确保本 mixin 绝不把 Item / Attribute 等核心类提前拉进
 * 类加载器，从而不再与 tact 等模组的 accessor mixin 抢时序。
 */
@Pseudo
@Mixin(targets = "net.minecraft.world.entity.ai.attributes.AttributeMap", remap = false)
public abstract class EzfixAttributeMapConcurrentModificationMixin {

    @Unique
    private static final Set<String> EZFIX$REPORTED_CALLSITE = ConcurrentHashMap.newKeySet();

    /**
     * 拦截 m_22146_ 内的 Map.computeIfAbsent 调用，替换为无内部计算态的等价实现。
     * require = 0：若未来 MC 版本改写该方法，静默跳过而非启动崩溃。
     */
    @Redirect(
            method = "m_22146_",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/Map;computeIfAbsent(Ljava/lang/Object;Ljava/util/function/Function;)Ljava/lang/Object;",
                    remap = false
            ),
            require = 0,
            remap = false
    )
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object ezfix$safeComputeIfAbsent(Map map, Object key, Function mappingFunction) {
        Object existing = map.get(key);
        if (existing != null) {
            return existing;
        }

        // 在 map 之外完成创建：可能触发 AttributeSupplier 回调链，
        // 回调中重入访问本 map 也不会与 computeIfAbsent 的内部状态冲突。
        Object created;
        try {
            created = mappingFunction.apply(key);
        } catch (ConcurrentModificationException cme) {
            // 极端情况：回调链内部仍有遍历期修改——记录一次并回退到已插入值
            reportOnce(cme);
            Object fallback = map.get(key);
            if (fallback != null) {
                return fallback;
            }
            throw cme;
        }
        if (created == null) {
            return null;
        }

        // HashMap 无 putIfAbsent：用 containsKey + put 实现 first-write-wins
        if (map.containsKey(key)) {
            return map.get(key);
        }
        map.put(key, created);
        return created;
    }

    @Unique
    private static void reportOnce(ConcurrentModificationException cme) {
        StackTraceElement[] frames = Thread.currentThread().getStackTrace();
        String site = frames.length > 3 ? frames[3].toString() : "unknown";
        if (EZFIX$REPORTED_CALLSITE.add(site)) {
            System.err.println("[ezfix] AttributeMap computeIfAbsent CME during attribute creation, recovered at: " + site);
            cme.printStackTrace(System.err);
        }
    }
}
