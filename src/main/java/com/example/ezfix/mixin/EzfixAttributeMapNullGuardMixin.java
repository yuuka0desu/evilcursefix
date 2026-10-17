package com.example.ezfix.mixin;

import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;
import java.util.UUID;

/**
 * 合并自 npefix：AttributeMap 属性供应者为空时的通用 NPE 防护。
 *
 * 某些模组构造的 LivingEntity 子类从未注册 AttributeSupplier，
 * AttributeMap 的公共属性访问器会对空 supplier 解引用导致服务端崩溃。
 * 此处让全部公共访问器空值容忍并返回安全默认值。
 *
 * 供应商字段通过反射读取（SRG 名 f_22141_），避免 @Shadow 对
 * 编译类路径 MCP 名的依赖；读取失败时保持原行为（不拦截）。
 *
 * 注解形式与 1.6.8 移动端修复保持一致：@Pseudo + 字符串 targets。
 * 原因见 EzfixAttributeMapConcurrentModificationMixin：以 Class 字面量声明
 * 目标会让 Mixin 在 prepare 阶段预加载 AttributeMap，其解析链会顺带把
 * net.minecraft.world.item.Item 提前拉进类加载器，抢在 tact 等模组的
 * accessor mixin 之前，导致 Android 启动抛
 *   MixinTargetAlreadyLoadedException: ... Item was loaded too early  → FATAL。
 * 二者目标是同一个类，必须采用同一防护形式。
 */
@Pseudo
@Mixin(targets = "net.minecraft.world.entity.ai.attributes.AttributeMap", remap = false)
public abstract class EzfixAttributeMapNullGuardMixin {

    private static volatile Field EZFIX$SUPPLIER_FIELD;

    private boolean ezfix$supplierMissing() {
        try {
            Field f = EZFIX$SUPPLIER_FIELD;
            if (f == null) {
                // 不用 AttributeMap.class 字面量：那会在类初始化期触发目标类加载，
                // 与本 mixin 的 @Pseudo 防预加载目的相悖。改为按类名反射获取，
                // 此时目标类必然已加载（本 mixin 已应用在它身上）。
                Class<?> target = Class.forName(
                        "net.minecraft.world.entity.ai.attributes.AttributeMap",
                        false, this.getClass().getClassLoader());
                f = target.getDeclaredField("f_22141_");
                f.setAccessible(true);
                EZFIX$SUPPLIER_FIELD = f;
            }
            return f.get(this) == null;
        } catch (Throwable t) {
            // 字段不存在（版本差异）：不拦截，保持原行为
            return false;
        }
    }

    @Inject(method = "m_22146_", at = @At("HEAD"), cancellable = true, remap = false)
    private void ezfix$getInstanceGuard(Attribute attribute, CallbackInfoReturnable<AttributeInstance> cir) {
        if (ezfix$supplierMissing()) {
            cir.setReturnValue(null);
        }
    }

    @Inject(method = "m_22171_", at = @At("HEAD"), cancellable = true, remap = false)
    private void ezfix$hasAttributeGuard(Attribute attribute, CallbackInfoReturnable<Boolean> cir) {
        if (ezfix$supplierMissing()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "m_22154_", at = @At("HEAD"), cancellable = true, remap = false)
    private void ezfix$hasModifierGuard(Attribute attribute, UUID uuid, CallbackInfoReturnable<Boolean> cir) {
        if (ezfix$supplierMissing()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "m_22181_", at = @At("HEAD"), cancellable = true, remap = false)
    private void ezfix$getValueGuard(Attribute attribute, CallbackInfoReturnable<Double> cir) {
        if (ezfix$supplierMissing()) {
            cir.setReturnValue(0.0D);
        }
    }

    @Inject(method = "m_22185_", at = @At("HEAD"), cancellable = true, remap = false)
    private void ezfix$getBaseValueGuard(Attribute attribute, CallbackInfoReturnable<Double> cir) {
        if (ezfix$supplierMissing()) {
            cir.setReturnValue(0.0D);
        }
    }

    @Inject(method = "m_22173_", at = @At("HEAD"), cancellable = true, remap = false)
    private void ezfix$getModifierValueGuard(Attribute attribute, UUID uuid, CallbackInfoReturnable<Double> cir) {
        if (ezfix$supplierMissing()) {
            cir.setReturnValue(0.0D);
        }
    }
}
