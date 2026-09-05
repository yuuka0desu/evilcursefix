package com.example.ezfix.mixin;

import com.rinko1231.peyroscythe.effect.SinfireEmbraceEffect;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 修复佩伊洛的镰刀「罪火拥抱」重复添加属性修饰符导致的服务器崩溃。
 *
 * 崩溃：IllegalArgumentException: Modifier is already applied on this attribute!
 * at SinfireEmbraceEffect.m_6385_。
 *
 * 原实现先 getModifier(UUID) 再 addPermanentModifier；效果重复应用或其他模组
 * 在检查和添加之间改变属性状态时，addPermanentModifier 仍会因同 UUID 抛异常。
 * 此处在真正添加位置再次做原子式防护：已有同 UUID 修饰符就沿用，只有仍不存在时添加。
 */
@Mixin(value = SinfireEmbraceEffect.class, remap = false)
public abstract class PeyroSinfireModifierMixin {

    @Redirect(
            method = "m_6385_",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/ai/attributes/AttributeInstance;m_22118_(Lnet/minecraft/world/entity/ai/attributes/AttributeModifier;)V"
            ),
            remap = false
    )
    private void ezfix$skipDuplicateModifier(AttributeInstance instance, AttributeModifier modifier) {
        if (instance.getModifier(modifier.getId()) != null) {
            return;
        }

        try {
            instance.addPermanentModifier(modifier);
        } catch (IllegalArgumentException duplicate) {
            // 仅吞掉同 UUID 已存在的竞态；其他参数错误仍保持原异常语义。
            if (instance.getModifier(modifier.getId()) == null) {
                throw duplicate;
            }
        }
    }
}
