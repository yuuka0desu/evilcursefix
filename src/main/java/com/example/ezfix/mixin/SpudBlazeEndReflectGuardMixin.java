package com.example.ezfix.mixin;

import net.minecraftforge.event.entity.living.LivingHurtEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 修复土豆盛宴 BlazeEnd 效果反伤的潜在无限递归。
 *
 * 审计发现：onLivingHurt 在攻击者带 BlazeEnd 效果时反弹 30% 生物攻击伤害
 * 给受害者。反弹伤害（mob_attack，攻击者不变）再次满足全部条件，仅靠
 * 无敌时间（invulnerableTime）终止递归；与清无敌时间的伤害链
 * （如 FastMakeEntityUtil.FastHurt 会清零 invulnerableTime）组合时
 * 可能形成无限循环。
 *
 * 修复：HEAD 线程级重入标记 + RETURN 清除，重入调用直接取消。
 */
@Mixin(targets = "com.dinzeer.spudiversity.effect.BlazeEnd", remap = false)
public abstract class SpudBlazeEndReflectGuardMixin {

    private static final ThreadLocal<Boolean> EZFIX$BE_HURT_IN_FLIGHT = ThreadLocal.withInitial(() -> Boolean.FALSE);

    @Inject(method = "onLivingHurt", at = @At("HEAD"), cancellable = true, remap = false)
    private static void ezfix$skipReentrant(LivingHurtEvent event, CallbackInfo ci) {
        if (EZFIX$BE_HURT_IN_FLIGHT.get()) {
            ci.cancel();
            return;
        }
        EZFIX$BE_HURT_IN_FLIGHT.set(Boolean.TRUE);
    }

    @Inject(method = "onLivingHurt", at = @At("RETURN"), remap = false)
    private static void ezfix$clearFlag(LivingHurtEvent event, CallbackInfo ci) {
        EZFIX$BE_HURT_IN_FLIGHT.set(Boolean.FALSE);
    }
}
