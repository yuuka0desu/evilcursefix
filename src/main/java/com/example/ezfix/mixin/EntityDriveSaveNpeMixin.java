package com.example.ezfix.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 修复拔刀剑 EntityDrive 跨维度存档时的空指针崩溃：
 *   NullPointerException: Cannot invoke "KnockBacks.ordinal()" because the
 *   return value of "EntityDrive.getKnockBack()" is null
 *   at EntityDrive.m_7380_(EntityDrive.java:117)
 *
 * 原因：字段 action 未初始化（例如合成时未设置）即为 null，存档时
 * putInt 直接调用 getKnockBack().ordinal()。
 *
 * 修复：ordinal() 被调用前若枚举为 null，改写为第一个枚举常量
 * （values()[0]）的 ordinal，写入合法默认值。
 */
@Mixin(value = mods.flammpfeil.slashblade.entity.EntityDrive.class, remap = false)
public abstract class EntityDriveSaveNpeMixin {

    @Redirect(
            method = "m_7380_",
            at = @At(
                    value = "INVOKE",
                    target = "Lmods/flammpfeil/slashblade/util/KnockBacks;ordinal()I",
                    remap = false
            ),
            remap = false
    )
    private int ezfix$defaultOrdinal(mods.flammpfeil.slashblade.util.KnockBacks knockBacks) {
        if (knockBacks == null) {
            mods.flammpfeil.slashblade.util.KnockBacks[] values =
                    mods.flammpfeil.slashblade.util.KnockBacks.values();
            return values.length > 0 ? values[0].ordinal() : 0;
        }
        return knockBacks.ordinal();
    }
}
