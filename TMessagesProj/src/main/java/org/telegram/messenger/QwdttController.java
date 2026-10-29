package org.telegram.messenger;

import android.content.Context;
import android.os.Build;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class QwdttController {

    private static final String TAG = "QwdttController";
    public static final int DEFAULT_SOCKS_PORT = 10809;
    public static final int DEFAULT_LISTEN_PORT = 9000;

    private static volatile QwdttController instance;

    private Process process;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final AtomicBoolean isStarting = new AtomicBoolean(false);
    private int currentPort = DEFAULT_SOCKS_PORT;
    private String lastLogs = "";

    public static class QwdttConfig {
        public String peer = "";
        public String vkHashes = "";
        public String password = "";
        public int socksPort = DEFAULT_SOCKS_PORT;
        public int listenPort = DEFAULT_LISTEN_PORT;
        public int workers = 9;

        public String toJson() {
            try {
                JSONObject obj = new JSONObject();
                obj.put("peer", peer);
                obj.put("vk", vkHashes);
                obj.put("password", password);
                obj.put("socksPort", socksPort);
                obj.put("listenPort", listenPort);
                obj.put("workers", workers);
                return obj.toString();
            } catch (Throwable ignored) {
                return "";
            }
        }

        public static QwdttConfig fromJson(String json) {
            QwdttConfig cfg = new QwdttConfig();
            if (TextUtils.isEmpty(json)) {
                return cfg;
            }
            try {
                JSONObject obj = new JSONObject(json);
                cfg.peer = obj.optString("peer", "");
                cfg.vkHashes = obj.optString("vk", "");
                cfg.password = obj.optString("password", "");
                cfg.socksPort = obj.optInt("socksPort", DEFAULT_SOCKS_PORT);
                cfg.listenPort = obj.optInt("listenPort", DEFAULT_LISTEN_PORT);
                cfg.workers = obj.optInt("workers", 9);
            } catch (Throwable ignored) {}
            return cfg;
        }
    }

    public interface QwdttCallback {
        void onSuccess(int socksPort);
        void onError(String error);
    }

    public static QwdttController getInstance() {
        if (instance == null) {
            synchronized (QwdttController.class) {
                if (instance == null) {
                    instance = new QwdttController();
                }
            }
        }
        return instance;
    }

    private QwdttController() {}

    public boolean isRunning() {
        return isRunning.get();
    }

    public boolean isStarting() {
        return isStarting.get();
    }

    public int getCurrentPort() {
        return currentPort;
    }

    public String getLastLogs() {
        return lastLogs;
    }

    public void start(QwdttConfig config, QwdttCallback callback) {
        Utilities.globalQueue.postRunnable(() -> {
            try {
                if (isRunning.get()) {
                    stopInternal();
                }

                Context context = ApplicationLoader.applicationContext;
                File binaryFile = getOrExtractBinary(context);
                if (binaryFile == null || !binaryFile.exists()) {
                    if (callback != null) {
                        AndroidUtilities.runOnUIThread(() -> callback.onError("qWDTT binary (libclient.so) not found or not executable"));
                    }
                    return;
                }

                int socksPort = config.socksPort > 0 && config.socksPort != DEFAULT_SOCKS_PORT ? config.socksPort : XrayController.findFreePort();
                int listenPort = config.listenPort > 0 && config.listenPort != DEFAULT_LISTEN_PORT ? config.listenPort : XrayController.findFreePort();

                isStarting.set(true);
                ensureVkProfileJson(context);

                List<String> cmd = new ArrayList<>();
                cmd.add(binaryFile.getAbsolutePath());
                cmd.add("-peer");
                cmd.add(config.peer);
                cmd.add("-vk");
                cmd.add(config.vkHashes);
                cmd.add("-n");
                cmd.add(String.valueOf(config.workers > 0 ? config.workers : 9));
                cmd.add("-listen");
                cmd.add("127.0.0.1:" + listenPort);
                cmd.add("-mode");
                cmd.add("socks");
                cmd.add("-socks");
                cmd.add("127.0.0.1:" + socksPort);

                String deviceId = SharedConfig.getQwdttDeviceId();
                if (!TextUtils.isEmpty(deviceId)) {
                    cmd.add("-device-id");
                    cmd.add(deviceId);
                }

                if (!TextUtils.isEmpty(config.password)) {
                    cmd.add("-password");
                    cmd.add(config.password);
                }

                // Required network and runtime parameters for VK TURN stability
                cmd.add("-go-dns");
                cmd.add("yandex");
                cmd.add("-captcha-mode");
                cmd.add("auto");
                cmd.add("-vk-auth");
                cmd.add("anonymous");
                cmd.add("-vk-anon-path");
                cmd.add("vkcalls");
                cmd.add("-obfs");
                cmd.add("audio");

                FileLog.d(TAG + " starting qWDTT process with cmd: " + cmd);

                ProcessBuilder pb = new ProcessBuilder(cmd);
                pb.directory(context.getFilesDir());
                pb.environment().put("LD_LIBRARY_PATH", context.getApplicationInfo().nativeLibraryDir);
                pb.redirectErrorStream(true);

                process = pb.start();

                // Monitor process lifetime in separate thread
                new Thread(() -> {
                    try {
                        Process p = process;
                        if (p != null) {
                            int exitCode = p.waitFor();
                            if (isRunning.get()) {
                                FileLog.e(TAG + " process unexpectedly died with exit code " + exitCode);
                                isRunning.set(false);
                                AndroidUtilities.runOnUIThread(() -> {
                                    if (SharedConfig.currentProxy != null && SharedConfig.currentProxy.isQwdtt()) {
                                        SharedConfig.currentProxy.available = false;
                                        SharedConfig.currentProxy.availableCheckTime = SystemClock.elapsedRealtime();
                                        SharedConfig.currentProxy.ping = 0;
                                        ProxyRotationController.getInstance().checkAndSwitchNow();
                                    }
                                });
                            }
                        }
                    } catch (Throwable ignored) {}
                }).start();

                // Read output in separate thread
                final StringBuilder logBuilder = new StringBuilder();
                new Thread(() -> {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            FileLog.d(TAG + " [OUT] " + line);
                            synchronized (logBuilder) {
                                if (logBuilder.length() > 5000) {
                                    logBuilder.delete(0, 2000);
                                }
                                logBuilder.append(line).append("\n");
                                lastLogs = logBuilder.toString();
                            }
                        }
                    } catch (Throwable ignored) {}
                }).start();

                // Wait for SOCKS port to be ready (up to 30s for VK TURN handshake & relays)
                boolean ready = waitForPort(socksPort, 30000);
                isStarting.set(false);
                if (ready) {
                    isRunning.set(true);
                    currentPort = socksPort;
                    FileLog.d(TAG + " qWDTT successfully listening on port " + socksPort);
                    if (callback != null) {
                        AndroidUtilities.runOnUIThread(() -> callback.onSuccess(socksPort));
                    }
                } else {
                    isRunning.set(false);
                    FileLog.e(TAG + " qWDTT failed to open socks port " + socksPort);
                    stopInternal();
                    if (callback != null) {
                        AndroidUtilities.runOnUIThread(() -> callback.onError("qWDTT process started but port " + socksPort + " did not respond in time.\n" + lastLogs));
                    }
                }

            } catch (Throwable e) {
                FileLog.e(TAG + " error starting qWDTT: " + e.getMessage(), e);
                isStarting.set(false);
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
        isStarting.set(false);
        isRunning.set(false);
        if (process != null) {
            try {
                OutputStream os = process.getOutputStream();
                if (os != null) {
                    os.write("STOP\n".getBytes());
                    os.flush();
                }
            } catch (Throwable ignored) {}

            try {
                process.destroy();
                for (int i = 0; i < 10; i++) {
                    try {
                        process.exitValue();
                        break;
                    } catch (IllegalThreadStateException e) {
                        try { Thread.sleep(50); } catch (Throwable ignored) {}
                    }
                }
                process.destroyForcibly();
            } catch (Throwable ignored) {}

            process = null;
            FileLog.d(TAG + " qWDTT stopped");
            try { Thread.sleep(150); } catch (Throwable ignored) {}
        }
    }

    private void ensureVkProfileJson(Context context) {
        try {
            File profileFile = new File(context.getFilesDir(), "vk_profile.json");
            if (!profileFile.exists() || profileFile.length() < 10) {
                JSONObject profile = new JSONObject();
                profile.put("user_agent", "Mozilla/5.0 (Linux; Android 14; Mobile; rv:131.0) Gecko/131.0 Firefox/131.0");
                JSONObject dev = new JSONObject();
                dev.put("screenWidth", 1080);
                dev.put("screenHeight", 2400);
                dev.put("screenAvailWidth", 1080);
                dev.put("screenAvailHeight", 2400);
                dev.put("innerWidth", 1080);
                dev.put("innerHeight", 2100);
                dev.put("devicePixelRatio", 2.5);
                dev.put("language", "ru-RU");
                org.json.JSONArray langs = new org.json.JSONArray();
                langs.put("ru-RU");
                langs.put("ru");
                langs.put("en-US");
                dev.put("languages", langs);
                dev.put("webdriver", false);
                dev.put("hardwareConcurrency", 8);
                dev.put("notificationsPermission", "default");
                profile.put("device_json", dev.toString());
                profile.put("browser_fp", "WhitegramAndroidClient_" + (System.currentTimeMillis() % 1000000));

                try (FileOutputStream fos = new FileOutputStream(profileFile)) {
                    fos.write(profile.toString().getBytes("UTF-8"));
                }
            }
        } catch (Throwable e) {
            FileLog.e(TAG + " failed to create vk_profile.json: " + e.getMessage());
        }
    }

    private File getOrExtractBinary(Context context) {
        String nativeDir = context.getApplicationInfo().nativeLibraryDir;
        File libFile = new File(nativeDir, "libclient.so");
        if (libFile.exists() && libFile.canExecute()) {
            return libFile;
        }

        // Fallback: check filesDir
        File filesLib = new File(context.getFilesDir(), "libclient.so");
        if (filesLib.exists() && filesLib.canExecute()) {
            return filesLib;
        }

        // Try to extract from APK native libs or assets if possible
        try {
            if (libFile.exists()) {
                // Copy to filesDir and set executable
                copyFile(libFile, filesLib);
                filesLib.setExecutable(true, false);
                if (filesLib.canExecute()) {
                    return filesLib;
                }
            }
        } catch (Throwable e) {
            FileLog.e(TAG + " error preparing binary", e);
        }

        return libFile.exists() ? libFile : filesLib;
    }

    private void copyFile(File src, File dst) throws Exception {
        try (InputStream in = new java.io.FileInputStream(src);
             OutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
        }
    }

    private boolean waitForPort(int port, int timeoutMs) {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < timeoutMs) {
            if (process == null) {
                return false;
            }
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress("127.0.0.1", port), 300);
                return true;
            } catch (Throwable ignored) {
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }
        return false;
    }
}
