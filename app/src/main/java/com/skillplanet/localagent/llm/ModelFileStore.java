package com.skillplanet.localagent.llm;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import com.skillplanet.localagent.LocalAgentApp;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;

/**
 * 把 SAF 选中的 GGUF（通常是 {@code content://}）变成 llama.cpp 能 mmap 的真实路径。
 *
 * <h3>为什么必须复制？</h3>
 * <ul>
 *   <li>llama.cpp 通过文件路径做 memory-map，不能直接吃 ContentResolver 流。</li>
 *   <li>SAF 是 Google Play 友好的选文件方式：无需 {@code READ_EXTERNAL_STORAGE} /
 *       {@code MANAGE_EXTERNAL_STORAGE}。</li>
 * </ul>
 *
 * <p>代价：首次加载会多一次磁盘拷贝（大模型可能要几十秒）。副本放在
 * {@code context.getFilesDir()}，仅本 App 可读，冷启动由 {@link LocalAgentApp} 清理。</p>
 */
public final class ModelFileStore {
    private ModelFileStore() {
    }

    /** 选中结果：展示名（来自 OpenableColumns）+ 可 mmap 的绝对路径。 */
    public static final class PickedModel {
        public final String displayName;
        public final String absolutePath;
        /** 复制后文件字节数，便于 UI 展示「约 xxx MB」。 */
        public final long sizeBytes;

        public PickedModel(String displayName, String absolutePath, long sizeBytes) {
            this.displayName = displayName;
            this.absolutePath = absolutePath;
            this.sizeBytes = sizeBytes;
        }

        public String sizeLabel() {
            if (sizeBytes <= 0L) {
                return "未知大小";
            }
            double mb = sizeBytes / (1024.0 * 1024.0);
            return String.format(Locale.US, "%.1f MB", mb);
        }
    }

    public static String resolveDisplayName(Context context, Uri uri) {
        Cursor cursor = context.getContentResolver()
                .query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (index >= 0) {
                        String name = cursor.getString(index);
                        if (name != null && !name.trim().isEmpty()) {
                            return name;
                        }
                    }
                }
            } finally {
                cursor.close();
            }
        }
        String last = uri.getLastPathSegment();
        return last == null || last.isEmpty() ? "model.gguf" : last;
    }

    /**
     * 将 URI 内容拷贝到 {@link LocalAgentApp#MODEL_COPY_NAME}。
     * 若目标已存在会覆盖（换模型场景）。
     */
    public static PickedModel copyToInternal(Context context, Uri uri) throws IOException {
        String displayName = resolveDisplayName(context, uri);
        File dest = new File(context.getFilesDir(), LocalAgentApp.MODEL_COPY_NAME);
        try (InputStream input = context.getContentResolver().openInputStream(uri);
             OutputStream output = new FileOutputStream(dest)) {
            if (input == null) {
                throw new IOException("无法打开模型文件: " + uri);
            }
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            output.flush();
        }
        return new PickedModel(displayName, dest.getAbsolutePath(), dest.length());
    }

    public static void deleteInternalCopy(Context context) {
        //noinspection ResultOfMethodCallIgnored
        new File(context.getFilesDir(), LocalAgentApp.MODEL_COPY_NAME).delete();
    }
}
