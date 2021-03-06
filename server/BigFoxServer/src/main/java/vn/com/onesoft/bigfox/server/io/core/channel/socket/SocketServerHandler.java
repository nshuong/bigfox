/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package vn.com.onesoft.bigfox.server.io.core.channel.socket;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import java.util.Random;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import vn.com.onesoft.bigfox.server.io.core.data.compress.BFCompressManager;
import vn.com.onesoft.bigfox.server.io.core.data.encrypt.BFEncryptManager;
import vn.com.onesoft.bigfox.server.io.core.data.pack.BFPacker;
import vn.com.onesoft.bigfox.server.io.core.business.session.BFSessionManager;
import vn.com.onesoft.bigfox.server.io.core.business.session.IBFSession;
import vn.com.onesoft.bigfox.server.io.message.base.BFLogger;
import vn.com.onesoft.bigfox.server.io.message.base.MessageExecute;
import vn.com.onesoft.bigfox.server.io.message.core.sc.SCValidationCode;
import vn.com.onesoft.bigfox.server.main.Main;

/**
 *
 * @author Quan
 */
public class SocketServerHandler extends ChannelInboundHandlerAdapter {

    public static ScheduledThreadPoolExecutor scheduleThread = new ScheduledThreadPoolExecutor(10);

    MessageExecute mf = MessageExecute.getInstance();

    public SocketServerHandler() {
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        IBFSession session = BFSessionManager.getInstance().getSessionByChannel(ctx.channel());
        if (session != null) {
            session.setLastTimeReceive(System.currentTimeMillis());
        }
        ByteBuf buf = (ByteBuf) msg;
        int length = buf.getInt(buf.readerIndex());
        byte[] data = new byte[length];
        buf.readBytes(data);
        data = BFPacker.pack(ctx.channel(), data);
        if (data != null) {
            try {

                //HuongNS
//                data = BFCompressManager.getInstance().decompress(data);
//                data = BFEncryptManager.crypt(ctx.channel(), data);

                mf.onMessage(ctx.channel(), data);
            } catch (Exception ex) {
                ctx.channel().close();
                BFLogger.getInstance().error(ex.getMessage(), ex);
            }
        }
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        BFLogger.getInstance().debug("channelActive : " + ctx.channel());
        Main.allChannels.add(ctx.channel());
        Random r = new Random();
        int validationCode = 0; // r.nextInt();
        BFSessionManager.getInstance().sendMessage(ctx.channel(), new SCValidationCode(validationCode));
        BFEncryptManager.mapChannelToValidationCode.put(ctx.channel(), validationCode);

    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        BFLogger.getInstance().debug("ChannelClosed: " + ctx.channel());
        IBFSession session = BFSessionManager.getInstance().getSessionByChannel(ctx.channel());
        if (session != null && session.getChannel() == ctx.channel()) {
            session.close();
        }
        BFSessionManager.getInstance().onChannelClose(ctx.channel());
        Main.allChannels.remove(ctx.channel());
        BFEncryptManager.mapChannelToValidationCode.remove(ctx.channel());

    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        BFLogger.getInstance().error(cause.getMessage(), cause);
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
        BFLogger.getInstance().debug("Channel unregistered");
    }

}
