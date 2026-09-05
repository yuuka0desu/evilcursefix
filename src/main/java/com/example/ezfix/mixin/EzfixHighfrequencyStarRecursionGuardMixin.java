package com.example.ezfix.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 合并自 anti_overflow：幻想之刃（1.3.1 实例 starSE 包）Highfrequency
 * onDoingSlash 的递归保护。
 *
 * 斩击触发 DoSlashEvent -> Highfrequency.onDoingSlash 监听 -> 回调中
 * 再次触发 doSlash() -> 无限递归。ThreadLocal 重入保护，递归时取消执行，
 * RETURN 处清理标记。目标模组未安装时静默跳过。
 */
@Mixin(targets = "com.yakumosakura.yakumoblade.specialeffects.starSE.Highfrequency", remap = false)
public abstract class EzfixHighfrequencyStarRecursionGuardMixin {

    @Unique
    private static final ThreadLocal<Boolean> EZFIX$HF_STAR_SLASH_GUARD =
            ThreadLocal.withInitial(() -> Boolean.FALSE);

    @Inject(
            method = "onDoingSlash(Lmods/flammpfeil/slashblade/event/SlashBladeEvent$DoSlashEvent;)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private static void ezfix$guardOnDoingSlash(CallbackInfo ci) {
        if (Boolean.TRUE.equals(EZFIX$HF_STAR_SLASH_GUARD.get())) {
            ci.cancel();
            return;
        }
        EZFIX$HF_STAR_SLASH_GUARD.set(Boolean.TRUE);
    }

    @Inject(
            method = "onDoingSlash(Lmods/flammpfeil/slashblade/event/SlashBladeEvent$DoSlashEvent;)V",
            at = @At("RETURN"),
            remap = false
    )
    private static void ezfix$clearGuard(CallbackInfo ci) {
        EZFIX$HF_STAR_SLASH_GUARD.remove();
    }
}
