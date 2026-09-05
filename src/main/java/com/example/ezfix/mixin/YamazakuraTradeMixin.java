package com.example.ezfix.mixin;

import com.dinzeer.yamazakura.util.VillagerSlashBladeListing;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 拔刀剑重锋·山樱之刃（yamazakura）村民交易的空指针防护。
 *
 * 错误报告（VillagerSlashBladeListing.java:29）：
 *   NullPointerException: Cannot invoke "net.minecraft.world.entity.Entity.m_9236_()" because "entity" is null
 * jeresources 收集村民交易时传入 null 实体导致。
 */
@Mixin(value = VillagerSlashBladeListing.class, remap = false)
public abstract class YamazakuraTradeMixin {

    @Inject(method = "m_213663_", at = @At("HEAD"), cancellable = true, remap = false)
    private void ezfix$nullSafeOffer(Entity entity, RandomSource random, CallbackInfoReturnable<MerchantOffer> cir) {
        if (entity == null) {
            cir.setReturnValue(null);
        }
    }
}
