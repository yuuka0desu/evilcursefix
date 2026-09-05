package com.example.ezfix.mixin;

import dev.shadowsoffire.apotheosis.adventure.socket.SocketingRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

/**
 * 神化（Apotheosis）打孔配方的越界防护。
 *
 * 错误报告（SocketingRecipe.java:63）：
 *   IndexOutOfBoundsException at java.util.ArrayList.set(ArrayList.java:441)
 *   at dev.shadowsoffire.apotheosis.adventure.socket.SocketingRecipe.m_5874_(SocketingRecipe.java:63)
 *
 * 反编译确认第 63 行是 gems 副本列表的 List.set(socketIndex, gem) 调用；
 * JEI 计算配方输出时使用尺寸/内容不匹配的假容器，getFirstEmptySocket
 * 返回的下标可能超出 gems 列表范围，导致越界。
 * 此处拦截 List.set：下标越界时静默跳过（JEI 场景下输出物品保持未打孔状态），
 * 正常游戏流程（下标合法）不受任何影响。
 *
 * 目标类名使用字符串 targets + remap=false：无需编译期依赖 apotheosis jar。
 */
@Mixin(value = SocketingRecipe.class, remap = false)
public abstract class SocketingRecipeMixin {

    @Redirect(method = "m_5874_", remap = false,
            at = @At(value = "INVOKE", target = "Ljava/util/List;set(ILjava/lang/Object;)Ljava/lang/Object;"))
    private Object ezfix$guardListSet(List<Object> list, int index, Object element) {
        if (index < 0 || index >= list.size()) {
            // 越界：跳过本次 set，避免 IndexOutOfBoundsException 刷屏。
            return null;
        }
        return list.set(index, element);
    }
}
