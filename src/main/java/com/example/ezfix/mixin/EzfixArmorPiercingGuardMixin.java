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
 * 目标类使用字符串 targets（而非 value=Class）是有意为之：
 * time_slow 是可选软依赖，GuardianMod jar 不在仓库中（build.gradle 条件
 * 编译），用字符串引用避免编译期依赖，缺失时运行时静默跳过；
 * 该 mixin 因此保留一条"should be specified in value"编译警告。
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
