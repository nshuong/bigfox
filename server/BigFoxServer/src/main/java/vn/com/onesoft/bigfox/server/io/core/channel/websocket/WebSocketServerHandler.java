/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package vn.com.onesoft.bigfox.server.io.core.channel.websocket;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import static io.netty.handler.codec.http.HttpHeaders.Names.HOST;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshakerFactory;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import java.util.Random;
import vn.com.onesoft.bigfox.server.io.core.data.encrypt.BFEncryptManager;
import vn.com.onesoft.bigfox.server.io.core.business.session.BFSessionManager;
import vn.com.onesoft.bigfox.server.io.core.business.session.IBFSession;
import vn.com.onesoft.bigfox.server.io.message.base.BFLogger;
import vn.com.onesoft.bigfox.server.io.message.base.MessageExecute;
import vn.com.onesoft.bigfox.server.io.message.core.sc.SCValidationCode;
import vn.com.onesoft.bigfox.server.main.Main;

/**
 *
 * @author phamquan
 */
public class WebSocketServerHandler extends SimpleChannelInboundHandler<Object> {

    private static final String WEBSOCKET_PATH = "/livetube";

    private WebSocketServerHandshaker handshaker;
    MessageExecute mf = MessageExecute.getInstance();

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        IBFSession session = BFSessionManager.getInstance().getSessionByChannel(ctx.channel());
        if (session != null) {
            session.setLastTimeReceive(System.currentTimeMillis());
        }
        if (msg instanceof FullHttpRequest) {
            handleHttpRequest(ctx, (FullHttpRequest) msg);
        } else if (msg instanceof WebSocketFrame) {

            handleWebSocketFrame(ctx, (WebSocketFrame) msg);
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();

    }

    private void handleHttpRequest(final ChannelHandlerContext ctx, FullHttpRequest req) {
//        if (req.uri().contains("vivulive"))
//            ctx.channel().close();
        // Handshake
        WebSocketServerHandshakerFactory wsFactory = new WebSocketServerHandshakerFactory(
                getWebSocketLocation(req), null, true);
        handshaker = wsFactory.newHandshaker(req);
        if (handshaker == null) {
            WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(ctx.channel());
        } else {
            handshaker.handshake(ctx.channel(), req).addListener(new GenericFutureListener() {

                @Override
                public void operationComplete(Future f) throws Exception {
                    Main.mapChannelWebSocket.put(ctx.channel(), Boolean.TRUE);
                    BFLogger.getInstance().debug("Client connected!: " + ctx.channel());
                    Main.allChannels.add(ctx.channel());
                    Random r = new Random();
                    int validationCode = r.nextInt();
                    BFSessionManager.getInstance().sendMessage(ctx.channel(), new SCValidationCode(validationCode));
                    BFEncryptManager.mapChannelToValidationCode.put(ctx.channel(), validationCode);
                }
            });
        }
    }

    private void handleWebSocketFrame(ChannelHandlerContext ctx, WebSocketFrame frame) {

        // Check for closing frame
        if (frame instanceof CloseWebSocketFrame) {
            handshaker.close(ctx.channel(), (CloseWebSocketFrame) frame.retain());
            return;
        }
        if (frame instanceof PingWebSocketFrame) {
            ctx.channel().write(new PongWebSocketFrame(frame.content().retain()));
            return;
        }
        if (frame instanceof BinaryWebSocketFrame) {
            BinaryWebSocketFrame bFrame = (BinaryWebSocketFrame) frame;
            ByteBuf buf = bFrame.content();
            int length = buf.capacity();

            byte[] data = new byte[length];
            buf.readBytes(data);
            try {

                //HuongNS
//                data = BFCompressManager.getInstance().decompress(data);
//                data = BFEncryptManager.crypt(ctx.channel(), data);
                mf.onMessage(ctx.channel(), data); //Thực thi yêu cầu từ Client
            } catch (Exception ex) {
                ctx.channel().close();
//                BFLogger.getInstance().error("handleWebSocketFrame - Exception");
                BFLogger.getInstance().error(ex.getMessage(), ex);
            }
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        ctx.close();
    }

    private static String getWebSocketLocation(FullHttpRequest req) {
        String location = req.headers().get(HOST) + WEBSOCKET_PATH;

        return "wss://" + location;

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
    protected void channelRead0(ChannelHandlerContext chc, Object i) throws Exception {

    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt instanceof SslHandshakeCompletionEvent) {
            if (!((SslHandshakeCompletionEvent) evt).isSuccess()) {
                SslHandshakeCompletionEvent evs = (SslHandshakeCompletionEvent) evt;
                BFLogger.getInstance().error(evs, evs.cause());
            }

        }
    }

}
