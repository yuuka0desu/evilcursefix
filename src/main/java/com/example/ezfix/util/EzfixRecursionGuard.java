package com.example.ezfix.util;

/**
 * 共享的线程级重入标记，用于打断“伤害事件内再次造成伤害”的递归环。
 *
 * 注意：本类必须放在 mixin 包（com.example.ezfix.mixin）之外。
 * Mixin 0.8.5 禁止 mixin 配置包内的非 mixin 类被 mixin 类直接引用
 * （IllegalClassLoadError），故辅助类置于 com.example.ezfix.util。
 */
public final class EzfixRecursionGuard {

    private static final ThreadLocal<Boolean> IMAGINARY_DAMAGE = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private EzfixRecursionGuard() {
    }

    public static boolean isImaginaryDamageInFlight() {
        return IMAGINARY_DAMAGE.get();
    }

    public static void beginImaginaryDamage() {
        IMAGINARY_DAMAGE.set(Boolean.TRUE);
    }

    public static void endImaginaryDamage() {
        IMAGINARY_DAMAGE.set(Boolean.FALSE);
    }
}
