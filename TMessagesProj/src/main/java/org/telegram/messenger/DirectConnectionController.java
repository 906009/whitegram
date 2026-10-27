package org.telegram.messenger;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.os.Build;
import android.os.SystemClock;

import org.telegram.tgnet.ConnectionsManager;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;

public class DirectConnectionController implements NotificationCenter.NotificationCenterDelegate {

    private static final String TAG = "DirectConnectionController";

    private static final DirectConnectionController INSTANCE = new DirectConnectionController();

    // Known Telegram Datacenter IPs for direct connectivity check
    private static final String[] DC_IPS = new String[]{
            "149.154.175.50",
            "149.154.167.51",
            "91.108.56.165"
    };
    private static final int DC_PORT = 443;
    private static final int CHECK_TIMEOUT_MS = 2500;
    private static final long INITIAL_INTERVAL_MS = 25_000L;
    private static final long MAX_INTERVAL_MS = 300_000L;
    private long currentIntervalMs = INITIAL_INTERVAL_MS;

    private final AtomicBoolean isDirectAvailable = new AtomicBoolean(false);
    private final AtomicBoolean isDirectActive = new AtomicBoolean(false);
    private final AtomicBoolean isChecking = new AtomicBoolean(false);

    private long lastCheckTime = 0;

    private final Runnable periodicCheckRunnable = new Runnable() {
        @Override
        public void run() {
            checkDirectConnection(() -> {
                if (isDirectAvailable.get()) {
                    currentIntervalMs = INITIAL_INTERVAL_MS;
                } else {
                    currentIntervalMs = Math.min(currentIntervalMs * 2, MAX_INTERVAL_MS);
                }
                AndroidUtilities.cancelRunOnUIThread(periodicCheckRunnable);
                AndroidUtilities.runOnUIThread(periodicCheckRunnable, currentIntervalMs);
            });
        }
    };

    public static DirectConnectionController getInstance() {
        return INSTANCE;
    }

    public static void init() {
        INSTANCE.initInternal();
    }

    private DirectConnectionController() {}

    private void initInternal() {
        for (int i = 0; i < UserConfig.MAX_ACCOUNT_COUNT; i++) {
            NotificationCenter.getInstance(i).addObserver(this, NotificationCenter.didUpdateConnectionState);
        }
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.proxySettingsChanged);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                ConnectivityManager cm = (ConnectivityManager) ApplicationLoader.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE);
                if (cm != null) {
                    cm.registerDefaultNetworkCallback(new ConnectivityManager.NetworkCallback() {
                        @Override
                        public void onAvailable(Network network) {
                            resetBackoffAndCheck();
                        }

                        @Override
                        public void onLost(Network network) {
                            resetBackoffAndCheck();
                        }
                    });
                }
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }

        AndroidUtilities.runOnUIThread(periodicCheckRunnable, 3000L);
    }

    public void resetBackoffAndCheck() {
        currentIntervalMs = INITIAL_INTERVAL_MS;
        AndroidUtilities.cancelRunOnUIThread(periodicCheckRunnable);
        checkDirectConnection(() -> {
            AndroidUtilities.cancelRunOnUIThread(periodicCheckRunnable);
            AndroidUtilities.runOnUIThread(periodicCheckRunnable, currentIntervalMs);
        });
    }

    public boolean isDirectConnectionAvailable() {
        return isDirectAvailable.get();
    }

    public boolean isDirectActive() {
        return isDirectActive.get();
    }

    public void checkNow() {
        checkDirectConnection(null);
    }

    public void checkDirectConnection(final Runnable onDone) {
        if (isChecking.getAndSet(true)) {
            if (onDone != null) {
                onDone.run();
            }
            return;
        }

        Utilities.globalQueue.postRunnable(() -> {
            boolean reachable = false;
            for (String ip : DC_IPS) {
                try (Socket socket = new Socket()) {
                    socket.connect(new InetSocketAddress(ip, DC_PORT), CHECK_TIMEOUT_MS);
                    reachable = true;
                    break;
                } catch (Throwable ignored) {}
            }

            final boolean directOk = reachable;
            isDirectAvailable.set(directOk);
            lastCheckTime = SystemClock.elapsedRealtime();
            isChecking.set(false);

            AndroidUtilities.runOnUIThread(() -> {
                evaluateRouting(directOk);
                if (onDone != null) {
                    onDone.run();
                }
            });
        });
    }

    private void evaluateRouting(boolean directOk) {
        if (!SharedConfig.isProxyEnabled() || !SharedConfig.proxyDirectFallback) {
            isDirectActive.set(false);
            return;
        }

        SharedConfig.ProxyInfo current = SharedConfig.currentProxy;
        if (current == null) {
            isDirectActive.set(false);
            return;
        }

        if (directOk) {
            // Direct works! Bypass proxy and connect directly
            if (!isDirectActive.get()) {
                FileLog.d(TAG + " Direct connection is available! Routing directly (proxy on standby).");
                isDirectActive.set(true);

                // Disable proxy in TgNet so traffic flows directly
                ConnectionsManager.setProxySettings(false, "", 1080, "", "", "");

                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxySettingsChanged);
            }
        } else {
            // Direct connection is blocked/unreachable! Re-engage active proxy
            if (isDirectActive.get()) {
                FileLog.d(TAG + " Direct connection failed! Switching back to proxy: " + current.address);
                isDirectActive.set(false);

                SharedConfig.applyCurrentProxy(true);

                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxySettingsChanged);
            }
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.proxySettingsChanged) {
            if (!SharedConfig.isProxyEnabled()) {
                isDirectActive.set(false);
            }
        } else if (id == NotificationCenter.didUpdateConnectionState && account == UserConfig.selectedAccount) {
            if (!SharedConfig.isProxyEnabled() || !SharedConfig.proxyDirectFallback) {
                return;
            }

            int state = ConnectionsManager.getInstance(account).getConnectionState();
            if (state == ConnectionsManager.ConnectionStateConnectingToProxy) {
                // If proxy is struggling or direct fallback might help, check direct
                if (SystemClock.elapsedRealtime() - lastCheckTime > 10_000L) {
                    checkNow();
                }
            } else if (state == ConnectionsManager.ConnectionStateConnecting && isDirectActive.get()) {
                // We thought direct was active, but connection is stuck connecting
                if (SystemClock.elapsedRealtime() - lastCheckTime > 8_000L) {
                    checkNow();
                }
            }
        }
    }
}
