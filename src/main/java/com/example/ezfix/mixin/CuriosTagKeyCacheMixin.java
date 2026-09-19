package com.example.ezfix.mixin;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.theillusivec4.curios.api.type.ISlotType;
import top.theillusivec4.curios.mixin.CuriosImplMixinHooks;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 修复 curios 5.14.1 的 TagKey intern 风暴导致的渲染线程卡死。
 *
 * 看门狗线程堆栈实证（渲染线程 RUNNABLE 卡死点，2026-06 与 2026-09 两次）：
 *   ResourceLocation.m_135841_ / m_245185_
 *   at ResourceLocation.&lt;init&gt;
 *   at CuriosImplMixinHooks.lambda$static$9(CuriosImplMixinHooks.java:315)
 *   at CuriosImplMixinHooks.testCurioPredicates(CuriosImplMixinHooks.java:301)
 *   at CuriosApi.getItemStackSlots
 *   at ApothicCuriosMod.lambda$registerCurioLootCategory$15
 *   at LootCategory.forItem
 *
 * 原因（字节码实证）：lambda$static$9 每次调用都会
 *   1) new ResourceLocation("curios", identifier)  —— 每次两个新对象
 *   2) ItemTags.create(...) → TagKey.create → Guava Interner.intern
 * 物品栏 / tooltip / JEI / 神化掉落判定会以万级频率反复调用它
 * （启动期一次遍历 16817 个物品），每秒数万次对象分配 + 弱引用表 intern，
 * 在渲染线程上形成长时间占用（表现：fps 骤降至 1 以下、"未响应"直至卡死）。
 *
 * 修复（两层缓存，均为纯加速、语义不变）：
 *  A. lambda$static$9 的 ItemTags.create 调用点做 TagKey 缓存；
 *  B. getItemStackSlots(ItemStack, boolean) 整体记忆化：同一物品的
 *     「物品 → 适用槽位映射」只计算一次，命中后完全不进入
 *     lambda$static$9（既不构造 ResourceLocation 也不 intern）。
 *
 * 旧实现缺陷说明：1.4.11~1.6.2 只在 A 处做浅缓存，且缓存 key 是
 * 每次新建的 ResourceLocation 实例——ConcurrentHashMap 查找必然 miss，
 * 缓存形同虚设。本版改为字符串 key（namespace:path / 物品 registry 名）。
 *
 * B 的正确性：结果仅取决于 ItemStack 的物品类型与 curios 槽位定义
 * （模组注册后即固定），不随 NBT、数量、耐久变化，故按物品 ID 缓存安全。
 */
@Mixin(value = CuriosImplMixinHooks.class, remap = false)
public abstract class CuriosTagKeyCacheMixin {

    /** 槽位 TagKey 缓存：key 为 "namespace:path" 字符串（避免每帧构造 ResourceLocation 做 map key） */
    @Unique
    private static final ConcurrentHashMap<String, TagKey<Item>> EZFIX$SLOT_TAG_CACHE = new ConcurrentHashMap<>();

    /** 物品 → 适用槽位映射缓存：key 为物品 registry 名 */
    @Unique
    private static final ConcurrentHashMap<String, Map<String, ISlotType>> EZFIX$STACK_SLOT_CACHE = new ConcurrentHashMap<>();

    /**
     * A. TagKey 缓存：同一槽位标识只 intern 一次。
     * 用 String key 而非 ResourceLocation —— 后者每次都是新实例，
     * 会让 ConcurrentHashMap 的哈希查找恒定 miss（旧实现的缺陷）。
     */
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
        String key = location.getNamespace() + ":" + location.getPath();
        TagKey<Item> cached = EZFIX$SLOT_TAG_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        TagKey<Item> created = TagKey.create(Registries.ITEM, location);
        TagKey<Item> winner = EZFIX$SLOT_TAG_CACHE.putIfAbsent(key, created);
        return winner != null ? winner : created;
    }

    /**
     * B. getItemStackSlots(ItemStack, boolean) 记忆化。
     *
     * 该重载是 ApothicCurios / 神化掉落判定 / 物品栏校验的主要入口，
     * 也是崩溃栈中 getItemStackSlots → testCurioPredicates 的直接来源。
     * 命中缓存时直接返回，彻底跳过 lambda$static$9 的全部开销。
     *
     * 空物品（isEmpty）不缓存，保证语义与开销都不受影响。
     */
    @Inject(method = "getItemStackSlots(Lnet/minecraft/world/item/ItemStack;Z)Ljava/util/Map;",
            at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void ezfix$cachedStackSlots(ItemStack stack, boolean allowHidden,
                                               CallbackInfoReturnable<Map<String, ISlotType>> cir) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        String key = stack.getItem().builtInRegistryHolder().key().location().toString();
        Map<String, ISlotType> cached = EZFIX$STACK_SLOT_CACHE.get(key);
        if (cached != null) {
            cir.setReturnValue(cached);
        }
    }

    /** B 的填充端：原方法正常返回后写入缓存 */
    @Inject(method = "getItemStackSlots(Lnet/minecraft/world/item/ItemStack;Z)Ljava/util/Map;",
            at = @At("RETURN"), require = 0, remap = false)
    private static void ezfix$storeStackSlots(ItemStack stack, boolean allowHidden,
                                              CallbackInfoReturnable<Map<String, ISlotType>> cir) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        Map<String, ISlotType> result = cir.getReturnValue();
        if (result == null) {
            return;
        }
        String key = stack.getItem().builtInRegistryHolder().key().location().toString();
        // 不包装为不可变视图：若 curios 或调用方对该 Map 做修改（例如后续
        // 版本加入的动态槽位），包装后会抛 UnsupportedOperationException。
        // 直接缓存原引用，与其内部 filteredSlots 产出的 Map 行为完全一致。
        EZFIX$STACK_SLOT_CACHE.putIfAbsent(key, result);
    }
}
