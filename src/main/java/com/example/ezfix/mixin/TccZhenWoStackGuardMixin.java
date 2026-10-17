package com.example.ezfix.mixin;

import com.example.ezfix.util.TccZhenWoBarrierGuard;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 修复 TCC「真我」护盾判定在 Capability 查询链上的无限递归（StackOverflowError）。
 *
 * 崩溃实证（2026-10-11 ~ 10-17 共 5 份报告，tcc 1.4.2 / 1.4.16 均出现）：
 *   EntityDragonBase.getCapability / EntityMaid.getCapability
 *     → ZhenWoGuard.isInsideActiveBarrier(entity)
 *     → ZhenWo.isInsideActiveBarrier(entity)
 *     → ZhenWo.isActiveBarrierWearer(player, pos, radius²)
 *     → CurioSearchHelper.findFirstEquippedStack(player, pred)
 *     → ICapabilityProvider.getCapability       ← 再次进入实体的 getCapability
 *     → EntityDragonBase.getCapability          ← 回到起点
 *   第三条入口：LivingEntity.barrierHealthFloor（tcc 自身 mixin）同样进入
 *   ZhenWoGuard.isInsideActiveBarrier。
 *
 * 修复：在 ZhenWoGuard.isInsideActiveBarrier 入口按 **实体 UUID** 去重。
 *  - 同一实体判定途中再次被判定 → 真递归，返回 false 切断；
 *  - 不同实体 → 合法嵌套（护盾本就判定范围内其它生物），正常放行。
 *
 * 为何不用 EzfixReentrySlot（线程级单标记）：它会把"判定 A 期间判定 B"这种
 * 合法嵌套也判为重入，导致护盾在嵌套调用中失效。详见 EzfixEntityReentryGuard。
 *
 * 共享状态放在 mixin 包之外的 TccZhenWoBarrierGuard：Mixin 的 @Unique 字段
 * 只存在于各自目标类中，两个 mixin 之间无法互访，必须经外部持有类共享同一实例。
 *
 * 兼容性：tcc 1.3.0 无 ZhenWoGuard 类；字符串 targets + require = 0
 * 保证编译期无关、运行时缺失则静默跳过。
 */
@Mixin(targets = "com.xlxyvergil.tcc.util.ZhenWoGuard", remap = false)
public abstract class TccZhenWoStackGuardMixin {

    @Inject(
            method = "isInsideActiveBarrier(Lnet/minecraft/world/entity/LivingEntity;)Z",
            at = @At("HEAD"),
            cancellable = true,
            require = 0,
            remap = false
    )
    private static void ezfix$guardBarrierRecursion(LivingEntity entity,
                                                    CallbackInfoReturnable<Boolean> cir) {
        if (entity != null && TccZhenWoBarrierGuard.INSTANCE.enter(entity.getUUID())) {
            // 同一实体判定中再次进入 → 递归环，切断
            cir.setReturnValue(false);
        }
    }

    @Inject(
            method = "isInsideActiveBarrier(Lnet/minecraft/world/entity/LivingEntity;)Z",
            at = @At("RETURN"),
            require = 0,
            remap = false
    )
    private static void ezfix$releaseGuard(LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
        if (entity != null) {
            TccZhenWoBarrierGuard.INSTANCE.exit(entity.getUUID());
        }
    }
}
