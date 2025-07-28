package com.rejahtavi.rfp2.network;

import com.rejahtavi.rfp2.RFP2;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class ArmorPlateConsumeResponseMessage implements IMessage {
    private boolean success;

    public ArmorPlateConsumeResponseMessage() {}

    public ArmorPlateConsumeResponseMessage(boolean success) {
        this.success = success;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        success = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(success);
    }

    public static class Handler implements IMessageHandler<ArmorPlateConsumeResponseMessage, IMessage> {
        @Override
        public IMessage onMessage(ArmorPlateConsumeResponseMessage message, MessageContext ctx) {
            Minecraft.getMinecraft().addScheduledTask(() -> {
                EntityPlayer player = Minecraft.getMinecraft().player;
                if (player != null) {
                    if (message.success) {
                        // 处理成功使用防弹插板的逻辑
                        RFP2.state.handleArmorPlateSuccess();
                    } else {
                        // 通知玩家没有防弹插板
                        RFP2.logToChat(TextFormatting.RED + "无防弹插板");
                    }
                }
            });
            return null;
        }
    }
}