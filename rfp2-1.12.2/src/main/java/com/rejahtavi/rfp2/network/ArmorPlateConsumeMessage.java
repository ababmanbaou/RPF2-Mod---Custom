package com.rejahtavi.rfp2.network;

import com.rejahtavi.rfp2.RFP2;
import com.rejahtavi.rfp2.item.ItemArmorPlate;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class ArmorPlateConsumeMessage implements IMessage {
    @Override
    public void fromBytes(ByteBuf buf) {
        // 不需要传递数据，仅作为使用请求
    }

    @Override
    public void toBytes(ByteBuf buf) {
        // 不需要传递数据，仅作为使用请求
    }

    // 使用 IMessageHandler 接口替代 AbstractMessageHandler
    public static class Handler implements IMessageHandler<ArmorPlateConsumeMessage, IMessage> {
        @Override
        public IMessage onMessage(ArmorPlateConsumeMessage message, MessageContext ctx) {
            // 获取服务器端玩家
            EntityPlayerMP player = ctx.getServerHandler().player;

            // 在服务器线程中处理消耗逻辑
            player.getServerWorld().addScheduledTask(() -> {
                if (consumeArmorPlate(player)) {
                    // 消耗成功，向客户端发送成功消息
                    RFP2.NETWORK.sendTo(new ArmorPlateConsumeResponseMessage(true), player);
                } else {
                    // 消耗失败
                    RFP2.NETWORK.sendTo(new ArmorPlateConsumeResponseMessage(false), player);
                }
            });

            return null;
        }

        private boolean consumeArmorPlate(EntityPlayer player) {
            // 检查主手
            ItemStack mainHand = player.getHeldItemMainhand();
            if (mainHand.getItem() == ItemArmorPlate.itemArmorPlate) {
                mainHand.shrink(1);
                player.inventory.markDirty();
                return true;
            }

            // 检查副手
            ItemStack offHand = player.getHeldItemOffhand();
            if (offHand.getItem() == ItemArmorPlate.itemArmorPlate) {
                offHand.shrink(1);
                player.inventory.markDirty();
                return true;
            }

            // 检查主物品栏
            for (int i = 0; i < player.inventory.mainInventory.size(); i++) {
                ItemStack stack = player.inventory.mainInventory.get(i);
                if (stack.getItem() == ItemArmorPlate.itemArmorPlate) {
                    stack.shrink(1);
                    player.inventory.markDirty();
                    return true;
                }
            }

            // 检查副手物品栏
            for (int i = 0; i < player.inventory.offHandInventory.size(); i++) {
                ItemStack stack = player.inventory.offHandInventory.get(i);
                if (stack.getItem() == ItemArmorPlate.itemArmorPlate) {
                    stack.shrink(1);
                    player.inventory.markDirty();
                    return true;
                }
            }

            return false;
        }
    }
}