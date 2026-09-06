package com.example.ezfix.mixin;

import com.Polarice3.Goety.api.magic.ISpell;
import com.Polarice3.Goety.common.magic.SpellStat;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.lang.reflect.Method;

/**
 * 修复车万女仆：万法皆通调用旧版诡厄巫法 WandUtil.getStats 的
 * NoSuchMethodError（goety 2.5.35.x 无 (LivingEntity, ISpell) 签名）：
 *   NoSuchMethodError: SpellStat WandUtil.getStats(LivingEntity, ISpell)
 *   at GoetyProvider.getSpellStats(GoetyProvider.java:383)
 *
 * 目标类使用字符串 targets（而非 value=Class）是有意为之：
 * touhou_little_maid_spell 是可选软依赖（build.gradle 条件编译），
 * 用字符串引用避免编译期依赖，缺失时运行时静默跳过；
 * 该 mixin 因此保留一条"should be specified in value"编译警告。
 *
 * 修复：反射探测当前 goety 的 getStats 重载并适配调用——
 *   1. 优先 (LivingEntity, ISpell)（goety 2.5.56.x 存在）；
 *   2. 否则枚举含 ISpell 参数的任意 getStats 重载；
 *   3. 都不存在则返回 null（法术数据缺失但不再崩溃）。
 */
@Mixin(targets = "com.github.yimeng261.maidspell.spell.providers.GoetyProvider", remap = false)
public abstract class GoetyWandStatsCompatMixin {

    private static volatile Method EZFIX$GET_STATS_EXACT;
    private static volatile Method EZFIX$GET_STATS_FALLBACK;

    @Redirect(
            method = "getSpellStats",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/Polarice3/Goety/utils/WandUtil;getStats(Lnet/minecraft/world/entity/LivingEntity;Lcom/Polarice3/Goety/api/magic/ISpell;)Lcom/Polarice3/Goety/common/magic/SpellStat;",
                    remap = false
            ),
            require = 0,
            remap = false
    )
    private static SpellStat ezfix$compatGetStats(LivingEntity entity, ISpell spell) {
        try {
            Method exact = EZFIX$GET_STATS_EXACT;
            if (exact == null) {
                exact = Class.forName("com.Polarice3.Goety.utils.WandUtil")
                        .getMethod("getStats", LivingEntity.class, ISpell.class);
                EZFIX$GET_STATS_EXACT = exact;
            }
            return (SpellStat) exact.invoke(null, entity, spell);
        } catch (NoSuchMethodException noExact) {
            try {
                Method fb = EZFIX$GET_STATS_FALLBACK;
                if (fb == null) {
                    Class<?> util = Class.forName("com.Polarice3.Goety.utils.WandUtil");
                    for (Method m : util.getMethods()) {
                        if (!m.getName().equals("getStats") || m.getParameterCount() == 0) {
                            continue;
                        }
                        Class<?>[] pts = m.getParameterTypes();
                        if (pts[0].isAssignableFrom(LivingEntity.class)
                                && ISpell.class.isAssignableFrom(pts[pts.length - 1])) {
                            fb = m;
                            break;
                        }
                    }
                    EZFIX$GET_STATS_FALLBACK = fb;
                }
                if (fb != null) {
                    Object result = fb.invoke(null, entity, spell);
                    if (result instanceof SpellStat stat) {
                        return stat;
                    }
                }
            } catch (Throwable ignored) {
            }
            return null;
        } catch (Throwable t) {
            System.err.println("[ezfix] Goety WandUtil.getStats compat failed:");
            t.printStackTrace(System.err);
            return null;
        }
    }
}
