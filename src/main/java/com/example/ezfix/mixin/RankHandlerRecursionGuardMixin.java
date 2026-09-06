package com.example.ezfix.mixin;

import com.dinzeer.slashblade_useful_addon.events.RankHandler;
import com.example.ezfix.util.EzfixReentrySlot;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 修复拔刀剑实用扩展旧版（1.1.x）RankHandler 的无限递归（StackOverflowError）。
 *
 * 崩溃栈循环（整合包 1.0.1 / slashblade_useful_addon-1.1.1）：
 *   RankHandler.OnHurtEntity -> FastMakeEntityUtil.FastHurt -> 实体再受击
 *   -> LivingHurtEvent -> RankHandler.OnHurtEntity -> ...
 *
 * 旧版 OnHurtEntity 在伤害事件里对实体再次造成伤害且无任何防重入条件，
 * 同线程无限递归直至栈溢出。新版（1.2.5）已移除该再伤害逻辑（仅剩伤害修正），
 * 故本守卫对 1.2.5 完全无害。
 *
 * 修复：方法入口使用 EzfixReentrySlot（嵌套深度 + TTL 自愈）设置重入标记；
 * FastHurt 触发的重入调用在入口被取消，打断循环，外层首次调用正常完成。
 * RETURN 处退出守卫。采用 HEAD+RETURN 组合而非调用点 Redirect，
 * 兼容 1.1.x/1.2.x 不同字节码；异常残留由 TTL 自动自愈。
 */
@Mixin(value = RankHandler.class, remap = false)
public abstract class RankHandlerRecursionGuardMixin {

    private static final EzfixReentrySlot EZFIX$RANK_HURT_SLOT =
            new EzfixReentrySlot(EzfixReentrySlot.DEFAULT_TTL_MS);

    @Inject(method = "OnHurtEntity", at = @At("HEAD"), cancellable = true, remap = false)
    private static void ezfix$skipReentrantRankHurt(LivingHurtEvent event, CallbackInfo ci) {
        if (EZFIX$RANK_HURT_SLOT.enter()) {
            ci.cancel();
        }
    }

    @Inject(method = "OnHurtEntity", at = @At("RETURN"), remap = false)
    private static void ezfix$clearRankHurtFlag(LivingHurtEvent event, CallbackInfo ci) {
        EZFIX$RANK_HURT_SLOT.exit();
    }
}
