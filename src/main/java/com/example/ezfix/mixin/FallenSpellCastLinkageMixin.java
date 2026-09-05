package com.example.ezfix.mixin;

import dev.shadowsoffire.apotheosis.adventure.loot.LootRarity;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.kayn.fallen_gems_affixes.adventure.affix.SpellCastAffix;
import net.kayn.fallen_gems_affixes.util.SpellCastUtil;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 修复 Fallen Gems & Affixes 与 Iron's Spells 版本不匹配时的实体 tick 崩溃。
 *
 * 历史崩溃涉及多个被 Iron's Spells 重命名/移动的网络包：
 * ClientboundUpdateCastingState、ClientboundSyncTargetingData。
 * 这些错误均为 NoClassDefFoundError（LinkageError），而 Fallen 原代码只捕获
 * Exception，因此错误会逃逸到实体 tick 并关闭服务器。
 *
 * 在延迟施法任务调用 SpellCastUtil.castSpell 的唯一入口包裹 LinkageError。
 * 当前兼容版本正常执行；未来或旧版 API 漂移时只取消该次词缀施法，实体与服务器继续运行。
 */
@Mixin(value = SpellCastAffix.class, remap = false)
public abstract class FallenSpellCastLinkageMixin {

    private static final Set<String> EZFIX$REPORTED_LINKAGE = ConcurrentHashMap.newKeySet();

    @Redirect(
            method = "lambda$triggerSpell$9",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/kayn/fallen_gems_affixes/util/SpellCastUtil;castSpell(Lnet/minecraft/world/entity/LivingEntity;Lio/redspace/ironsspellbooks/api/spells/AbstractSpell;ILnet/minecraft/world/entity/LivingEntity;)V"
            ),
            remap = false
    )
    private void ezfix$guardSpellCast(LivingEntity caster, AbstractSpell spell, int spellLevel,
                                      LivingEntity target) {
        try {
            SpellCastUtil.castSpell(caster, spell, spellLevel, target);
        } catch (LinkageError linkageError) {
            String key = linkageError.getClass().getName() + ":" + linkageError.getMessage();
            if (EZFIX$REPORTED_LINKAGE.add(key)) {
                System.err.println("[ezfix] Fallen Gems spell cast skipped because Iron's Spells API changed: " + key);
                linkageError.printStackTrace(System.err);
            }
        }
    }
}
