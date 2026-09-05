package com.example.ezfix.mixin;

import com.github.L_Ender.cataclysm.entity.projectile.Laser_Beam_Entity;
import com.github.L_Ender.cataclysm.init.ModEntities;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Constructor;

/**
 * 修复 traveloptics 极速激光法术的 NoSuchMethodError：
 *   'void Laser_Beam_Entity.<init>(EntityType, LivingEntity, double x7, float, Level)'
 *   at RapidLaserSpell.spawnLaserBeam(RapidLaserSpell.java:105)
 *
 * 原因：traveloptics 按旧版灾变 API 编译；新版灾变（3.31）删除了 7-double
 * 构造器，改为 (EntityType, LivingEntity, x, y, z, Vec3, float, Level)。
 *
 * 修复：在方法入口接管 spawnLaserBeam 并按当前灾变版本**探测构造器**：
 *   新版 → (type, caster, x, y, z, Vec3(dir*speed), damage, level)
 *   旧版 → (type, caster, x, y, z, dx, dy, dz, speed?, damage, level) 原样转发
 * 两个版本都不存在时仅跳过（打日志），保证跨整合包版本（1.0.x/1.3.1）通用。
 */
@Mixin(value = com.gametechbc.traveloptics.spells.lightning.RapidLaserSpell.class, remap = false)
public abstract class TravelopticsRapidLaserMixin {

    @Inject(method = "spawnLaserBeam", at = @At("HEAD"), cancellable = true, remap = false)
    private void ezfix$rewriteSpawnLaser(Level level, LivingEntity caster, float damage, float speed,
                                         CallbackInfo ci) {
        ci.cancel();
        try {
            Vec3 lookVec = caster.getViewVector(1.0F).normalize();
            Vec3 spawnPos = new Vec3(
                    caster.getX(),
                    caster.getY() + caster.getEyeHeight() * 0.8F,
                    caster.getZ());
            float yRot = (float) (Mth.atan2(lookVec.z, lookVec.x) * 57.29577951308232D) + 90.0F;
            float xRot = -(float) (Mth.atan2(lookVec.y, Math.sqrt(lookVec.z * lookVec.z + lookVec.x * lookVec.x))
                    * 57.29577951308232D);

            Laser_Beam_Entity laser = constructLaser(level, caster, spawnPos, lookVec, speed, damage);
            if (laser == null) {
                return;
            }
            laser.setYRot(yRot);
            laser.setXRot(xRot);
            level.addFreshEntity(laser);
        } catch (Throwable t) {
            System.err.println("[ezfix] Traveloptics rapid laser spawn failed, skipping:");
            t.printStackTrace(System.err);
        }
    }

    private static Laser_Beam_Entity constructLaser(Level level, LivingEntity caster, Vec3 spawnPos,
                                                    Vec3 lookVec, float speed, float damage) throws Exception {
        EntityType<?> type = ModEntities.LASER_BEAM.get();
        // 新版灾变（3.31+）：(EntityType, LivingEntity, DDD, Vec3, float, Level)
        try {
            Constructor<?> ctor = Laser_Beam_Entity.class.getConstructor(
                    EntityType.class, LivingEntity.class,
                    double.class, double.class, double.class,
                    Vec3.class, float.class, Level.class);
            return (Laser_Beam_Entity) ctor.newInstance(
                    type, caster,
                    spawnPos.x, spawnPos.y, spawnPos.z,
                    new Vec3(lookVec.x * speed, lookVec.y * speed, lookVec.z * speed),
                    damage, level);
        } catch (NoSuchMethodException noNewCtor) {
            // 旧版灾变：6-double 构造器，原样转发
            Constructor<?> ctor = Laser_Beam_Entity.class.getConstructor(
                    EntityType.class, LivingEntity.class,
                    double.class, double.class, double.class,
                    double.class, double.class, double.class,
                    float.class, Level.class);
            return (Laser_Beam_Entity) ctor.newInstance(
                    type, caster,
                    spawnPos.x, spawnPos.y, spawnPos.z,
                    lookVec.x * speed, lookVec.y * speed, lookVec.z * speed,
                    damage, level);
        }
    }
}
