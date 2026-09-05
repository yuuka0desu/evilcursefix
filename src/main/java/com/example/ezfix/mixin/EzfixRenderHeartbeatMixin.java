package com.example.ezfix.mixin;

import com.example.ezfix.util.EzfixMonitor;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 渲染心跳：在客户端渲染循环（Minecraft.m_91383_）末尾更新看门狗心跳，
 * 用于检测渲染线程卡死（"游戏未响应"）。
 */
@Mixin(value = Minecraft.class, remap = false)
public abstract class EzfixRenderHeartbeatMixin {

    @Inject(method = "m_91383_", at = @At("TAIL"), remap = false)
    private void ezfix$renderFrameHeartbeat(CallbackInfo ci) {
        EzfixMonitor.renderHeartbeatMillis = System.currentTimeMillis();
        EzfixMonitor.renderFrames++;
    }
}
