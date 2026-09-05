package com.example.ezfix.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.level.dimension.DimensionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 合并自 npefix：LightTexture 亮度计算时玩家为空的防护。
 *
 * Alex's Caves 等模组的 LightTextureMixin 在断线/世界切换窗口内对
 * Minecraft.getInstance().player 无判空解引用；此处提前返回满亮度并
 * 跳过整个方法，使被污染的处理器不会在空玩家下运行。
 */
@Mixin(value = LightTexture.class, remap = false)
public abstract class EzfixLightTextureNullGuardMixin {

    @Inject(method = "m_234316_", at = @At("HEAD"), cancellable = true, remap = false)
    private static void ezfix$getBrightnessGuard(DimensionType type, int lightLevel,
                                                 CallbackInfoReturnable<Float> cir) {
        if (Minecraft.getInstance().player == null) {
            cir.setReturnValue(1.0F);
        }
    }

    @Inject(method = "m_109881_", at = @At("HEAD"), cancellable = true, remap = false)
    private void ezfix$updateLightTextureGuard(float partialTick, CallbackInfo ci) {
        if (Minecraft.getInstance().player == null) {
            ci.cancel();
        }
    }
}
