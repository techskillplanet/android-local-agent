package com.skillplanet.localagent;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;

import com.skillplanet.localagent.chat.ChatController;
import com.skillplanet.localagent.chat.ChatUiMessage;
import com.skillplanet.localagent.chat.GenerationSettings;
import com.skillplanet.localagent.ui.MessageBubbleFactory;
import com.techskillplanet.planetcomponents.system.BasicEdgeToEdgeHelper;
import com.techskillplanet.planetcomponents.theme.BasicColors;
import com.techskillplanet.planetcomponents.widget.BasicAlertView;
import com.techskillplanet.planetcomponents.widget.BasicButton;
import com.techskillplanet.planetcomponents.widget.BasicChipView;
import com.techskillplanet.planetcomponents.widget.BasicEmptyView;
import com.techskillplanet.planetcomponents.widget.BasicInputView;
import com.techskillplanet.planetcomponents.widget.BasicLoadingDialog;
import com.techskillplanet.planetcomponents.widget.BasicModalDialog;
import com.techskillplanet.planetcomponents.widget.BasicOptionSheet;
import com.techskillplanet.planetcomponents.widget.BasicStickyFooterView;
import com.techskillplanet.planetcomponents.widget.BasicTextView;
import com.techskillplanet.planetcomponents.widget.BasicToast;
import com.techskillplanet.planetcomponents.widget.BasicTopBarView;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Local Agent 主界面。
 *
 * <p>布局原则（针对开源 Sample 可读性）：</p>
 * <ul>
 *   <li>顶部信息区紧凑，避免挤占对话空间。</li>
 *   <li>「选择 GGUF」只保留一处主入口（空状态卡内），加载后改为「更换 / 卸载」。</li>
 *   <li>次要操作用 Chip，对比度高于纯文字链。</li>
 *   <li>色调由 {@link ThemeBootstrap} 注入，默认深空青扁平科技风（cyber_flat）。</li>
 * </ul>
 */
public class MainActivity extends Activity implements ChatController.Listener {

    public static final String EXTRA_MODEL_PATH = "com.skillplanet.localagent.MODEL_PATH";
    /** Debug：注入示例对话，便于预览气泡/头像布局（不加载模型）。 */
    public static final String EXTRA_DEMO_CHAT = "com.skillplanet.localagent.DEMO_CHAT";
    private static final int REQ_PICK_MODEL = 1001;

    private AgentPreferences preferences;
    private ChatController controller;
    private BasicColors colors;

    private BasicTopBarView topBar;
    private BasicAlertView statusAlert;
    private BasicTextView statsLabel;
    private LinearLayout modelActions;
    private BasicButton changeModelButton;
    private BasicButton unloadButton;
    private BasicEmptyView emptyView;
    /** 模型已加载且无消息时的轻量提示（无「选择 GGUF」按钮）。 */
    private LinearLayout readyHint;
    private LinearLayout messageList;
    private ScrollView messageScroll;
    private LinearLayout promptChips;
    private BasicInputView inputView;
    private BasicButton sendButton;
    private BasicButton stopButton;
    private BasicChipView regenerateButton;
    private BasicLoadingDialog loadingDialog;

    private final Map<ChatUiMessage, View> bubbleByMessage = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = new AgentPreferences(this);
        applyTheme(preferences.getColorTheme(), preferences.getStyleProfile(), false);

        controller = new ChatController(this);
        controller.setListener(this);
        controller.setSystemPrompt(getString(R.string.system_prompt));
        controller.setSettings(GenerationSettings.fromPreset(preferences.getPreset()));

        setContentView(buildUi());
        BasicEdgeToEdgeHelper.applyWindow(
                this,
                BasicEdgeToEdgeHelper.shouldUseLightStatusBarIcons(colors)
        );
        refreshActionEnabled();

        String path = getIntent() != null ? getIntent().getStringExtra(EXTRA_MODEL_PATH) : null;
        if (path != null && !path.isEmpty()) {
            controller.loadModelFromPath(path);
        } else if (getIntent() != null && getIntent().getBooleanExtra(EXTRA_DEMO_CHAT, false)) {
            showDemoChatPreview();
        }
    }

    /** 仅用于 UI 预览：不走推理，直接渲染示例气泡。 */
    private void showDemoChatPreview() {
        List<ChatUiMessage> demo = Arrays.asList(
                new ChatUiMessage("user", "用一句话介绍你自己。"),
                new ChatUiMessage(
                        "assistant",
                        "我是跑在本机上的 Local Agent，基于 GGUF 小模型，数据不会离开设备。"
                ),
                new ChatUiMessage("user", "什么是扁平科技风？"),
                new ChatUiMessage(
                        "assistant",
                        "深空底色、电青强调、无阴影抬起，信息层级靠对比与间距，而不是材质光影。"
                )
        );
        onMessagesChanged(demo, false);
        statusAlert.setVariant(BasicAlertView.VARIANT_SUCCESS);
        statusAlert.setMessage("Demo chat preview（未加载模型）");
    }

    @Override
    protected void onDestroy() {
        if (controller != null) {
            controller.destroy();
        }
        super.onDestroy();
    }

    private LinearLayout buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(colors.backgroundPage);

        topBar = new BasicTopBarView(this);
        topBar.setTitle(getString(R.string.app_name));
        topBar.setBackVisible(false);
        topBar.setImmersiveStatusBar(true);
        root.addView(topBar, fullWidth());

        // —— 顶部信息：可滚动，避免矮屏挤爆 ——
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(16), dp(8), dp(16), dp(4));

        statusAlert = new BasicAlertView(this);
        statusAlert.setVariant(BasicAlertView.VARIANT_INFO);
        statusAlert.setTitle(getString(R.string.model_status_title));
        statusAlert.setMessage(getString(R.string.model_none));
        header.addView(statusAlert, fullWidth());

        statsLabel = new BasicTextView(this);
        statsLabel.setVariant("caption");
        statsLabel.setText(controller.getSettings().summary());
        header.addView(statsLabel, marginTop(fullWidth(), dp(6)));

        modelActions = buildModelActions();
        header.addView(modelActions, marginTop(fullWidth(), dp(8)));
        modelActions.setVisibility(View.GONE);

        header.addView(buildToolChips(), marginTop(fullWidth(), dp(8)));
        root.addView(header, fullWidth());

        // —— 对话区 ——
        emptyView = new BasicEmptyView(this);
        emptyView.setTitle(getString(R.string.empty_title));
        emptyView.setMessage(getString(R.string.empty_chat));
        emptyView.setActionText(getString(R.string.pick_model));
        emptyView.getActionButton().setVariant(BasicButton.VARIANT_PRIMARY);
        emptyView.getActionButton().setOnClickListener(v -> openModelPicker());

        readyHint = buildReadyHint();
        readyHint.setVisibility(View.GONE);

        messageList = new LinearLayout(this);
        messageList.setOrientation(LinearLayout.VERTICAL);
        messageList.setPadding(dp(12), dp(8), dp(12), dp(16));
        messageScroll = new ScrollView(this);
        messageScroll.setFillViewport(true);
        messageScroll.addView(messageList, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        messageScroll.setVisibility(View.GONE);

        FrameLayout chatArea = new FrameLayout(this);
        chatArea.setPadding(dp(16), dp(4), dp(16), 0);
        chatArea.addView(emptyView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
        ));
        chatArea.addView(readyHint, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
        ));
        chatArea.addView(messageScroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        root.addView(chatArea, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ));

        promptChips = buildPromptChips();
        root.addView(promptChips, fullWidth());
        root.addView(buildComposer(), fullWidth());
        updateChatSurface();
        return root;
    }

    /** 已就绪、尚无对话：轻量居中提示，不再放「选择 GGUF」。 */
    private LinearLayout buildReadyHint() {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setGravity(Gravity.CENTER_HORIZONTAL);
        wrap.setPadding(dp(24), dp(16), dp(24), dp(16));

        BasicTextView title = new BasicTextView(this);
        title.setVariant("title");
        title.setText(getString(R.string.ready_hint_title));
        title.setGravity(Gravity.CENTER);
        wrap.addView(title, fullWidth());

        BasicTextView body = new BasicTextView(this);
        body.setVariant("caption");
        body.setText(getString(R.string.ready_hint_body));
        body.setGravity(Gravity.CENTER);
        wrap.addView(body, marginTop(fullWidth(), dp(8)));
        return wrap;
    }

    /**
     * 对话区三态：
     * <ul>
     *   <li>未加载模型 → 空状态卡 +「选择 GGUF」</li>
     *   <li>已加载且无消息 → 轻量就绪提示（无选模按钮）</li>
     *   <li>有消息 → 气泡列表</li>
     * </ul>
     */
    private void updateChatSurface() {
        if (emptyView == null || readyHint == null || messageScroll == null || promptChips == null) {
            return;
        }
        boolean ready = controller != null && controller.isReady();
        List<ChatUiMessage> messages = controller == null
                ? java.util.Collections.emptyList()
                : controller.getMessages();
        boolean hasMessages = !messages.isEmpty();

        if (hasMessages) {
            emptyView.setVisibility(View.GONE);
            readyHint.setVisibility(View.GONE);
            messageScroll.setVisibility(View.VISIBLE);
        } else if (ready) {
            emptyView.setVisibility(View.GONE);
            readyHint.setVisibility(View.VISIBLE);
            messageScroll.setVisibility(View.GONE);
        } else {
            emptyView.setVisibility(View.VISIBLE);
            readyHint.setVisibility(View.GONE);
            messageScroll.setVisibility(View.GONE);
        }
        // 快捷提问始终保留，方便连续点选
        promptChips.setVisibility(View.VISIBLE);
    }

    /** 模型已加载时显示：更换 / 卸载。 */
    private LinearLayout buildModelActions() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        changeModelButton = new BasicButton(this);
        changeModelButton.setVariant(BasicButton.VARIANT_DEFAULT);
        changeModelButton.setBasicText(getString(R.string.change_model));
        changeModelButton.setOnClickListener(v -> openModelPicker());
        row.addView(changeModelButton, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        unloadButton = new BasicButton(this);
        unloadButton.setVariant(BasicButton.VARIANT_DEFAULT);
        unloadButton.setBasicText(getString(R.string.unload_model));
        unloadButton.setOnClickListener(v -> controller.unloadModel());
        LinearLayout.LayoutParams unloadLp =
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        unloadLp.leftMargin = dp(8);
        row.addView(unloadButton, unloadLp);
        return row;
    }

    /** 工具 Chip：对比清晰，避免「幽灵文字链」。 */
    private View buildToolChips() {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(toolChip(getString(R.string.settings), v -> showSettingsSheet()), chipLp());
        row.addView(toolChip(getString(R.string.theme), v -> showThemeSheet()), chipLp());
        row.addView(toolChip(getString(R.string.about), v -> showAbout()), chipLp());
        regenerateButton = toolChip(getString(R.string.regenerate), v -> controller.regenerate());
        row.addView(regenerateButton, chipLp());
        row.addView(toolChip(getString(R.string.clear_chat), v -> controller.clearChat()), chipLp());
        scroll.addView(row);
        LinearLayout wrap = new LinearLayout(this);
        wrap.addView(scroll, fullWidth());
        return wrap;
    }

    private BasicChipView toolChip(String label, View.OnClickListener click) {
        BasicChipView chip = new BasicChipView(this);
        chip.setBasicText(label);
        chip.setOnClickListener(click);
        return chip;
    }

    private LinearLayout.LayoutParams chipLp() {
        LinearLayout.LayoutParams lp = wrap();
        lp.rightMargin = dp(8);
        return lp;
    }

    private LinearLayout buildPromptChips() {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(dp(16), dp(4), dp(16), dp(4));

        BasicTextView label = new BasicTextView(this);
        label.setVariant("caption");
        label.setText(getString(R.string.prompt_suggestions));
        wrap.addView(label, fullWidth());

        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        String[] prompts = {
                getString(R.string.chip_hello),
                getString(R.string.chip_summary),
                getString(R.string.chip_translate),
                getString(R.string.chip_explain),
                getString(R.string.chip_tips),
                getString(R.string.chip_recipe),
                getString(R.string.chip_plan),
                getString(R.string.chip_email),
                getString(R.string.chip_compare),
                getString(R.string.chip_quiz),
                getString(R.string.chip_safe),
                getString(R.string.chip_code),
                getString(R.string.chip_poem),
                getString(R.string.chip_weather),
        };
        for (String prompt : prompts) {
            BasicChipView chip = new BasicChipView(this);
            chip.setBasicText(shorten(prompt, 16));
            chip.setOnClickListener(v -> {
                if (!controller.isReady()) {
                    BasicToast.show(this, getString(R.string.need_model), "warning", Toast.LENGTH_SHORT);
                    openModelPicker();
                    return;
                }
                controller.sendUserMessage(prompt);
            });
            row.addView(chip, chipLp());
        }
        scroll.addView(row);
        wrap.addView(scroll, marginTop(fullWidth(), dp(6)));
        return wrap;
    }

    private View buildComposer() {
        BasicStickyFooterView footer = new BasicStickyFooterView(this);
        footer.setSubtle(false);

        LinearLayout composer = new LinearLayout(this);
        composer.setOrientation(LinearLayout.HORIZONTAL);
        composer.setGravity(Gravity.CENTER_VERTICAL);
        composer.setPadding(dp(12), dp(10), dp(12), dp(12));

        inputView = new BasicInputView(this);
        boolean readyNow = controller != null && controller.isReady();
        inputView.getEditText().setHint(getString(
                readyNow ? R.string.input_hint : R.string.input_hint_need_model
        ));
        inputView.getEditText().setSingleLine(false);
        inputView.getEditText().setMaxLines(4);
        inputView.getEditText().setImeOptions(EditorInfo.IME_ACTION_SEND);
        inputView.getEditText().setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendFromInput();
                return true;
            }
            return false;
        });
        composer.addView(inputView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        sendButton = new BasicButton(this);
        sendButton.setVariant(BasicButton.VARIANT_PRIMARY);
        sendButton.setBasicText(getString(R.string.send));
        sendButton.setOnClickListener(v -> sendFromInput());
        LinearLayout.LayoutParams sendLp = wrap();
        sendLp.leftMargin = dp(8);
        composer.addView(sendButton, sendLp);

        stopButton = new BasicButton(this);
        stopButton.setVariant(BasicButton.VARIANT_DANGER);
        stopButton.setBasicText(getString(R.string.stop));
        stopButton.setOnClickListener(v -> controller.stopGeneration());
        stopButton.setVisibility(View.GONE);
        composer.addView(stopButton, sendLp);

        footer.addView(composer, fullWidth());
        return footer;
    }

    private void sendFromInput() {
        CharSequence cs = inputView.getEditText().getText();
        String text = cs == null ? "" : cs.toString();
        inputView.setBasicText("");
        controller.sendUserMessage(text);
    }

    private void openModelPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/octet-stream", "*/*"});
        startActivityForResult(intent, REQ_PICK_MODEL);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_PICK_MODEL || resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) {
        }
        controller.loadModelFromUri(uri);
    }

    private void showSettingsSheet() {
        List<String> options = Arrays.asList(
                GenerationSettings.balanced().presetLabel(),
                GenerationSettings.creative().presetLabel(),
                GenerationSettings.precise().presetLabel()
        );
        BasicOptionSheet sheet = new BasicOptionSheet(this);
        sheet.setTitleText(getString(R.string.settings));
        sheet.setOptions(options, controller.getSettings().preset.ordinal());
        sheet.setOnOptionSelectedListener((index, option) -> {
            GenerationSettings.Preset preset = GenerationSettings.Preset.values()[index];
            GenerationSettings next = GenerationSettings.fromPreset(preset);
            preferences.setPreset(preset);
            controller.setSettings(next);
            statsLabel.setText(next.summary());
            BasicToast.show(this, getString(R.string.settings_applied, next.presetLabel()), "success", Toast.LENGTH_SHORT);
            if (controller.isReady()) {
                controller.reloadCurrentModel();
            }
        });
        sheet.show();
    }

    private void showThemeSheet() {
        List<String> options = Arrays.asList(
                "深空青 · Cyber Flat（默认）",
                "星夜 · Night",
                "琥珀石板 · Ember",
                "薄荷 · Mint",
                "天蓝 · Sky"
        );
        String current = preferences.getColorTheme();
        int selected = 0;
        if (ThemeBootstrap.THEME_NIGHT.equals(current)) {
            selected = 1;
        } else if (ThemeBootstrap.THEME_EMBER_OBSIDIAN.equals(current)) {
            selected = 2;
        } else if (ThemeBootstrap.THEME_MINT.equals(current)) {
            selected = 3;
        } else if (ThemeBootstrap.THEME_SKY.equals(current)) {
            selected = 4;
        }
        BasicOptionSheet sheet = new BasicOptionSheet(this);
        sheet.setTitleText(getString(R.string.theme));
        sheet.setOptions(options, selected);
        sheet.setOnOptionSelectedListener((index, option) -> {
            String color;
            String style = ThemeBootstrap.STYLE_FLAT;
            switch (index) {
                case 1:
                    color = ThemeBootstrap.THEME_NIGHT;
                    break;
                case 2:
                    color = ThemeBootstrap.THEME_EMBER_OBSIDIAN;
                    style = ThemeBootstrap.STYLE_RAISED;
                    break;
                case 3:
                    color = ThemeBootstrap.THEME_MINT;
                    break;
                case 4:
                    color = ThemeBootstrap.THEME_SKY;
                    style = ThemeBootstrap.STYLE_RAISED;
                    break;
                case 0:
                default:
                    color = ThemeBootstrap.THEME_CYBER_FLAT;
                    break;
            }
            preferences.setTheme(color, style);
            applyTheme(color, style, true);
        });
        sheet.show();
    }

    private void showAbout() {
        BasicModalDialog dialog = new BasicModalDialog(this);
        dialog.setTitleText(getString(R.string.about_title));
        dialog.setMessage(getString(R.string.about_body));
        dialog.setConfirmText("好的");
        dialog.setCancelText("关闭");
        dialog.show();
    }

    private void applyTheme(String colorTheme, String styleProfile, boolean recreateUi) {
        ThemeBootstrap.apply(this, colorTheme, styleProfile);
        colors = com.techskillplanet.planetcomponents.theme.BasicThemeManager.colors();
        if (recreateUi) {
            loadingDialog = null;
            setContentView(buildUi());
            BasicEdgeToEdgeHelper.applyWindow(
                    this,
                    BasicEdgeToEdgeHelper.shouldUseLightStatusBarIcons(colors)
            );
            onMessagesChanged(controller.getMessages(), controller.isGenerating());
            if (controller.isReady()) {
                onModelReady(controller.getModelDisplayName(), "—", 0);
            }
            statsLabel.setText(controller.getSettings().summary());
            refreshActionEnabled();
            BasicToast.show(this, getString(R.string.theme_applied), "success", Toast.LENGTH_SHORT);
        }
    }

    @Override
    public void onModelLoading() {
        showLoading(getString(R.string.model_loading));
        refreshActionEnabled();
    }

    @Override
    public void onModelReady(String displayName, String sizeLabel, long loadElapsedMs) {
        hideLoading();
        statusAlert.setVariant(BasicAlertView.VARIANT_SUCCESS);
        statusAlert.setTitle(getString(R.string.model_status_title));
        statusAlert.setMessage(getString(R.string.model_ready, displayName, sizeLabel, loadElapsedMs));
        modelActions.setVisibility(View.VISIBLE);
        updateChatSurface();
        refreshActionEnabled();
        if (inputView != null) {
            inputView.getEditText().setHint(getString(R.string.input_hint));
            inputView.getEditText().requestFocus();
        }
        BasicToast.show(this, "模型加载完成", "success", Toast.LENGTH_SHORT);
    }

    @Override
    public void onModelUnloaded() {
        statusAlert.setVariant(BasicAlertView.VARIANT_INFO);
        statusAlert.setTitle(getString(R.string.model_status_title));
        statusAlert.setMessage(getString(R.string.model_none));
        modelActions.setVisibility(View.GONE);
        updateChatSurface();
        refreshActionEnabled();
        if (inputView != null) {
            inputView.getEditText().setHint(getString(R.string.input_hint_need_model));
        }
    }

    @Override
    public void onModelError(String message) {
        hideLoading();
        statusAlert.setVariant(BasicAlertView.VARIANT_ERROR);
        statusAlert.setTitle(getString(R.string.model_status_title));
        statusAlert.setMessage(getString(R.string.error_load, message));
        modelActions.setVisibility(View.GONE);
        updateChatSurface();
        refreshActionEnabled();
        BasicToast.show(this, message, "error", Toast.LENGTH_LONG);
    }

    @Override
    public void onMessagesChanged(List<ChatUiMessage> snapshot, boolean generating) {
        messageList.removeAllViews();
        bubbleByMessage.clear();
        if (!snapshot.isEmpty()) {
            for (ChatUiMessage message : snapshot) {
                View bubble = MessageBubbleFactory.create(this, colors, message);
                messageList.addView(bubble, MessageBubbleFactory.layoutParams(this, message.isUser()));
                bubbleByMessage.put(message, bubble);
            }
            messageScroll.post(() -> messageScroll.fullScroll(View.FOCUS_DOWN));
        }
        updateChatSurface();
        refreshActionEnabled();
    }

    @Override
    public void onAssistantToken(ChatUiMessage assistant, String fullText) {
        View bubble = bubbleByMessage.get(assistant);
        if (bubble != null) {
            MessageBubbleFactory.updateText(bubble, fullText);
            messageScroll.post(() -> messageScroll.fullScroll(View.FOCUS_DOWN));
        }
    }

    @Override
    public void onGenerationFinished(long elapsedMs, int tokenEvents, boolean cancelled) {
        refreshActionEnabled();
        if (cancelled) {
            BasicToast.show(this, getString(R.string.gen_cancelled), "info", Toast.LENGTH_SHORT);
            return;
        }
        if (elapsedMs > 0) {
            statsLabel.setText(String.format(
                    Locale.US,
                    "%s · 上次 %d ms / ~%d tok",
                    controller.getSettings().summary(),
                    elapsedMs,
                    tokenEvents
            ));
        }
    }

    @Override
    public void onGenerationError(String message) {
        refreshActionEnabled();
        statusAlert.setVariant(BasicAlertView.VARIANT_ERROR);
        statusAlert.setMessage(getString(R.string.error_generate, message));
        BasicToast.show(this, message, "error", Toast.LENGTH_LONG);
    }

    private void refreshActionEnabled() {
        boolean ready = controller != null && controller.isReady();
        boolean busy = controller != null && controller.isGenerating();
        boolean loading = loadingDialog != null && loadingDialog.isShowing();
        boolean blocked = busy || loading;

        inputView.setBasicDisabled(!ready || blocked);
        sendButton.setBasicDisabled(!ready || blocked);
        sendButton.setVisibility(busy ? View.GONE : View.VISIBLE);
        stopButton.setVisibility(busy ? View.VISIBLE : View.GONE);
        if (changeModelButton != null) {
            changeModelButton.setBasicDisabled(blocked);
        }
        if (unloadButton != null) {
            unloadButton.setBasicDisabled(blocked);
        }
        if (regenerateButton != null) {
            regenerateButton.setBasicDisabled(!ready || blocked || controller.getMessages().isEmpty());
            regenerateButton.setAlpha((!ready || blocked || controller.getMessages().isEmpty()) ? 0.45f : 1f);
        }
        emptyView.getActionButton().setBasicDisabled(blocked);
    }

    private void showLoading(String message) {
        if (loadingDialog == null) {
            loadingDialog = new BasicLoadingDialog(this, message);
            loadingDialog.setCancelable(false);
        } else {
            loadingDialog.setMessage(message);
        }
        if (!loadingDialog.isShowing()) {
            loadingDialog.show();
        }
        refreshActionEnabled();
    }

    private void hideLoading() {
        if (loadingDialog != null && loadingDialog.isShowing()) {
            loadingDialog.dismiss();
        }
        refreshActionEnabled();
    }

    private static String shorten(String text, int max) {
        if (text == null || text.length() <= max) {
            return text;
        }
        return text.substring(0, max - 1) + "…";
    }

    private LinearLayout.LayoutParams fullWidth() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }

    private LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }

    private LinearLayout.LayoutParams marginTop(LinearLayout.LayoutParams source, int top) {
        source.topMargin = top;
        return source;
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                getResources().getDisplayMetrics()
        ));
    }
}
