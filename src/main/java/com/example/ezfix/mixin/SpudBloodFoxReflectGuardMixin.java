package com.example.ezfix.mixin;

import net.minecraftforge.event.entity.living.LivingHurtEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 修复土豆盛宴血狐土豆反伤的无限递归风险。
 *
 * 审计发现：BloodPotatoOnHurt 在攻击者穿戴血狐土豆时反弹 f_268515_ 类伤害，
 * 而该伤害类型不在方法自身的排除集（f_268566_/f_268464_）内——反弹伤害
 * 再次进入处理器，攻击者条件仍满足，同线程无限递归（与 InvisibleWaiting
 * 同类问题，依赖无敌时间不总能兜底，尤其与清无敌时间的伤害链组合时）。
 *
 * 修复：HEAD 设置线程级重入标记，重入调用直接取消；RETURN（所有返回路径）
 * 清除标记。未发生重入时行为与原来完全一致。
 */
@Mixin(targets = "com.dinzeer.spudiversity.event.compat.godfox.BloodFoxEvent", remap = false)
public abstract class SpudBloodFoxReflectGuardMixin {

    private static final ThreadLocal<Boolean> EZFIX$BF_HURT_IN_FLIGHT = ThreadLocal.withInitial(() -> Boolean.FALSE);

    @Inject(method = "BloodPotatoOnHurt", at = @At("HEAD"), cancellable = true, remap = false)
    private static void ezfix$skipReentrant(LivingHurtEvent event, CallbackInfo ci) {
        if (EZFIX$BF_HURT_IN_FLIGHT.get()) {
            ci.cancel();
            return;
        }
        EZFIX$BF_HURT_IN_FLIGHT.set(Boolean.TRUE);
    }

    @Inject(method = "BloodPotatoOnHurt", at = @At("RETURN"), remap = false)
    private static void ezfix$clearFlag(LivingHurtEvent event, CallbackInfo ci) {
        EZFIX$BF_HURT_IN_FLIGHT.set(Boolean.FALSE);
    }
}
