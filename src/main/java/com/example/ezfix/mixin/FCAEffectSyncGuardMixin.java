package com.example.ezfix.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ConcurrentModificationException;

/**
 * 修复 flame_chase_artifacts（追焰神器）药水效果同步导致的属性遍历竞态崩溃：
 *   ConcurrentModificationException
 *   at HashMap.forEach
 *   at AttributeMap.setAttributesUpdating(AttributeMap.java:562)
 *
 * 触发链（1.3.1 实例、flame_chase_artifacts 1.1.9）：
 *   神化药水护符 inventoryTick -> addEffect -> MobEffectEvent.Added
 *   -> Trickery.buff -> FCAEntityHelper.shortenEffect -> syncEffect
 *   -> ServerPlayer.m_141973_(属性重算) -> 神化 onEffectUpdateAddAttribute
 *   -> AttributeMap 遍历中被修改 -> CME
 *
 * 修复：拦截 syncEffect 中的属性重算调用，捕获 CME 并跳过本次同步
 * （fail-fast 后 map 无损，下个 tick 会重新同步）。
 *
 * 注意：目标调用为 SRG 名 m_141973_（3 参数与无参两个重载），
 * handler 用反射调用以避开编译期 MCP 命名依赖，并覆盖各版本差异。
 */
@Mixin(targets = "com.inolia_zaicek.flame_chase_artifacts.util.FCAEntityHelper", remap = false)
public abstract class FCAEffectSyncGuardMixin {

    private static volatile Method EZFIX$M_141973_3;
    private static volatile Method EZFIX$M_141973_0;

    @Redirect(
            method = "syncEffect",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerPlayer;m_141973_(Lnet/minecraft/world/effect/MobEffectInstance;ZLnet/minecraft/world/entity/Entity;)V",
                    remap = false
            ),
            require = 0,
            remap = false
    )
    private static void ezfix$safeAttributeSync(ServerPlayer player, MobEffectInstance effect, boolean flag, Entity source) {
        try {
            Method m = EZFIX$M_141973_3;
            if (m == null) {
                m = ServerPlayer.class.getMethod(
                        "m_141973_", MobEffectInstance.class, boolean.class, Entity.class);
                EZFIX$M_141973_3 = m;
            }
            m.invoke(player, effect, flag, source);
        } catch (NoSuchMethodException e) {
            // 运行时不存在该重载（版本差异）：不做任何事
        } catch (InvocationTargetException e) {
            if (!(e.getCause() instanceof ConcurrentModificationException)) {
                // 非 CME 异常保持原传播语义
                throw new RuntimeException(e.getCause());
            }
            // CME：属性 map 遍历期间的竞态，跳过本次同步，map 未损坏
        } catch (IllegalAccessException ignored) {
        }
    }

    @Redirect(
            method = "syncEffect",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerPlayer;m_141973_()V",
                    remap = false
            ),
            require = 0,
            remap = false
    )
    private static void ezfix$safeAttributeSyncNoArgs(ServerPlayer player) {
        try {
            Method m = EZFIX$M_141973_0;
            if (m == null) {
                m = ServerPlayer.class.getMethod("m_141973_");
                EZFIX$M_141973_0 = m;
            }
            m.invoke(player);
        } catch (NoSuchMethodException e) {
            // 运行时不存在该重载（版本差异）：不做任何事
        } catch (InvocationTargetException e) {
            if (!(e.getCause() instanceof ConcurrentModificationException)) {
                throw new RuntimeException(e.getCause());
            }
            // CME：跳过本次同步
        } catch (IllegalAccessException ignored) {
        }
    }
}
