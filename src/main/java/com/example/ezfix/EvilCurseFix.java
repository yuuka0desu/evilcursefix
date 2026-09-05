package com.example.ezfix;

import com.example.ezfix.util.EzfixMonitor;
import net.minecraftforge.fml.common.Mod;

/**
 * 恶咒修复 (Evil Curse Fix)
 *
 * 针对整合包"终恶重置1.3.1"(Forge 1.20.1 / 47.4.0) 错误报告的专用修复模组：
 *  - 修复 EventBus 异常日志路径的 log4j MessageSupplier 类加载冲突导致的致命崩溃
 *  - 修复诡厄巫法(Goety) 等模组村民/流浪商人交易类的空指针异常
 *  - 修复神化(Apotheosis) 打孔配方在 JEI 中的越界错误
 *  - 内置独立看门狗线程：周期输出游戏状态摘要；检测到服务器/渲染线程
 *    卡死时自动打印全线程堆栈，用于定位"游戏未响应"类问题。
 *
 * 注意：内部包名保留历史命名 com.example.ezfix（不影响模组外部可见性，
 * 模组 ID/显示名/产物名均为 evilcursefix / 恶咒修复 (Evil Curse Fix)）。
 */
@Mod("evilcursefix")
public class EvilCurseFix {

    public EvilCurseFix() {
        // 启动独立监控线程（daemon，只读心跳并输出日志，绝不干扰主线程）。
        EzfixMonitor.start();
    }
}
