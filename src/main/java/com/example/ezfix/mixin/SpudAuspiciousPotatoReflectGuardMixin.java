package com.example.ezfix.mixin;

import net.minecraftforge.event.entity.living.LivingHurtEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 修复土豆盛宴祥瑞土豆反伤的无限递归风险。
 *
 * 审计发现：BloodPotatoOnHurt 有三个反伤分支，其中 f_268515_ / f_286979_
 * 两个反伤伤害类型不在方法自身的排除集（f_268464_/f_268631_）内——
 * 反弹伤害再次进入处理器且攻击者条件仍满足时同线程无限递归。
 *
 * 修复：HEAD 线程级重入标记 + RETURN 清除，重入调用直接取消。
 */
@Mixin(targets = "com.dinzeer.spudiversity.item.compat.godfox.AuspiciousPotato", remap = false)
public abstract class SpudAuspiciousPotatoReflectGuardMixin {

    private static final ThreadLocal<Boolean> EZFIX$AP_HURT_IN_FLIGHT = ThreadLocal.withInitial(() -> Boolean.FALSE);

    @Inject(method = "BloodPotatoOnHurt", at = @At("HEAD"), cancellable = true, remap = false)
    private static void ezfix$skipReentrant(LivingHurtEvent event, CallbackInfo ci) {
        if (EZFIX$AP_HURT_IN_FLIGHT.get()) {
            ci.cancel();
            return;
        }
        EZFIX$AP_HURT_IN_FLIGHT.set(Boolean.TRUE);
    }

    @Inject(method = "BloodPotatoOnHurt", at = @At("RETURN"), remap = false)
    private static void ezfix$clearFlag(LivingHurtEvent event, CallbackInfo ci) {
        EZFIX$AP_HURT_IN_FLIGHT.set(Boolean.FALSE);
    }
}
