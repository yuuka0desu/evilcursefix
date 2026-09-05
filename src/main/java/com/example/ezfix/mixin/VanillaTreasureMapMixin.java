package com.example.ezfix.mixin;

import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 原版村民交易"宝藏地图"的空指针防护。
 *
 * jeresources 在收集村民交易时会以 null 实体调用
 * VillagerTrades$TreasureMapForEmeralds.getOffer（m_213663_），
 * 原代码第一步就访问 entity.level，导致 NPE 刷屏且 JEI 交易缺失。
 *
 * 该类为 private 静态内部类，使用字符串 targets；方法名按运行时
 * srg 字面名 m_213663_ 匹配（remap=false）。
 */
@Mixin(targets = "net.minecraft.world.entity.npc.VillagerTrades$TreasureMapForEmeralds", remap = false)
public abstract class VanillaTreasureMapMixin {

    @Inject(method = "m_213663_", at = @At("HEAD"), cancellable = true, remap = false)
    private void ezfix$nullSafeOffer(Entity entity, RandomSource random, CallbackInfoReturnable<MerchantOffer> cir) {
        if (entity == null) {
            cir.setReturnValue(null);
        }
    }
}
