package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SubscriptionManager {

    private static final String TAG = "SubscriptionManager";

    private static volatile SubscriptionManager instance;

    public static class Subscription {
        public static final int TYPE_XRAY = 0;
        public static final int TYPE_QWDTT = 1;

        public String id;
        public String title;
        public String url;
        public int type;
        public long lastUpdateTime;
        public int serversCount;

        public Subscription() {
            this.id = UUID.randomUUID().toString();
        }

        public JSONObject toJson() {
            try {
                JSONObject obj = new JSONObject();
                obj.put("id", id);
                obj.put("title", title);
                obj.put("url", url);
                obj.put("type", type);
                obj.put("lastUpdateTime", lastUpdateTime);
                obj.put("serversCount", serversCount);
                return obj;
            } catch (Throwable e) {
                return new JSONObject();
            }
        }

        public static Subscription fromJson(JSONObject obj) {
            Subscription sub = new Subscription();
            sub.id = obj.optString("id", UUID.randomUUID().toString());
            sub.title = obj.optString("title", "");
            sub.url = obj.optString("url", "");
            sub.type = obj.optInt("type", TYPE_XRAY);
            sub.lastUpdateTime = obj.optLong("lastUpdateTime", 0);
            sub.serversCount = obj.optInt("serversCount", 0);
            return sub;
        }
    }

    public interface SubscriptionCallback {
        void onSuccess(int addedCount);
        void onError(String error);
    }

    private final ArrayList<Subscription> subscriptions = new ArrayList<>();
    private boolean loaded = false;

    public static SubscriptionManager getInstance() {
        if (instance == null) {
            synchronized (SubscriptionManager.class) {
                if (instance == null) {
                    instance = new SubscriptionManager();
                }
            }
        }
        return instance;
    }

    private SubscriptionManager() {
        loadSubscriptions();
    }

    public synchronized ArrayList<Subscription> getSubscriptions() {
        if (!loaded) {
            loadSubscriptions();
        }
        return new ArrayList<>(subscriptions);
    }

    public synchronized void addSubscription(Subscription sub) {
        subscriptions.add(sub);
        saveSubscriptions();
    }

    public synchronized void deleteSubscription(String id) {
        for (int i = 0; i < subscriptions.size(); i++) {
            if (subscriptions.get(i).id.equals(id)) {
                subscriptions.remove(i);
                break;
            }
        }
        saveSubscriptions();
    }

    public synchronized void deleteSubscriptionAndServers(String subId) {
        Subscription targetSub = null;
        for (Subscription s : subscriptions) {
            if (s.id.equals(subId)) {
                targetSub = s;
                break;
            }
        }
        deleteSubscriptionAndServers(targetSub != null ? targetSub : new Subscription());
    }

    public synchronized void deleteSubscriptionAndServers(Subscription sub) {
        if (sub == null) return;
        deleteSubscription(sub.id);

        SharedConfig.loadProxyList();
        boolean changed = false;
        for (int i = SharedConfig.proxyList.size() - 1; i >= 0; i--) {
            SharedConfig.ProxyInfo info = SharedConfig.proxyList.get(i);
            if (info != null && (info.isXray() || info.isQwdtt())) {
                boolean match = false;
                if (!TextUtils.isEmpty(sub.id) && info.extra != null && info.extra.contains(sub.id)) {
                    match = true;
                } else if (!TextUtils.isEmpty(sub.title) && info.username != null && (info.username.startsWith(sub.title) || info.username.contains(sub.title))) {
                    match = true;
                } else if (subscriptions.isEmpty()) {
                    match = true;
                }
                if (match) {
                    if (SharedConfig.currentProxy == info) {
                        SharedConfig.currentProxy = null;
                    }
                    SharedConfig.proxyList.remove(i);
                    changed = true;
                }
            }
        }
        if (changed) {
            SharedConfig.saveProxyList();
            if (SharedConfig.currentProxy == null && !SharedConfig.proxyList.isEmpty()) {
                SharedConfig.currentProxy = SharedConfig.proxyList.get(0);
            }
            SharedConfig.applyCurrentProxy(SharedConfig.currentProxy != null);
        }
    }

    public synchronized void loadSubscriptions() {
        subscriptions.clear();
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("proxy_subscriptions", Context.MODE_PRIVATE);
        String json = preferences.getString("subscriptions_list", "[]");
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                subscriptions.add(Subscription.fromJson(array.getJSONObject(i)));
            }
        } catch (Throwable e) {
            FileLog.e(TAG + " failed to load subscriptions: " + e.getMessage());
        }
        loaded = true;
    }

    public synchronized void saveSubscriptions() {
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("proxy_subscriptions", Context.MODE_PRIVATE);
        JSONArray array = new JSONArray();
        for (Subscription sub : subscriptions) {
            array.put(sub.toJson());
        }
        preferences.edit().putString("subscriptions_list", array.toString()).apply();
    }

    public void updateAllSubscriptions(SubscriptionCallback callback) {
        Utilities.globalQueue.postRunnable(() -> {
            ArrayList<Subscription> list = getSubscriptions();
            int totalAdded = 0;
            String lastError = null;

            for (Subscription sub : list) {
                try {
                    int count = updateSubscriptionInternal(sub);
                    totalAdded += count;
                } catch (Throwable e) {
                    lastError = e.getMessage();
                    FileLog.e(TAG + " error updating sub " + sub.title + ": " + e.getMessage());
                }
            }

            saveSubscriptions();
            final int resultCount = totalAdded;
            final String err = lastError;

            AndroidUtilities.runOnUIThread(() -> {
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxySettingsChanged);
                if (callback != null) {
                    if (resultCount > 0 || err == null) {
                        callback.onSuccess(resultCount);
                    } else {
                        callback.onError(err);
                    }
                }
            });
        });
    }

    public void updateSubscription(Subscription sub, SubscriptionCallback callback) {
        Utilities.globalQueue.postRunnable(() -> {
            try {
                int count = updateSubscriptionInternal(sub);
                saveSubscriptions();
                AndroidUtilities.runOnUIThread(() -> {
                    NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxySettingsChanged);
                    if (callback != null) {
                        callback.onSuccess(count);
                    }
                });
            } catch (Throwable e) {
                FileLog.e(TAG + " error updating subscription: " + e.getMessage(), e);
                AndroidUtilities.runOnUIThread(() -> {
                    if (callback != null) {
                        callback.onError(e.getMessage());
                    }
                });
            }
        });
    }

    private int updateSubscriptionInternal(Subscription sub) throws Exception {
        try {
            return updateSubscriptionInternal(sub, false);
        } catch (Throwable e) {
            if (SharedConfig.proxyUpdateSubsViaProxy && XrayController.getInstance().isRunning()) {
                FileLog.d("Direct subscription download failed: " + e.getMessage() + ", retrying via active proxy...");
                return updateSubscriptionInternal(sub, true);
            }
            throw e;
        }
    }

    private int updateSubscriptionInternal(Subscription sub, boolean useProxy) throws Exception {
        String urlString = sub.url.trim();
        String hwid = SharedConfig.getXrayHwid();
        String deviceId = SharedConfig.getQwdttDeviceId();

        // Append HWID / device-id query parameter if appropriate
        if (!urlString.contains("hwid=") && !urlString.contains("device_id=")) {
            String separator = urlString.contains("?") ? "&" : "?";
            if (sub.type == Subscription.TYPE_XRAY) {
                urlString += separator + "hwid=" + hwid;
            } else {
                urlString += separator + "device_id=" + deviceId;
            }
        }

        HttpURLConnection conn = null;
        int redirects = 0;
        int responseCode = 0;

        while (redirects < 5) {
            URL url = new URL(urlString);
            if (useProxy && XrayController.getInstance().isRunning()) {
                int port = XrayController.getInstance().getCurrentPort();
                java.net.Proxy proxy = new java.net.Proxy(java.net.Proxy.Type.SOCKS, new java.net.InetSocketAddress("127.0.0.1", port));
                String authUser = XrayController.getInstance().getCurrentAuthUser();
                String authPass = XrayController.getInstance().getCurrentAuthPass();
                if (!TextUtils.isEmpty(authUser) && !TextUtils.isEmpty(authPass)) {
                    java.net.Authenticator.setDefault(new java.net.Authenticator() {
                        @Override
                        protected java.net.PasswordAuthentication getPasswordAuthentication() {
                            return new java.net.PasswordAuthentication(authUser, authPass.toCharArray());
                        }
                    });
                }
                conn = (HttpURLConnection) url.openConnection(proxy);
            } else {
                conn = (HttpURLConnection) url.openConnection();
            }
            conn.setInstanceFollowRedirects(true);
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(6000);
            conn.setReadTimeout(10000);

            if (sub.type == Subscription.TYPE_XRAY) {
                conn.setRequestProperty("User-Agent", "v2rayNG/1.8.5 (Whitegram; HWID " + hwid + ")");
                conn.setRequestProperty("X-HWID", hwid);
                conn.setRequestProperty("X-Device-Id", hwid);
                conn.setRequestProperty("HWID", hwid);
            } else {
                conn.setRequestProperty("User-Agent", "qWDTT/1.4.4 (Android; Device " + deviceId + ")");
                conn.setRequestProperty("X-Device-Id", deviceId);
                conn.setRequestProperty("Device-Id", deviceId);
            }

            responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                responseCode == HttpURLConnection.HTTP_SEE_OTHER ||
                responseCode == 307 || responseCode == 308) {
                String loc = conn.getHeaderField("Location");
                if (!TextUtils.isEmpty(loc)) {
                    if (loc.startsWith("/")) {
                        URL prev = new URL(urlString);
                        urlString = prev.getProtocol() + "://" + prev.getHost() + (prev.getPort() > 0 ? ":" + prev.getPort() : "") + loc;
                    } else {
                        urlString = loc;
                    }
                    redirects++;
                    conn.disconnect();
                    continue;
                }
            }
            break;
        }

        if (responseCode != 200) {
            throw new Exception("HTTP " + responseCode + (conn != null && conn.getResponseMessage() != null ? ": " + conn.getResponseMessage() : ""));
        }

        StringBuilder response = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line).append("\n");
            }
        }

        String rawContent = response.toString().trim();
        List<SharedConfig.ProxyInfo> newProxies = new ArrayList<>();

        if (sub.type == Subscription.TYPE_XRAY) {
            newProxies.addAll(parseXrayContent(rawContent, sub.title, sub.id));
        } else {
            newProxies.addAll(parseQwdttContent(rawContent, sub.title));
        }

        if (newProxies.isEmpty()) {
            throw new Exception("No valid servers found in subscription response");
        }

        // Add parsed proxies to SharedConfig
        SharedConfig.loadProxyList();
        int added = 0;
        for (SharedConfig.ProxyInfo info : newProxies) {
            SharedConfig.addProxy(info, false);
            added++;
        }
        SharedConfig.saveProxyList();

        sub.lastUpdateTime = System.currentTimeMillis();
        sub.serversCount = added;
        return added;
    }

    private List<SharedConfig.ProxyInfo> parseXrayContent(String content, String subTitle, String subId) {
        List<SharedConfig.ProxyInfo> list = new ArrayList<>();
        if (TextUtils.isEmpty(content)) {
            return list;
        }

        String decodedText = content.trim();

        // 1. Try base64 decoding if not obviously JSON or link
        if (!decodedText.startsWith("{") && !decodedText.startsWith("[") && !decodedText.contains("://")) {
            try {
                byte[] bytes = null;
                try {
                    bytes = Base64.decode(decodedText, Base64.DEFAULT);
                } catch (Throwable e) {
                    bytes = Base64.decode(decodedText, Base64.URL_SAFE);
                }
                if (bytes != null && bytes.length > 0) {
                    String candidate = new String(bytes, "UTF-8").trim();
                    if (candidate.contains("://") || candidate.startsWith("{") || candidate.startsWith("[")) {
                        decodedText = candidate;
                    }
                }
            } catch (Throwable ignored) {}
        }

        // 2. If it's a JSON array: [...]
        if (decodedText.startsWith("[")) {
            try {
                JSONArray arr = new JSONArray(decodedText);
                for (int i = 0; i < arr.length(); i++) {
                    Object item = arr.get(i);
                    if (item instanceof JSONObject) {
                        JSONObject obj = (JSONObject) item;
                        String remark = obj.optString("remarks", obj.optString("name", obj.optString("ps", "")));
                        if (TextUtils.isEmpty(remark)) {
                            remark = (!TextUtils.isEmpty(subTitle) ? subTitle + " #" : "Xray #") + (list.size() + 1);
                        }
                        try { if (!TextUtils.isEmpty(subId)) obj.put("subId", subId); } catch (Throwable ignored) {}
                        SharedConfig.ProxyInfo info = new SharedConfig.ProxyInfo(
                                "127.0.0.1",
                                XrayController.DEFAULT_SOCKS_PORT,
                                remark,
                                "",
                                "",
                                SharedConfig.ProxyInfo.PROXY_TYPE_XRAY,
                                obj.toString()
                        );
                        list.add(info);
                    } else if (item instanceof String) {
                        parseXrayLine((String) item, subTitle, list);
                    }
                }
            } catch (Throwable e) {
                FileLog.e(TAG + " error parsing JSON array: " + e.getMessage());
            }
        } else if (decodedText.startsWith("{")) {
            try {
                JSONObject obj = new JSONObject(decodedText);
                JSONArray serversArr = obj.optJSONArray("servers");
                if (serversArr == null) serversArr = obj.optJSONArray("configs");
                if (serversArr == null) serversArr = obj.optJSONArray("profiles");

                if (serversArr != null) {
                    for (int i = 0; i < serversArr.length(); i++) {
                        Object item = serversArr.get(i);
                        if (item instanceof JSONObject) {
                            JSONObject subObj = (JSONObject) item;
                            String remark = subObj.optString("remarks", subObj.optString("name", subObj.optString("ps", "")));
                            if (TextUtils.isEmpty(remark)) {
                                remark = (!TextUtils.isEmpty(subTitle) ? subTitle + " #" : "Xray #") + (list.size() + 1);
                            }
                            try { if (!TextUtils.isEmpty(subId)) subObj.put("subId", subId); } catch (Throwable ignored) {}
                            SharedConfig.ProxyInfo info = new SharedConfig.ProxyInfo(
                                    "127.0.0.1",
                                    XrayController.DEFAULT_SOCKS_PORT,
                                    remark,
                                    "",
                                    "",
                                    SharedConfig.ProxyInfo.PROXY_TYPE_XRAY,
                                    subObj.toString()
                            );
                            list.add(info);
                        } else if (item instanceof String) {
                            parseXrayLine((String) item, subTitle, list);
                        }
                    }
                } else if (obj.has("outbounds") || obj.has("inbounds")) {
                    String remark = obj.optString("remarks", obj.optString("name", obj.optString("ps", "")));
                    if (TextUtils.isEmpty(remark)) {
                        remark = !TextUtils.isEmpty(subTitle) ? subTitle : "Xray Config";
                    }
                    SharedConfig.ProxyInfo info = new SharedConfig.ProxyInfo(
                            "127.0.0.1",
                            XrayController.DEFAULT_SOCKS_PORT,
                            remark,
                            "",
                            "",
                            SharedConfig.ProxyInfo.PROXY_TYPE_XRAY,
                            obj.toString()
                    );
                    list.add(info);
                }
            } catch (Throwable e) {
                FileLog.e(TAG + " error parsing JSON object: " + e.getMessage());
            }
        }

        // 3. Line by line parsing for share links
        if (list.isEmpty()) {
            String[] lines = decodedText.split("[\r\n]+");
            for (String line : lines) {
                parseXrayLine(line, subTitle, list);
            }
        }

        return list;
    }

    private void parseXrayLine(String line, String subTitle, List<SharedConfig.ProxyInfo> list) {
        line = line != null ? line.trim() : "";
        if (TextUtils.isEmpty(line)) return;

        if (line.startsWith("vless://") || line.startsWith("vmess://") ||
            line.startsWith("trojan://") || line.startsWith("ss://") ||
            line.startsWith("socks://") || line.startsWith("hysteria://") ||
            line.startsWith("hy2://")) {

            String remark = "";
            int hashIdx = line.indexOf('#');
            if (hashIdx != -1 && hashIdx + 1 < line.length()) {
                try {
                    remark = URLDecoder.decode(line.substring(hashIdx + 1), "UTF-8");
                } catch (Throwable ignored) {
                    remark = line.substring(hashIdx + 1);
                }
            }

            if (TextUtils.isEmpty(remark)) {
                remark = (!TextUtils.isEmpty(subTitle) ? subTitle + " - " : "Xray ") + (list.size() + 1);
            }

            SharedConfig.ProxyInfo info = new SharedConfig.ProxyInfo(
                    "127.0.0.1",
                    XrayController.DEFAULT_SOCKS_PORT,
                    remark,
                    "",
                    "",
                    SharedConfig.ProxyInfo.PROXY_TYPE_XRAY,
                    line
            );
            list.add(info);
        }
    }

    private List<SharedConfig.ProxyInfo> parseQwdttContent(String content, String subTitle) {
        List<SharedConfig.ProxyInfo> list = new ArrayList<>();
        String decodedText = content;

        try {
            byte[] bytes = Base64.decode(content.trim(), Base64.DEFAULT);
            String candidate = new String(bytes, "UTF-8");
            if (candidate.contains("qwdtt://") || candidate.contains("peer") || candidate.contains("hashes")) {
                decodedText = candidate;
            }
        } catch (Throwable ignored) {}

        // Check if JSON array or JSON object
        if (decodedText.startsWith("{") || decodedText.startsWith("[")) {
            try {
                if (decodedText.startsWith("[")) {
                    JSONArray arr = new JSONArray(decodedText);
                    for (int i = 0; i < arr.length(); i++) {
                        SharedConfig.ProxyInfo info = parseQwdttJsonObject(arr.getJSONObject(i), subTitle, list.size() + 1);
                        if (info != null) list.add(info);
                    }
                } else {
                    JSONObject obj = new JSONObject(decodedText);
                    if (obj.has("profiles")) {
                        JSONArray arr = obj.getJSONArray("profiles");
                        for (int i = 0; i < arr.length(); i++) {
                            SharedConfig.ProxyInfo info = parseQwdttJsonObject(arr.getJSONObject(i), subTitle, list.size() + 1);
                            if (info != null) list.add(info);
                        }
                    } else {
                        SharedConfig.ProxyInfo info = parseQwdttJsonObject(obj, subTitle, 1);
                        if (info != null) list.add(info);
                    }
                }
            } catch (Throwable ignored) {}
        }

        // Check line by line for qwdtt:// links
        String[] lines = decodedText.split("[\r\n]+");
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("qwdtt://")) {
                SharedConfig.ProxyInfo info = parseQwdttLink(line, subTitle, list.size() + 1);
                if (info != null) {
                    list.add(info);
                }
            }
        }

        return list;
    }

    private SharedConfig.ProxyInfo parseQwdttJsonObject(JSONObject obj, String subTitle, int index) {
        try {
            String name = obj.optString("name", "");
            if (TextUtils.isEmpty(name)) {
                name = (!TextUtils.isEmpty(subTitle) ? subTitle + " - " : "qWDTT ") + index;
            }
            QwdttController.QwdttConfig cfg = new QwdttController.QwdttConfig();
            cfg.peer = obj.optString("peer", "");
            cfg.vkHashes = obj.optString("vkHashes", obj.optString("hashes", obj.optString("vk", "")));
            cfg.password = obj.optString("password", obj.optString("pass", ""));
            cfg.socksPort = obj.optInt("socksPort", obj.optInt("port", QwdttController.DEFAULT_SOCKS_PORT));
            cfg.workers = obj.optInt("workers", obj.optInt("workersPerHash", 9));

            if (TextUtils.isEmpty(cfg.peer)) {
                return null;
            }

            return new SharedConfig.ProxyInfo(
                    "127.0.0.1",
                    cfg.socksPort > 0 ? cfg.socksPort : QwdttController.DEFAULT_SOCKS_PORT,
                    name,
                    "",
                    "",
                    SharedConfig.ProxyInfo.PROXY_TYPE_QWDTT,
                    cfg.toJson()
            );
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static SharedConfig.ProxyInfo parseQwdttLink(String link, String subTitle, int index) {
        try {
            // format: qwdtt://config?name=...&peer=...&hashes=...&pass=...&port=...
            int queryIdx = link.indexOf('?');
            if (queryIdx == -1) return null;

            String query = link.substring(queryIdx + 1);
            String[] params = query.split("&");

            String name = "";
            String peer = "";
            String hashes = "";
            String pass = "";
            int port = QwdttController.DEFAULT_SOCKS_PORT;
            int workers = 9;

            for (String param : params) {
                int eq = param.indexOf('=');
                if (eq == -1) continue;
                String k = param.substring(0, eq).trim();
                String v = URLDecoder.decode(param.substring(eq + 1).trim(), "UTF-8");

                switch (k) {
                    case "name":
                        name = v;
                        break;
                    case "peer":
                        peer = v;
                        break;
                    case "hashes":
                    case "vk":
                        hashes = v;
                        break;
                    case "pass":
                    case "password":
                        pass = v;
                        break;
                    case "port":
                        try { port = Integer.parseInt(v); } catch (Throwable ignored) {}
                        break;
                    case "workers":
                        try { workers = Integer.parseInt(v); } catch (Throwable ignored) {}
                        break;
                }
            }

            if (TextUtils.isEmpty(peer)) return null;

            if (TextUtils.isEmpty(name)) {
                name = (!TextUtils.isEmpty(subTitle) ? subTitle + " - " : "qWDTT ") + index;
            }

            QwdttController.QwdttConfig cfg = new QwdttController.QwdttConfig();
            cfg.peer = peer;
            cfg.vkHashes = hashes;
            cfg.password = pass;
            cfg.socksPort = port;
            cfg.workers = workers;

            return new SharedConfig.ProxyInfo(
                    "127.0.0.1",
                    port,
                    name,
                    "",
                    "",
                    SharedConfig.ProxyInfo.PROXY_TYPE_QWDTT,
                    cfg.toJson()
            );
        } catch (Throwable e) {
            return null;
        }
    }
}
