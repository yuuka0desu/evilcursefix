package com.example.ezfix.mixin;

import com.qoocies.goeticlegacy.common.prefab.job.PrefabJobRuntime;
import com.qoocies.goeticlegacy.event.prefab.PrefabJobLifecycleEvents;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 修复诡厄遗物关闭容器事件的无限递归（StackOverflowError）。
 *
 * 原始递归链：
 * PlayerContainerEvent.Close
 * -> PrefabJobLifecycleEvents.onContainerClose
 * -> PrefabJobRuntime.syncPlayerBlueprints
 * -> player.containerMenu.broadcastChanges
 * -> SophisticatedCore StorageContainerMenuBase 检测到存储状态异常
 * -> player.closeContainer
 * -> 再次发布 PlayerContainerEvent.Close
 * -> 无限递归。
 *
 * 此处只包裹 onContainerClose 对 syncPlayerBlueprints 的调用，并按玩家 UUID
 * 添加线程内重入保护。第一次同步完整执行；同步期间再次产生的同一玩家关闭事件
 * 跳过嵌套同步，让 closeContainer 正常完成。finally 保证异常时也会清理保护状态。
 */
@Mixin(value = PrefabJobLifecycleEvents.class, remap = false)
public abstract class GoeticLegacyContainerCloseMixin {

    private static final ThreadLocal<Set<UUID>> EZFIX$SYNCING_PLAYERS =
            ThreadLocal.withInitial(HashSet::new);

    @Redirect(
            method = "onContainerClose",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/qoocies/goeticlegacy/common/prefab/job/PrefabJobRuntime;syncPlayerBlueprints(Lnet/minecraft/server/level/ServerPlayer;)V"
            ),
            remap = false
    )
    private static void ezfix$guardBlueprintSync(PrefabJobRuntime runtime, ServerPlayer player) {
        Set<UUID> syncingPlayers = EZFIX$SYNCING_PLAYERS.get();
        UUID playerId = player.getUUID();

        if (!syncingPlayers.add(playerId)) {
            return;
        }

        try {
            runtime.syncPlayerBlueprints(player);
        } finally {
            syncingPlayers.remove(playerId);
            if (syncingPlayers.isEmpty()) {
                EZFIX$SYNCING_PLAYERS.remove();
            }
        }
    }
}
