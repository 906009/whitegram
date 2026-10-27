package org.telegram.messenger;

import android.content.Context;
import android.os.SystemClock;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;

import libXray.LibXray;

public class XrayController {

    private static final String TAG = "XrayController";
    public static final int DEFAULT_SOCKS_PORT = 10808;

    private static volatile XrayController instance;

    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private int currentPort = DEFAULT_SOCKS_PORT;
    private String activeConfigJson = "";
    private String currentAuthUser = "";
    private String currentAuthPass = "";

    public interface XrayCallback {
        void onSuccess(int socksPort);
        void onError(String error);
    }

    public static XrayController getInstance() {
        if (instance == null) {
            synchronized (XrayController.class) {
                if (instance == null) {
                    instance = new XrayController();
                }
            }
        }
        return instance;
    }

    private XrayController() {}

    public boolean isRunning() {
        return isRunning.get();
    }

    public int getCurrentPort() {
        return currentPort;
    }

    public String getCurrentAuthUser() {
        return currentAuthUser;
    }

    public String getCurrentAuthPass() {
        return currentAuthPass;
    }

    public static int findFreePort() {
        try (java.net.ServerSocket s = new java.net.ServerSocket(0)) {
            s.setReuseAddress(true);
            return s.getLocalPort();
        } catch (Throwable e) {
            return 20000 + (int)(Math.random() * 40000);
        }
    }

    public void start(String configOrLink, int preferredPort, XrayCallback callback) {
        start(configOrLink, preferredPort, null, null, callback);
    }

    public void start(String configOrLink, int preferredPort, String authUser, String authPass, XrayCallback callback) {
        Utilities.globalQueue.postRunnable(() -> {
            try {
                if (isRunning.get()) {
                    stopInternal();
                }

                int port = preferredPort > 0 ? preferredPort : findFreePort();
                String configJson = prepareConfigJson(configOrLink, port, authUser, authPass);

                if (TextUtils.isEmpty(configJson)) {
                    if (callback != null) {
                        AndroidUtilities.runOnUIThread(() -> callback.onError("Failed to parse or load Xray configuration"));
                    }
                    return;
                }

                FileLog.d(TAG + " starting Xray with port " + port);

                JSONObject request = new JSONObject();
                request.put("apiVersion", 3);
                request.put("method", "runXray");

                JSONObject payload = new JSONObject();
                payload.put("xrayJson", configJson);
                request.put("payload", payload);

                String resultStr = LibXray.invoke(request.toString());
                FileLog.d(TAG + " runXray result: " + resultStr);

                // Check state after short initialization delay
                Thread.sleep(300);

                boolean portReady = waitForPort(port, 3000);
                if (portReady) {
                    isRunning.set(true);
                    currentPort = port;
                    currentAuthUser = authUser != null ? authUser : "";
                    currentAuthPass = authPass != null ? authPass : "";
                    activeConfigJson = configJson;
                    FileLog.d(TAG + " Xray is successfully running on port " + port);
                    if (callback != null) {
                        AndroidUtilities.runOnUIThread(() -> callback.onSuccess(port));
                    }
                } else {
                    isRunning.set(false);
                    FileLog.e(TAG + " Xray port " + port + " did not become ready");
                    if (callback != null) {
                        AndroidUtilities.runOnUIThread(() -> callback.onError("Xray started but port " + port + " is unreachable"));
                    }
                }
            } catch (Throwable e) {
                FileLog.e(TAG + " error starting Xray: " + e.getMessage(), e);
                isRunning.set(false);
                if (callback != null) {
                    AndroidUtilities.runOnUIThread(() -> callback.onError(e.getMessage()));
                }
            }
        });
    }

    public void stop() {
        Utilities.globalQueue.postRunnable(this::stopInternal);
    }

    private void stopInternal() {
        try {
            FileLog.d(TAG + " stopping Xray...");
            JSONObject request = new JSONObject();
            request.put("apiVersion", 3);
            request.put("method", "stopXray");
            LibXray.invoke(request.toString());
        } catch (Throwable e) {
            FileLog.e(TAG + " error stopping Xray", e);
        } finally {
            isRunning.set(false);
        }
    }

    private String prepareConfigJson(String configOrLink, int port, String authUser, String authPass) throws Exception {
        String jsonStr = configOrLink;

        if (TextUtils.isEmpty(jsonStr)) {
            return "";
        }

        jsonStr = jsonStr.trim();

        // If it's a share link (vless://, vmess://, trojan://, ss://, etc.)
        if (jsonStr.contains("://")) {
            JSONObject convertReq = new JSONObject();
            convertReq.put("apiVersion", 3);
            convertReq.put("method", "convertShareLinksToXrayJson");
            JSONObject payload = new JSONObject();
            payload.put("text", jsonStr);
            convertReq.put("payload", payload);

            String convertRes = LibXray.invoke(convertReq.toString());
            FileLog.d(TAG + " convertShareLinksToXrayJson result: " + convertRes);

            if (!TextUtils.isEmpty(convertRes)) {
                JSONObject resObj = new JSONObject(convertRes);
                if (resObj.optBoolean("success", true)) {
                    JSONObject resPayload = resObj.optJSONObject("payload");
                    if (resPayload != null && resPayload.has("xrayJson")) {
                        jsonStr = resPayload.getString("xrayJson");
                    } else if (resObj.has("data")) {
                        jsonStr = resObj.getString("data");
                    }
                }
            }
        }

        // Ensure JSON has a SOCKS5 inbound for Telegram
        return ensureSocksInbound(jsonStr, port, authUser, authPass);
    }

    private String ensureSocksInbound(String jsonStr, int port, String authUser, String authPass) {
        try {
            JSONObject root = new JSONObject(jsonStr);
            JSONArray inbounds = root.optJSONArray("inbounds");
            if (inbounds == null) {
                inbounds = new JSONArray();
                root.put("inbounds", inbounds);
            }

            boolean hasSocks = false;
            for (int i = 0; i < inbounds.length(); i++) {
                JSONObject inb = inbounds.optJSONObject(i);
                if (inb != null && "socks".equalsIgnoreCase(inb.optString("protocol"))) {
                    String listen = inb.optString("listen");
                    if ("127.0.0.1".equals(listen) || "0.0.0.0".equals(listen) || TextUtils.isEmpty(listen)) {
                        inb.put("port", port);
                        if (!TextUtils.isEmpty(authUser) && !TextUtils.isEmpty(authPass)) {
                            JSONObject settings = inb.optJSONObject("settings");
                            if (settings == null) {
                                settings = new JSONObject();
                                inb.put("settings", settings);
                            }
                            settings.put("auth", "password");
                            JSONArray accounts = new JSONArray();
                            JSONObject acc = new JSONObject();
                            acc.put("user", authUser);
                            acc.put("pass", authPass);
                            accounts.put(acc);
                            settings.put("accounts", accounts);
                        }
                        hasSocks = true;
                        break;
                    }
                }
            }

            if (!hasSocks) {
                JSONObject socksInbound = new JSONObject();
                socksInbound.put("tag", "socks-in");
                socksInbound.put("listen", "127.0.0.1");
                socksInbound.put("port", port);
                socksInbound.put("protocol", "socks");

                JSONObject settings = new JSONObject();
                if (!TextUtils.isEmpty(authUser) && !TextUtils.isEmpty(authPass)) {
                    settings.put("auth", "password");
                    JSONArray accounts = new JSONArray();
                    JSONObject acc = new JSONObject();
                    acc.put("user", authUser);
                    acc.put("pass", authPass);
                    accounts.put(acc);
                    settings.put("accounts", accounts);
                } else {
                    settings.put("auth", "noauth");
                }
                settings.put("udp", true);
                socksInbound.put("settings", settings);

                inbounds.put(socksInbound);
            }

            return root.toString();
        } catch (Throwable e) {
            FileLog.e(TAG + " failed to modify inbounds: " + e.getMessage());
            return jsonStr;
        }
    }

    private boolean waitForPort(int port, int timeoutMs) {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < timeoutMs) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress("127.0.0.1", port), 200);
                return true;
            } catch (Throwable ignored) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }
        return false;
    }

    private static final java.util.concurrent.ExecutorService pingExecutor = java.util.concurrent.Executors.newFixedThreadPool(8);

    public static void checkServerPing(SharedConfig.ProxyInfo info, Utilities.Callback<Long> callback) {
        pingExecutor.execute(() -> {
            long ping = -1;
            try {
                String host = null;
                int port = -1;

                if (info.isXray()) {
                    String extra = info.extra != null ? info.extra.trim() : "";
                    if (extra.startsWith("{")) {
                        try {
                            JSONObject root = new JSONObject(extra);
                            JSONArray outbounds = root.optJSONArray("outbounds");
                            if (outbounds != null && outbounds.length() > 0) {
                                JSONObject target = null;
                                JSONObject first = outbounds.optJSONObject(0);
                                if (first != null) {
                                    JSONObject stream = first.optJSONObject("streamSettings");
                                    if (stream != null) {
                                        JSONObject sockopt = stream.optJSONObject("sockopt");
                                        if (sockopt != null) {
                                            String dialer = sockopt.optString("dialerProxy");
                                            if (!TextUtils.isEmpty(dialer)) {
                                                for (int i = 0; i < outbounds.length(); i++) {
                                                    JSONObject ob = outbounds.optJSONObject(i);
                                                    if (ob != null && dialer.equals(ob.optString("tag"))) {
                                                        target = ob;
                                                        break;
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                if (target == null) {
                                    target = first;
                                }
                                if (target != null) {
                                    JSONObject settings = target.optJSONObject("settings");
                                    if (settings != null) {
                                        JSONArray vnext = settings.optJSONArray("vnext");
                                        if (vnext != null && vnext.length() > 0) {
                                            JSONObject node = vnext.optJSONObject(0);
                                            host = node.optString("address");
                                            port = node.optInt("port", -1);
                                        } else if (settings.has("address")) {
                                            host = settings.optString("address");
                                            port = settings.optInt("port", -1);
                                        } else {
                                            JSONArray servers = settings.optJSONArray("servers");
                                            if (servers != null && servers.length() > 0) {
                                                JSONObject node = servers.optJSONObject(0);
                                                host = node.optString("address");
                                                port = node.optInt("port", -1);
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (Throwable ignored) {}
                    } else if (extra.contains("://")) {
                        try {
                            int atIdx = extra.indexOf('@');
                            if (atIdx != -1) {
                                int colonIdx = extra.indexOf(':', atIdx);
                                int slashIdx = extra.indexOf('/', atIdx);
                                int queryIdx = extra.indexOf('?', atIdx);
                                int endHost = colonIdx != -1 ? colonIdx : (slashIdx != -1 ? slashIdx : (queryIdx != -1 ? queryIdx : extra.length()));
                                host = extra.substring(atIdx + 1, endHost);
                                if (colonIdx != -1) {
                                    int endPort = slashIdx != -1 ? slashIdx : (queryIdx != -1 ? queryIdx : extra.length());
                                    port = Integer.parseInt(extra.substring(colonIdx + 1, endPort));
                                }
                            }
                        } catch (Throwable ignored) {}
                    }
                } else if (info.isQwdtt()) {
                    QwdttController.QwdttConfig cfg = QwdttController.QwdttConfig.fromJson(info.extra);
                    if (!TextUtils.isEmpty(cfg.peer)) {
                        String peer = cfg.peer;
                        int colon = peer.indexOf(':');
                        if (colon != -1) {
                            host = peer.substring(0, colon);
                            port = Integer.parseInt(peer.substring(colon + 1));
                        } else {
                            host = peer;
                            port = 9000;
                        }
                    }
                }

                if (!TextUtils.isEmpty(host) && port > 0) {
                    long t0 = SystemClock.elapsedRealtime();
                    try (java.net.Socket s = new java.net.Socket()) {
                        s.connect(new java.net.InetSocketAddress(host, port), 1200);
                        ping = SystemClock.elapsedRealtime() - t0;
                    }
                }
            } catch (Throwable ignored) {
                ping = -1;
            }

            final long resultPing = ping;
            AndroidUtilities.runOnUIThread(() -> {
                if (callback != null) {
                    callback.run(resultPing);
                }
            });
        });
    }

}
