package com.example.ezfix.mixin;

import com.example.ezfix.util.EzfixMonitor;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;

/**
 * 服务器心跳：在 MinecraftServer tick 循环（m_5703_）末尾更新看门狗心跳。
 * 服务器停止（m_7041_ 正常停止 / m_7570_ 紧急停止）时清零心跳，
 * 避免退出世界后监控误报"服务器卡死"。
 */
@Mixin(value = MinecraftServer.class, remap = false)
public abstract class EzfixServerHeartbeatMixin {

    @Inject(method = "m_5703_", at = @At("TAIL"), remap = false)
    private void ezfix$serverTickHeartbeat(BooleanSupplier hasTime, CallbackInfo ci) {
        EzfixMonitor.serverHeartbeatMillis = System.currentTimeMillis();
        EzfixMonitor.serverTicks++;
    }

    @Inject(method = "m_7041_", at = @At("HEAD"), remap = false)
    private void ezfix$serverStopped(CallbackInfo ci) {
        EzfixMonitor.serverHeartbeatMillis = 0L;
    }

    @Inject(method = "m_7570_", at = @At("HEAD"), remap = false)
    private void ezfix$serverHalted(boolean wait, CallbackInfo ci) {
        EzfixMonitor.serverHeartbeatMillis = 0L;
    }
}
