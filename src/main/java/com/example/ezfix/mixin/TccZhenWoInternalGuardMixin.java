package com.example.ezfix.mixin;

import com.example.ezfix.util.TccZhenWoBarrierGuard;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 第二道防线：直接守卫 TCC 护盾递归的**真实发生点** isActiveBarrierWearer。
 *
 * 字节码实证（tcc 1.4.16，ZhenWo）：
 *   private static boolean isActiveBarrierWearer(LivingEntity, Vec3, double)
 *   方法第一步就调用 CurioSearchHelper.findFirstEquippedStack(entity, pred)，
 *   该调用会触发 ICapabilityProvider.getCapability。若 entity 是重写了
 *   getCapability 并回调护盾判定的类型（EntityDragonBase / EntityMaid），
 *   立刻回到 isInsideActiveBarrier → isActiveBarrierWearer，形成死循环。
 *
 * 与 TccZhenWoStackGuardMixin 互补：
 *  - 前者守 isInsideActiveBarrier（对外入口，覆盖 barrierHealthFloor 路径）；
 *  - 本 mixin 守 isActiveBarrierWearer（对内实现，覆盖任何直接调用者，
 *    包括未来 tcc 版本新增的调用路径）。
 * 两者共享 TccZhenWoBarrierGuard.INSTANCE，同一条链上任一级先登记即可断环。
 *
 * 修复方式：同一实体在判定途中再次进入 → 返回 false（该实体不被视为护盾
 * 佩戴者），首次/其它实体的调用完整执行。
 *
 * 兼容性：tcc 1.3.0 无此类；字符串 targets + require = 0 → 静默跳过。
 * 目标为 private 方法，故必须精确匹配方法描述符。
 */
@Mixin(targets = "com.xlxyvergil.tcc.items.curios.bound.ZhenWo", remap = false)
public abstract class TccZhenWoInternalGuardMixin {

    @Inject(
            method = "isActiveBarrierWearer(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/phys/Vec3;D)Z",
            at = @At("HEAD"),
            cancellable = true,
            require = 0,
            remap = false
    )
    private static void ezfix$guardWearerRecursion(LivingEntity entity, Vec3 center, double radiusSq,
                                                    CallbackInfoReturnable<Boolean> cir) {
        if (entity != null && TccZhenWoBarrierGuard.INSTANCE.enter(entity.getUUID())) {
            // 同一实体判定中再次进入 → 拒绝再次发起 Curios 查询
            cir.setReturnValue(false);
        }
    }

    @Inject(
            method = "isActiveBarrierWearer(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/phys/Vec3;D)Z",
            at = @At("RETURN"),
            require = 0,
            remap = false
    )
    private static void ezfix$releaseWearerGuard(LivingEntity entity, Vec3 center, double radiusSq,
                                                 CallbackInfoReturnable<Boolean> cir) {
        if (entity != null) {
            TccZhenWoBarrierGuard.INSTANCE.exit(entity.getUUID());
        }
    }
}
