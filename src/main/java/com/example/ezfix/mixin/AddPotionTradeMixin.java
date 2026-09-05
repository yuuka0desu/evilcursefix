package com.example.ezfix.mixin;

import com.Hileb.add_potion.common.world.ModTrades;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * add_potion（食物附魔）药剂师交易的空指针防护。
 *
 * 错误报告（ModTrades.java:84）：
 *   NullPointerException: Cannot invoke "net.minecraft.world.entity.Entity.m_20148_()" because "trader" is null
 * jeresources 收集村民交易时传入 null 实体导致。
 */
@Mixin(value = ModTrades.FoodWithRandomEffectsForEmerald.class, remap = false)
public abstract class AddPotionTradeMixin {

    @Inject(method = "m_213663_", at = @At("HEAD"), cancellable = true, remap = false)
    private void ezfix$nullSafeOffer(Entity entity, RandomSource random, CallbackInfoReturnable<MerchantOffer> cir) {
        if (entity == null) {
            cir.setReturnValue(null);
        }
    }
}
