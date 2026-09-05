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
 */
@Mixin(value = com.xlxyvergil.tcc.items.curios.MetaMorph.class, remap = false)
public abstract class TccMetaMorphReentrancyMixin {

    @Redirect(
            method = "onLivingHurt",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/xlxyvergil/tcc/event/TccAttributeEvents;applyImaginaryDamage(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/damagesource/DamageSource;F)Z",
                    remap = false
            ),
            remap = false
    )
    private static boolean ezfix$skipReentrantApply(LivingEntity victim, DamageSource source, float amount) {
        if (EzfixRecursionGuard.isImaginaryDamageInFlight()) {
            return false;
        }
        return com.xlxyvergil.tcc.event.TccAttributeEvents.applyImaginaryDamage(victim, source, amount);
    }
}
