package com.rejahtavi.rfp2.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import com.rejahtavi.rfp2.RFP2;

public class StimulantConsumeResponseMessage implements IMessage {
    private boolean success;

    // 必须有默认构造函数
    public StimulantConsumeResponseMessage() {}

    public StimulantConsumeResponseMessage(boolean success) {
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

    // 客户端处理器
    public static class Handler implements IMessageHandler<StimulantConsumeResponseMessage, IMessage> {
        @Override
        public IMessage onMessage(StimulantConsumeResponseMessage message, MessageContext ctx) {
            Minecraft.getMinecraft().addScheduledTask(() -> {
                if (message.success) {
                    // 服务器确认消耗成功，更新客户端状态
                    RFP2.state.handleStimulantSuccess();
                } else {
                    // 消耗失败，回滚视觉效果
                    RFP2.state.handleStimulantFailure();
                }
            });
            return null;
        }
    }
}