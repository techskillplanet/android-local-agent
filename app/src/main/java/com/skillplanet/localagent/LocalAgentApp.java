package com.skillplanet.localagent;

import android.app.Application;

import java.io.File;

/**
 * Application 入口。
 *
 * <p>冷启动删除临时 GGUF 副本，保证「关掉 App 不留下数 GB 工作副本」——
 * 这是开源隐私演示的一部分：对话默认不落盘，模型副本也不跨会话驻留。
 * 用户每次启动需重新选择模型（或用 adb EXTRA 预加载）。</p>
 */
public class LocalAgentApp extends Application {
    /** 工作副本文件名，固定以便清理与覆盖。 */
    public static final String MODEL_COPY_NAME = "current-model.gguf";

    @Override
    public void onCreate() {
        super.onCreate();
        //noinspection ResultOfMethodCallIgnored
        new File(getFilesDir(), MODEL_COPY_NAME).delete();
    }
}
