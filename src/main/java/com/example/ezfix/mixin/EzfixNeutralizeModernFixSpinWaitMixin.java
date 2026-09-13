package com.example.ezfix.mixin;

import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.locks.LockSupport;

/**
 * 中和 ModernFix 的 perf.fix_loop_spin_waiting 在集成服务器上造成的"退出/暂停挂起"。
 *
 * 背景（F 实例日志实证，2026-09-13）：
 * 玩家在暂停菜单点击"保存并退出"后，服务器线程永久停在
 *   runServer -> waitUntilNextTick -> managedBlock -> waitForTasks(m_5667_) -> parkNanos
 * tick 冻结、stopServer 从未被调用，客户端 clearLevel 等服务器停止的循环永不结束
 * ——整个退出流程永久挂起（画面正常但世界死亡）。
 *
 * 原因：ModernFix priority=500 的 fix_loop_spin_waiting.MinecraftServerMixin 覆盖了
 * MinecraftServer.m_5667_（waitForTasks，继承自 BlockableEventLoop），把等待改写为
 *   parkNanos("waiting for tasks", nextTickTime * 1e6 - now)
 * 即"睡到下一 tick 目标时刻"。一旦 nextTickTime 因暂停/退出状态不再推进，等待即永久化；
 * 原版此路径为有限等待 + 轮询，不存在该问题。
 *
 * 修复：以更高优先级（1500，后应用）在同一方法 HEAD 处 cancel（跳过 ModernFix 的实现），
 * 并由本处理器执行"有界等待"——1ms 的 parkNanos。该实现：
 *  - 数学上不可能永睡（每次唤醒后由 managedBlock 循环重新检查条件）；
 *  - 唤醒频率（1000/s）远高于服务器 tick 周期（20/s），对调度精度无实际影响，
 *    CPU 开销与原版此路径同量级；
 *  - 仅当 ModernFix 存在时由 EzfixMixinPlugin 条件应用；ModernFix 缺席时
 *    require=0 自然跳过（原版继承实现保持使用）。
 *
 * ModernFix 的其它优化不受影响（仅该等待路径被替换）。
 */
@Mixin(value = MinecraftServer.class, priority = 1500, remap = false)
public abstract class EzfixNeutralizeModernFixSpinWaitMixin {

    @Inject(method = "m_5667_", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void ezfix$boundedWaitReplacesModernFixPark(CallbackInfo ci) {
        // 跳过 ModernFix 的"睡到 nextTickTime"实现
        ci.cancel();
        // 有界等待：被唤醒/超时后由 managedBlock 循环重新检查条件，不可能永久挂起
        LockSupport.parkNanos(1_000_000L);
    }
}
