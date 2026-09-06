package com.example.ezfix.util;

/**
 * 线程级重入守卫槽位（嵌套深度 + TTL 自愈）。
 *
 * 用于替代旧的 ThreadLocal&lt;Boolean&gt; 标记，解决两个缺陷：
 * 1. 重入调用被取消后其 RETURN 注入仍会执行，Boolean 方案会把外层仍在
 *    执行中的标记误清除，破坏嵌套保护语义；
 * 2. 目标方法中途抛异常时 RETURN 注入不执行，Boolean 标记永久残留，
 *    导致该线程后续所有同类调用被静默取消（服务端主线程尤其严重）。
 *
 * 本实现：
 * - enter() 维护嵌套深度：首次进入置 entered=true；深度>0 且未过期时
 *   判定为重入（返回 true，调用方应跳过本次执行）；
 * - 被取消的重入调用在 RETURN 处调用 exit() 时因 entered=false 不会
 *   减少外层的深度；
 * - 异常残留时深度保持 >0，超过 TTL 后下一次 enter() 自动重置自愈，
 *   把"永久失效"降级为"短暂过度防护"。
 *
 * 注意：本类位于 mixin 包之外（com.example.ezfix.util），
 * Mixin 0.8.5 禁止 mixin 配置包内的非 mixin 类被 mixin 类直接引用。
 */
public final class EzfixReentrySlot {

    private static final class State {
        long stamp;
        int depth;
        boolean entered;
    }

    /** 默认自愈窗口：正常伤害/斩击嵌套链远小于该值 */
    public static final long DEFAULT_TTL_MS = 5_000L;

    private final ThreadLocal<State> state = ThreadLocal.withInitial(State::new);
    private final long ttlMs;

    public EzfixReentrySlot(long ttlMs) {
        this.ttlMs = ttlMs;
    }

    /**
     * 进入守卫。
     *
     * @return true 表示重入（已有未过期的外层执行），调用方应跳过本次执行；
     *         false 表示成功进入（本次是外层），调用方正常执行。
     */
    public boolean enter() {
        State s = state.get();
        long now = System.currentTimeMillis();
        if (s.depth > 0) {
            if (now - s.stamp < ttlMs) {
                s.entered = false;
                return true;
            }
            // 残留标记已过期：自愈重置，本次按首次进入处理
            s.depth = 0;
            s.stamp = 0;
            s.entered = false;
        }
        s.depth = 1;
        s.stamp = now;
        s.entered = true;
        return false;
    }

    /**
     * 退出守卫。只对"本次成功进入"的调用生效；
     * 被跳过（enter 返回 true）的调用退出时不会减少外层的深度。
     */
    public void exit() {
        State s = state.get();
        if (!s.entered) {
            return;
        }
        s.entered = false;
        s.depth--;
        if (s.depth == 0) {
            s.stamp = 0;
        }
    }
}
