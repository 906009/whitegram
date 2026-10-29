package org.telegram.messenger;

import android.content.SharedPreferences;
import android.os.SystemClock;

import org.telegram.tgnet.ConnectionsManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ProxyRotationController implements NotificationCenter.NotificationCenterDelegate {
    private final static ProxyRotationController INSTANCE = new ProxyRotationController();

    public final static int DEFAULT_TIMEOUT_INDEX = 1;
    public final static List<Integer> ROTATION_TIMEOUTS = Arrays.asList(
            5, 10, 15, 30, 60
    );

    private boolean isCurrentlyChecking;
    private final java.util.concurrent.atomic.AtomicInteger pendingChecksCount = new java.util.concurrent.atomic.AtomicInteger(0);

    private final Runnable checkProxyAndSwitchRunnable = this::performCheckAndSwitch;

    private void performCheckAndSwitch() {
        int currentAccount = UserConfig.selectedAccount;
        int connState = ConnectionsManager.getInstance(currentAccount).getConnectionState();

        // Never disconnect or switch if already happily connected!
        if (connState == ConnectionsManager.ConnectionStateConnected && SharedConfig.currentProxy != null && SharedConfig.currentProxy.available) {
            isCurrentlyChecking = false;
            return;
        }

        // If qWDTT is currently initializing its TURN allocation, allow it more time
        if (QwdttController.getInstance().isStarting()) {
            AndroidUtilities.runOnUIThread(checkProxyAndSwitchRunnable, 10000L);
            return;
        }

        isCurrentlyChecking = true;

        if (SharedConfig.currentProxy != null) {
            if (connState == ConnectionsManager.ConnectionStateConnectingToProxy) {
                SharedConfig.currentProxy.available = false;
                SharedConfig.currentProxy.availableCheckTime = SystemClock.elapsedRealtime();
                SharedConfig.currentProxy.ping = 0;
            }
        }

        pendingChecksCount.set(0);
        java.util.List<SharedConfig.ProxyInfo> toCheck = new java.util.ArrayList<>();
        for (int i = 0; i < SharedConfig.proxyList.size(); i++) {
            SharedConfig.ProxyInfo proxyInfo = SharedConfig.proxyList.get(i);
            if (proxyInfo.checking || SystemClock.elapsedRealtime() - proxyInfo.availableCheckTime < 2 * 60 * 1000) {
                continue;
            }
            toCheck.add(proxyInfo);
        }

        if (toCheck.isEmpty()) {
            isCurrentlyChecking = false;
            switchToAvailable();
            return;
        }

        pendingChecksCount.set(toCheck.size());

        for (SharedConfig.ProxyInfo proxyInfo : toCheck) {
            proxyInfo.checking = true;

            if (proxyInfo.isXray() || proxyInfo.isQwdtt()) {
                if (proxyInfo == SharedConfig.currentProxy && (XrayController.getInstance().isRunning() || QwdttController.getInstance().isRunning())) {
                    String authUser = proxyInfo.isXray() ? XrayController.getInstance().getCurrentAuthUser() : "";
                    String authPass = proxyInfo.isXray() ? XrayController.getInstance().getCurrentAuthPass() : "";
                    int checkPort = proxyInfo.isXray() ? XrayController.getInstance().getCurrentPort() : QwdttController.getInstance().getCurrentPort();
                    if (checkPort <= 0) checkPort = proxyInfo.port;
                    proxyInfo.proxyCheckPingId = ConnectionsManager.getInstance(currentAccount).checkProxy("127.0.0.1", checkPort, authUser, authPass, "", time -> AndroidUtilities.runOnUIThread(() -> {
                        proxyInfo.availableCheckTime = SystemClock.elapsedRealtime();
                        proxyInfo.checking = false;
                        if (time == -1) {
                            proxyInfo.available = false;
                            proxyInfo.ping = 0;
                        } else {
                            proxyInfo.ping = time;
                            proxyInfo.available = true;
                        }
                        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxyCheckDone, proxyInfo);
                        checkCompletion();
                    }));
                } else if (proxyInfo == SharedConfig.currentProxy && QwdttController.getInstance().isStarting()) {
                    proxyInfo.checking = false;
                    checkCompletion();
                } else {
                    XrayController.checkServerPing(proxyInfo, time -> {
                        proxyInfo.availableCheckTime = SystemClock.elapsedRealtime();
                        proxyInfo.checking = false;
                        if (time <= 0) {
                            proxyInfo.available = false;
                            proxyInfo.ping = 0;
                        } else {
                            proxyInfo.ping = time;
                            proxyInfo.available = true;
                        }
                        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxyCheckDone, proxyInfo);
                        checkCompletion();
                    });
                }
                continue;
            }

            proxyInfo.proxyCheckPingId = ConnectionsManager.getInstance(currentAccount).checkProxy(proxyInfo.address, proxyInfo.port, proxyInfo.username, proxyInfo.password, proxyInfo.secret, time -> AndroidUtilities.runOnUIThread(() -> {
                proxyInfo.availableCheckTime = SystemClock.elapsedRealtime();
                proxyInfo.checking = false;
                if (time == -1) {
                    proxyInfo.available = false;
                    proxyInfo.ping = 0;
                } else {
                    proxyInfo.ping = time;
                    proxyInfo.available = true;
                }
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxyCheckDone, proxyInfo);
                checkCompletion();
            }));
        }
    }

    private void checkCompletion() {
        if (pendingChecksCount.decrementAndGet() <= 0) {
            AndroidUtilities.runOnUIThread(() -> {
                int state = ConnectionsManager.getInstance(UserConfig.selectedAccount).getConnectionState();
                if (state != ConnectionsManager.ConnectionStateConnected) {
                    switchToAvailable();
                } else {
                    isCurrentlyChecking = false;
                }
            });
        }
    }

    public static ProxyRotationController getInstance() {
        return INSTANCE;
    }

    public static int getTypePriorityRank(int proxyType, int priorityMode) {
        if (priorityMode == SharedConfig.PRIORITY_MODE_XRAY_FIRST) {
            if (proxyType == SharedConfig.ProxyInfo.PROXY_TYPE_XRAY) return 0;
            if (proxyType == SharedConfig.ProxyInfo.PROXY_TYPE_QWDTT) return 1;
            return 2;
        } else if (priorityMode == SharedConfig.PRIORITY_MODE_QWDTT_FIRST) {
            if (proxyType == SharedConfig.ProxyInfo.PROXY_TYPE_QWDTT) return 0;
            if (proxyType == SharedConfig.ProxyInfo.PROXY_TYPE_XRAY) return 1;
            return 2;
        } else if (priorityMode == SharedConfig.PRIORITY_MODE_STANDARD_FIRST) {
            if (proxyType == SharedConfig.ProxyInfo.PROXY_TYPE_SOCKS5 || proxyType == SharedConfig.ProxyInfo.PROXY_TYPE_MTPROTO) return 0;
            if (proxyType == SharedConfig.ProxyInfo.PROXY_TYPE_XRAY) return 1;
            return 2;
        }
        return 0;
    }

    public static String getPriorityModeName(int mode) {
        switch (mode) {
            case SharedConfig.PRIORITY_MODE_XRAY_FIRST:
                return "Xray → qWDTT → Обычный";
            case SharedConfig.PRIORITY_MODE_QWDTT_FIRST:
                return "qWDTT → Xray → Обычный";
            case SharedConfig.PRIORITY_MODE_STANDARD_FIRST:
                return "Обычный → Xray → qWDTT";
            case SharedConfig.PRIORITY_MODE_PING:
            default:
                return "По минимальному пингу (балансировка)";
        }
    }

    public void checkAndSwitchNow() {
        AndroidUtilities.cancelRunOnUIThread(checkProxyAndSwitchRunnable);
        AndroidUtilities.runOnUIThread(checkProxyAndSwitchRunnable);
    }

    public static void init() {
        INSTANCE.initInternal();
    }

    @SuppressWarnings("ComparatorCombinators")
    private void switchToAvailable() {
        isCurrentlyChecking = false;

        if (!SharedConfig.proxyRotationEnabled) {
            return;
        }

        // Never switch if currently connected!
        int connState = ConnectionsManager.getInstance(UserConfig.selectedAccount).getConnectionState();
        if (connState == ConnectionsManager.ConnectionStateConnected && SharedConfig.currentProxy != null && SharedConfig.currentProxy.available) {
            return;
        }

        List<SharedConfig.ProxyInfo> sortedList = new ArrayList<>(SharedConfig.proxyList);
        Collections.sort(sortedList, (o1, o2) -> {
            if (SharedConfig.proxyPriorityMode != SharedConfig.PRIORITY_MODE_PING) {
                int r1 = getTypePriorityRank(o1.proxyType, SharedConfig.proxyPriorityMode);
                int r2 = getTypePriorityRank(o2.proxyType, SharedConfig.proxyPriorityMode);
                if (r1 != r2) {
                    return Integer.compare(r1, r2);
                }
            }
            return Long.compare(o1.ping, o2.ping);
        });
        boolean switched = false;
        for (SharedConfig.ProxyInfo info : sortedList) {
            if (info == SharedConfig.currentProxy || info.checking) {
                continue;
            }
            if (!info.available && info.availableCheckTime > 0) {
                continue;
            }

            SharedPreferences.Editor editor = MessagesController.getGlobalMainSettings().edit();
            editor.putString("proxy_ip", info.address);
            editor.putString("proxy_pass", info.password);
            editor.putString("proxy_user", info.username);
            editor.putInt("proxy_port", info.port);
            editor.putString("proxy_secret", info.secret);
            editor.putBoolean("proxy_enabled", true);

            if (!info.secret.isEmpty()) {
                editor.putBoolean("proxy_enabled_calls", false);
            }
            editor.apply();

            SharedConfig.currentProxy = info;
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxySettingsChanged);
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxyChangedByRotation);
            SharedConfig.applyCurrentProxy(true);
            switched = true;
            break;
        }

        if (!switched && !SharedConfig.proxyList.isEmpty()) {
            FileLog.d("ProxyRotationController: all proxies exhausted, testing direct and auto-updating subscriptions");
            DirectConnectionController.getInstance().checkNow();

            SubscriptionManager.getInstance().updateAllSubscriptions(null);

            AndroidUtilities.cancelRunOnUIThread(checkProxyAndSwitchRunnable);
            AndroidUtilities.runOnUIThread(() -> {
                for (SharedConfig.ProxyInfo p : SharedConfig.proxyList) {
                    p.availableCheckTime = 0;
                }
                checkAndSwitchNow();
            }, 60_000L);
        }
    }

    private void initInternal() {
        for (int i = 0; i < UserConfig.MAX_ACCOUNT_COUNT; i++) {
            NotificationCenter.getInstance(i).addObserver(this, NotificationCenter.didUpdateConnectionState);
        }
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.proxyCheckDone);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.proxySettingsChanged);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.proxyCheckDone) {
            // Do not switch immediately on individual ping results to prevent thrashing
            return;
        } else if (id == NotificationCenter.proxySettingsChanged) {
            AndroidUtilities.cancelRunOnUIThread(checkProxyAndSwitchRunnable);
        } else if (id == NotificationCenter.didUpdateConnectionState && account == UserConfig.selectedAccount) {
            if (!SharedConfig.isProxyEnabled() || !SharedConfig.proxyRotationEnabled || SharedConfig.proxyList.size() <= 1) {
                return;
            }

            int state = ConnectionsManager.getInstance(account).getConnectionState();

            if (state == ConnectionsManager.ConnectionStateConnectingToProxy) {
                if (!isCurrentlyChecking) {
                    long timeoutSec = ROTATION_TIMEOUTS.get(SharedConfig.proxyRotationTimeout);
                    if (SharedConfig.currentProxy != null && SharedConfig.currentProxy.isQwdtt()) {
                        // qWDTT takes ~15-25 seconds to authenticate and allocate TURN relay channels
                        timeoutSec = Math.max(35, timeoutSec);
                    }
                    if (QwdttController.getInstance().isStarting()) {
                        timeoutSec = Math.max(35, timeoutSec);
                    }
                    AndroidUtilities.runOnUIThread(checkProxyAndSwitchRunnable, timeoutSec * 1000L);
                }
            } else {
                if (state == ConnectionsManager.ConnectionStateConnected && SharedConfig.currentProxy != null) {
                    SharedConfig.currentProxy.available = true;
                    SharedConfig.currentProxy.availableCheckTime = SystemClock.elapsedRealtime();
                }
                AndroidUtilities.cancelRunOnUIThread(checkProxyAndSwitchRunnable);
            }
        }
    }
}
