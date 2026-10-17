package com.example.ezfix.mixin;

import com.example.ezfix.util.EzfixRecursionGuard;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 配合 TccImaginaryDamageGuardMixin：虚化伤害重入期间只跳过 MetaMorph 的
 * “再次造成虚化伤害”这一步（applyImaginaryDamage 调用），
 * 事件处理器本身的伤害加成逻辑保持原样执行。
 *
 * 这样打断 MetaMorph <-> applyImaginaryDamage 递归环的同时，
 * 不会吞掉合法的伤害修正。
 *
 * 版本差异（实测）：
 *   tcc 1.3.0  → com.xlxyvergil.tcc.items.curios.MetaMorph
 *   tcc 1.4.16 → com.xlxyvergil.tcc.items.curios.bound.MetaMorph
 * 本 mixin 原先以 value = Class 形式引用旧路径，在 1.4.16 实例上既编译
 * 不通过、运行时也报 "MetaMorph was not found"。现改为字符串 targets，
 * 并保留 require = 0：任一版本缺失时静默跳过，不影响启动。
 */
@Mixin(targets = "com.xlxyvergil.tcc.items.curios.bound.MetaMorph", remap = false)
public abstract class TccMetaMorphReentrancyMixin {

    @Redirect(
            method = "onLivingHurt",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/xlxyvergil/tcc/event/TccAttributeEvents;applyImaginaryDamage(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/damagesource/DamageSource;F)Z",
                    remap = false
            ),
            require = 0,
            remap = false
    )
    private static boolean ezfix$skipReentrantApply(LivingEntity victim, DamageSource source, float amount) {
        if (EzfixRecursionGuard.isImaginaryDamageInFlight()) {
            return false;
        }
        return com.xlxyvergil.tcc.event.TccAttributeEvents.applyImaginaryDamage(victim, source, amount);
    }
}
