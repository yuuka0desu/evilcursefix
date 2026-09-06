package com.example.ezfix.mixin;

import net.minecraftforge.eventbus.EventBus;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.IEventListener;
import org.apache.logging.log4j.LogManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 核心修复：拦截 EventBus.handleException 的日志路径故障。
 *
 * 错误报告中的致命崩溃链：
 *   ReportedException: Ticking entity
 *   Caused by: LinkageError: loader constraint violation ...
 *       org.apache.logging.log4j.util.MessageSupplier
 *   at EventBus.handleException(EventBus.java:329)
 *
 * 原因：监听器抛异常后，EventBus.handleException 调用
 * LOGGER.error(Marker, MessageSupplier) 记录日志；由于整合包存在 log4j
 * MessageSupplier 接口的双重类定义（类加载冲突），该日志调用抛出
 * LinkageError 并顶替原始异常传播回实体 tick 线程，导致服务器崩溃。
 *
 * 修复：完全接管 handleException，改用 log4j 的字符串重载
 * error(Marker, String, Object...)（走 ParameterizedMessage，不触碰
 * MessageSupplier 接口），并在外层 catch(Throwable) 兜底：
 * 无论日志系统发生什么故障，都只把信息写入 System.err，绝不抛回调用方。
 * 事件分发语义保持不变（原始监听器异常照常被记录/打印）。
 */
@Mixin(value = EventBus.class, remap = false)
public abstract class EventBusHandleExceptionMixin {

    /**
     * 本修复是模组的核心（防致命崩溃），require=1：eventbus 版本漂移导致
     * 应用失败时启动即报错，而不是静默失效后由 defaultRequire 兜底跳过。
     */
    @Inject(method = "handleException", at = @At("HEAD"), cancellable = true, require = 1, remap = false)
    private void ezfix$safeHandleException(IEventBus bus, Event event, IEventListener[] listeners, int index,
                                           Throwable throwable, CallbackInfo ci) {
        ci.cancel();
        try {
            LogManager.getLogger(EventBus.class).error(
                    "EventBus listener exception at index {} for event {}",
                    index, event, throwable);
        } catch (Throwable logFailure) {
            // 日志路径自身故障（log4j MessageSupplier 类加载冲突等）。
            // 绝不能把 Error 抛回实体 tick 线程导致游戏崩溃。
            System.err.println("[ezfix] Intercepted EventBus error-logging failure (log4j class-loading conflict):");
            logFailure.printStackTrace(System.err);
            System.err.println("[ezfix] Original listener exception:");
            throwable.printStackTrace(System.err);
        }
    }
}
