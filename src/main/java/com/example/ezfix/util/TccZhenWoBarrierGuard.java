package com.example.ezfix.util;

/**
 * TCC「真我」护盾判定的共享重入标记持有者。
 *
 * 为什么单独放一个类：
 * TccZhenWoStackGuardMixin（目标 ZhenWoGuard）与
 * TccZhenWoInternalGuardMixin（目标 ZhenWo）是两个不同的 mixin，会被合并进
 * 两个不同的目标类。Mixin 的 @Unique 字段只存在于各自的目标类里，两者之间
 * 无法互相访问——因此必须把共享状态放在 mixin 包之外的普通类中，由两个
 * mixin 共同引用同一个静态实例。
 *
 * 必须位于 mixin 配置包之外：Mixin 0.8.5 禁止 mixin 类直接引用配置包内的
 * 非 mixin 类（IllegalClassLoadError）。
 */
public final class TccZhenWoBarrierGuard {

    /**
     * 两级守卫共享的实例：
     *  - ZhenWoGuard.isInsideActiveBarrier（对外入口）
     *  - ZhenWo.isActiveBarrierWearer（对内实现，真正的递归发生点）
     * 同一条判定链上，任一级先进入即登记该实体 UUID；另一级再次要求判定
     * 同一实体时即判定为重入，从而切断 Capability 递归环。
     */
    public static final EzfixEntityReentryGuard INSTANCE =
            new EzfixEntityReentryGuard(EzfixEntityReentryGuard.DEFAULT_TTL_MS);

    private TccZhenWoBarrierGuard() {
    }
}
