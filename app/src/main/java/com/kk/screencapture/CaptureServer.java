package com.kk.screencapture;

import android.graphics.Bitmap;
import android.media.Image;
import android.util.Log;

import com.corundumstudio.socketio.AckRequest;
import com.corundumstudio.socketio.Configuration;
import com.corundumstudio.socketio.SocketConfig;
import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import com.corundumstudio.socketio.listener.ConnectListener;
import com.corundumstudio.socketio.listener.DataListener;
import com.corundumstudio.socketio.listener.DisconnectListener;
import com.corundumstudio.socketio.transport.NamespaceClient;
import com.google.gson.Gson;
import com.kk.screencapture.model.ActionMessage;
import com.kk.screencapture.model.ArkResponse;
import com.kk.screencapture.util.BitmapUtil;
import com.kk.screencapture.util.FixedLenQueue;
import com.kk.screencapture.util.ThreadUtil;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 截屏服务器
 * 负责将截图传递给pc端的客户端套接字，并接收客户端指令，修改截图频率、质量等等
 **/
public class CaptureServer {
    private static final String TAG = "SockIOUtil";

    private static final String EVENT_MESSAGE = "message";

    private static final String EVENT_ACTION = "action";

    private static final String EVENT_BMP_BASE64 = "bmp_base64";

    /**
     * 支持的动作选项
     **/
    private static final String ACTION_START = "start";
    private static final String ACTION_STOP = "stop";
    private static final String ACTION_SIZE = "size"; // 改变截图尺寸
    private static final String ACTION_FRENQUENCY = "frequency"; // 改变采样频率
    private static final String ACTION_QUALITY = "quality"; // 图片质量，默认90

    private int port = 9999;

    private SocketIOServer socketIOServer;
    private Configuration conf;

    ForegroundScreencapService screencapService;
    FixedLenQueue<Image> imgBufferedQueue;
    ExecutorService executorService = Executors.newFixedThreadPool(30);

    Map<String, NamespaceClient> clientMap = new HashMap<>();
    Set<String> workingSidSet = new HashSet<>();
    List<String> sidList = new ArrayList<>();

    AtomicLong sendCounter = new AtomicLong();

    public CaptureServer(ForegroundScreencapService screencapService) {
        this.screencapService = screencapService;
        this.imgBufferedQueue = screencapService.imgBufferedQueue;

    }

    private static void logI(String log) {
        Log.i(TAG, log);
    }

    public void startDaemon() {
        logI("start server port:" + port);

        SocketConfig socketConfig = new SocketConfig();
        socketConfig.setReuseAddress(true);
        socketConfig.setTcpKeepAlive(true);
        socketConfig.setTcpNoDelay(true);
        socketConfig.setSoLinger(0);

        conf = new Configuration();
        conf.setPort(port);
        conf.setSocketConfig(socketConfig);

        socketIOServer = new SocketIOServer(conf);

        ServerListenerAdapter serverListenerAdapter = new ServerListenerAdapter();

        socketIOServer.addConnectListener(serverListenerAdapter);
        socketIOServer.addDisconnectListener(serverListenerAdapter);

        socketIOServer.addEventListener(EVENT_ACTION, ActionMessage.class, serverListenerAdapter);

        socketIOServer.start();
    }

    /**
     * 循环广播缓存队列的图片
     **/
    public void loopBroadcastCaptures() {
        ThreadUtil.doRun(new BmpSendRunnable());
    }

    long lastSize = 0;

    public class BmpSendRunnable implements Runnable {
        @Override
        public void run() {
            while (true) {
                // 阻塞式获取最新的图片
                Image capturedImg = imgBufferedQueue.take();

                final long index = sendCounter.addAndGet(1);
                logI(String.format("prepare to send bmp:%s,queue:%s", index, imgBufferedQueue.size()));

                Bitmap bmp = imgToBmp(capturedImg);
                capturedImg.close();

                String base64Img = BitmapUtil.bitmapToString(bmp);

                if (!bmp.isRecycled()) {
                    bmp.recycle();
                }

                // 微小差异不处理
                int curSize = base64Img.length();

                if (Math.abs(curSize - lastSize) < 10) {
                    Log.w(TAG, "ignore tiny diff");
                    continue;
                }

                lastSize = curSize;

                base64Img = index + "::data:image/webp;base64," + base64Img;

                final int size = sidList.size();

                if (size == 0) {
                    Log.w(TAG, "no alive client available!");
                    continue;
                }

                final String finalBase64Img = base64Img;
                executorService.submit(new Runnable() {
                    @Override
                    public void run() {
                        String sid = chooseConnnectedClient();

                        if (null == sid) {
                            Log.w(TAG, "fail to send bmp for lack of idle client");
                            return;
                        }

                        workingSidSet.add(sid);

                        logI(String.format("index:%s,broadcasting base64 image:%s",
                            index, finalBase64Img.length()));

                        clientMap.get(sid).sendEvent(EVENT_BMP_BASE64, finalBase64Img);

                        workingSidSet.remove(sid);
                    }
                });
            }
        }
    }

    /**
     * 获取一个连接的客户端
     **/
    public String chooseConnnectedClient() {
        Random randomNum = new Random();
        int maxFail = 10;
        while (true) {
            int choseClientIndex = randomNum.nextInt(sidList.size());

            NamespaceClient client = clientMap.get(sidList.get(choseClientIndex));

            if (client.getBaseClient().isConnected()) {
                return sidList.get(choseClientIndex);
            }

            maxFail--;
            if (maxFail < 0) {
                return null;
            }
        }
    }

    /**
     * image转换为Bitmap
     **/
    private Bitmap imgToBmp(Image image) {
        int width = image.getWidth();
        int height = image.getHeight();

        final Image.Plane[] planes = image.getPlanes();
        final ByteBuffer buffer = planes[0].getBuffer();
        int pixelStride = planes[0].getPixelStride();
        int rowStride = planes[0].getRowStride();
        int rowPadding = rowStride - pixelStride * width;

        Bitmap bmp = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888);
        bmp.copyPixelsFromBuffer(buffer);
        bmp = Bitmap.createBitmap(bmp, 0, 0, width, height);

        return bmp;
    }

    /**
     * 释放
     **/
    public void release() {
        if (null != socketIOServer) {
            socketIOServer.stop();
            socketIOServer = null;
        }
    }

    /**
     * 监听器适配器
     **/
    private class ServerListenerAdapter implements ConnectListener, DisconnectListener, DataListener<ActionMessage> {
        @Override
        public void onConnect(SocketIOClient client) {
            String sid = client.getSessionId().toString();

            if (sidList.contains(sid)) {
                return;
            }

            sidList.add(sid);
            clientMap.put(sid, (NamespaceClient) client);

            loopBroadcastCaptures();

            logI(String.format("connect,sid:%s,sid count:%s,client count:%s",
                client.getSessionId(), sidList.size(), socketIOServer.getAllClients().size()));

            client.sendEvent(EVENT_MESSAGE, "i am server");
        }

        @Override
        public void onDisconnect(SocketIOClient client) {
            String sid = client.getSessionId().toString();

            if (!sidList.contains(sid)) return;

            sidList.remove(sid);
            clientMap.remove(sid);

            logI(String.format("disconnect,sid:%s,sid count:%s,client count:%s",
                client.getSessionId(), sidList.size(), socketIOServer.getAllClients().size()));
        }

        AtomicInteger actionId = new AtomicInteger();

        long lastActionTime = 0;
        int id = 0;
        long minActionIntervalMillis = 10 * 1000; // 最少间隔10s以上才接收下一个指令

        @Override
        public void onData(SocketIOClient client, ActionMessage actionMsg, AckRequest ackSender) throws Exception {
            if (0 == lastActionTime) {
                lastActionTime = System.currentTimeMillis();
            }

            if (System.currentTimeMillis() - lastActionTime < minActionIntervalMillis) {
                respBad(ackSender, "cannot do action too frequently!");
                return;
            }

            Log.i(TAG, "recv action from client:" + actionMsg);

            switch (actionMsg.getAction()) {
                case ACTION_START:
                    screencapService.startCaptureProcess();
                    break;

                case ACTION_STOP:
                    screencapService.stopCaptureProcess();
                    break;

                case ACTION_SIZE:
                    if (actionMsg.getWidth() <= 0) {
                        respBad(ackSender, "width cannot be <=0");
                        return;
                    }

                    if (actionMsg.getHeight() <= 0) {
                        respBad(ackSender, "height cannot be <=0");
                        return;
                    }

                    screencapService.mScreenWidth = actionMsg.getWidth();
                    screencapService.mScreenHeight = actionMsg.getHeight();

                    screencapService.restartCaptureProcess();
                    break;

                case ACTION_FRENQUENCY:
                    if (actionMsg.getFrequency() <= 0) {
                        respBad(ackSender, "frequency cannot be <= 0");
                        return;
                    }

                    screencapService.captureIntervalMillis = actionMsg.getFrequency();
                    screencapService.restartCaptureProcess();
                    break;

                case ACTION_QUALITY:
                    int quality = actionMsg.getQuality();

                    if (quality > 100 || quality < 40) {
                        respBad(ackSender, "quality must between 40 and 100");
                        return;
                    }

                    BitmapUtil.quality = quality;
                    break;

                default:
                    String msg = "action is not supported,only suppport:start | stop | size | frequency";
                    Log.w(TAG, msg);

                    respBad(ackSender, msg);
                    return;
            }

            respOk(ackSender);
        }

        private void respOk(AckRequest ackSender) {
            if (null != ackSender) {
                ackSender.sendAckData(new Gson().toJson(ArkResponse.ok()));
            }
        }

        private void respBad(AckRequest ackSender, String msg) {
            if (null != ackSender) {
                ackSender.sendAckData(new Gson().toJson(ArkResponse.bad(msg)));
            }
        }
    }
}
