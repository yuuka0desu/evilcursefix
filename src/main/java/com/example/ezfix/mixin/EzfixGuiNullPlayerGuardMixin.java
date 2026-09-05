package com.example.ezfix.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 合并自 npefix：HUD 渲染时玩家为空的防御性防护。
 *
 * 世界加载中但本地玩家被清除的过渡窗口内，多个 HUD/渲染模组假定
 * Minecraft.getInstance().player 非空；此处直接跳过该帧 HUD 渲染。
 */
@Mixin(value = Gui.class, remap = false)
public abstract class EzfixGuiNullPlayerGuardMixin {

    @Inject(method = "m_280421_", at = @At("HEAD"), cancellable = true, remap = false)
    private void ezfix$skipRenderWhenPlayerMissing(CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.player == null) {
            ci.cancel();
        }
    }
}
