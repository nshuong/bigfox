/*
 * Author: QuanPH
 * Copyright @ 2015 by OneSoft.,JSC
 * 
 */
package vn.com.onesoft.bigfox.server.io.core.session;

import com.google.common.collect.MapMaker;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import vn.com.onesoft.bigfox.server.io.core.compress.BFCompressManager;
import vn.com.onesoft.bigfox.server.io.core.encrypt.BFEncryptManager;
import vn.com.onesoft.bigfox.server.io.core.zone.BFZoneManager;
import vn.com.onesoft.bigfox.server.io.core.zone.IBFZone;
import vn.com.onesoft.bigfox.server.io.message.annotations.Message;
import vn.com.onesoft.bigfox.server.io.message.base.BFLogger;
import vn.com.onesoft.bigfox.server.io.message.base.MessageIn;
import vn.com.onesoft.bigfox.server.io.message.base.MessageOut;
import vn.com.onesoft.bigfox.server.io.message.core.cs.CSClientInfo;
import vn.com.onesoft.bigfox.server.io.message.core.objects.ClientInfo;
import vn.com.onesoft.bigfox.server.io.message.core.sc.SCInitSession;
import vn.com.onesoft.bigfox.server.io.message.core.tags.CoreTags;
import vn.com.onesoft.bigfox.server.main.BFConfig;
import vn.com.onesoft.bigfox.server.main.Main;

/**
 *
 * @author QuanPH
 */
public class BFSessionManager {

    private final Map<Channel, IBFSession> mapChannelToSession;
    private final Map<String, IBFSession> mapSessionIdToSession;

    public final static int SESSION_TIMEOUT = BFConfig.getInstance().getSessionTimeout(); //300 s
    private static BFSessionManager _instance;
    private IBFSessionEvent sessionEvent = new BFDefaultSessionEvent();

    public static BFSessionManager getInstance() {
        if (_instance == null) {
            _instance = new BFSessionManager();
            _instance.startControlThread();
        }
        return _instance;
    }

    public BFSessionManager() {
        this.mapSessionIdToSession = new MapMaker().makeMap();
        this.mapChannelToSession = new MapMaker().makeMap();
    }

    public IBFSession createSession(Channel channel, ClientInfo clientInfo) {
        IBFSession session = new BFSession();
        session.setChannel(channel);
        session.setClientInfo(clientInfo);
        session.setLastTimeReceive(System.currentTimeMillis());
        mapChannelToSession.put(channel, session);
        mapSessionIdToSession.put(clientInfo.sessionId, session);
        sessionEvent.startSession(session);
        session.setLastTimeReceive(System.currentTimeMillis());
        return session;
    }

    public IBFSession getSessionByChannel(Channel channel) {
        return mapChannelToSession.get(channel);
    }

    public IBFSession getSessionById(String id) {
        return mapSessionIdToSession.get(id);
    }

    public void removeSession(IBFSession session) {
        mapChannelToSession.remove(session.getChannel());
        mapSessionIdToSession.remove(session.getClientInfo().sessionId);
        sessionEvent.removeSession(session);
    }

    public boolean containsSession(IBFSession session) {
        if (mapChannelToSession.get(session.getChannel()) != null) {
            return true;
        } else {
            return false;
        }
    }

    public List<IBFSession> getAllSessions() {
        return (List<IBFSession>) mapChannelToSession.values();
    }

    public void sendMessage(Channel channel, MessageOut mOut) {
        if (channel == null || !channel.isActive() || !channel.isOpen()) {
            return;
        }
        synchronized (channel) {
            try {
                Message m = mOut.getClass().getAnnotation(Message.class);
                mOut.setTag(m.tag());
                IBFSession session = getSessionByChannel(channel);
                if (!m.isCore()) {
                    mOut.setMSequence(session.getMSequence());
                    session.setSSequence(session.getSSequence() + 1);
                    mOut.setSSequence(session.getSSequence());
                    session.putOutMessageOnQueue(mOut);
                }
                BFLogger.getInstance().info(channel + "\n" + mOut);
                byte[] data = mOut.toBytes();
                if (mOut.getTag() != CoreTags.SC_VALIDATION_CODE) {
                    data = BFEncryptManager.crypt(channel, data);
                    data = BFCompressManager.getInstance().compress(data);
                }

                if (Main.mapChannelWebSocket.get(channel) != null) {
                    channel.writeAndFlush(new BinaryWebSocketFrame(Unpooled.wrappedBuffer(data)));
                } else {
                    channel.writeAndFlush(Unpooled.wrappedBuffer(data));
                }
            } catch (Exception ex) {
                BFLogger.getInstance().error(ex.getMessage(), ex);
                if (channel.isActive()) {
                    channel.close();
                }
            }
        }
    }

    /**
     * send Message without putting in cache
     *
     * @param channel
     * @param mOut
     */
    private void write(Channel channel, MessageOut mOut) {
        if (channel == null) {
            return;
        }
        BFLogger.getInstance().info(mOut);

        byte[] data = mOut.toBytes();
        if (mOut.getTag() != CoreTags.SC_VALIDATION_CODE) {
            data = BFEncryptManager.crypt(channel, data);
            data = BFCompressManager.getInstance().compress(data);
        }

        if (Main.mapChannelWebSocket.get(channel) != null) {
            channel.writeAndFlush(new BinaryWebSocketFrame(Unpooled.wrappedBuffer(data)));
        } else {
            channel.writeAndFlush(Unpooled.wrappedBuffer(data));
        }
    }

    public void onMessage(Channel channel, MessageIn mIn) {

        BFLogger.getInstance().info(mIn);

        if (mIn instanceof CSClientInfo) {
            CSClientInfo csClientInfo = (CSClientInfo) mIn;
            IBFZone bfZone = BFZoneManager.getInstance().getZone(csClientInfo.getClientInfo().zone);
            if (bfZone == null) {
                channel.close();
                return;
            } else {
                BFZoneManager.getInstance().assignChannelToZone(channel, bfZone);
            }
            String sessionId = csClientInfo.getClientInfo().sessionId;
            IBFSession bfSession = this.getSessionById(sessionId);
            if (bfSession == null) { //Kết nối mới
                createSession(channel, csClientInfo.getClientInfo());
                write(channel, new SCInitSession(SCInitSession.START_NEW_SESSION));
            } else { // Kết nối lại
                bfSession.setChannel(channel);
                mapChannelToSession.put(channel, bfSession);
                bfSession.setClientInfo(csClientInfo.getClientInfo());
                write(channel, new SCInitSession(SCInitSession.CONTINUE_OLD_SESSION));
                bfSession.reSendMessageFromQueue();
                sessionEvent.reconnectSession(bfSession);
            }
        }
        IBFSession session = this.getSessionByChannel(channel);
        Message m = mIn.getClass().getAnnotation(Message.class);
        if (session != null) {
            if (mIn.getMSequence() <= session.getMSequence()) {
                return; //Không thực thi bản tin đã thực thi rồi
            }
            if (!m.isCore()) {
                session.setMSequence(mIn.getMSequence());
                session.cleanOutMessageQueue(mIn);
            }
            session.setLastTimeReceive(System.currentTimeMillis());

        }
        mIn.execute(channel);
    }

    /**
     * Liên tục quét mỗi giây một lần toàn bộ các session xem session nào bị
     * timeout thì xoá
     */
    private void startControlThread() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    Iterator it = mapSessionIdToSession.keySet().iterator();
                    while (it.hasNext()) {
                        String sessionId = (String) it.next();
                        IBFSession session = mapSessionIdToSession.get(sessionId);
                        if (System.currentTimeMillis() - session.getLastTimeReceive() >= BFSessionManager.SESSION_TIMEOUT * 1000) {
                            session.onTimeout();
                            removeSession(session);
                        }
                    }
                    try {
                        Thread.sleep(1000);
                    } catch (Exception tex) {

                    }
                }
            }
        }).start();
    }

    public void onChannelClose(Channel channel) {
        IBFSession session = this.getSessionByChannel(channel);
        if (session != null) {
            session.onChannelClose(channel);
            sessionEvent.closeChannel(session);
        }
        mapChannelToSession.remove(channel);
    }

    public void sendToAll(MessageOut mOut) {
        Iterator it = mapChannelToSession.keySet().iterator();
        while (it.hasNext()) {
            Channel channel = (Channel) it.next();
            sendMessage(channel, mOut.clone());
        }
    }

    public void setSessionEvent(IBFSessionEvent sessionEvent) {
        this.sessionEvent = sessionEvent;
    }
}