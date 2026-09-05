package com.example.ezfix.mixin;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 合并自 anti_overflow：time_slow（GuardianMod）ArmorPiercingHandler
 * 二次 hurt 调用的重入保护。
 *
 * 目标类通过字符串指定（time_slow 为可选软依赖，缺失时静默跳过）；
 * 守卫包裹处理器内部的 m_6469_ 二次伤害调用，嵌套事件只跳过
 * ArmorPiercingHandler 自身，底层 hurt 不受影响。
 */
@Mixin(targets = "net.wzz.time_slow.event.ArmorPiercingHandler", remap = false)
public abstract class EzfixArmorPiercingGuardMixin {

    @Unique
    private static final ThreadLocal<Boolean> EZFIX$ARMOR_PIERCING_GUARD =
            ThreadLocal.withInitial(() -> Boolean.FALSE);

    @Inject(
            method = "onLivingHurt(Lnet/minecraftforge/event/entity/living/LivingHurtEvent;)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private static void ezfix$skipNestedHandler(LivingHurtEvent event, CallbackInfo ci) {
        if (Boolean.TRUE.equals(EZFIX$ARMOR_PIERCING_GUARD.get())) {
            ci.cancel();
        }
    }

    @Redirect(
            method = "onLivingHurt(Lnet/minecraftforge/event/entity/living/LivingHurtEvent;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;m_6469_(Lnet/minecraft/world/damagesource/DamageSource;F)Z",
                    remap = false
            ),
            require = 0,
            remap = false
    )
    private static boolean ezfix$guardNestedHurt(LivingEntity target, DamageSource source, float amount) {
        EZFIX$ARMOR_PIERCING_GUARD.set(Boolean.TRUE);
        try {
            return target.hurt(source, amount);
        } finally {
            EZFIX$ARMOR_PIERCING_GUARD.remove();
        }
    }
}
