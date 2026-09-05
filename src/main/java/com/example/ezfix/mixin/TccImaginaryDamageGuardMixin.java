package com.example.ezfix.mixin;

import com.example.ezfix.util.EzfixRecursionGuard;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 修复 Tacz饰品（tcc）虚化伤害的无限递归（StackOverflowError）。
 *
 * 崩溃栈中的循环：
 *   MetaMorph.onLivingHurt -> TccAttributeEvents.applyImaginaryDamage
 *   -> entity.hurt(imaginarySource) -> LivingHurtEvent -> MetaMorph.onLivingHurt -> ...
 *
 * 原因：applyImaginaryDamage 在 LivingHurtEvent 处理中再次对同一实体造成
 * 伤害，重新触发 LivingHurtEvent；原代码虽检查 IMAGINARY_DAMAGE_TAG，
 * 但部分伤害链（例如实体重写 m_6469_ 后源被替换）未命中该标签，
 * 同线程无限递归直至栈溢出。
 *
 * 修复：applyImaginaryDamage 的 hurt 调用期间设置线程级重入标记；
 * 重入的 applyImaginaryDamage 直接返回 false、重入的 MetaMorph.onLivingHurt
 * 直接返回，打断循环。
 */
@Mixin(value = com.xlxyvergil.tcc.event.TccAttributeEvents.class, remap = false)
public abstract class TccImaginaryDamageGuardMixin {

    @Inject(method = "applyImaginaryDamage", at = @At("HEAD"), cancellable = true, remap = false)
    private static void ezfix$skipReentrantApply(LivingEntity entity, DamageSource source, float amount,
                                                 CallbackInfoReturnable<Boolean> cir) {
        if (EzfixRecursionGuard.isImaginaryDamageInFlight()) {
            cir.setReturnValue(false);
        }
    }

    @Redirect(
            method = "applyImaginaryDamage",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;m_6469_(Lnet/minecraft/world/damagesource/DamageSource;F)Z",
                    remap = false
            ),
            remap = false
    )
    private static boolean ezfix$guardedHurt(LivingEntity victim, DamageSource source, float amount) {
        if (EzfixRecursionGuard.isImaginaryDamageInFlight()) {
            return false;
        }
        EzfixRecursionGuard.beginImaginaryDamage();
        try {
            return victim.hurt(source, amount);
        } finally {
            EzfixRecursionGuard.endImaginaryDamage();
        }
    }
}
