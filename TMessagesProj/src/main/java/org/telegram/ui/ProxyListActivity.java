/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import org.telegram.messenger.XrayController;
import org.telegram.messenger.QwdttController;
import org.telegram.messenger.DirectConnectionController;
import org.telegram.messenger.SubscriptionManager;
import org.telegram.messenger.ProxyRotationController;
import org.telegram.ui.Components.EditTextBoldCursor;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;
import java.util.UUID;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DownloadController;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.ProxyRotationController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.CheckBox2;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.NumberTextView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SlideChooseView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ProxyListActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {
    private final static boolean IS_PROXY_ROTATION_AVAILABLE = true;
    private static final int MENU_DELETE = 0;
    private static final int MENU_SHARE = 1;

    private ListAdapter listAdapter;
    private RecyclerListView listView;
    @SuppressWarnings("FieldCanBeLocal")
    private LinearLayoutManager layoutManager;

    private int currentConnectionState;

    private boolean useProxySettings;
    private boolean useProxyForCalls;

    private int rowCount;
    private int useProxyRow;
    private int directFallbackRow;
    private int directFallbackInfoRow;
    private int subscriptionHeaderRow;
    private int subscriptionAddRow;
    private int subscriptionUpdateRow;
    private int subscriptionUpdateViaProxyRow;
    private int subscriptionStartRow;
    private int subscriptionEndRow;
    private int hwidSettingsRow;
    private int subscriptionShadowRow;
    private int useProxyShadowRow;
    private int connectionsHeaderRow;
    private int proxyStartRow;
    private int proxyEndRow;
    private int proxyAddRow;
    private int proxyShadowRow;
    private int callsRow;
    private int rotationRow;
    private int proxyPriorityRow;
    private int rotationTimeoutRow;
    private int rotationTimeoutInfoRow;
    private int callsDetailRow;
    private int deleteAllRow;

    private ItemTouchHelper itemTouchHelper;
    private NumberTextView selectedCountTextView;
    private ActionBarMenuItem shareMenuItem;
    private ActionBarMenuItem deleteMenuItem;

    private List<SharedConfig.ProxyInfo> selectedItems = new ArrayList<>();
    private List<SharedConfig.ProxyInfo> proxyList = new ArrayList<>();
    private boolean wasCheckedAllList;

    public class TextDetailProxyCell extends FrameLayout {

        private TextView textView;
        private TextView valueTextView;
        private ImageView checkImageView;
        private SharedConfig.ProxyInfo currentInfo;
        private Drawable checkDrawable;

        private CheckBox2 checkBox;
        private boolean isSelected;
        private boolean isSelectionEnabled;

        private int color;

        public TextDetailProxyCell(Context context) {
            super(context);

            textView = new TextView(context);
            textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            textView.setLines(1);
            textView.setMaxLines(1);
            textView.setSingleLine(true);
            textView.setEllipsize(TextUtils.TruncateAt.END);
            textView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
            addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, (LocaleController.isRTL ? 56 : 21), 10, (LocaleController.isRTL ? 21 : 56), 0));

            valueTextView = new TextView(context);
            valueTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            valueTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            valueTextView.setLines(1);
            valueTextView.setMaxLines(1);
            valueTextView.setSingleLine(true);
            valueTextView.setCompoundDrawablePadding(AndroidUtilities.dp(6));
            valueTextView.setEllipsize(TextUtils.TruncateAt.END);
            valueTextView.setPadding(0, 0, 0, 0);
            addView(valueTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, (LocaleController.isRTL ? 56 : 21), 35, (LocaleController.isRTL ? 21 : 56), 0));

            checkImageView = new ImageView(context);
            checkImageView.setImageResource(R.drawable.msg_info);
            checkImageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText3), PorterDuff.Mode.MULTIPLY));
            checkImageView.setScaleType(ImageView.ScaleType.CENTER);
            checkImageView.setContentDescription(LocaleController.getString(R.string.Edit));
            addView(checkImageView, LayoutHelper.createFrame(48, 48, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.TOP, 8, 8, 8, 0));
            checkImageView.setOnClickListener(v -> presentFragment(new ProxySettingsActivity(currentInfo)));

            checkBox = new CheckBox2(context, 21);
            checkBox.setColor(Theme.key_checkbox, Theme.key_radioBackground, Theme.key_checkboxCheck);
            checkBox.setDrawBackgroundAsArc(14);
            checkBox.setVisibility(GONE);
            addView(checkBox, LayoutHelper.createFrame(24, 24, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL, 16, 0, 8, 0));

            setWillNotDraw(false);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(64) + 1, MeasureSpec.EXACTLY));
        }

        public void setProxy(SharedConfig.ProxyInfo proxyInfo) {
            if (proxyInfo.isXray()) {
                String title = !TextUtils.isEmpty(proxyInfo.username) ? proxyInfo.username : ("Xray (" + (proxyInfo.port > 0 ? proxyInfo.port : XrayController.DEFAULT_SOCKS_PORT) + ")");
                textView.setText(title);
            } else if (proxyInfo.isQwdtt()) {
                String title = !TextUtils.isEmpty(proxyInfo.username) ? proxyInfo.username : "";
                if (TextUtils.isEmpty(title)) {
                    QwdttController.QwdttConfig cfg = QwdttController.QwdttConfig.fromJson(proxyInfo.extra);
                    String peer = TextUtils.isEmpty(cfg.peer) ? "" : cfg.peer;
                    title = "qWDTT" + (peer.isEmpty() ? "" : " (" + peer + ")");
                }
                textView.setText(title);
            } else {
                textView.setText(proxyInfo.address + ":" + proxyInfo.port);
            }
            currentInfo = proxyInfo;
        }

        public void updateStatus() {
            int colorKey;
            if (SharedConfig.currentProxy == currentInfo && useProxySettings) {
                if (DirectConnectionController.getInstance().isDirectActive()) {
                    colorKey = Theme.key_windowBackgroundWhiteGreenText;
                    valueTextView.setText("Прямое подключение (прокси в резерве)");
                } else if (currentConnectionState == ConnectionsManager.ConnectionStateConnected || currentConnectionState == ConnectionsManager.ConnectionStateUpdating) {
                    colorKey = Theme.key_windowBackgroundWhiteBlueText6;
                    if (currentInfo.ping != 0) {
                        valueTextView.setText(LocaleController.getString(R.string.Connected) + ", " + LocaleController.formatString("Ping", R.string.Ping, currentInfo.ping));
                    } else {
                        valueTextView.setText(LocaleController.getString(R.string.Connected));
                    }
                    if (!currentInfo.checking && !currentInfo.available) {
                        currentInfo.availableCheckTime = 0;
                    }
                } else {
                    colorKey = Theme.key_windowBackgroundWhiteGrayText2;
                    valueTextView.setText(LocaleController.getString(R.string.Connecting));
                }
            } else {
                if (currentInfo.checking) {
                    valueTextView.setText(LocaleController.getString(R.string.Checking));
                    colorKey = Theme.key_windowBackgroundWhiteGrayText2;
                } else if (currentInfo.available) {
                    if (currentInfo.ping != 0) {
                        valueTextView.setText(LocaleController.getString(R.string.Available) + ", " + LocaleController.formatString("Ping", R.string.Ping, currentInfo.ping));
                    } else {
                        valueTextView.setText(LocaleController.getString(R.string.Available));
                    }
                    colorKey = Theme.key_windowBackgroundWhiteGreenText;
                } else {
                    valueTextView.setText(LocaleController.getString(R.string.Unavailable));
                    colorKey = Theme.key_text_RedRegular;
                }
            }
            color = Theme.getColor(colorKey);
            valueTextView.setTag(colorKey);
            valueTextView.setTextColor(color);
            if (checkDrawable != null) {
                checkDrawable.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY));
            }
        }

        public void setSelectionEnabled(boolean enabled, boolean animated) {
            if (isSelectionEnabled == enabled && animated) {
                return;
            }
            isSelectionEnabled = enabled;

            float fromX = 0, toX = LocaleController.isRTL ? -AndroidUtilities.dp(32) : AndroidUtilities.dp(32);
            if (!animated) {
                float x = enabled ? toX : fromX;
                textView.setTranslationX(x);
                valueTextView.setTranslationX(x);
                checkImageView.setTranslationX(x);
                checkBox.setTranslationX((LocaleController.isRTL ? AndroidUtilities.dp(32) : -AndroidUtilities.dp(32)) + x);
                checkImageView.setVisibility(enabled ? GONE : VISIBLE);
                checkImageView.setAlpha(1f);
                checkImageView.setScaleX(1f);
                checkImageView.setScaleY(1f);
                checkBox.setVisibility(enabled ? VISIBLE : GONE);
                checkBox.setAlpha(1f);
                checkBox.setScaleX(1f);
                checkBox.setScaleY(1f);
            } else {
                ValueAnimator animator = ValueAnimator.ofFloat(enabled ? 0 : 1, enabled ? 1 : 0).setDuration(200);
                animator.setInterpolator(CubicBezierInterpolator.DEFAULT);
                animator.addUpdateListener(animation -> {
                    float val = (float) animation.getAnimatedValue();
                    float x = AndroidUtilities.lerp(fromX, toX, val);
                    textView.setTranslationX(x);
                    valueTextView.setTranslationX(x);
                    checkImageView.setTranslationX(x);
                    checkBox.setTranslationX((LocaleController.isRTL ? AndroidUtilities.dp(32) : -AndroidUtilities.dp(32)) + x);

                    float scale = 0.5f + val * 0.5f;
                    checkBox.setScaleX(scale);
                    checkBox.setScaleY(scale);
                    checkBox.setAlpha(val);

                    scale = 0.5f + (1f - val) * 0.5f;
                    checkImageView.setScaleX(scale);
                    checkImageView.setScaleY(scale);
                    checkImageView.setAlpha(1f - val);
                });
                animator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationStart(Animator animation) {
                        if (enabled) {
                            checkBox.setAlpha(0f);
                            checkBox.setVisibility(VISIBLE);
                        } else {
                            checkImageView.setAlpha(0f);
                            checkImageView.setVisibility(VISIBLE);
                        }
                    }

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (enabled) {
                            checkImageView.setVisibility(GONE);
                        } else {
                            checkBox.setVisibility(GONE);
                        }
                    }
                });
                animator.start();
            }
        }

        public void setItemSelected(boolean selected, boolean animated) {
            if (selected == isSelected && animated) {
                return;
            }
            isSelected = selected;
            checkBox.setChecked(selected, animated);
        }

        public void setChecked(boolean checked) {
            if (checked) {
                if (checkDrawable == null) {
                    checkDrawable = getResources().getDrawable(R.drawable.proxy_check).mutate();
                }
                if (checkDrawable != null) {
                    checkDrawable.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY));
                }
                if (LocaleController.isRTL) {
                    valueTextView.setCompoundDrawablesWithIntrinsicBounds(null, null, checkDrawable, null);
                } else {
                    valueTextView.setCompoundDrawablesWithIntrinsicBounds(checkDrawable, null, null, null);
                }
            } else {
                valueTextView.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null);
            }
        }

        public void setValue(CharSequence value) {
            valueTextView.setText(value);
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            updateStatus();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.drawLine(LocaleController.isRTL ? 0 : AndroidUtilities.dp(20), getMeasuredHeight() - 1, getMeasuredWidth() - (LocaleController.isRTL ? AndroidUtilities.dp(20) : 0), getMeasuredHeight() - 1, Theme.dividerPaint);
        }
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();

        SharedConfig.loadProxyList();
        currentConnectionState = ConnectionsManager.getInstance(currentAccount).getConnectionState();

        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.proxyChangedByRotation);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.proxySettingsChanged);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.proxyCheckDone);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.didUpdateConnectionState);

        final SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        useProxySettings = preferences.getBoolean("proxy_enabled", false) && !SharedConfig.proxyList.isEmpty();
        useProxyForCalls = preferences.getBoolean("proxy_enabled_calls", false);

        updateRows(true);

        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.proxyChangedByRotation);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.proxySettingsChanged);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.proxyCheckDone);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.didUpdateConnectionState);
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonDrawable(new BackDrawable(false));
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString(R.string.ProxySettings));
        if (AndroidUtilities.isTablet()) {
            actionBar.setOccupyStatusBar(false);
        }
        actionBar.setAllowOverlayTitle(false);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        listAdapter = new ListAdapter(context);

        fragmentView = new FrameLayout(context);
        fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        FrameLayout frameLayout = (FrameLayout) fragmentView;

        listView = new RecyclerListView(context) {
            @Override
            protected void dispatchDraw(Canvas canvas) {
                drawSectionBackground(canvas, proxyStartRow, proxyEndRow, Theme.getColor(Theme.key_windowBackgroundWhite));
                super.dispatchDraw(canvas);
            }
        };
        ((DefaultItemAnimator) listView.getItemAnimator()).setDelayAnimations(false);
        ((DefaultItemAnimator) listView.getItemAnimator()).setTranslationInterpolator(CubicBezierInterpolator.DEFAULT);
        listView.setVerticalScrollBarEnabled(false);
        listView.setLayoutManager(layoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));
        listView.setAdapter(listAdapter);
        listView.setOnItemClickListener((view, position) -> {
            if (position == directFallbackRow) {
                SharedConfig.proxyDirectFallback = !SharedConfig.proxyDirectFallback;
                TextCheckCell textCheckCell = (TextCheckCell) view;
                textCheckCell.setChecked(SharedConfig.proxyDirectFallback);
                SharedConfig.saveProxyList();
                DirectConnectionController.getInstance().checkNow();
                return;
            } else if (position == useProxyRow) {
                if (SharedConfig.currentProxy == null) {
                    if (!proxyList.isEmpty()) {
                        SharedConfig.currentProxy = proxyList.get(0);

                        if (!useProxySettings) {
                            SharedPreferences preferences = MessagesController.getGlobalMainSettings();
                            SharedPreferences.Editor editor = MessagesController.getGlobalMainSettings().edit();
                            editor.putString("proxy_ip", SharedConfig.currentProxy.address);
                            editor.putString("proxy_pass", SharedConfig.currentProxy.password);
                            editor.putString("proxy_user", SharedConfig.currentProxy.username);
                            editor.putInt("proxy_port", SharedConfig.currentProxy.port);
                            editor.putString("proxy_secret", SharedConfig.currentProxy.secret);
                            editor.commit();
                        }
                    } else {
                        presentFragment(new ProxySettingsActivity());
                        return;
                    }
                }
                useProxySettings = !useProxySettings;
                updateRows(true);

                SharedPreferences preferences = MessagesController.getGlobalMainSettings();

                TextCheckCell textCheckCell = (TextCheckCell) view;
                textCheckCell.setChecked(useProxySettings);
                if (!useProxySettings) {
                    RecyclerListView.Holder holder = (RecyclerListView.Holder) listView.findViewHolderForAdapterPosition(callsRow);
                    if (holder != null) {
                        textCheckCell = (TextCheckCell) holder.itemView;
                        textCheckCell.setChecked(false);
                    }
                    useProxyForCalls = false;
                }

                SharedPreferences.Editor editor = MessagesController.getGlobalMainSettings().edit();
                editor.putBoolean("proxy_enabled", useProxySettings);
                editor.commit();

                SharedConfig.applyCurrentProxy(useProxySettings);
                NotificationCenter.getGlobalInstance().removeObserver(ProxyListActivity.this, NotificationCenter.proxySettingsChanged);
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxySettingsChanged);
                NotificationCenter.getGlobalInstance().addObserver(ProxyListActivity.this, NotificationCenter.proxySettingsChanged);

                for (int a = proxyStartRow; a < proxyEndRow; a++) {
                    RecyclerListView.Holder holder = (RecyclerListView.Holder) listView.findViewHolderForAdapterPosition(a);
                    if (holder != null) {
                        TextDetailProxyCell cell = (TextDetailProxyCell) holder.itemView;
                        cell.updateStatus();
                    }
                }
            } else if (position == subscriptionAddRow) {
                showAddSubscriptionDialog();
            } else if (position == subscriptionUpdateRow) {
                updateAllSubscriptionsWithProgress();
            } else if (position == subscriptionUpdateViaProxyRow) {
                SharedConfig.proxyUpdateSubsViaProxy = !SharedConfig.proxyUpdateSubsViaProxy;
                MessagesController.getGlobalMainSettings().edit().putBoolean("proxy_update_subs_via_proxy", SharedConfig.proxyUpdateSubsViaProxy).apply();
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(SharedConfig.proxyUpdateSubsViaProxy);
                }
            } else if (position >= subscriptionStartRow && position < subscriptionEndRow) {
                ArrayList<SubscriptionManager.Subscription> subsList = SubscriptionManager.getInstance().getSubscriptions();
                int idx = position - subscriptionStartRow;
                if (idx >= 0 && idx < subsList.size()) {
                    showSubscriptionOptionsDialog(subsList.get(idx));
                }
            } else if (position == hwidSettingsRow) {
                showHwidSettingsDialog();
            } else if (position == proxyPriorityRow) {
                showProxyPriorityDialog();
            } else if (position == rotationRow) {
                SharedConfig.proxyRotationEnabled = !SharedConfig.proxyRotationEnabled;
                TextCheckCell textCheckCell = (TextCheckCell) view;
                textCheckCell.setChecked(SharedConfig.proxyRotationEnabled);
                SharedConfig.saveConfig();

                updateRows(true);
            } else if (position == callsRow) {
                useProxyForCalls = !useProxyForCalls;
                TextCheckCell textCheckCell = (TextCheckCell) view;
                textCheckCell.setChecked(useProxyForCalls);
                SharedPreferences.Editor editor = MessagesController.getGlobalMainSettings().edit();
                editor.putBoolean("proxy_enabled_calls", useProxyForCalls);
                editor.commit();
            } else if (position >= proxyStartRow && position < proxyEndRow) {
                if (!selectedItems.isEmpty()) {
                    listAdapter.toggleSelected(position);
                    return;
                }
                SharedConfig.ProxyInfo info = proxyList.get(position - proxyStartRow);
                useProxySettings = true;
                SharedPreferences.Editor editor = MessagesController.getGlobalMainSettings().edit();
                editor.putString("proxy_ip", info.address);
                editor.putString("proxy_pass", info.password);
                editor.putString("proxy_user", info.username);
                editor.putInt("proxy_port", info.port);
                editor.putString("proxy_secret", info.secret);
                editor.putBoolean("proxy_enabled", useProxySettings);
                if (!info.secret.isEmpty()) {
                    useProxyForCalls = false;
                    editor.putBoolean("proxy_enabled_calls", false);
                }
                editor.commit();
                SharedConfig.currentProxy = info;
                for (int a = proxyStartRow; a < proxyEndRow; a++) {
                    RecyclerListView.Holder holder = (RecyclerListView.Holder) listView.findViewHolderForAdapterPosition(a);
                    if (holder != null) {
                        TextDetailProxyCell cell = (TextDetailProxyCell) holder.itemView;
                        cell.setChecked(cell.currentInfo == info);
                        cell.updateStatus();
                    }
                }
                updateRows(false);
                RecyclerListView.Holder holder = (RecyclerListView.Holder) listView.findViewHolderForAdapterPosition(useProxyRow);
                if (holder != null) {
                    TextCheckCell textCheckCell = (TextCheckCell) holder.itemView;
                    textCheckCell.setChecked(true);
                }
                SharedConfig.applyCurrentProxy(useProxySettings);
            } else if (position == proxyAddRow) {
                presentFragment(new ProxySettingsActivity());
            } else if (position == deleteAllRow) {
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                builder.setMessage(LocaleController.getString(R.string.DeleteAllProxiesConfirm));
                builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
                builder.setTitle(LocaleController.getString(R.string.DeleteProxyTitle));
                builder.setPositiveButton(LocaleController.getString(R.string.Delete), (dialog, which) -> {
                    for (SharedConfig.ProxyInfo info : proxyList) {
                        SharedConfig.deleteProxy(info);
                    }
                    useProxyForCalls = false;
                    useProxySettings = false;
                    NotificationCenter.getGlobalInstance().removeObserver(ProxyListActivity.this, NotificationCenter.proxySettingsChanged);
                    NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxySettingsChanged);
                    NotificationCenter.getGlobalInstance().addObserver(ProxyListActivity.this, NotificationCenter.proxySettingsChanged);
                    updateRows(true);
                    if (listAdapter != null) {
                        listAdapter.notifyItemChanged(useProxyRow, ListAdapter.PAYLOAD_CHECKED_CHANGED);
                        listAdapter.notifyItemChanged(callsRow, ListAdapter.PAYLOAD_CHECKED_CHANGED);
                        listAdapter.clearSelected();
                    }
                });
                AlertDialog dialog = builder.create();
                showDialog(dialog);
                TextView button = (TextView) dialog.getButton(DialogInterface.BUTTON_POSITIVE);
                if (button != null) {
                    button.setTextColor(Theme.getColor(Theme.key_text_RedBold));
                }
            }
        });
        listView.setOnItemLongClickListener((view, position) -> {
            if (position >= proxyStartRow && position < proxyEndRow) {
                listAdapter.toggleSelected(position);
                return true;
            }
            return false;
        });

        ActionBarMenu actionMode = actionBar.createActionMode();
        selectedCountTextView = new NumberTextView(actionMode.getContext());
        selectedCountTextView.setTextSize(18);
        selectedCountTextView.setTypeface(AndroidUtilities.bold());
        selectedCountTextView.setTextColor(Theme.getColor(Theme.key_actionBarActionModeDefaultIcon));
        actionMode.addView(selectedCountTextView, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1.0f, 72, 0, 0, 0));
        selectedCountTextView.setOnTouchListener((v, event) -> true);

        shareMenuItem = actionMode.addItemWithWidth(MENU_SHARE, R.drawable.msg_share, AndroidUtilities.dp(54));
        deleteMenuItem = actionMode.addItemWithWidth(MENU_DELETE, R.drawable.msg_delete, AndroidUtilities.dp(54));

        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                switch (id) {
                    case -1:
                        if (selectedItems.isEmpty()) {
                            finishFragment();
                        } else {
                            listAdapter.clearSelected();
                        }
                        break;
                    case MENU_DELETE:
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        builder.setMessage(LocaleController.getString(selectedItems.size() > 1 ? R.string.DeleteProxyMultiConfirm : R.string.DeleteProxyConfirm));
                        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
                        builder.setTitle(LocaleController.getString(R.string.DeleteProxyTitle));
                        builder.setPositiveButton(LocaleController.getString(R.string.Delete), (dialog, which) -> {
                            for (SharedConfig.ProxyInfo info : selectedItems) {
                                SharedConfig.deleteProxy(info);
                            }
                            if (SharedConfig.currentProxy == null) {
                                useProxyForCalls = false;
                                useProxySettings = false;
                            }
                            NotificationCenter.getGlobalInstance().removeObserver(ProxyListActivity.this, NotificationCenter.proxySettingsChanged);
                            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxySettingsChanged);
                            NotificationCenter.getGlobalInstance().addObserver(ProxyListActivity.this, NotificationCenter.proxySettingsChanged);
                            updateRows(true);
                            if (listAdapter != null) {
                                if (SharedConfig.currentProxy == null) {
                                    listAdapter.notifyItemChanged(useProxyRow, ListAdapter.PAYLOAD_CHECKED_CHANGED);
                                    listAdapter.notifyItemChanged(callsRow, ListAdapter.PAYLOAD_CHECKED_CHANGED);
                                }
                                listAdapter.clearSelected();
                            }
                        });
                        AlertDialog dialog = builder.create();
                        showDialog(dialog);
                        TextView button = (TextView) dialog.getButton(DialogInterface.BUTTON_POSITIVE);
                        if (button != null) {
                            button.setTextColor(Theme.getColor(Theme.key_text_RedBold));
                        }
                        break;
                    case MENU_SHARE:
                        StringBuilder links = new StringBuilder();
                        for (SharedConfig.ProxyInfo info : selectedItems) {
                            if (links.length() > 0) {
                                links.append("\n\n");
                            }
                            links.append(info.getLink());
                        }

                        Intent shareIntent = new Intent(Intent.ACTION_SEND);
                        shareIntent.setType("text/plain");
                        shareIntent.putExtra(Intent.EXTRA_TEXT, links.toString());
                        Intent chooserIntent = Intent.createChooser(shareIntent, LocaleController.getString(selectedItems.size() > 1 ? R.string.ShareLinks : R.string.ShareLink));
                        chooserIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(chooserIntent);

                        if (listAdapter != null) {
                            listAdapter.clearSelected();
                        }
                        break;
                }
            }
        });

        return fragmentView;
    }

    private void showHwidSettingsDialog() {
        if (getParentActivity() == null) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle("Идентификаторы (HWID / Device ID)");

        android.widget.LinearLayout linearLayout = new android.widget.LinearLayout(getParentActivity());
        linearLayout.setOrientation(android.widget.LinearLayout.VERTICAL);
        linearLayout.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(16), AndroidUtilities.dp(24), AndroidUtilities.dp(16));

        android.widget.TextView xrayLabel = new android.widget.TextView(getParentActivity());
        xrayLabel.setText("Xray HWID (для подписок и идентификации):");
        xrayLabel.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        xrayLabel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        linearLayout.addView(xrayLabel);

        EditTextBoldCursor xrayField = new EditTextBoldCursor(getParentActivity());
        xrayField.setText(SharedConfig.getXrayHwid());
        xrayField.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        xrayField.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        xrayField.setHintColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
        linearLayout.addView(xrayField, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 16));

        android.widget.TextView qwdttLabel = new android.widget.TextView(getParentActivity());
        qwdttLabel.setText("qWDTT Device ID (привязка устройства к серверу):");
        qwdttLabel.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        qwdttLabel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        linearLayout.addView(qwdttLabel);

        EditTextBoldCursor qwdttField = new EditTextBoldCursor(getParentActivity());
        qwdttField.setText(SharedConfig.getQwdttDeviceId());
        qwdttField.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        qwdttField.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        qwdttField.setHintColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
        linearLayout.addView(qwdttField, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 8));

        builder.setView(linearLayout);
        builder.setPositiveButton(LocaleController.getString(R.string.Save), (dialog, which) -> {
            String newHwid = xrayField.getText().toString().trim();
            String newDevId = qwdttField.getText().toString().trim();
            if (!TextUtils.isEmpty(newHwid)) {
                SharedConfig.setXrayHwid(newHwid);
            }
            if (!TextUtils.isEmpty(newDevId)) {
                SharedConfig.setQwdttDeviceId(newDevId);
            }
            Toast.makeText(getParentActivity(), "Идентификаторы сохранены", Toast.LENGTH_SHORT).show();
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxySettingsChanged);
        });
        builder.setNeutralButton("Сгенерировать новые", (dialog, which) -> {
            SharedConfig.setXrayHwid(UUID.randomUUID().toString());
            SharedConfig.setQwdttDeviceId(UUID.randomUUID().toString().replace("-", "").substring(0, 16));
            Toast.makeText(getParentActivity(), "Сгенерированы новые HWID и Device ID", Toast.LENGTH_SHORT).show();
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxySettingsChanged);
            showHwidSettingsDialog();
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private void showAddSubscriptionDialog() {
        if (getParentActivity() == null) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle("Добавить подписку");

        android.widget.LinearLayout linearLayout = new android.widget.LinearLayout(getParentActivity());
        linearLayout.setOrientation(android.widget.LinearLayout.VERTICAL);
        linearLayout.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(16), AndroidUtilities.dp(24), AndroidUtilities.dp(16));

        final int[] selectedType = new int[]{SubscriptionManager.Subscription.TYPE_XRAY};

        android.widget.TextView typeLabel = new android.widget.TextView(getParentActivity());
        typeLabel.setText("Тип подписки:");
        typeLabel.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        typeLabel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        linearLayout.addView(typeLabel);

        RadioGroup radioGroup = new RadioGroup(getParentActivity());
        radioGroup.setOrientation(RadioGroup.HORIZONTAL);
        RadioButton xrayRadio = new RadioButton(getParentActivity());
        xrayRadio.setId(android.view.View.generateViewId());
        xrayRadio.setText("Xray");
        xrayRadio.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        radioGroup.addView(xrayRadio);

        RadioButton qwdttRadio = new RadioButton(getParentActivity());
        qwdttRadio.setId(android.view.View.generateViewId());
        qwdttRadio.setText("qWDTT");
        qwdttRadio.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        radioGroup.addView(qwdttRadio);

        radioGroup.check(xrayRadio.getId());

        radioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == xrayRadio.getId()) {
                selectedType[0] = SubscriptionManager.Subscription.TYPE_XRAY;
            } else if (checkedId == qwdttRadio.getId()) {
                selectedType[0] = SubscriptionManager.Subscription.TYPE_QWDTT;
            }
        });
        linearLayout.addView(radioGroup, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 12));

        EditTextBoldCursor titleField = new EditTextBoldCursor(getParentActivity());
        titleField.setHintText("Название (например: My Servers)");
        titleField.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        titleField.setHintColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
        titleField.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        linearLayout.addView(titleField, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 12));

        EditTextBoldCursor urlField = new EditTextBoldCursor(getParentActivity());
        urlField.setHintText("URL подписки (https://...)");
        urlField.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        urlField.setHintColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
        urlField.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        linearLayout.addView(urlField, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 8));

        builder.setView(linearLayout);
        builder.setPositiveButton(LocaleController.getString(R.string.OK), (dialog, which) -> {
            String url = urlField.getText().toString().trim();
            String title = titleField.getText().toString().trim();
            if (TextUtils.isEmpty(url)) return;
            if (TextUtils.isEmpty(title)) {
                title = selectedType[0] == SubscriptionManager.Subscription.TYPE_XRAY ? "Xray Sub" : "qWDTT Sub";
            }

            SubscriptionManager.Subscription sub = new SubscriptionManager.Subscription();
            sub.title = title;
            sub.url = url;
            sub.type = selectedType[0];

            SubscriptionManager.getInstance().addSubscription(sub);

            AlertDialog progress = new AlertDialog(getParentActivity(), 3);
            progress.setMessage("Загрузка серверов подписки...");
            progress.setCanceledOnTouchOutside(false);
            progress.setCancelable(false);
            showDialog(progress);

            SubscriptionManager.getInstance().updateSubscription(sub, new SubscriptionManager.SubscriptionCallback() {
                @Override
                public void onSuccess(int addedCount) {
                    progress.dismiss();
                    updateRows(true);
                    if (getParentActivity() != null) {
                        Toast.makeText(getParentActivity(), "Добавлено серверов: " + addedCount, Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onError(String error) {
                    progress.dismiss();
                    if (getParentActivity() != null) {
                        Toast.makeText(getParentActivity(), "Ошибка загрузки: " + error, Toast.LENGTH_LONG).show();
                    }
                }
            });
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

        private void showSubscriptionOptionsDialog(SubscriptionManager.Subscription sub) {
        if (getParentActivity() == null || sub == null) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(sub.title);
        CharSequence[] items = new CharSequence[]{
                "🔄 Обновить подписку",
                "🗑️ Удалить подписку и её серверы",
                "❌ Удалить только подписку"
        };
        builder.setItems(items, (dialog, which) -> {
            if (which == 0) {
                AlertDialog progress = new AlertDialog(getParentActivity(), 3);
                progress.setMessage("Обновление подписки «" + sub.title + "»...");
                progress.setCanceledOnTouchOutside(false);
                progress.setCancelable(false);
                showDialog(progress);

                SubscriptionManager.getInstance().updateSubscription(sub, new SubscriptionManager.SubscriptionCallback() {
                    @Override
                    public void onSuccess(int addedCount) {
                        progress.dismiss();
                        updateRows(true);
                        if (getParentActivity() != null) {
                            Toast.makeText(getParentActivity(), "Обновлено. Серверов: " + addedCount, Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onError(String error) {
                        progress.dismiss();
                        if (getParentActivity() != null) {
                            Toast.makeText(getParentActivity(), "Ошибка: " + error, Toast.LENGTH_LONG).show();
                        }
                    }
                });
            } else if (which == 1) {
                SubscriptionManager.getInstance().deleteSubscriptionAndServers(sub);
                updateRows(true);
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxySettingsChanged);
                if (getParentActivity() != null) {
                    Toast.makeText(getParentActivity(), "Подписка и серверы удалены", Toast.LENGTH_SHORT).show();
                }
            } else if (which == 2) {
                SubscriptionManager.getInstance().deleteSubscription(sub.id);
                updateRows(true);
                if (getParentActivity() != null) {
                    Toast.makeText(getParentActivity(), "Подписка удалена", Toast.LENGTH_SHORT).show();
                }
            }
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private void updateAllSubscriptionsWithProgress() {
        if (getParentActivity() == null) return;
        AlertDialog progress = new AlertDialog(getParentActivity(), 3);
        progress.setMessage("Обновление всех подписок...");
        progress.setCanceledOnTouchOutside(false);
        progress.setCancelable(false);
        showDialog(progress);

        SubscriptionManager.getInstance().updateAllSubscriptions(new SubscriptionManager.SubscriptionCallback() {
            @Override
            public void onSuccess(int addedCount) {
                progress.dismiss();
                updateRows(true);
                if (getParentActivity() != null) {
                    Toast.makeText(getParentActivity(), "Подписки обновлены. Загружено серверов: " + addedCount, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onError(String error) {
                progress.dismiss();
                if (getParentActivity() != null) {
                    Toast.makeText(getParentActivity(), "Ошибка: " + error, Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private void showProxyPriorityDialog() {
        if (getParentActivity() == null) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle("Приоритет и автовыбор");

        CharSequence[] options = new CharSequence[]{
                (SharedConfig.proxyPriorityMode == 0 ? "✓ " : "    ") + "По минимальному пингу (балансировка)",
                (SharedConfig.proxyPriorityMode == 1 ? "✓ " : "    ") + "Xray → qWDTT → Обычный",
                (SharedConfig.proxyPriorityMode == 2 ? "✓ " : "    ") + "qWDTT → Xray → Обычный",
                (SharedConfig.proxyPriorityMode == 3 ? "✓ " : "    ") + "Обычный → Xray → qWDTT"
        };

        builder.setItems(options, (dialog, which) -> {
            SharedConfig.proxyPriorityMode = which;
            SharedConfig.saveConfig();
            updateRows(true);
            ProxyRotationController.getInstance().checkAndSwitchNow();
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    @Override
    public boolean onBackPressed() {
        if (!selectedItems.isEmpty()) {
            listAdapter.clearSelected();
            return false;
        }
        return true;
    }

    private void updateRows(boolean notify) {
        rowCount = 0;
        useProxyRow = rowCount++;
        directFallbackRow = rowCount++;
        directFallbackInfoRow = rowCount++;
        if (useProxySettings && SharedConfig.currentProxy != null && SharedConfig.proxyList.size() > 1 && IS_PROXY_ROTATION_AVAILABLE) {
            rotationRow = rowCount++;
            if (SharedConfig.proxyRotationEnabled) {
                proxyPriorityRow = rowCount++;
                rotationTimeoutRow = rowCount++;
                rotationTimeoutInfoRow = rowCount++;
            } else {
                proxyPriorityRow = -1;
                rotationTimeoutRow = -1;
                rotationTimeoutInfoRow = -1;
            }
        } else {
            rotationRow = -1;
            proxyPriorityRow = -1;
            rotationTimeoutRow = -1;
            rotationTimeoutInfoRow = -1;
        }
        if (rotationTimeoutInfoRow == -1) {
            useProxyShadowRow = rowCount++;
        } else {
            useProxyShadowRow = -1;
        }
        connectionsHeaderRow = rowCount++;

        if (notify) {
            proxyList.clear();
            proxyList.addAll(SharedConfig.proxyList);

            boolean checking = false;
            if (!wasCheckedAllList) {
                for (SharedConfig.ProxyInfo info : proxyList) {
                    if (info.checking || info.availableCheckTime == 0) {
                        checking = true;
                        break;
                    }
                }
                if (!checking) {
                    wasCheckedAllList = true;
                }
            }

            boolean isChecking = checking;
            Collections.sort(proxyList, (o1, o2) -> {
                long bias1 = SharedConfig.currentProxy == o1 ? -200000 : 0;
                if (!o1.available) {
                    bias1 += 100000;
                }
                long bias2 = SharedConfig.currentProxy == o2 ? -200000 : 0;
                if (!o2.available) {
                    bias2 += 100000;
                }
                return Long.compare(isChecking && o1 != SharedConfig.currentProxy ? SharedConfig.proxyList.indexOf(o1) * 10000L : o1.ping + bias1,
                        isChecking && o2 != SharedConfig.currentProxy ? SharedConfig.proxyList.indexOf(o2) * 10000L : o2.ping + bias2);
            });
        }

        if (!proxyList.isEmpty()) {
            proxyStartRow = rowCount;
            rowCount += proxyList.size();
            proxyEndRow = rowCount;
        } else {
            proxyStartRow = -1;
            proxyEndRow = -1;
        }
        proxyAddRow = rowCount++;
        proxyShadowRow = rowCount++;
        subscriptionHeaderRow = rowCount++;
        subscriptionAddRow = rowCount++;
        subscriptionUpdateRow = rowCount++;
        subscriptionUpdateViaProxyRow = rowCount++;
        ArrayList<SubscriptionManager.Subscription> subs = SubscriptionManager.getInstance().getSubscriptions();
        if (!subs.isEmpty()) {
            subscriptionStartRow = rowCount;
            rowCount += subs.size();
            subscriptionEndRow = rowCount;
        } else {
            subscriptionStartRow = -1;
            subscriptionEndRow = -1;
        }
        hwidSettingsRow = rowCount++;
        subscriptionShadowRow = rowCount++;
        if (SharedConfig.currentProxy == null || SharedConfig.currentProxy.secret.isEmpty()) {
            boolean change = callsRow == -1;
            callsRow = rowCount++;
            callsDetailRow = rowCount++;
            if (!notify && change) {
                listAdapter.notifyItemChanged(proxyShadowRow);
                listAdapter.notifyItemRangeInserted(proxyShadowRow + 1, 2);
            }
        } else {
            boolean change = callsRow != -1;
            callsRow = -1;
            callsDetailRow = -1;
            if (!notify && change) {
                listAdapter.notifyItemChanged(proxyShadowRow);
                listAdapter.notifyItemRangeRemoved(proxyShadowRow + 1, 2);
            }
        }
        if (proxyList.size() >= 10) {
            deleteAllRow = rowCount++;
        } else {
            deleteAllRow = -1;
        }
        checkProxyList();
        if (notify && listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    private void checkProxyList() {
        for (int a = 0, count = proxyList.size(); a < count; a++) {
            final SharedConfig.ProxyInfo proxyInfo = proxyList.get(a);
            if (proxyInfo.checking || SystemClock.elapsedRealtime() - proxyInfo.availableCheckTime < 2 * 60 * 1000) {
                continue;
            }
            proxyInfo.checking = true;

            if (proxyInfo.isXray() || proxyInfo.isQwdtt()) {
                if (proxyInfo == SharedConfig.currentProxy && (XrayController.getInstance().isRunning() || QwdttController.getInstance().isRunning())) {
                    int checkPort = proxyInfo.isXray() ? XrayController.getInstance().getCurrentPort() : QwdttController.getInstance().getCurrentPort();
                    String user = proxyInfo.isXray() ? XrayController.getInstance().getCurrentAuthUser() : "";
                    String pass = proxyInfo.isXray() ? XrayController.getInstance().getCurrentAuthPass() : "";
                    proxyInfo.proxyCheckPingId = ConnectionsManager.getInstance(currentAccount).checkProxy("127.0.0.1", checkPort, user, pass, "", time -> AndroidUtilities.runOnUIThread(() -> {
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
                } else if (proxyInfo == SharedConfig.currentProxy && QwdttController.getInstance().isStarting()) {
                    // Still starting up, avoid premature inactive check
                    proxyInfo.checking = false;
                    continue;
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
                    });
                }
            } else {
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
        }
    }

    @Override
    protected void onDialogDismiss(Dialog dialog) {
        DownloadController.getInstance(currentAccount).checkAutodownloadSettings();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.proxyChangedByRotation) {
            listView.forAllChild(view -> {
                RecyclerView.ViewHolder holder = listView.getChildViewHolder(view);
                if (holder.itemView instanceof TextDetailProxyCell) {
                    TextDetailProxyCell cell = (TextDetailProxyCell) holder.itemView;
                    cell.setChecked(cell.currentInfo == SharedConfig.currentProxy);
                    cell.updateStatus();
                }
            });

            updateRows(false);
        } else if (id == NotificationCenter.proxySettingsChanged) {
            updateRows(true);
        } else if (id == NotificationCenter.didUpdateConnectionState) {
            int state = ConnectionsManager.getInstance(account).getConnectionState();
            if (currentConnectionState != state) {
                currentConnectionState = state;
                if (listView != null && SharedConfig.currentProxy != null) {
                    int idx = proxyList.indexOf(SharedConfig.currentProxy);
                    if (idx >= 0) {
                        RecyclerListView.Holder holder = (RecyclerListView.Holder) listView.findViewHolderForAdapterPosition(idx + proxyStartRow);
                        if (holder != null) {
                            TextDetailProxyCell cell = (TextDetailProxyCell) holder.itemView;
                            cell.updateStatus();
                        }
                    }

                    if (currentConnectionState == ConnectionsManager.ConnectionStateConnected) {
                        updateRows(true);
                    }
                }
            }
        } else if (id == NotificationCenter.proxyCheckDone) {
            if (listView != null) {
                SharedConfig.ProxyInfo proxyInfo = (SharedConfig.ProxyInfo) args[0];
                int idx = proxyList.indexOf(proxyInfo);
                if (idx >= 0) {
                    RecyclerListView.Holder holder = (RecyclerListView.Holder) listView.findViewHolderForAdapterPosition(idx + proxyStartRow);
                    if (holder != null) {
                        TextDetailProxyCell cell = (TextDetailProxyCell) holder.itemView;
                        cell.updateStatus();
                    }
                }

                boolean checking = false;
                if (!wasCheckedAllList) {
                    for (SharedConfig.ProxyInfo info : proxyList) {
                        if (info.checking || info.availableCheckTime == 0) {
                            checking = true;
                            break;
                        }
                    }
                    if (!checking) {
                        wasCheckedAllList = true;
                    }
                }
                if (!checking) {
                    updateRows(true);
                }
            }
        }
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {
        private final static int VIEW_TYPE_SHADOW = 0,
            VIEW_TYPE_TEXT_SETTING = 1,
            VIEW_TYPE_HEADER = 2,
            VIEW_TYPE_TEXT_CHECK = 3,
            VIEW_TYPE_INFO = 4,
            VIEW_TYPE_PROXY_DETAIL = 5,
            VIEW_TYPE_SLIDE_CHOOSER = 6;

        public static final int PAYLOAD_CHECKED_CHANGED = 0;
        public static final int PAYLOAD_SELECTION_CHANGED = 1;
        public static final int PAYLOAD_SELECTION_MODE_CHANGED = 2;

        private Context mContext;

        public ListAdapter(Context context) {
            mContext = context;

            setHasStableIds(true);
        }

        public void toggleSelected(int position) {
            if (position < proxyStartRow || position >= proxyEndRow) {
                return;
            }
            SharedConfig.ProxyInfo info = proxyList.get(position - proxyStartRow);
            if (selectedItems.contains(info)) {
                selectedItems.remove(info);
            } else {
                selectedItems.add(info);
            }
            notifyItemChanged(position, PAYLOAD_SELECTION_CHANGED);
            checkActionMode();
        }

        public void clearSelected() {
            selectedItems.clear();
            notifyItemRangeChanged(proxyStartRow, proxyEndRow - proxyStartRow, PAYLOAD_SELECTION_CHANGED);
            checkActionMode();
        }

        private void checkActionMode() {
            int selectedCount = selectedItems.size();
            boolean actionModeShowed = actionBar.isActionModeShowed();
            if (selectedCount > 0) {
                selectedCountTextView.setNumber(selectedCount, actionModeShowed);
                if (!actionModeShowed) {
                    actionBar.showActionMode();
                    notifyItemRangeChanged(proxyStartRow, proxyEndRow - proxyStartRow, PAYLOAD_SELECTION_MODE_CHANGED);
                }
            } else if (actionModeShowed) {
                actionBar.hideActionMode();
                notifyItemRangeChanged(proxyStartRow, proxyEndRow - proxyStartRow, PAYLOAD_SELECTION_MODE_CHANGED);
            }
        }

        @Override
        public int getItemCount() {
            return rowCount;
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case VIEW_TYPE_SHADOW: {
                    if (position == proxyShadowRow && callsRow == -1) {
                        holder.itemView.setBackgroundDrawable(Theme.getThemedDrawableByKey(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    } else {
                        holder.itemView.setBackgroundDrawable(Theme.getThemedDrawableByKey(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    }
                    break;
                }
                case VIEW_TYPE_TEXT_SETTING: {
                    TextSettingsCell textCell = (TextSettingsCell) holder.itemView;
                    textCell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                    if (position == proxyPriorityRow) {
                        textCell.setTextAndValue("Приоритет типов", ProxyRotationController.getPriorityModeName(SharedConfig.proxyPriorityMode), false, true);
                    } else if (position == proxyAddRow) {
                        textCell.setText(LocaleController.getString(R.string.AddProxy), true);
                    } else if (position == subscriptionAddRow) {
                        textCell.setText("Добавить подписку (Xray / qWDTT)", true);
                    } else if (position == subscriptionUpdateRow) {
                        int subsCount = SubscriptionManager.getInstance().getSubscriptions().size();
                        textCell.setText("Обновить все подписки" + (subsCount > 0 ? " (" + subsCount + ")" : ""), true);
                    } else if (position >= subscriptionStartRow && position < subscriptionEndRow) {
                        ArrayList<SubscriptionManager.Subscription> subsList = SubscriptionManager.getInstance().getSubscriptions();
                        int idx = position - subscriptionStartRow;
                        if (idx >= 0 && idx < subsList.size()) {
                            SubscriptionManager.Subscription sub = subsList.get(idx);
                            String typeStr = sub.type == SubscriptionManager.Subscription.TYPE_XRAY ? "Xray" : "qWDTT";
                            String info = typeStr + " • " + sub.serversCount + " серв.";
                            textCell.setTextAndValue(sub.title, info, false, true);
                        }
                    } else if (position == hwidSettingsRow) {
                        textCell.setText("Идентификаторы (HWID / Device ID)", deleteAllRow != -1);
                    } else if (position == deleteAllRow) {
                        textCell.setTextColor(Theme.getColor(Theme.key_text_RedRegular));
                        textCell.setText(LocaleController.getString(R.string.DeleteAllProxies), false);
                    }
                    break;
                }
                case VIEW_TYPE_HEADER: {
                    HeaderCell headerCell = (HeaderCell) holder.itemView;
                    if (position == connectionsHeaderRow) {
                        headerCell.setText(LocaleController.getString(R.string.ProxyConnections));
                    } else if (position == subscriptionHeaderRow) {
                        headerCell.setText("ПОДПИСКИ И ИДЕНТИФИКАТОРЫ");
                    }
                    break;
                }
                case VIEW_TYPE_TEXT_CHECK: {
                    TextCheckCell checkCell = (TextCheckCell) holder.itemView;
                    if (position == useProxyRow) {
                        checkCell.setTextAndCheck(LocaleController.getString(R.string.UseProxySettings), useProxySettings, rotationRow != -1);
                    } else if (position == directFallbackRow) {
                        checkCell.setTextAndCheck("Прямое подключение при наличии связи", SharedConfig.proxyDirectFallback, false);
                    } else if (position == callsRow) {
                        checkCell.setTextAndCheck(LocaleController.getString(R.string.UseProxyForCalls), useProxyForCalls, false);
                    } else if (position == rotationRow) {
                        checkCell.setTextAndCheck(LocaleController.getString(R.string.UseProxyRotation), SharedConfig.proxyRotationEnabled, true);
                    } else if (position == subscriptionUpdateViaProxyRow) {
                        checkCell.setTextAndCheck("Обновлять подписки через прокси", SharedConfig.proxyUpdateSubsViaProxy, false);
                    }
                    break;
                }
                case VIEW_TYPE_INFO: {
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    if (position == directFallbackInfoRow) {
                        cell.setText("Если к серверам Telegram есть прямое подключение без блокировок, клиент работает напрямую для максимальной скорости, держа прокси в резерве.");
                        cell.setBackground(Theme.getThemedDrawableByKey(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    } else if (position == callsDetailRow) {
                        cell.setText(LocaleController.getString(R.string.UseProxyForCallsInfo));
                        cell.setBackground(Theme.getThemedDrawableByKey(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    } else if (position == rotationTimeoutInfoRow) {
                        cell.setText(LocaleController.getString(R.string.ProxyRotationTimeoutInfo));
                        cell.setBackground(Theme.getThemedDrawableByKey(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    }
                    break;
                }
                case VIEW_TYPE_PROXY_DETAIL: {
                    TextDetailProxyCell cell = (TextDetailProxyCell) holder.itemView;
                    SharedConfig.ProxyInfo info = proxyList.get(position - proxyStartRow);
                    cell.setProxy(info);
                    cell.setChecked(SharedConfig.currentProxy == info);
                    cell.setItemSelected(selectedItems.contains(proxyList.get(position - proxyStartRow)), false);
                    cell.setSelectionEnabled(!selectedItems.isEmpty(), false);
                    break;
                }
                case VIEW_TYPE_SLIDE_CHOOSER: {
                    if (position == rotationTimeoutRow) {
                        SlideChooseView chooseView = (SlideChooseView) holder.itemView;
                        ArrayList<Integer> options = new ArrayList<>(ProxyRotationController.ROTATION_TIMEOUTS);
                        String[] values = new String[options.size()];
                        for (int i = 0; i < options.size(); i++) {
                            values[i] = LocaleController.formatString(R.string.ProxyRotationTimeoutSeconds, options.get(i));
                        }
                        chooseView.setCallback(i -> {
                            SharedConfig.proxyRotationTimeout = i;
                            SharedConfig.saveConfig();
                        });
                        chooseView.setOptions(SharedConfig.proxyRotationTimeout, values);
                    }
                    break;
                }
            }
        }

        @SuppressWarnings("unchecked")
        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, @NonNull List payloads) {
            if (holder.getItemViewType() == VIEW_TYPE_PROXY_DETAIL && !payloads.isEmpty()) {
                TextDetailProxyCell cell = (TextDetailProxyCell) holder.itemView;
                if (payloads.contains(PAYLOAD_SELECTION_CHANGED)) {
                    cell.setItemSelected(selectedItems.contains(proxyList.get(position - proxyStartRow)), true);
                }
                if (payloads.contains(PAYLOAD_SELECTION_MODE_CHANGED)) {
                    cell.setSelectionEnabled(!selectedItems.isEmpty(), true);
                }
            } else if (holder.getItemViewType() == VIEW_TYPE_TEXT_CHECK && payloads.contains(PAYLOAD_CHECKED_CHANGED)) {
                TextCheckCell checkCell = (TextCheckCell) holder.itemView;
                if (position == useProxyRow) {
                    checkCell.setChecked(useProxySettings);
                } else if (position == directFallbackRow) {
                    checkCell.setChecked(SharedConfig.proxyDirectFallback);
                } else if (position == callsRow) {
                    checkCell.setChecked(useProxyForCalls);
                } else if (position == rotationRow) {
                    checkCell.setChecked(SharedConfig.proxyRotationEnabled);
                }
            } else {
                super.onBindViewHolder(holder, position, payloads);
            }
        }

        @Override
        public void onViewAttachedToWindow(RecyclerView.ViewHolder holder) {
            int viewType = holder.getItemViewType();
            if (viewType == VIEW_TYPE_TEXT_CHECK) {
                TextCheckCell checkCell = (TextCheckCell) holder.itemView;
                int position = holder.getAdapterPosition();
                if (position == useProxyRow) {
                    checkCell.setChecked(useProxySettings);
                } else if (position == callsRow) {
                    checkCell.setChecked(useProxyForCalls);
                } else if (position == rotationRow) {
                    checkCell.setChecked(SharedConfig.proxyRotationEnabled);
                }
            }
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int position = holder.getAdapterPosition();
            return position == useProxyRow || position == directFallbackRow || position == rotationRow || position == proxyPriorityRow || position == callsRow || position == proxyAddRow || position == subscriptionAddRow || position == subscriptionUpdateRow || position == subscriptionUpdateViaProxyRow || (position >= subscriptionStartRow && position < subscriptionEndRow) || position == hwidSettingsRow || position == deleteAllRow || position >= proxyStartRow && position < proxyEndRow;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case VIEW_TYPE_SHADOW:
                    view = new ShadowSectionCell(mContext);
                    break;
                case VIEW_TYPE_TEXT_SETTING:
                    view = new TextSettingsCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case VIEW_TYPE_HEADER:
                    view = new HeaderCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case VIEW_TYPE_TEXT_CHECK:
                    view = new TextCheckCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case VIEW_TYPE_INFO:
                    view = new TextInfoPrivacyCell(mContext);
                    view.setBackground(Theme.getThemedDrawableByKey(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    break;
                case VIEW_TYPE_SLIDE_CHOOSER:
                    view = new SlideChooseView(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case VIEW_TYPE_PROXY_DETAIL:
                default:
                    view = new TextDetailProxyCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public long getItemId(int position) {
            // Random stable ids, could be anything non-repeating
            if (position == useProxyShadowRow) {
                return -1;
            } else if (position == proxyShadowRow) {
                return -2;
            } else if (position == proxyAddRow) {
                return -3;
            } else if (position == useProxyRow) {
                return -4;
            } else if (position == callsRow) {
                return -5;
            } else if (position == connectionsHeaderRow) {
                return -6;
            } else if (position == subscriptionHeaderRow) {
                return -14;
            } else if (position == subscriptionAddRow) {
                return -15;
            } else if (position == subscriptionUpdateRow) {
                return -16;
            } else if (position == hwidSettingsRow) {
                return -17;
            } else if (position == subscriptionShadowRow) {
                return -18;
            } else if (position == deleteAllRow) {
                return -8;
            } else if (position == directFallbackRow) {
                return -12;
            } else if (position == directFallbackInfoRow) {
                return -13;
            } else if (position == rotationRow) {
                return -9;
            } else if (position == proxyPriorityRow) {
                return -20;
            } else if (position == rotationTimeoutRow) {
                return -10;
            } else if (position == rotationTimeoutInfoRow) {
                return -11;
            } else if (position >= proxyStartRow && position < proxyEndRow) {
                return proxyList.get(position - proxyStartRow).hashCode();
            } else {
                return -7;
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == useProxyShadowRow || position == proxyShadowRow || position == subscriptionShadowRow) {
                return VIEW_TYPE_SHADOW;
            } else if (position == proxyPriorityRow || position == proxyAddRow || position == subscriptionAddRow || position == subscriptionUpdateRow || (position >= subscriptionStartRow && position < subscriptionEndRow) || position == hwidSettingsRow || position == deleteAllRow) {
                return VIEW_TYPE_TEXT_SETTING;
            } else if (position == useProxyRow || position == directFallbackRow || position == rotationRow || position == callsRow || position == subscriptionUpdateViaProxyRow) {
                return VIEW_TYPE_TEXT_CHECK;
            } else if (position == directFallbackInfoRow) {
                return VIEW_TYPE_INFO;
            } else if (position == connectionsHeaderRow || position == subscriptionHeaderRow) {
                return VIEW_TYPE_HEADER;
            } else if (position == rotationTimeoutRow) {
                return VIEW_TYPE_SLIDE_CHOOSER;
            } else if (position >= proxyStartRow && position < proxyEndRow) {
                return VIEW_TYPE_PROXY_DETAIL;
            } else {
                return VIEW_TYPE_INFO;
            }
        }
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{TextSettingsCell.class, TextCheckCell.class, HeaderCell.class, TextDetailProxyCell.class}, null, null, null, Theme.key_windowBackgroundWhite));
        themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundGray));

        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{ShadowSectionCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextSettingsCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteValueText));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextDetailProxyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_CHECKTAG | ThemeDescription.FLAG_IMAGECOLOR, new Class[]{TextDetailProxyCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueText6));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_CHECKTAG | ThemeDescription.FLAG_IMAGECOLOR, new Class[]{TextDetailProxyCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_CHECKTAG | ThemeDescription.FLAG_IMAGECOLOR, new Class[]{TextDetailProxyCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGreenText));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_CHECKTAG | ThemeDescription.FLAG_IMAGECOLOR, new Class[]{TextDetailProxyCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_text_RedRegular));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_IMAGECOLOR, new Class[]{TextDetailProxyCell.class}, new String[]{"checkImageView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText3));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{HeaderCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueHeader));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrack));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrackChecked));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{TextInfoPrivacyCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextInfoPrivacyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText4));

        return themeDescriptions;
    }
}
