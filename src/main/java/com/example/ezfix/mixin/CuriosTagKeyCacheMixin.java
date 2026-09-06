package com.example.ezfix.mixin;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import top.theillusivec4.curios.mixin.CuriosImplMixinHooks;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 修复 curios 5.14.1 的 TagKey intern 风暴导致的渲染线程卡死。
 *
 * 看门狗线程堆栈实证（渲染线程 RUNNABLE 卡死点）：
 *   at com.google.common.collect.Interners$InternerImpl.intern
 *   at net.minecraft.tags.TagKey.m_203882_(TagKey.java:36)
 *   at net.minecraft.tags.ItemTags.create(ItemTags.java:116)
 *   at top.theillusivec4.curios.mixin.CuriosImplMixinHooks.lambda$static$9
 *   at CuriosImplMixinHooks.testCurioPredicates
 *   at CuriosApi.getItemStackSlots
 *
 * 原因：curios 的饰品谓词每次调用都 new ResourceLocation 并调用
 * ItemTags.create -> TagKey.create -> Guava Interner.intern。Guava 的
 * intern 是弱引用表：每次传入的都是新对象，intern 需遍历 segment 并插入，
 * 条目在 GC 下反复失效重建。getItemStackSlots 被物品栏/tooltip/各模组
 * 高频调用，每秒数千次 intern，配合大堆 GC 形成风暴，渲染线程长时间
 * 消耗在 MapMakerInternalMap.getEntry 中（表现：fps 降至 1-2，"未响应"）。
 *
 * 修复：缓存 curios 槽位 TagKey（ConcurrentHashMap 按 ResourceLocation
 * 去重），首次创建后复用同一对象，彻底绕过 Guava intern 风暴。
 */
@Mixin(value = CuriosImplMixinHooks.class, remap = false)
public abstract class CuriosTagKeyCacheMixin {

    private static final ConcurrentHashMap<ResourceLocation, TagKey<Item>> EZFIX$TAG_CACHE = new ConcurrentHashMap<>();

    @Redirect(
            method = "lambda$static$9",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/tags/ItemTags;create(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/tags/TagKey;",
                    remap = false
            ),
            require = 0,
            remap = false
    )
    private static TagKey<Item> ezfix$cachedTag(ResourceLocation location) {
        // 无锁缓存：命中直接返回；未命中在锁外创建（TagKey.create 内部含
        // Guava intern，可能较慢），再 putIfAbsent 合并。避免 computeIfAbsent
        // 的映射函数在锁内执行导致多线程自旋卡死（1.4.11 回归缺陷修复）。
        TagKey<Item> existing = EZFIX$TAG_CACHE.get(location);
        if (existing != null) {
            return existing;
        }
        TagKey<Item> created = TagKey.create(Registries.ITEM, location);
        TagKey<Item> winner = EZFIX$TAG_CACHE.putIfAbsent(location, created);
        return winner != null ? winner : created;
    }
}
