package com.example.ezfix.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 合并自 anti_overflow：拔刀剑 AttackManager.doSlash 的递归保护。
 *
 * doSlash() 在创建斩击效果前发布 DoSlashEvent；若监听器回调中再次触发
 * doSlash() 则无限递归。此处用 ThreadLocal 重入保护，递归时返回 null，
 * 无论正常返回还是提前取消均在 RETURN 处清理标记。
 *
 * 目标类通过字符串指定：拔刀剑未安装时静默跳过。
 */
@Mixin(targets = "mods.flammpfeil.slashblade.util.AttackManager", remap = false)
public abstract class EzfixSlashRecursionGuardMixin {

    @Unique
    private static final ThreadLocal<Boolean> EZFIX$SLASH_GUARD =
            ThreadLocal.withInitial(() -> Boolean.FALSE);

    @Inject(
            method = "doSlash(Lnet/minecraft/world/entity/LivingEntity;FI"
                    + "Lnet/minecraft/world/phys/Vec3;ZZD"
                    + "Lmods/flammpfeil/slashblade/util/KnockBacks;)"
                    + "Lmods/flammpfeil/slashblade/entity/EntitySlashEffect;",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private static void ezfix$guardDoSlash(CallbackInfoReturnable<Object> cir) {
        if (Boolean.TRUE.equals(EZFIX$SLASH_GUARD.get())) {
            cir.setReturnValue(null);
            return;
        }
        EZFIX$SLASH_GUARD.set(Boolean.TRUE);
    }

    @Inject(
            method = "doSlash(Lnet/minecraft/world/entity/LivingEntity;FI"
                    + "Lnet/minecraft/world/phys/Vec3;ZZD"
                    + "Lmods/flammpfeil/slashblade/util/KnockBacks;)"
                    + "Lmods/flammpfeil/slashblade/entity/EntitySlashEffect;",
            at = @At("RETURN"),
            remap = false
    )
    private static void ezfix$clearGuard(CallbackInfoReturnable<Object> cir) {
        EZFIX$SLASH_GUARD.remove();
    }
}
