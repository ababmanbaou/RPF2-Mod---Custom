package com.rejahtavi.rfp2.network;

import com.rejahtavi.rfp2.RFP2;
import com.rejahtavi.rfp2.item.Itemstimulant;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class StimulantConsumeHandler implements IMessageHandler<StimulantConsumeMessage, IMessage> {
    @Override
    public IMessage onMessage(StimulantConsumeMessage message, MessageContext ctx) {
        // 在服务器主线程处理
        ctx.getServerHandler().player.getServerWorld().addScheduledTask(() -> {
            EntityPlayerMP player = ctx.getServerHandler().player;
            if (player == null) return;

            // 执行消耗逻辑（必须调用！）
            boolean consumed = consumeStimulantServer(player);
            if (consumed) {

                // 发送成功回执给客户端
                RFP2.NETWORK.sendTo(new StimulantConsumeResponseMessage(true), player);
            } else {

                // 发送失败回执
                RFP2.NETWORK.sendTo(new StimulantConsumeResponseMessage(false), player);
                RFP2.logToChat(TextFormatting.RED + "无强心针");
            }
        });
        return null;
    }

    // 服务器端消耗逻辑（确保执行）
    private boolean consumeStimulantServer(EntityPlayerMP player) {
        // 检查主手
        if (consumeFromHand(player, true)) return true;
        // 检查副手
        if (consumeFromHand(player, false)) return true;
        // 检查背包
        return consumeFromInventory(player);
    }

    private boolean consumeFromHand(EntityPlayerMP player, boolean isMainHand) {
        ItemStack stack = isMainHand ? player.getHeldItemMainhand() : player.getHeldItemOffhand();
        if (stack.getItem() == Itemstimulant.itemstimulant) {
            stack.shrink(1);
            player.inventory.markDirty(); // 强制同步
            return true;
        }
        return false;
    }

    private boolean consumeFromInventory(EntityPlayerMP player) {
        for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
            ItemStack stack = player.inventory.getStackInSlot(i);
            if (stack.getItem() == Itemstimulant.itemstimulant) {
                stack.shrink(1);
                player.inventory.markDirty(); // 强制同步
                return true;
            }
        }
        return false;
    }
}