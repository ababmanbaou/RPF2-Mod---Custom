package com.rejahtavi.rfp2.network;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

// 空消息（仅用于触发服务器消耗逻辑，无需携带数据，服务器可直接获取发送者）
public class StimulantConsumeMessage implements IMessage
{
    @Override
    public void fromBytes(ByteBuf buf) {
        // 无需读取数据
    }

    @Override
    public void toBytes(ByteBuf buf) {
        // 无需写入数据
    }
}
