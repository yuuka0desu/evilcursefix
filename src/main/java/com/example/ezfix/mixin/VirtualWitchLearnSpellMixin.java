package com.example.ezfix.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 修复 Virtual Witch Arrangement 研究界面引用旧版 Iron's Spells 网络包导致的客户端崩溃。
 *
 * 不同整合包版本中 Iron's Spells 的网络包类名不同：
 *   新版（3.15.x）：network.spells.LearnSpellPacket + setup.PacketDistributor
 *   旧版（3.4.x）：无 LearnSpellPacket（VWA 指向 network.ServerboundLearnSpell）
 *
 * 本注入**不直接引用任何 ISS 网络包类**，全部用反射探测并容错：
 * 任何版本/类缺失都只跳过本次发送，绝不在 mixin 应用期触发类加载崩溃。
 *
 * javap 实证（m_88315_ 字节码）：NEW ServerboundLearnSpell 位于
 * "heldSpellTime > 15" 分支内，仅按住学习 15 tick 后每帧触发一次；
 * NEW 之后的字节码依次是 sendToServer、学习成功音效（LEARN_ELDRITCH_SPELL）、
 * heldSpellTime=-1 重置、蓄力 UI_TICK 音效。因此：
 * 1. 注入点只在学习分支触发，不存在"每帧执行"问题；
 * 2. 但 cancel 会跳过原 heldSpellTime=-1 重置，导致按住期间每帧重发——
 *    此处 handler 发送成功后自行把 heldSpellTime 重置为 -1，与原语义一致；
 * 3. cancel 同时跳过学习成功音效，此处 handler 用反射补放 LEARN_ELDRITCH_SPELL。
 *
 * Field/Method 全部缓存，避免重复 setAccessible/getMethod。
 */
@Pseudo
@Mixin(value = com.rinko1231.vwa.NewEldritchResearchScreen.class, remap = false)
public abstract class VirtualWitchLearnSpellMixin {

    private static volatile Field EZFIX$NODES_FIELD;
    private static volatile Field EZFIX$HELD_INDEX_FIELD;
    private static volatile Field EZFIX$ACTIVE_HAND_FIELD;
    private static volatile Field EZFIX$HELD_TIME_FIELD;
    private static volatile Field EZFIX$SPELL_NODE_SPELL_FIELD;
    private static volatile Method EZFIX$PLAY_SOUND_METHOD;

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
            Object spell = getSpellNodeSpell(node);
            if (spell == null) {
                ci.cancel();
                return;
            }
            Method getId = spell.getClass().getMethod("getSpellId");
            Object spellId = getId.invoke(spell);
            Object hand = readActiveHand();
            sendLearnPacket(hand, spellId);
            // 原字节码在 NEW 之后执行 heldSpellTime = -1；cancel 会跳过它，
            // 导致按住期间每帧重发学习包。此处自行重置，恢复原语义。
            if (EZFIX$HELD_TIME_FIELD != null) {
                EZFIX$HELD_TIME_FIELD.set(this, -1);
            }
            playLearnSound();
        } catch (Throwable error) {
            System.err.println("[ezfix] Virtual Witch learn-spell compatibility packet failed:");
            error.printStackTrace(System.err);
        }
        // 阻止执行随后引用已删除 ServerboundLearnSpell 类的旧字节码。
        ci.cancel();
    }

    private Object getNodeByHeldIndex() throws Exception {
        Field heldField = EZFIX$HELD_INDEX_FIELD;
        if (heldField == null) {
            heldField = findField(this.getClass(), "heldSpellIndex");
            if (heldField == null) {
                return null;
            }
            EZFIX$HELD_INDEX_FIELD = heldField;
        }
        if (!(heldField.get(this) instanceof Integer idx)) {
            return null;
        }
        Field nodesField = EZFIX$NODES_FIELD;
        if (nodesField == null) {
            nodesField = findField(this.getClass(), "nodes");
            if (nodesField == null) {
                return null;
            }
            EZFIX$NODES_FIELD = nodesField;
        }
        if (!(nodesField.get(this) instanceof List<?> nodes)) {
            return null;
        }
        if (idx < 0 || idx >= nodes.size()) {
            return null;
        }
        return nodes.get(idx);
    }

    private Object getSpellNodeSpell(Object node) throws Exception {
        Field f = EZFIX$SPELL_NODE_SPELL_FIELD;
        if (f == null) {
            Class<?> spellNodeClass = Class.forName("com.rinko1231.vwa.NewEldritchResearchScreen$SpellNode");
            f = spellNodeClass.getDeclaredField("spell");
            f.setAccessible(true);
            EZFIX$SPELL_NODE_SPELL_FIELD = f;
        }
        return f.get(node);
    }

    private Object readActiveHand() throws Exception {
        Field f = EZFIX$ACTIVE_HAND_FIELD;
        if (f == null) {
            f = findField(this.getClass(), "activeHand");
            if (f == null) {
                return null;
            }
            EZFIX$ACTIVE_HAND_FIELD = f;
        }
        return f.get(this);
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
     * 补放原字节码中 NEW 之后的学习成功音效（LEARN_ELDRITCH_SPELL）。
     * 反射调用 LocalPlayer.m_6330_(SoundEvent, SoundSource, float, float)，
     * 任何一步失败仅静默（音效缺失不影响功能）。
     */
    private void playLearnSound() {
        try {
            Method m = EZFIX$PLAY_SOUND_METHOD;
            if (m == null) {
                m = LocalPlayer.class.getMethod("m_6330_", SoundEvent.class, SoundSource.class, float.class, float.class);
                EZFIX$PLAY_SOUND_METHOD = m;
            }
            Object soundEvent = null;
            Class<?> soundRegistry = Class.forName("io.redspace.ironsspellbooks.registries.SoundRegistry");
            Field learnField = soundRegistry.getField("LEARN_ELDRITCH_SPELL");
            Object registryObject = learnField.get(null);
            if (registryObject != null) {
                Method get = registryObject.getClass().getMethod("get");
                soundEvent = get.invoke(registryObject);
            }
            if (soundEvent == null) {
                return;
            }
            // 运行时反射拿玩家（SRG 名 m_91087_/f_91074_ 仅存在于运行时，
            // 编译路径为 official 名，直接引用会编译失败）
            Class<?> mcClass = Class.forName("net.minecraft.client.Minecraft");
            Object mc = mcClass.getMethod("m_91087_").invoke(null);
            Object player = mcClass.getField("f_91074_").get(mc);
            if (player == null) {
                return;
            }
            float pitch = 0.9F + ThreadLocalRandom.current().nextFloat() * 0.2F;
            m.invoke(player, soundEvent, SoundSource.valueOf("MASTER"), 1.0F, pitch);
        } catch (Throwable ignored) {
            // 音效为可选反馈：失败静默
        }
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
