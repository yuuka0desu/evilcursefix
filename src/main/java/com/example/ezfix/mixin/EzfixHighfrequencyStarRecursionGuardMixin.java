package com.example.ezfix.mixin;

import com.example.ezfix.util.EzfixReentrySlot;
import com.yakumosakura.yakumoblade.specialeffects.starSE.Highfrequency;
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
 * 再次触发 doSlash() -> 无限递归。EzfixReentrySlot（嵌套深度 + TTL 自愈）
 * 重入保护：递归时取消执行，RETURN 处退出守卫；异常残留由 TTL 自愈。
 * 目标模组未安装时静默跳过。目标类通过 stub 提供编译期解析
 * （yakumoblade 为可选软依赖）。
 */
@Mixin(value = Highfrequency.class, remap = false)
public abstract class EzfixHighfrequencyStarRecursionGuardMixin {

    @Unique
    private static final EzfixReentrySlot EZFIX$HF_STAR_SLASH_SLOT =
            new EzfixReentrySlot(EzfixReentrySlot.DEFAULT_TTL_MS);

    @Inject(
            method = "onDoingSlash(Lmods/flammpfeil/slashblade/event/SlashBladeEvent$DoSlashEvent;)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private static void ezfix$guardOnDoingSlash(CallbackInfo ci) {
        if (EZFIX$HF_STAR_SLASH_SLOT.enter()) {
            ci.cancel();
        }
    }

    @Inject(
            method = "onDoingSlash(Lmods/flammpfeil/slashblade/event/SlashBladeEvent$DoSlashEvent;)V",
            at = @At("RETURN"),
            remap = false
    )
    private static void ezfix$clearGuard(CallbackInfo ci) {
        EZFIX$HF_STAR_SLASH_SLOT.exit();
    }
}
