package com.example.ezfix.mixin;

import dev.shadowsoffire.apotheosis.adventure.loot.LootCategory;
import dev.shadowsoffire.apotheosis.adventure.loot.LootController;
import dev.shadowsoffire.apotheosis.adventure.loot.LootRarity;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 修复 Apotheosis 全局掉落转换器为物品找不到可用词缀时抛异常，
 * 进而导致死亡掉落链中的实体 tick 崩溃。
 *
 * 崩溃示例：heart_of_revenge / curios:heart / apotheosis:epic
 * RuntimeException: Failed to locate any affixes...
 *
 * 注入点是原方法仅在「最终词缀列表为空」分支调用的 String.format，
 * 因此此处返回原 ItemStack 等价于“本次随机词缀转换失败，保留正常原始掉落”。
 * 其他能够生成词缀的装备仍走 Apotheosis 原逻辑。
 */
@Mixin(value = LootController.class, remap = false)
public abstract class ApotheosisEmptyAffixMixin {

    private static final Set<String> EZFIX$REPORTED_CATEGORIES = ConcurrentHashMap.newKeySet();

    @Inject(
            method = "createLootItem(Lnet/minecraft/world/item/ItemStack;Ldev/shadowsoffire/apotheosis/adventure/loot/LootCategory;Ldev/shadowsoffire/apotheosis/adventure/loot/LootRarity;Lnet/minecraft/util/RandomSource;)Lnet/minecraft/world/item/ItemStack;",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/lang/String;format(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;"
            ),
            cancellable = true,
            remap = false
    )
    private static void ezfix$keepOriginalDrop(ItemStack stack, LootCategory category, LootRarity rarity,
                                                RandomSource random,
                                                CallbackInfoReturnable<ItemStack> cir) {
        String key = category + " / " + rarity;
        if (EZFIX$REPORTED_CATEGORIES.add(key)) {
            System.err.println("[ezfix] Apotheosis found no affixes for " + stack.getItem()
                    + " (" + key + "); keeping the original loot item.");
        }
        cir.setReturnValue(stack);
    }
}
