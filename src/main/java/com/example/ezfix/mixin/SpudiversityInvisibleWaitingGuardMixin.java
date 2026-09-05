package com.example.ezfix.mixin;

import net.minecraftforge.event.entity.living.LivingHurtEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 修复土豆盛宴 InvisibleWaiting 效果的反伤自循环（StackOverflowError）。
 *
 * 崩溃栈循环（1.3.1 实例）：
 *   TargetDummyEntity.hurt -> LivingHurtEvent -> InvisibleWaiting.LivingHurt
 *   -> FastMakeEntityUtil.FastHurt(受害者, 15% 火伤) -> 受害者再次受伤
 *   -> LivingHurtEvent -> ...（FastHurt 生成的伤害源未被原代码的火伤检查拦截）
 *
 * 与 tcc 虚化伤害守卫配合：InvisibleWaiting 与 MetaMorph 两个反伤链交替嵌套，
 * 本守卫负责打断 InvisibleWaiting 链（tcc 守卫负责 MetaMorph 链）。
 *
 * 修复：HEAD 设置线程级重入标记，重入调用直接取消；RETURN（所有返回路径）
 * 清除标记。采用 HEAD+RETURN 组合，兼容 1.0.x/1.3.1 各版本字节码。
 */
@Mixin(targets = "com.dinzeer.spudiversity.effect.InvisibleWaiting", remap = false)
public abstract class SpudiversityInvisibleWaitingGuardMixin {

    private static final ThreadLocal<Boolean> EZFIX$IW_HURT_IN_FLIGHT = ThreadLocal.withInitial(() -> Boolean.FALSE);

    @Inject(method = "LivingHurt", at = @At("HEAD"), cancellable = true, remap = false)
    private static void ezfix$skipReentrantLivingHurt(LivingHurtEvent event, CallbackInfo ci) {
        if (EZFIX$IW_HURT_IN_FLIGHT.get()) {
            ci.cancel();
            return;
        }
        EZFIX$IW_HURT_IN_FLIGHT.set(Boolean.TRUE);
    }

    @Inject(method = "LivingHurt", at = @At("RETURN"), remap = false)
    private static void ezfix$clearLivingHurtFlag(LivingHurtEvent event, CallbackInfo ci) {
        EZFIX$IW_HURT_IN_FLIGHT.set(Boolean.FALSE);
    }
}
