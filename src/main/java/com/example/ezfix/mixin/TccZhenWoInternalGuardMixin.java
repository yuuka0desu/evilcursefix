package com.example.ezfix.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 修复 TCC「真我」死亡保护被非玩家实体触发时的 StackOverflowError。
 *
 * 崩溃链（恶咒落幕 1.3.3v，tcc 1.4.16）：
 *   LivingEntity.hurt → 各模组 onLivingHurt 回调
 *   → ZhenWo.canPreventDeath(entity)
 *   → CuriosApi.getCuriosInventory(entity) → 遍历饰品
 *   → 冰火传说龙类 / Curios 的实体 tick 回调再次进入死亡保护判定
 *   → 无限递归 → StackOverflowError
 *
 * 原因：「真我」是玩家专属饰品，但 canPreventDeath 未做实体类型判断，
 * 任何 LivingEntity（包括冰火龙、仆从等）受伤时都会走 Curios 查询路径；
 * 这些实体的 tick 链上又存在会再次触发 hurt 的模组逻辑，形成递归环。
 *
 * 修复：入口处对非玩家实体直接返回 false（不可阻止死亡）。
 * 「真我」本就只对玩家生效，对其他实体返回 false 与设计语义一致。
 *
 * 兼容性：目标类仅在 tcc 1.4.16+ 存在（1.3.0 无 ZhenWo）。
 * 全程只引用 MC 类型、不引用 tcc 类；@Pseudo 表示"目标类可能不存在"，
 * 使 Mixin AP 不做编译期硬解析（否则会因本地编译依赖无此类而报错）。
 * 运行时由 EzfixMixinPlugin 按类存在性 + require=0 双重保护：
 * 1.3.0 实例上静默跳过，绝不导致启动失败。
 */
@Pseudo
@Mixin(targets = "com.xlxyvergil.tcc.items.curios.bound.ZhenWo", remap = false)
public abstract class TccZhenWoInternalGuardMixin {

    @Inject(method = "canPreventDeath", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void ezfix$skipCuriosLookupForNonPlayers(LivingEntity entity,
                                                            CallbackInfoReturnable<Boolean> cir) {
        if (!(entity instanceof Player)) {
            cir.setReturnValue(false);
        }
    }
}
