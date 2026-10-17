package com.example.ezfix.util;

/**
 * 线程级「按实体去重」重入标记。
 *
 * 用于 TCC「真我」护盾判定的递归环防护。与 EzfixReentrySlot 的区别：
 *
 *  - EzfixReentrySlot 是「同一方法的自我递归」守卫（深度 &gt; 0 即视为重入），
 *    适用于 doSlash / 伤害反射这类"方法内再调用同名方法"的场景；
 *  - 本类按 **实体 UUID** 去重，用于"同一条判定链上重复判定同一实体"的场景。
 *
 * 为什么 TCC 需要按实体去重：
 *   isInsideActiveBarrier(A) 在实现中会合法地遍历并判定其它实体（护盾范围内
 *   的生物），若用线程级单标记，这些**合法的嵌套判定**会被误判为重入而返回
 *   false，导致护盾保护失效。只有"正在判定 A，判定途中又要求判定 A"才是
 *   真正的递归环，必须拦截。
 *
 * 语义：
 *  - enter(id) 返回 true  → 该实体已在判定中（重入），调用方应跳过并返回 false；
 *  - enter(id) 返回 false → 成功进入，调用方正常执行，必须在结束时 exit(id)；
 *  - exit(id) 只移除本线程集合中的该 id，不影响其它实体的嵌套判定。
 *
 * 异常残留：若目标方法抛异常导致 exit 未执行，该 id 会残留在集合中。
 * 为此集合条目带时间戳，enter 时若发现残留超过 TTL 则视为过期并放行，
 * 把"永久误拦截"降级为"短暂过度防护"。
 *
 * 注意：本类位于 mixin 包之外（com.example.ezfix.util），
 * Mixin 0.8.5 禁止 mixin 配置包内的非 mixin 类被 mixin 类直接引用。
 */
public final class EzfixEntityReentryGuard {

    /** 默认自愈窗口：护盾判定链远快于此 */
    public static final long DEFAULT_TTL_MS = 3_000L;

    private static final class Entry {
        long stamp = System.currentTimeMillis();
    }

    private final ThreadLocal<java.util.Map<java.util.UUID, Entry>> inFlight =
            ThreadLocal.withInitial(java.util.HashMap::new);
    private final long ttlMs;

    public EzfixEntityReentryGuard(long ttlMs) {
        this.ttlMs = ttlMs;
    }

    /**
     * 尝试进入某实体的判定。
     *
     * @return true 表示该实体已在判定中（重入，应跳过本次执行）；
     *         false 表示成功进入，调用方必须在结束（含异常路径尽量）时调用 exit。
     */
    public boolean enter(java.util.UUID id) {
        if (id == null) {
            return false;
        }
        java.util.Map<java.util.UUID, Entry> map = inFlight.get();
        long now = System.currentTimeMillis();
        Entry existing = map.get(id);
        if (existing != null) {
            if (now - existing.stamp < ttlMs) {
                return true;   // 仍在判定窗口内 → 真正的重入
            }
            // 残留已过期（上次判定异常退出）：刷新时间戳并放行
            existing.stamp = now;
            return false;
        }
        map.put(id, new Entry());
        return false;
    }

    /** 退出某实体的判定。只移除本线程内的该 id。 */
    public void exit(java.util.UUID id) {
        if (id == null) {
            return;
        }
        java.util.Map<java.util.UUID, Entry> map = inFlight.get();
        map.remove(id);
        if (map.isEmpty()) {
            inFlight.remove();
        }
    }
}
