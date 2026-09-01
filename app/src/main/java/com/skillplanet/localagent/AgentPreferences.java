package com.skillplanet.localagent;

import android.content.Context;
import android.content.SharedPreferences;

import com.skillplanet.localagent.chat.GenerationSettings;

/**
 * 轻量偏好：主题与采样预设。不存聊天记录（隐私优先）。
 */
public final class AgentPreferences {
    private static final String PREF = "local_agent_prefs";
    private static final String KEY_THEME = "color_theme";
    private static final String KEY_STYLE = "style_profile";
    private static final String KEY_PRESET = "gen_preset";

    public static final String THEME_DAY = ThemeBootstrap.THEME_CYBER_FLAT;
    public static final String THEME_NIGHT = ThemeBootstrap.THEME_NIGHT;
    public static final String THEME_MINT = ThemeBootstrap.THEME_MINT;
    public static final String THEME_SKY = ThemeBootstrap.THEME_SKY;
    public static final String THEME_EMBER = ThemeBootstrap.THEME_EMBER_OBSIDIAN;
    public static final String STYLE_RAISED = ThemeBootstrap.STYLE_RAISED;
    public static final String STYLE_FLAT = ThemeBootstrap.STYLE_FLAT;

    private final SharedPreferences prefs;

    public AgentPreferences(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public String getColorTheme() {
        if (!prefs.getBoolean("theme_migrated_v4", false)) {
            prefs.edit()
                    .putString(KEY_THEME, THEME_DAY)
                    .putString(KEY_STYLE, STYLE_FLAT)
                    .putBoolean("theme_migrated_v4", true)
                    .apply();
            return THEME_DAY;
        }
        return prefs.getString(KEY_THEME, THEME_DAY);
    }

    public String getStyleProfile() {
        if (!prefs.getBoolean("theme_migrated_v4", false)) {
            return STYLE_FLAT;
        }
        return prefs.getString(KEY_STYLE, STYLE_FLAT);
    }

    public void setTheme(String colorTheme, String styleProfile) {
        prefs.edit()
                .putString(KEY_THEME, colorTheme)
                .putString(KEY_STYLE, styleProfile)
                .apply();
    }

    public GenerationSettings.Preset getPreset() {
        String raw = prefs.getString(KEY_PRESET, GenerationSettings.Preset.BALANCED.name());
        try {
            return GenerationSettings.Preset.valueOf(raw);
        } catch (IllegalArgumentException ignored) {
            return GenerationSettings.Preset.BALANCED;
        }
    }

    public void setPreset(GenerationSettings.Preset preset) {
        prefs.edit().putString(KEY_PRESET, preset.name()).apply();
    }
}
