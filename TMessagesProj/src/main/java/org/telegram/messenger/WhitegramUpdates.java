package org.telegram.messenger;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.text.TextUtils;
import android.util.TypedValue;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.RadioColorCell;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;

public class WhitegramUpdates {

    public static final String UPDATE_MANIFEST_URL = "https://raw.githubusercontent.com/906009/whitegram/main/version.json";
    public static final String UPDATE_MANIFEST_URL_MASTER = "https://raw.githubusercontent.com/906009/whitegram/master/version.json";

    public interface UpdateCheckCallback {
        void onResult(boolean hasUpdate, String newVersion, String changelog, String apkUrl, String error);
    }

    public static void showUpdateSettingsDialog(Activity activity, Theme.ResourcesProvider resourcesProvider, Runnable onCheckClick) {
        if (activity == null) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(activity, resourcesProvider);
        builder.setTitle("Whitegram " + BuildVars.BUILD_VERSION_STRING);

        final LinearLayout linearLayout = new LinearLayout(activity);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        linearLayout.setPadding(0, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8));

        TextView infoView = new TextView(activity);
        infoView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        infoView.setTextColor(Theme.getColor(Theme.key_dialogTextGray2, resourcesProvider));
        infoView.setPadding(AndroidUtilities.dp(24), 0, AndroidUtilities.dp(24), AndroidUtilities.dp(12));
        infoView.setText("Ветка обновлений:");
        linearLayout.addView(infoView);

        final RadioColorCell[] cells = new RadioColorCell[2];
        String[] titles = new String[]{
                "12.1.1 LTS (Стабильная)\nОбновления ядра Xray и безопасность",
                "Latest (Актуальная)\nНовые версии Telegram по мере выхода"
        };

        int currentIdx = SharedConfig.UPDATE_CHANNEL_LATEST.equals(SharedConfig.updateChannel) ? 1 : 0;

        for (int a = 0; a < 2; a++) {
            final int idx = a;
            RadioColorCell cell = new RadioColorCell(activity, resourcesProvider);
            cell.setPadding(AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12), 0);
            cell.setCheckColor(Theme.getColor(Theme.key_radioBackground, resourcesProvider), Theme.getColor(Theme.key_dialogRadioBackgroundChecked, resourcesProvider));
            cell.setTextAndValue(titles[a], currentIdx == a);
            cells[a] = cell;
            linearLayout.addView(cell);

            cell.setOnClickListener(v -> {
                for (int i = 0; i < 2; i++) {
                    cells[i].setChecked(i == idx, true);
                }
                SharedConfig.setUpdateChannel(idx == 1 ? SharedConfig.UPDATE_CHANNEL_LATEST : SharedConfig.UPDATE_CHANNEL_LTS);
            });
        }

        builder.setView(linearLayout);
        builder.setPositiveButton("Проверить обновления", (dialog, which) -> {
            if (onCheckClick != null) {
                onCheckClick.run();
            }
        });
        builder.setNegativeButton("Закрыть", null);
        builder.show();
    }

    public static void checkUpdates(Context context, UpdateCheckCallback callback) {
        Utilities.globalQueue.postRunnable(() -> {
            try {
                String response = fetchManifest(UPDATE_MANIFEST_URL);
                if (TextUtils.isEmpty(response)) {
                    response = fetchManifest(UPDATE_MANIFEST_URL_MASTER);
                }
                if (TextUtils.isEmpty(response)) {
                    AndroidUtilities.runOnUIThread(() -> callback.onResult(false, null, null, null, "Сервер обновлений пока не настроен"));
                    return;
                }

                JSONObject root = new JSONObject(response);
                JSONObject channels = root.optJSONObject("channels");
                if (channels == null) {
                    AndroidUtilities.runOnUIThread(() -> callback.onResult(false, null, null, null, "Неверный формат ответа"));
                    return;
                }

                String currentChannel = SharedConfig.updateChannel;
                JSONObject channelObj = channels.optJSONObject(currentChannel);
                if (channelObj == null) {
                    channelObj = channels.optJSONObject(SharedConfig.UPDATE_CHANNEL_LTS);
                }

                if (channelObj == null) {
                    AndroidUtilities.runOnUIThread(() -> callback.onResult(false, null, null, null, null));
                    return;
                }

                int remoteVersionCode = channelObj.optInt("version_code", 0);
                String remoteVersionName = channelObj.optString("version_name", "");
                String apkUrl = channelObj.optString("apk_url", "");
                String changelog = channelObj.optString("changelog", "");

                int currentCode = 0;
                try {
                    PackageInfo pInfo = ApplicationLoader.applicationContext.getPackageManager().getPackageInfo(ApplicationLoader.applicationContext.getPackageName(), 0);
                    currentCode = pInfo.versionCode;
                } catch (Exception ignore) {}

                final boolean hasUpdate = remoteVersionCode > currentCode && !TextUtils.isEmpty(apkUrl);
                AndroidUtilities.runOnUIThread(() -> callback.onResult(hasUpdate, remoteVersionName, changelog, apkUrl, null));
            } catch (Exception e) {
                FileLog.e(e);
                AndroidUtilities.runOnUIThread(() -> callback.onResult(false, null, null, null, e.getMessage()));
            }
        });
    }

    private static String fetchManifest(String urlString) {
        String res = fetchDirect(urlString, null);
        if (res == null && SharedConfig.currentProxy != null && SharedConfig.isProxyEnabled()) {
            res = fetchDirect(urlString, new Proxy(Proxy.Type.SOCKS, new InetSocketAddress("127.0.0.1", 10808)));
        }
        return res;
    }

    private static String fetchDirect(String urlString, Proxy proxy) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlString);
            conn = (HttpURLConnection) (proxy != null ? url.openConnection(proxy) : url.openConnection());
            conn.setConnectTimeout(6000);
            conn.setReadTimeout(6000);
            conn.setRequestProperty("User-Agent", "Whitegram-Updater");
            if (conn.getResponseCode() != 200) {
                return null;
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            reader.close();
            return sb.toString();
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) {
                try {
                    conn.disconnect();
                } catch (Exception ignore) {}
            }
        }
    }
}
