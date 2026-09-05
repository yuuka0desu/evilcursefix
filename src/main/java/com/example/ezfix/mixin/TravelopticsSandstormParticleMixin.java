package com.example.ezfix.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 修复 traveloptics 闪电沙尘暴投射物在专用服务器上的崩溃：
 *   NoClassDefFoundError: com/github/L_Ender/cataclysm/client/particle/Lightning_Zap_Particle$ZapData
 *   at LightningSandstormPierceProjectileEntity.spawnImpactParticles(...)
 *
 * 原因：该方法在 isClientSide 检查之后仍持有对灾变客户端粒子类的直接引用，
 * 专用服务器加载此类即失败（客户端类不存在于服务端 jar）。
 *
 * 修复：方法入口强制服务端直接返回（原代码第一行本就只允许客户端执行）。
 */
@Mixin(value = com.gametechbc.traveloptics.entity.projectiles.lightning_sandstorm_projectile.LightningSandstormPierceProjectileEntity.class, remap = false)
public abstract class TravelopticsSandstormParticleMixin {

    @Inject(method = "spawnImpactParticles", at = @At("HEAD"), cancellable = true, remap = false)
    private void ezfix$skipParticlesOnServer(Vec3 hitPos, CallbackInfo ci) {
        Level level = ((net.minecraft.world.entity.Entity) (Object) this).level();
        if (level != null && !level.isClientSide()) {
            ci.cancel();
        }
    }
}
