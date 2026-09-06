package com.example.ezfix.mixin;

import com.example.ezfix.util.EzfixReentrySlot;
import mods.flammpfeil.slashblade.util.AttackManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 合并自 anti_overflow：拔刀剑 AttackManager.doSlash 的递归保护。
 *
 * doSlash() 在创建斩击效果前发布 DoSlashEvent；若监听器回调中再次触发
 * doSlash() 则无限递归。此处用 EzfixReentrySlot（嵌套深度 + TTL 自愈）
 * 重入保护：递归时返回 null；正常返回与提前取消均在 RETURN 处退出守卫；
 * 异常残留由 TTL 自动自愈（避免"斩击永久返回 null"）。
 *
 * 目标类直接引用：拔刀剑为编译期依赖（compileOnly），
 * 未安装时运行时由 EzfixMixinPlugin 条件 + Mixin 服务静默跳过。
 */
@Mixin(value = AttackManager.class, remap = false)
public abstract class EzfixSlashRecursionGuardMixin {

    @Unique
    private static final EzfixReentrySlot EZFIX$SLASH_SLOT =
            new EzfixReentrySlot(EzfixReentrySlot.DEFAULT_TTL_MS);

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
        if (EZFIX$SLASH_SLOT.enter()) {
            cir.setReturnValue(null);
        }
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
        EZFIX$SLASH_SLOT.exit();
    }
}
