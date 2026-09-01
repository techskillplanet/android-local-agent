package com.skillplanet.localagent;

import android.content.Context;

import com.techskillplanet.planetcomponents.theme.BasicThemeManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Sample 主题引导：默认「扁平科技感」深空青（cyber_flat）+ {@code island_flat}。
 *
 * <p>相对 sky / ember：更冷、更平、强调色用 neon cyan，匹配本地 Agent 工具气质。</p>
 */
public final class ThemeBootstrap {
    /** 默认：深空底 + 电青强调，扁平无抬起阴影。 */
    public static final String THEME_CYBER_FLAT = "cyber_flat";
    public static final String THEME_EMBER_OBSIDIAN = "ember_obsidian";
    public static final String THEME_SKY = "sky_planet_day";
    public static final String THEME_NIGHT = "star_planet_night";
    public static final String THEME_MINT = "mint_planet_day";

    public static final String STYLE_FLAT = "island_flat";
    public static final String STYLE_RAISED = "island_raised";

    private ThemeBootstrap() {
    }

    public static void apply(Context context, String themeName, String styleProfile) {
        try {
            JSONObject root = new JSONObject(readAsset(context, "theme/color_token.json"));
            injectCyberFlat(root);
            injectEmberObsidian(root);
            String resolved = themeName == null || themeName.isEmpty()
                    ? THEME_CYBER_FLAT
                    : themeName;
            if (!root.getJSONObject("themes").has(resolved)) {
                resolved = THEME_CYBER_FLAT;
            }
            String style = styleProfile == null || styleProfile.isEmpty() ? STYLE_FLAT : styleProfile;
            BasicThemeManager.init(context, resolved, style, root);
        } catch (Exception error) {
            BasicThemeManager.init(context, THEME_NIGHT, STYLE_FLAT);
        }
    }

    /** 深空青：扁平科技感（克制钢青，避免霓虹刺眼）。 */
    private static void injectCyberFlat(JSONObject root) throws JSONException {
        JSONObject themes = root.getJSONObject("themes");
        JSONObject base = new JSONObject(themes.getJSONObject(THEME_SKY).toString());
        JSONObject primitive = base.getJSONObject("primitive");

        // 品牌：钢青而非霓虹青，与深空底更和谐
        JSONObject sky = primitive.getJSONObject("sky");
        sky.put("pageEnd", "#080C14");
        sky.put("pageMist", "#0B121C");
        sky.put("brandSubtle", "#122830");
        sky.put("pageStart", "#0E1520");
        sky.put("borderSoft", "#1C2736");
        sky.put("brandHover", "#5ECAD6");
        sky.put("brandPrimary", "#2AA8B8");
        sky.put("brandPressed", "#1E8A98");

        JSONObject cloud = primitive.getJSONObject("cloud");
        cloud.put("white", "#131B28");
        cloud.put("surface", "#131B28");
        cloud.put("soft", "#182232");
        cloud.put("border", "#2A3648");
        cloud.put("mist", "#1A2434");

        JSONObject sun = primitive.getJSONObject("sun");
        sun.put("highlightSoft", "#E8D5A8");
        sun.put("highlight", "#D4B483");
        sun.put("soft", "#3A3220");

        // 成功色贴近品牌钢青，避免翠绿与品牌撞色
        JSONObject aurora = primitive.getJSONObject("aurora");
        aurora.put("successHover", "#6BC9B8");
        aurora.put("success", "#3BB8A8");
        aurora.put("successPressed", "#2A9A8C");
        aurora.put("selectedFill", "#122830");

        JSONObject coral = primitive.getJSONObject("coral");
        coral.put("dangerHover", "#E8A0A8");
        coral.put("danger", "#D47884");
        coral.put("dangerPressed", "#B85A66");

        JSONObject ink = primitive.getJSONObject("ink");
        ink.put("textTertiary", "#6B7A8D");
        ink.put("textSecondary", "#8B9AAD");
        ink.put("textPrimary", "#DCE4EE");
        ink.put("textStrong", "#F0F4F8");

        JSONObject neutral = primitive.getJSONObject("neutral");
        neutral.put("white", "#F0F4F8");
        neutral.put("black", "#060A12");
        neutral.put("scrimBlue", "#060A12AA");

        JSONObject semantic = base.getJSONObject("semantic");
        JSONObject button = semantic.getJSONObject("control").getJSONObject("button");
        // 钢青底 + 近黑字：对比清晰，不再白字贴霓虹
        button.put("primaryBackground", "{primitive.sky.brandPrimary}");
        button.put("primaryText", "{primitive.neutral.black}");
        // 品牌实心上的反色字（头像「你」、空状态 ○）
        semantic.getJSONObject("text").put("inverse", "{primitive.neutral.black}");

        themes.put(THEME_CYBER_FLAT, base);
    }

    /** 保留上一版琥珀石板，主题切换仍可用。 */
    private static void injectEmberObsidian(JSONObject root) throws JSONException {
        JSONObject themes = root.getJSONObject("themes");
        JSONObject base = new JSONObject(themes.getJSONObject(THEME_SKY).toString());
        JSONObject primitive = base.getJSONObject("primitive");

        JSONObject sky = primitive.getJSONObject("sky");
        sky.put("pageEnd", "#EEF1F4");
        sky.put("pageMist", "#E6EAF0");
        sky.put("brandSubtle", "#F3E7D4");
        sky.put("pageStart", "#DDE3EA");
        sky.put("borderSoft", "#C5CED8");
        sky.put("brandHover", "#E0973A");
        sky.put("brandPrimary", "#D97706");
        sky.put("brandPressed", "#B45309");

        JSONObject cloud = primitive.getJSONObject("cloud");
        cloud.put("white", "#F8FAFC");
        cloud.put("surface", "#FFFFFF");
        cloud.put("soft", "#F1F5F9");
        cloud.put("border", "#CBD5E1");
        cloud.put("mist", "#E2E8F0");

        JSONObject ink = primitive.getJSONObject("ink");
        ink.put("textTertiary", "#64748B");
        ink.put("textSecondary", "#475569");
        ink.put("textPrimary", "#0F172A");
        ink.put("textStrong", "#020617");

        themes.put(THEME_EMBER_OBSIDIAN, base);
    }

    private static String readAsset(Context context, String path) throws IOException {
        try (InputStream input = context.getApplicationContext().getAssets().open(path);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }
}
