package com.skillplanet.localagent.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.skillplanet.localagent.chat.ChatUiMessage;
import com.techskillplanet.planetcomponents.drawable.BasicDrawableFactory;
import com.techskillplanet.planetcomponents.theme.BasicColors;
import com.techskillplanet.planetcomponents.widget.BasicToast;

/**
 * 聊天行：头像 + 气泡，布局贴近常见 IM。
 *
 * <pre>
 *  助手:  [头像] [气泡————] ·····
 *  用户:  ····· [————气泡] [头像]
 * </pre>
 */
public final class MessageBubbleFactory {
    private static final int AVATAR_DP = 36;
    private static final int BUBBLE_RADIUS_DP = 16;
    private static final int ROW_GAP_DP = 16;
    /** 气泡最大约占屏宽 72%，避免拉成通栏。 */
    private static final float BUBBLE_MAX_WIDTH_RATIO = 0.72f;

    private MessageBubbleFactory() {
    }

    /**
     * @return 整行容器（含头像）；正文 TextView 的 tag 为 {@code body}，供流式刷新。
     */
    public static View create(Context context, BasicColors colors, ChatUiMessage message) {
        boolean isUser = message.isUser();

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.BOTTOM);
        row.setPadding(0, dp(context, 2), 0, dp(context, 2));

        View avatar = buildAvatar(context, colors, isUser);
        View bubble = buildBubble(context, colors, message, isUser);

        LinearLayout.LayoutParams avatarLp = new LinearLayout.LayoutParams(
                dp(context, AVATAR_DP),
                dp(context, AVATAR_DP)
        );
        LinearLayout.LayoutParams bubbleLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        LinearLayout.LayoutParams flexLp = new LinearLayout.LayoutParams(0, 1, 1f);

        if (isUser) {
            // 右侧说话：弹性占位 → 气泡 → 头像
            row.addView(new View(context), flexLp);
            bubbleLp.rightMargin = dp(context, 8);
            row.addView(bubble, bubbleLp);
            row.addView(avatar, avatarLp);
        } else {
            // 左侧说话：头像 → 气泡 → 弹性占位
            avatarLp.rightMargin = dp(context, 8);
            row.addView(avatar, avatarLp);
            row.addView(bubble, bubbleLp);
            row.addView(new View(context), flexLp);
        }

        row.setOnLongClickListener(v -> {
            ClipboardManager clipboard =
                    (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("chat", message.text));
                BasicToast.show(context, "已复制到剪贴板", "success", Toast.LENGTH_SHORT);
            }
            return true;
        });
        return row;
    }

    private static View buildAvatar(Context context, BasicColors colors, boolean isUser) {
        FrameLayout wrap = new FrameLayout(context);
        int size = dp(context, AVATAR_DP);
        TextView avatar = new TextView(context);
        avatar.setGravity(Gravity.CENTER);
        avatar.setTypeface(Typeface.DEFAULT_BOLD);
        avatar.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        if (isUser) {
            avatar.setText("你");
            avatar.setTextColor(colors.textInverse);
            avatar.setBackground(BasicDrawableFactory.roundedFill(colors.brandPrimary, size / 2f));
        } else {
            avatar.setText("AI");
            avatar.setTextColor(colors.brandPrimary);
            avatar.setBackground(BasicDrawableFactory.roundedFillStroke(
                    colors.backgroundSurface,
                    colors.brandPrimary,
                    dp(context, 1.5f),
                    size / 2f
            ));
        }
        wrap.addView(avatar, new FrameLayout.LayoutParams(size, size));
        return wrap;
    }

    private static View buildBubble(
            Context context,
            BasicColors colors,
            ChatUiMessage message,
            boolean isUser
    ) {
        TextView body = new TextView(context);
        body.setText(message.text.isEmpty() ? "…" : message.text);
        body.setTextColor(colors.textPrimary);
        body.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        body.setLineSpacing(dp(context, 3), 1.2f);
        body.setTag("body");
        int padH = dp(context, 14);
        int padV = dp(context, 10);
        body.setPadding(padH, padV, padH, padV);
        body.setMaxWidth(Math.round(
                context.getResources().getDisplayMetrics().widthPixels * BUBBLE_MAX_WIDTH_RATIO
        ));

        float r = dp(context, BUBBLE_RADIUS_DP);
        float tip = dp(context, 5);
        // 靠近头像一侧圆角更小，模拟聊天气泡指向
        if (isUser) {
            body.setBackground(BasicDrawableFactory.roundedFillStroke(
                    colors.brandPrimarySubtle,
                    colors.brandPrimary,
                    dp(context, 1),
                    r, tip, r, r
            ));
        } else {
            body.setBackground(BasicDrawableFactory.roundedFillStroke(
                    colors.backgroundSurface,
                    colors.borderDefault,
                    dp(context, 1),
                    tip, r, r, r
            ));
        }
        return body;
    }

    public static void updateText(View row, String text) {
        View body = row.findViewWithTag("body");
        if (body instanceof TextView) {
            ((TextView) body).setText(text == null || text.isEmpty() ? "…" : text);
        }
    }

    public static LinearLayout.LayoutParams layoutParams(Context context, boolean isUser) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.topMargin = dp(context, ROW_GAP_DP);
        lp.bottomMargin = dp(context, 2);
        return lp;
    }

    private static int dp(Context context, float value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                context.getResources().getDisplayMetrics()
        ));
    }
}
