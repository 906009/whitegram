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
    private Runnable checkProxyAndSwitchRunnable = () -> {
        isCurrentlyChecking = true;

        int currentAccount = UserConfig.selectedAccount;
        if (SharedConfig.currentProxy != null) {
            int connState = ConnectionsManager.getInstance(currentAccount).getConnectionState();
            if (connState == ConnectionsManager.ConnectionStateConnectingToProxy) {
                SharedConfig.currentProxy.available = false;
                SharedConfig.currentProxy.availableCheckTime = SystemClock.elapsedRealtime();
                SharedConfig.currentProxy.ping = 0;
            }
        }
        boolean startedCheck = false;
        for (int i = 0; i < SharedConfig.proxyList.size(); i++) {
            SharedConfig.ProxyInfo proxyInfo = SharedConfig.proxyList.get(i);
            if (proxyInfo.checking || SystemClock.elapsedRealtime() - proxyInfo.availableCheckTime < 2 * 60 * 1000) {
                continue;
            }
            if (proxyInfo.isXray() || proxyInfo.isQwdtt()) {
                if (proxyInfo == SharedConfig.currentProxy) {
                    startedCheck = true;
                    proxyInfo.checking = true;
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
                    }));
                } else {
                    startedCheck = true;
                    proxyInfo.checking = true;
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
                    });
                }
                continue;
            }
            startedCheck = true;
            proxyInfo.checking = true;
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
            }));
        }

        if (!startedCheck) {
            isCurrentlyChecking = false;
            switchToAvailable();
        }
    };

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
            if (!SharedConfig.isProxyEnabled() || !SharedConfig.proxyRotationEnabled || SharedConfig.proxyList.size() <= 1 || !isCurrentlyChecking) {
                return;
            }

            switchToAvailable();
        } else if (id == NotificationCenter.proxySettingsChanged) {
            AndroidUtilities.cancelRunOnUIThread(checkProxyAndSwitchRunnable);
        } else if (id == NotificationCenter.didUpdateConnectionState && account == UserConfig.selectedAccount) {
            if (!SharedConfig.isProxyEnabled() && !SharedConfig.proxyRotationEnabled || SharedConfig.proxyList.size() <= 1) {
                return;
            }

            int state = ConnectionsManager.getInstance(account).getConnectionState();

            if (state == ConnectionsManager.ConnectionStateConnectingToProxy) {
                if (!isCurrentlyChecking) {
                    AndroidUtilities.runOnUIThread(checkProxyAndSwitchRunnable, ROTATION_TIMEOUTS.get(SharedConfig.proxyRotationTimeout) * 1000L);
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
