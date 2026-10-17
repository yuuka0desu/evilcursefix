package com.example.ezfix.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * 合并自 anti_overflow：按目标类存在性启用可选防护层。
 *
 * 仅控制依赖可选项（拔刀剑/幻想之刃）的斩击递归守卫；
 * 目标模组未安装时静默跳过（类加载 + 资源回退双通道检查，
 * 避免 Class.forName 在 TransformingClassLoader 上误判）。
 */
public class EzfixMixinPlugin implements IMixinConfigPlugin {

    private static final String MIXIN_PACKAGE = "com.example.ezfix.mixin.";

    private static final String ATTACK_MANAGER_CLASS =
            "mods.flammpfeil.slashblade.util.AttackManager";
    private static final String HIGH_FREQUENCY_CLASS =
            "com.yakumosakura.yakumoblade.registry.specialeffects.Highfrequency";
    private static final String HIGH_FREQUENCY_STAR_CLASS =
            "com.yakumosakura.yakumoblade.specialeffects.starSE.Highfrequency";
    /** ModernFix 主类；该模组存在时才应用"退出挂起中和"覆盖 */
    private static final String MODERNFIX_CLASS =
            "org.embeddedt.modernfix.ModernFix";
    /** tcc「真我」相关类（1.4.16+ 才有；1.3.0 无此类） */
    private static final String TCC_ZHENWO_GUARD_CLASS =
            "com.xlxyvergil.tcc.util.ZhenWoGuard";
    private static final String TCC_ZHENWO_CLASS =
            "com.xlxyvergil.tcc.items.curios.bound.ZhenWo";
    /** PickablePets 主类 */
    private static final String PICKABLE_PETS_CLASS =
            "com.shiftthedev.pickablepets.PickablePets";

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.equals(MIXIN_PACKAGE + "EzfixSlashRecursionGuardMixin")) {
            return classExists(ATTACK_MANAGER_CLASS);
        }
        if (mixinClassName.equals(MIXIN_PACKAGE + "EzfixHighfrequencyRecursionGuardMixin")) {
            return classExists(HIGH_FREQUENCY_CLASS) && classExists(ATTACK_MANAGER_CLASS);
        }
        if (mixinClassName.equals(MIXIN_PACKAGE + "EzfixHighfrequencyStarRecursionGuardMixin")) {
            // 与 registry 包版本互斥的 starSE 包版本（1.3.1 实例）。
            return classExists(HIGH_FREQUENCY_STAR_CLASS) && classExists(ATTACK_MANAGER_CLASS);
        }
        if (mixinClassName.equals(MIXIN_PACKAGE + "EzfixNeutralizeModernFixSpinWaitMixin")) {
            // 仅在装了 ModernFix 的实例上应用（其 fix_loop_spin_waiting 会覆盖
            // MinecraftServer.m_5667_；缺席时目标方法不存在，require=0 也会跳过）。
            return classExists(MODERNFIX_CLASS);
        }
        // ---- 以下为跨实例版本差异守卫：目标类不存在时静默跳过 ----
        // tcc「真我」仅 1.4.16+ 有 ZhenWo/ZhenWoGuard；1.3.0 实例无此类。
        if (mixinClassName.equals(MIXIN_PACKAGE + "TccZhenWoStackGuardMixin")) {
            return classExists(TCC_ZHENWO_GUARD_CLASS);
        }
        if (mixinClassName.equals(MIXIN_PACKAGE + "TccZhenWoInternalGuardMixin")) {
            return classExists(TCC_ZHENWO_CLASS);
        }
        if (mixinClassName.equals(MIXIN_PACKAGE + "PickablePetsSaveGuardMixin")) {
            return classExists(PICKABLE_PETS_CLASS);
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass,
                         String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass,
                          String mixinClassName, IMixinInfo mixinInfo) {
    }

    private boolean classExists(String className) {
        try {
            Class.forName(className, false, getClass().getClassLoader());
            return true;
        } catch (Throwable e) {
            return classResourceExists(className);
        }
    }

    private boolean classResourceExists(String className) {
        String path = className.replace('.', '/') + ".class";
        try {
            return Thread.currentThread().getContextClassLoader().getResource(path) != null;
        } catch (Throwable e) {
            return false;
        }
    }
}
