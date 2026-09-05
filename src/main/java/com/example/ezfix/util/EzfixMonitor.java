package com.example.ezfix.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;

/**
 * 独立于主线程的游戏状态看门狗。
 *
 * 功能：
 * 1. 周期性（60 秒）向日志输出简要状态摘要：内存占用、服务器 tick 数、
 *    渲染帧数、近似 TPS/FPS。
 * 2. 卡死检测：服务器 tick 或渲染帧心跳停止超过 30 秒，判定疑似卡死，
 *    自动向日志打印**全部线程堆栈快照**（60 秒冷却防刷屏），
 *    用于定位"游戏未响应"类问题的根因。
 *
 * 输出走 log4j（确保写入 latest.log）并同时回显 System.err（写入控制台
 * 与部分启动器的日志捕获），双保险。
 *
 * 心跳由 Mixin 在 MinecraftServer.m_5703_（服务器 tick 循环）与
 * Minecraft.m_91383_（客户端渲染循环）末尾更新；服务器停止（m_7041_/
 * m_7570_）时清零，避免退出世界后误报。
 *
 * 本线程为 daemon，只读写 volatile 字段并输出日志，不调用任何 MC API，
 * 不持锁，保证绝不干扰主线程。
 */
public final class EzfixMonitor {

    private static final Logger LOGGER = LogManager.getLogger("ezfix-monitor");

    public static volatile long serverHeartbeatMillis;
    public static volatile long renderHeartbeatMillis;
    public static volatile long serverTicks;
    public static volatile long renderFrames;

    private static final long CHECK_INTERVAL_MS = 5_000L;
    private static final long SUMMARY_INTERVAL_MS = 60_000L;
    private static final long STALL_THRESHOLD_MS = 30_000L;
    private static final long STACK_DUMP_COOLDOWN_MS = 60_000L;

    private EzfixMonitor() {
    }

    public static void start() {
        Thread monitor = new Thread(EzfixMonitor::run, "ezfix-monitor");
        monitor.setDaemon(true);
        monitor.setPriority(Thread.MIN_PRIORITY);
        monitor.start();
    }

    private static void run() {
        long lastSummary = System.currentTimeMillis();
        long lastServerStallWarn = 0L;
        long lastRenderStallWarn = 0L;
        long prevServerTicks = serverTicks;
        long prevRenderFrames = renderFrames;
        long prevSampleTime = lastSummary;

        LOGGER.warn("started. stall threshold: {} ms, summary interval: {} ms",
                STALL_THRESHOLD_MS, SUMMARY_INTERVAL_MS);
        while (true) {
            try {
                Thread.sleep(CHECK_INTERVAL_MS);
            } catch (InterruptedException e) {
                return;
            }
            long now = System.currentTimeMillis();
            boolean serverAlive = serverHeartbeatMillis != 0L;
            boolean renderAlive = renderHeartbeatMillis != 0L;

            // ---- 卡死检测：打印全线程堆栈 ----
            if (serverAlive && now - serverHeartbeatMillis > STALL_THRESHOLD_MS
                    && now - lastServerStallWarn > STACK_DUMP_COOLDOWN_MS) {
                lastServerStallWarn = now;
                LOGGER.error("WARNING: server tick has NOT progressed for {} ms (tick={}). Dumping all thread stacks:\n{}",
                        now - serverHeartbeatMillis, serverTicks, threadStacks());
            }
            if (renderAlive && now - renderHeartbeatMillis > STALL_THRESHOLD_MS
                    && now - lastRenderStallWarn > STACK_DUMP_COOLDOWN_MS) {
                lastRenderStallWarn = now;
                LOGGER.error("WARNING: render thread has NOT progressed for {} ms (frame={}). Dumping all thread stacks:\n{}",
                        now - renderHeartbeatMillis, renderFrames, threadStacks());
            }

            // ---- 定期状态摘要 ----
            if (now - lastSummary >= SUMMARY_INTERVAL_MS) {
                long elapsedSec = Math.max(1, (now - prevSampleTime) / 1000L);
                double tps = (serverAlive ? (serverTicks - prevServerTicks) : 0) / (double) elapsedSec;
                double fps = (renderAlive ? (renderFrames - prevRenderFrames) : 0) / (double) elapsedSec;
                Runtime rt = Runtime.getRuntime();
                long usedMb = (rt.totalMemory() - rt.freeMemory()) / 1048576L;
                long maxMb = rt.maxMemory() / 1048576L;
                LOGGER.warn("status: mem={}MB/{}MB server_ticks={} render_frames={} tps={} fps={} server_alive={} render_alive={}",
                        usedMb, maxMb, serverTicks, renderFrames,
                        String.format("%.1f", tps), String.format("%.1f", fps),
                        serverAlive, renderAlive);
                prevServerTicks = serverTicks;
                prevRenderFrames = renderFrames;
                prevSampleTime = now;
                lastSummary = now;
            }
        }
    }

    private static String threadStacks() {
        StringBuilder sb = new StringBuilder(16384);
        sb.append("=== thread dump begin ===\n");
        Map<Thread, StackTraceElement[]> all = Thread.getAllStackTraces();
        all.entrySet().stream()
                .sorted((a, b) -> {
                    int byState = a.getKey().getState().compareTo(b.getKey().getState());
                    return byState != 0 ? byState : a.getKey().getName().compareTo(b.getKey().getName());
                })
                .forEach(entry -> {
                    Thread t = entry.getKey();
                    sb.append("  \"").append(t.getName()).append("\" ")
                            .append(t.isDaemon() ? "daemon " : "")
                            .append(t.getState()).append("\n");
                    StackTraceElement[] frames = entry.getValue();
                    int limit = Math.min(frames.length, 25);
                    for (int i = 0; i < limit; i++) {
                        sb.append("      at ").append(frames[i]).append("\n");
                    }
                    if (frames.length > limit) {
                        sb.append("      ... ").append(frames.length - limit).append(" more\n");
                    }
                });
        sb.append("=== thread dump end ===");
        return sb.toString();
    }
}
