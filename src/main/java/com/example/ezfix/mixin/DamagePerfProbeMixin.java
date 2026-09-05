package com.example.ezfix.mixin;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.ForgeHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 伤害事件链性能诊断器。
 *
 * 玩家反馈"打出伤害时计算数值过多导致线程卡顿"——LivingHurtEvent 链上
 * 注册了大量监听器（神化词缀、tcc、土豆盛宴、长袍、l2damagetracker 等），
 * 单次伤害可能触发昂贵的属性/NBT/集合计算。
 *
 * 本诊断器在 ForgeHooks.onLivingHurt 入口/出口计时（支持重入栈），
 * 每 30 秒输出一次统计：事件次数、平均/最大耗时、超过 10ms 的慢事件数；
 * 单次超过 25ms 时打印当前线程栈（60 秒冷却），直接定位卡顿监听器。
 *
 * 纯计时与日志输出，不改变任何伤害语义。
 */
@Mixin(value = ForgeHooks.class, remap = false)
public abstract class DamagePerfProbeMixin {

    private static final ThreadLocal<Deque<Long>> EZFIX$HURT_TIMERS =
            ThreadLocal.withInitial(ArrayDeque::new);
    private static final long WINDOW_MS = 30_000L;
    private static final long SLOW_THRESHOLD_MS = 25L;
    private static final long REPORT_COOLDOWN_MS = 60_000L;

    private static final AtomicLong EZFIX$COUNT = new AtomicLong();
    private static final AtomicLong EZFIX$TOTAL_NS = new AtomicLong();
    private static final AtomicLong EZFIX$MAX_NS = new AtomicLong();
    private static final AtomicLong EZFIX$SLOW_COUNT = new AtomicLong();
    private static volatile long EZFIX$WINDOW_START = System.currentTimeMillis();
    private static volatile long EZFIX$LAST_SLOW_REPORT;

    @Inject(
            method = "onLivingHurt(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/damagesource/DamageSource;F)F",
            at = @At("HEAD"),
            remap = false
    )
    private static void ezfix$hurtStart(LivingEntity entity, DamageSource source, float amount,
                                        CallbackInfoReturnable<Float> cir) {
        EZFIX$HURT_TIMERS.get().push(System.nanoTime());
    }

    @Inject(
            method = "onLivingHurt(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/damagesource/DamageSource;F)F",
            at = @At("RETURN"),
            remap = false
    )
    private static void ezfix$hurtEnd(LivingEntity entity, DamageSource source, float amount,
                                      CallbackInfoReturnable<Float> cir) {
        Deque<Long> stack = EZFIX$HURT_TIMERS.get();
        if (stack.isEmpty()) {
            return;
        }
        long start = stack.pop();
        long elapsedNs = System.nanoTime() - start;
        long elapsedMs = elapsedNs / 1_000_000L;

        EZFIX$COUNT.incrementAndGet();
        EZFIX$TOTAL_NS.addAndGet(elapsedNs);
        long max;
        while ((max = EZFIX$MAX_NS.get()) < elapsedNs) {
            if (EZFIX$MAX_NS.compareAndSet(max, elapsedNs)) {
                break;
            }
        }
        if (elapsedMs >= 10L) {
            EZFIX$SLOW_COUNT.incrementAndGet();
        }
        if (elapsedMs >= SLOW_THRESHOLD_MS) {
            long now = System.currentTimeMillis();
            if (now - EZFIX$LAST_SLOW_REPORT > REPORT_COOLDOWN_MS) {
                EZFIX$LAST_SLOW_REPORT = now;
                System.err.println("[ezfix-perf] SLOW LivingHurtEvent: " + elapsedMs
                        + " ms. Thread stack of the hot path:");
                for (StackTraceElement frame : Thread.currentThread().getStackTrace()) {
                    System.err.println("      at " + frame);
                }
            }
        }

        long now = System.currentTimeMillis();
        if (now - EZFIX$WINDOW_START >= WINDOW_MS) {
            synchronized (DamagePerfProbeMixin.class) {
                if (now - EZFIX$WINDOW_START >= WINDOW_MS) {
                    long count = EZFIX$COUNT.getAndSet(0);
                    long total = EZFIX$TOTAL_NS.getAndSet(0);
                    long maxNs = EZFIX$MAX_NS.getAndSet(0);
                    long slow = EZFIX$SLOW_COUNT.getAndSet(0);
                    if (count > 0) {
                        System.err.println("[ezfix-perf] LivingHurtEvent stats (last "
                                + (WINDOW_MS / 1000) + "s): count=" + count
                                + " avg_ms=" + String.format("%.2f", total / 1_000_000.0 / count)
                                + " max_ms=" + (maxNs / 1_000_000L)
                                + " slow(>=10ms)=" + slow);
                    }
                    EZFIX$WINDOW_START = now;
                }
            }
        }
    }
}
