package com.example.ezfix.mixin;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 修复诡厄巫法 Nether 长袍火伤反射造成的无限递归（StackOverflowError）。
 *
 * 崩溃栈中的循环：
 *   Warden.hurt -> LivingAttackEvent -> RobeEvents.AttackEvent 反射
 *   magicFireBreath 伤害 -> 又触发 LivingAttackEvent（火伤）-> 再次反射 -> ...
 *
 * 原因：AttackEvent 对“火类伤害”无差别反射，反射产生的新火伤再次满足
 * 反射条件，同线程内无限递归直至栈溢出（崩溃报告中 130+ 层）。
 *
 * 修复：反射调用（entity.hurt）期间设置线程级重入标记；重入的
 * AttackEvent 直接返回，不再次反射，打破循环。
 */
@Mixin(value = com.Polarice3.Goety.common.events.RobeEvents.class, remap = false)
public abstract class GoetyRobeReflectGuardMixin {

    private static final ThreadLocal<Boolean> EZFIX$REFLECTING = ThreadLocal.withInitial(() -> Boolean.FALSE);

    @Inject(method = "AttackEvent", at = @At("HEAD"), cancellable = true, remap = false)
    private static void ezfix$skipReentrantReflection(LivingAttackEvent event, CallbackInfo ci) {
        if (EZFIX$REFLECTING.get()) {
            ci.cancel();
        }
    }

    @Redirect(
            method = "AttackEvent",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;m_6469_(Lnet/minecraft/world/damagesource/DamageSource;F)Z",
                    remap = false
            ),
            remap = false
    )
    private static boolean ezfix$guardedReflect(net.minecraft.world.entity.LivingEntity victim,
                                                DamageSource source, float amount) {
        if (EZFIX$REFLECTING.get()) {
            return false;
        }
        EZFIX$REFLECTING.set(Boolean.TRUE);
        try {
            return victim.hurt(source, amount);
        } finally {
            EZFIX$REFLECTING.set(Boolean.FALSE);
        }
    }
}
