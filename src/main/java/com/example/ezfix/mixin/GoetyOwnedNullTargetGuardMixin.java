package com.example.ezfix.mixin;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 修复诡厄巫法 Owned 实体目标判定的空指针崩溃：
 *   NullPointerException: Cannot invoke "Entity.m_5647_()" because "p_20355_" is null
 *   at Entity.m_7307_(Entity.java:2236)
 *   at Owned.m_7307_(Owned.java:186)
 *   at HurtByTargetGoal.m_26047_（反击目标查找）
 *
 * 某些修改过 HurtByTargetGoal 的模组（如 revelationfix）在目标为空时仍会
 * 调用 Owned 的 canAttack（m_7307_），Owned 内部对 null 目标直接转发给
 * 父类方法导致 NPE。此处入口拦截 null 目标并返回 false（不可攻击）。
 */
@Mixin(targets = "com.Polarice3.Goety.common.entities.neutral.Owned", remap = false)
public abstract class GoetyOwnedNullTargetGuardMixin {

    @Inject(method = "m_7307_", at = @At("HEAD"), cancellable = true, remap = false)
    private void ezfix$nullTargetGuard(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (entity == null) {
            cir.setReturnValue(false);
        }
    }
}
