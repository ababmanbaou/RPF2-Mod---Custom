package com.rejahtavi.rfp2.network;

import com.rejahtavi.rfp2.RFP2;
import com.rejahtavi.rfp2.item.ItemArmorPlate;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class ArmorPlateConsumeHandler implements IMessageHandler<ArmorPlateConsumeMessage, IMessage> {

    @Override
    public IMessage onMessage(ArmorPlateConsumeMessage message, MessageContext ctx) {
        // 确保在服务器线程中处理
        if (ctx.side.isServer()) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            if (player != null) {
                player.getServerWorld().addScheduledTask(() -> {
                    handleConsumeRequest(player);
                });
            }
        }
        return null;
    }

    private void handleConsumeRequest(EntityPlayer player) {
        boolean consumed = consumeArmorPlate(player);
        // 向客户端发送响应
        if (consumed) {
            RFP2.NETWORK.sendTo(new ArmorPlateConsumeResponseMessage(true), (EntityPlayerMP) player);
        } else {
            RFP2.NETWORK.sendTo(new ArmorPlateConsumeResponseMessage(false), (EntityPlayerMP) player);
        }
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
