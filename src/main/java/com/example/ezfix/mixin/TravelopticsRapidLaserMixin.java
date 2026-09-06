package com.example.ezfix.mixin;

import com.github.L_Ender.cataclysm.entity.projectile.Laser_Beam_Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.lang.reflect.Constructor;

/**
 * 修复 traveloptics 极速激光法术的 NoSuchMethodError：
 *   'void Laser_Beam_Entity.<init>(EntityType, LivingEntity, double x7, float, Level)'
 *   at RapidLaserSpell.spawnLaserBeam(RapidLaserSpell.java:105)
 *
 * 原因：traveloptics 按旧版灾变 API 编译；新版灾变（3.31）删除了
 * (EntityType, LivingEntity, 6×double, float, Level) 构造器，
 * 改为 (EntityType, LivingEntity, x, y, z, Vec3, float, Level)。
 *
 * 修复：只 Redirect 构造器调用（javap 实证 spawnLaserBeam 内唯一的
 * NEW Laser_Beam_Entity 指令，参数顺序 x,y,z,dx,dy,dz,damage,level），
 * 反射探测构造器：
 *   旧版 → 原样转发（旧整合包行为与原来完全一致）；
 *   新版 → (type, caster, x, y, z, new Vec3(dx, dy, dz), damage, level)。
 * 原方法体其余逻辑（setYRot/setXRot/入世界等副作用）全部保留，
 * 不再整方法接管，避免覆盖新版本行为。
 * 两个构造器都不存在时抛出带明确信息的异常（与原 NoSuchMethodError
 * 的崩溃传播路径一致），构造器按首次成功结果缓存。
 */
@Mixin(value = com.gametechbc.traveloptics.spells.lightning.RapidLaserSpell.class, remap = false)
public abstract class TravelopticsRapidLaserMixin {

    private static volatile Constructor<?> EZFIX$OLD_CTOR;
    private static volatile Constructor<?> EZFIX$NEW_CTOR;
    private static volatile boolean EZFIX$CTORS_RESOLVED;

    @Redirect(
            method = "spawnLaserBeam",
            at = @At(
                    value = "NEW",
                    target = "Lcom/github/L_Ender/cataclysm/entity/projectile/Laser_Beam_Entity;",
                    remap = false
            ),
            remap = false
    )
    private static Laser_Beam_Entity ezfix$constructLaser(EntityType<?> type, LivingEntity caster,
                                                          double x, double y, double z,
                                                          double dx, double dy, double dz,
                                                          float damage, Level level) {
        if (!EZFIX$CTORS_RESOLVED) {
            resolveConstructors();
        }
        try {
            if (EZFIX$OLD_CTOR != null) {
                return (Laser_Beam_Entity) EZFIX$OLD_CTOR.newInstance(
                        type, caster, x, y, z, dx, dy, dz, damage, level);
            }
            if (EZFIX$NEW_CTOR != null) {
                return (Laser_Beam_Entity) EZFIX$NEW_CTOR.newInstance(
                        type, caster, x, y, z, new Vec3(dx, dy, dz), damage, level);
            }
        } catch (ReflectiveOperationException | IllegalArgumentException e) {
            throw new RuntimeException("[ezfix] Laser_Beam_Entity construction failed", e);
        }
        throw new IllegalStateException(
                "[ezfix] no compatible Laser_Beam_Entity constructor found (cataclysm version drift)");
    }

    private static void resolveConstructors() {
        try {
            EZFIX$OLD_CTOR = Laser_Beam_Entity.class.getConstructor(
                    EntityType.class, LivingEntity.class,
                    double.class, double.class, double.class,
                    double.class, double.class, double.class,
                    float.class, Level.class);
        } catch (NoSuchMethodException ignored) {
            EZFIX$OLD_CTOR = null;
        }
        try {
            EZFIX$NEW_CTOR = Laser_Beam_Entity.class.getConstructor(
                    EntityType.class, LivingEntity.class,
                    double.class, double.class, double.class,
                    Vec3.class, float.class, Level.class);
        } catch (NoSuchMethodException ignored) {
            EZFIX$NEW_CTOR = null;
        }
        EZFIX$CTORS_RESOLVED = true;
    }
}
