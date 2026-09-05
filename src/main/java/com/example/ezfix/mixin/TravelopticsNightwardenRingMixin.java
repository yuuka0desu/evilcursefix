package com.example.ezfix.mixin;

import com.buldog.compatfix.CompatRingData;
import com.github.L_Ender.cataclysm.client.particle.RingParticle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.lang.reflect.Constructor;

/**
 * 修复 traveloptics 暗夜典狱长 Boss 客户端粒子崩溃：
 *   NoSuchMethodError: 'void CompatRingData.<init>(float, float, int, float,
 *   float, float, float, float, boolean, RingParticle$EnumRingBehavior)'
 *   at NightwardenBossEntity.m_8119_(NightwardenBossEntity.java:741)
 *
 * 原因：traveloptics 按旧版灾变编译，调用 RingData(7 个 float) 构造器；
 * cataclysm-to-magic-fix 的 coremod 把类名替换为 CompatRingData，但当前
 * compatfix 1.1.0 的构造器只有 6 个 float（少了旧版的尺寸参数 50.0）。
 *
 * 修复：把 NEW CompatRingData 调用适配为当前构造器：
 *   (角度, 角度, 数量, R, G, B, alpha, 50.0, boolean, enum) 旧 10 参数
 *   -> (角度, 角度, 数量, R, G, B, alpha, boolean, enum) 新 9 参数（丢弃 50.0）。
 *
 * 注意：Mixin 对构造器 NEW 的 @Redirect 要求 handler 返回类型必须是
 * CompatRingData（Object 会被判为签名非法）。构造过程用反射容错；
 * 未安装 compatfix 的实例上本 mixin 应用失败仅产生 WARN（require=0 兜底）。
 */
@Mixin(value = com.gametechbc.traveloptics.entity.mobs.nightwarden_boss.NightwardenBossEntity.class, remap = false)
public abstract class TravelopticsNightwardenRingMixin {

    @Redirect(
            method = "m_8119_",
            at = @At(
                    value = "NEW",
                    target = "com/buldog/compatfix/CompatRingData",
                    remap = false
            ),
            require = 0,
            remap = false
    )
    private CompatRingData ezfix$adaptCompatRing(float f1, float f2, int i3,
                                                 float f4, float f5, float f6,
                                                 float f7, float f8,
                                                 boolean b9, RingParticle.EnumRingBehavior e10) {
        try {
            Constructor<?> ctor = CompatRingData.class.getConstructor(
                    float.class, float.class, int.class,
                    float.class, float.class, float.class, float.class,
                    boolean.class, RingParticle.EnumRingBehavior.class);
            return (CompatRingData) ctor.newInstance(f1, f2, i3, f4, f5, f6, f7, b9, e10);
        } catch (Throwable t) {
            System.err.println("[ezfix] CompatRingData adaption failed, skipping particle:");
            t.printStackTrace(System.err);
            return null;
        }
    }
}
