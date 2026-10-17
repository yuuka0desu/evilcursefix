package com.example.ezfix.mixin;

import com.shiftthedev.pickablepets.utils.CachedPets;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.io.IOException;
import java.nio.file.Path;

/**
 * 修复 PickablePets 保存 pets.dat 时文件被占用导致的崩溃。
 *
 * 崩溃现象：小退 / 切换世界 / 服务器 tick 期间抛出 IOException
 * （Windows 下典型的 "文件正被另一进程使用"），异常逃逸到 ServerLevel tick
 * 链导致崩溃或卡顿。
 *
 * 原因：PickablePets 在 ServerLevel 的 tick lambda 中直接调用
 * CachedPets.save(path)，未捕获 IOException。Windows 的文件锁在
 * 杀毒软件扫描、备份工具、上一次写入尚未刷盘等情况下会短暂占用文件。
 *
 * 修复：把该 save 调用替换为「最多重试 8 次、每次间隔递增」的包装：
 *  - 每次失败前短暂等待（10ms → 80ms），给持有者释放文件的时间；
 *  - 全部重试仍失败时记录一次警告并跳过本次保存（数据仍保留在内存中，
 *    下一次 tick 会再次尝试），绝不让 IOException 逃逸到 tick 链。
 *
 * 兼容性：require = 0；目标模组未安装或版本变化时静默跳过。
 */
@Mixin(targets = "com.shiftthedev.pickablepets.PickablePets", remap = false)
public abstract class PickablePetsSaveGuardMixin {

    @Unique
    private static final int EZFIX$MAX_RETRIES = 8;

    @Unique
    private static volatile boolean EZFIX$SAVE_FAIL_WARNED = false;

    @Redirect(
            method = "lambda$init$1(Lnet/minecraft/server/level/ServerLevel;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/shiftthedev/pickablepets/utils/CachedPets;save(Ljava/nio/file/Path;)V",
                    remap = false
            ),
            require = 0,
            remap = false
    )
    private static void ezfix$saveWithRetry(Path path) {
        IOException last = null;
        for (int attempt = 1; attempt <= EZFIX$MAX_RETRIES; attempt++) {
            try {
                CachedPets.save(path);
                return;
            } catch (Throwable t) {
                IOException io = ezfix$findIoError(t);
                if (io == null) {
                    // 非 IO 异常：保持原语义向上抛出
                    throw t;
                }
                last = io;
                try {
                    Thread.sleep(10L * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        if (!EZFIX$SAVE_FAIL_WARNED) {
            EZFIX$SAVE_FAIL_WARNED = true;
            System.err.println("[ezfix] PickablePets pets.dat save failed after "
                    + EZFIX$MAX_RETRIES + " attempts; skipping this save (will retry next tick): " + path);
            if (last != null) {
                last.printStackTrace(System.err);
            }
        }
    }

    /** 在异常链中查找 IOException（含被包装的情形） */
    @Unique
    private static IOException ezfix$findIoError(Throwable t) {
        Throwable cur = t;
        int guard = 0;
        while (cur != null && guard++ < 16) {
            if (cur instanceof IOException io) {
                return io;
            }
            cur = cur.getCause();
        }
        return null;
    }
}
