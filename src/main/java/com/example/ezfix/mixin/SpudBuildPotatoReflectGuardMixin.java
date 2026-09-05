package com.example.ezfix.mixin;

import net.minecraftforge.event.entity.living.LivingHurtEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 修复土豆盛宴建造土豆反伤的无限递归风险。
 *
 * 审计发现：OnLivingHurt 的反伤伤害类型 f_268448_ 不在方法自身的排除集
 * （f_268464_）内——反弹伤害再次进入处理器且攻击者仍穿戴该物品时
 * 同线程无限递归（反伤调用位于其 lambda 内，但 lambda 同步执行于
 * 处理器方法体，HEAD+RETURN 标记仍能覆盖）。
 *
 * 修复：HEAD 线程级重入标记 + RETURN 清除，重入调用直接取消。
 */
@Mixin(targets = "com.dinzeer.spudiversity.item.curios.BuildPotato", remap = false)
public abstract class SpudBuildPotatoReflectGuardMixin {

    private static final ThreadLocal<Boolean> EZFIX$BP_HURT_IN_FLIGHT = ThreadLocal.withInitial(() -> Boolean.FALSE);

    @Inject(method = "OnLivingHurt", at = @At("HEAD"), cancellable = true, remap = false)
    private static void ezfix$skipReentrant(LivingHurtEvent event, CallbackInfo ci) {
        if (EZFIX$BP_HURT_IN_FLIGHT.get()) {
            ci.cancel();
            return;
        }
        EZFIX$BP_HURT_IN_FLIGHT.set(Boolean.TRUE);
    }

    @Inject(method = "OnLivingHurt", at = @At("RETURN"), remap = false)
    private static void ezfix$clearFlag(LivingHurtEvent event, CallbackInfo ci) {
        EZFIX$BP_HURT_IN_FLIGHT.set(Boolean.FALSE);
    }
}
