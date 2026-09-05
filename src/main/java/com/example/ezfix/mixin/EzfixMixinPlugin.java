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
