package com.example.ezfix.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/**
 * 修复 Virtual Witch Arrangement 研究界面引用旧版 Iron's Spells 网络包导致的客户端崩溃。
 *
 * 不同整合包版本中 Iron's Spells 的网络包类名不同：
 *   新版（3.15.x）：network.spells.LearnSpellPacket + setup.PacketDistributor
 *   旧版（3.4.x）：无 LearnSpellPacket（VWA 指向 network.ServerboundLearnSpell）
 *
 * 本注入**不直接引用任何 ISS 网络包类**，全部用反射探测并容错：
 * 任何版本/类缺失都只跳过本次发送，绝不在 mixin 应用期触发类加载崩溃。
 * 字段也改用反射读取（VWA 字段变化时同样只降级不崩溃）。
 */
@Pseudo
@Mixin(value = com.rinko1231.vwa.NewEldritchResearchScreen.class, remap = false)
public abstract class VirtualWitchLearnSpellMixin {

    @Inject(
            method = "m_88315_",
            at = @At(
                    value = "NEW",
                    target = "Lio/redspace/ironsspellbooks/network/ServerboundLearnSpell;",
                    shift = At.Shift.BEFORE
            ),
            cancellable = true,
            remap = false
    )
    private void ezfix$sendModernLearnPacket(GuiGraphics graphics, int mouseX, int mouseY,
                                             float partialTick, CallbackInfo ci) {
        try {
            Object node = getNodeByHeldIndex();
            if (node == null) {
                ci.cancel();
                return;
            }
            Field spellField = findField(node.getClass(), "spell");
            if (spellField == null) {
                ci.cancel();
                return;
            }
            Object spell = spellField.get(node);
            if (spell == null) {
                ci.cancel();
                return;
            }
            Method getId = spell.getClass().getMethod("getSpellId");
            Object spellId = getId.invoke(spell);
            Object hand = readField(this, "activeHand");
            sendLearnPacket(hand, spellId);
        } catch (Throwable error) {
            System.err.println("[ezfix] Virtual Witch learn-spell compatibility packet failed:");
            error.printStackTrace(System.err);
        }
        // 阻止执行随后引用已删除 ServerboundLearnSpell 类的旧字节码。
        ci.cancel();
    }

    private Object getNodeByHeldIndex() throws Exception {
        Object held = readField(this, "heldSpellIndex");
        if (!(held instanceof Integer idx)) {
            return null;
        }
        Object nodesObj = readField(this, "nodes");
        if (!(nodesObj instanceof List<?> nodes)) {
            return null;
        }
        if (idx < 0 || idx >= nodes.size()) {
            return null;
        }
        return nodes.get(idx);
    }

    private static Object readField(Object target, String name) throws Exception {
        Field f = findField(target.getClass(), name);
        return f == null ? null : f.get(target);
    }

    private static Field findField(Class<?> cls, String name) {
        for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {
            }
        }
        return null;
    }

    /**
     * 反射发送"学习法术"网络包，兼容 ISS 3.x 各版本的类名差异。
     */
    private static void sendLearnPacket(Object hand, Object spellId) throws Exception {
        Class<?> packetClass = null;
        for (String name : new String[]{
                "io.redspace.ironsspellbooks.network.spells.LearnSpellPacket",
                "io.redspace.ironsspellbooks.network.ServerboundLearnSpell",
                "io.redspace.ironsspellbooks.network.spells.ServerboundLearnSpell",
        }) {
            try {
                packetClass = Class.forName(name);
                break;
            } catch (ClassNotFoundException ignored) {
            }
        }
        if (packetClass == null) {
            throw new ClassNotFoundException("[ezfix] no learn-spell packet class found");
        }
        Object packet = packetClass.getConstructor(InteractionHand.class, ResourceLocation.class)
                .newInstance(hand, spellId);
        for (String sender : new String[]{
                "io.redspace.ironsspellbooks.setup.PacketDistributor",
                "io.redspace.ironsspellbooks.network.PacketHandler",
        }) {
            try {
                Class<?> cls = Class.forName(sender);
                try {
                    cls.getMethod("sendToServer", Object.class).invoke(null, packet);
                    return;
                } catch (NoSuchMethodException noStatic) {
                    Object inst = cls.getField("INSTANCE").get(null);
                    cls.getMethod("sendToServer", Object.class).invoke(inst, packet);
                    return;
                }
            } catch (ClassNotFoundException ignored) {
            }
        }
        throw new ClassNotFoundException("[ezfix] no packet sender found");
    }
}
