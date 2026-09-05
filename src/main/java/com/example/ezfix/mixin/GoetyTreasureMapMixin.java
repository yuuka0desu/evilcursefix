package com.example.ezfix.mixin;

import com.Polarice3.Goety.utils.ModTradeUtil;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 诡厄巫法（恶咒）流浪商人"宝藏地图"交易的空指针防护 —— 本模组核心针对性修复。
 *
 * 错误报告（ModTradeUtil.java:88）：
 *   NullPointerException: Cannot read field "f_19853_" because "entity" is null
 *   at com.Polarice3.Goety.utils.ModTradeUtil$TreasureMapForEmeralds.m_213663_(ModTradeUtil.java:88)
 *
 * 反编译确认 m_213663_ 第一条字节码即为 getfield f_19853_（entity.level），
 * 对 null 实体无任何防护。jeresources 收集交易时传入 null 实体即触发 NPE。
 * 此处按原代码既有语义（非 ServerLevel 时返回 null）补上判空。
 *
 * 目标类名使用字符串 targets + remap=false：仅引用 MC 类型，无需编译期
 * 依赖 goety jar；goety 未安装时 mixin 服务仅发 WARN 并跳过。
 */
@Mixin(value = ModTradeUtil.TreasureMapForEmeralds.class, remap = false)
public abstract class GoetyTreasureMapMixin {

    @Inject(method = "m_213663_", at = @At("HEAD"), cancellable = true, remap = false)
    private void ezfix$nullSafeOffer(Entity entity, RandomSource random, CallbackInfoReturnable<MerchantOffer> cir) {
        if (entity == null) {
            cir.setReturnValue(null);
        }
    }
}
