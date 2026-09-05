package com.example.ezfix.mixin;

import net.minecraft.util.RandomSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

/**
 * 修复 gunsmithlib_std_ammo（枪匠库标准弹药）的商人刷新器崩溃：
 *   IllegalArgumentException: Bound must be positive
 *   at BitRandomSource.m_188503_(BitRandomSource.java:22)
 *   at BulletMerchantSpawner.spawn(BulletMerchantSpawner.java:133)
 *
 * 反编译确认：spawn 对筛选后的在线玩家列表先调用 nextInt(list.size()) 再
 * list.get(idx)。当列表为空（无满足条件的在线玩家）时 size()==0，
 * RandomSource.nextInt(0) 抛 IllegalArgumentException，随后沿实体 tick 链
 * 崩溃服务器。
 *
 * 修复（两层，互相配合）：
 *   1) 把 spawn 内的 nextInt 边界钳制为至少 1——空列表时不会抛异常；
 *   2) 把后续 List.get 改为安全访问——空列表返回 null，
 *      原代码紧跟的 player==null 检查将直接返回 false（原语义：无目标不生成）。
 */
@Mixin(value = cn.chloeprime.gunsmithlib_std_ammo.common.entity.BulletMerchantSpawner.class, remap = false)
public abstract class GunsmithlibSpawnBoundMixin {

    @Redirect(
            method = "spawn",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/RandomSource;m_188503_(I)I",
                    ordinal = 0,
                    remap = false
            ),
            remap = false
    )
    private int ezfix$clampBound(RandomSource random, int bound) {
        return random.nextInt(Math.max(1, bound));
    }

    @Redirect(
            method = "spawn",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/List;get(I)Ljava/lang/Object;",
                    remap = false
            ),
            remap = false
    )
    private Object ezfix$safePlayerGet(List<Object> players, int index) {
        return players.isEmpty() ? null : players.get(index);
    }
}
