package com.skillplanet.localagent.chat;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import com.skillplanet.localagent.llm.GgufLlamaEngine;
import com.skillplanet.localagent.llm.ModelFileStore;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 聊天业务控制器：把 Activity 从「加载模型 / 流式生成 / 取消」细节里解放出来。
 *
 * <p>线程模型：</p>
 * <ul>
 *   <li>所有磁盘与 llama.cpp 调用都在单线程 {@link #ioExecutor} 上串行，避免并发加载。</li>
 *   <li>所有 UI 回调切回主线程 {@link #mainHandler}。</li>
 * </ul>
 *
 * <p>这是开源 sample 有意保持的「无 androidx.lifecycle」写法，与
 * {@code planet-components} Android View samples 技术栈一致。</p>
 */
public final class ChatController {

    /** UI 侧需要实现的观察者。全部在主线程回调。 */
    public interface Listener {
        void onModelLoading();

        void onModelReady(String displayName, String sizeLabel, long loadElapsedMs);

        void onModelUnloaded();

        void onModelError(String message);

        void onMessagesChanged(List<ChatUiMessage> snapshot, boolean generating);

        void onAssistantToken(ChatUiMessage assistant, String fullText);

        void onGenerationFinished(long elapsedMs, int tokenEvents, boolean cancelled);

        void onGenerationError(String message);
    }

    private final Context appContext;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "local-agent-llm");
        t.setPriority(Thread.NORM_PRIORITY - 1);
        return t;
    });
    private final GgufLlamaEngine engine = new GgufLlamaEngine();
    private final List<ChatUiMessage> messages = new ArrayList<>();
    private final AtomicBoolean generating = new AtomicBoolean(false);

    private Listener listener;
    private GenerationSettings settings = GenerationSettings.balanced();
    private String systemPrompt;
    private Future<?> runningTask;
    private String modelDisplayName;
    private String modelSizeLabel;

    public ChatController(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    public GenerationSettings getSettings() {
        return settings;
    }

    public void setSettings(GenerationSettings settings) {
        this.settings = settings == null ? GenerationSettings.balanced() : settings;
    }

    public boolean isReady() {
        return engine.isReady();
    }

    public boolean isGenerating() {
        return generating.get();
    }

    public String getModelDisplayName() {
        return modelDisplayName;
    }

    public List<ChatUiMessage> getMessages() {
        return Collections.unmodifiableList(new ArrayList<>(messages));
    }

    public void destroy() {
        stopGeneration();
        ioExecutor.shutdownNow();
        engine.unload();
        listener = null;
    }

    /** SAF 选中的 content URI → 复制 → 加载。 */
    public void loadModelFromUri(Uri uri) {
        if (generating.get()) {
            return;
        }
        notifyLoading();
        runningTask = ioExecutor.submit(() -> {
            long start = System.currentTimeMillis();
            try {
                ModelFileStore.PickedModel picked = ModelFileStore.copyToInternal(appContext, uri);
                engine.load(picked.absolutePath, settings);
                modelDisplayName = picked.displayName;
                modelSizeLabel = picked.sizeLabel();
                long elapsed = System.currentTimeMillis() - start;
                mainHandler.post(() -> {
                    if (listener != null) {
                        listener.onModelReady(modelDisplayName, modelSizeLabel, elapsed);
                    }
                    emitMessages();
                });
            } catch (Throwable t) {
                engine.unload();
                modelDisplayName = null;
                modelSizeLabel = null;
                mainHandler.post(() -> {
                    if (listener != null) {
                        listener.onModelError(safeMessage(t));
                    }
                });
            }
        });
    }

    /** 自动化 / adb 预加载绝对路径，跳过 SAF。 */
    public void loadModelFromPath(String path) {
        if (generating.get()) {
            return;
        }
        notifyLoading();
        runningTask = ioExecutor.submit(() -> {
            long start = System.currentTimeMillis();
            try {
                File file = new File(path);
                engine.load(path, settings);
                modelDisplayName = file.getName();
                modelSizeLabel = file.exists()
                        ? new ModelFileStore.PickedModel(file.getName(), path, file.length()).sizeLabel()
                        : "未知大小";
                long elapsed = System.currentTimeMillis() - start;
                mainHandler.post(() -> {
                    if (listener != null) {
                        listener.onModelReady(modelDisplayName, modelSizeLabel, elapsed);
                    }
                    emitMessages();
                });
            } catch (Throwable t) {
                engine.unload();
                modelDisplayName = null;
                modelSizeLabel = null;
                mainHandler.post(() -> {
                    if (listener != null) {
                        listener.onModelError(safeMessage(t));
                    }
                });
            }
        });
    }

    /**
     * 按当前 {@link #settings} 重新打开已加载模型（改 threads/ctx 后需要）。
     * 若尚无模型则忽略。
     */
    public void reloadCurrentModel() {
        String path = engine.getLoadedPath();
        if (path == null || generating.get()) {
            return;
        }
        notifyLoading();
        final String name = modelDisplayName;
        final String size = modelSizeLabel;
        runningTask = ioExecutor.submit(() -> {
            long start = System.currentTimeMillis();
            try {
                engine.load(path, settings);
                long elapsed = System.currentTimeMillis() - start;
                mainHandler.post(() -> {
                    if (listener != null) {
                        listener.onModelReady(
                                name == null ? new File(path).getName() : name,
                                size == null ? "" : size,
                                elapsed
                        );
                    }
                });
            } catch (Throwable t) {
                engine.unload();
                mainHandler.post(() -> {
                    if (listener != null) {
                        listener.onModelError(safeMessage(t));
                    }
                });
            }
        });
    }

    public void unloadModel() {
        if (generating.get()) {
            return;
        }
        engine.unload();
        ModelFileStore.deleteInternalCopy(appContext);
        modelDisplayName = null;
        modelSizeLabel = null;
        messages.clear();
        if (listener != null) {
            listener.onModelUnloaded();
            emitMessages();
        }
    }

    public void clearChat() {
        if (generating.get()) {
            return;
        }
        messages.clear();
        emitMessages();
    }

    public void sendUserMessage(String rawText) {
        if (!engine.isReady() || generating.get()) {
            return;
        }
        String text = rawText == null ? "" : rawText.trim();
        if (text.isEmpty()) {
            return;
        }
        messages.add(new ChatUiMessage("user", text));
        ChatUiMessage assistant = new ChatUiMessage("assistant", "");
        messages.add(assistant);
        startGeneration(assistant, /*regenerate*/ false);
    }

    /**
     * 丢掉最后一条助手回复，基于到最后一条用户消息为止的历史重新生成。
     */
    public void regenerate() {
        if (!engine.isReady() || generating.get() || messages.isEmpty()) {
            return;
        }
        int lastUser = -1;
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i).isUser()) {
                lastUser = i;
                break;
            }
        }
        if (lastUser < 0) {
            return;
        }
        while (messages.size() > lastUser + 1) {
            messages.remove(messages.size() - 1);
        }
        ChatUiMessage assistant = new ChatUiMessage("assistant", "");
        messages.add(assistant);
        startGeneration(assistant, /*regenerate*/ true);
    }

    public void stopGeneration() {
        engine.requestCancel();
        if (runningTask != null) {
            runningTask.cancel(true);
        }
        if (generating.getAndSet(false)) {
            emitMessages();
            if (listener != null) {
                listener.onGenerationFinished(0L, 0, true);
            }
        }
    }

    private void startGeneration(ChatUiMessage assistant, boolean regenerate) {
        generating.set(true);
        emitMessages();

        List<GgufLlamaEngine.ChatTurn> history = new ArrayList<>();
        for (ChatUiMessage message : messages) {
            if (message == assistant) {
                continue;
            }
            history.add(new GgufLlamaEngine.ChatTurn(message.role, message.text));
        }

        final GenerationSettings snap = settings;
        final String prompt = systemPrompt;
        final StringBuilder reply = new StringBuilder();

        runningTask = ioExecutor.submit(() -> engine.generateChat(
                prompt,
                history,
                snap,
                new GgufLlamaEngine.TokenListener() {
                    @Override
                    public void onToken(String token) {
                        reply.append(token);
                        String full = reply.toString();
                        mainHandler.post(() -> {
                            assistant.text = full;
                            if (listener != null) {
                                listener.onAssistantToken(assistant, full);
                            }
                        });
                    }

                    @Override
                    public void onComplete(long elapsedMs, int tokenEvents) {
                        mainHandler.post(() -> {
                            generating.set(false);
                            emitMessages();
                            if (listener != null) {
                                listener.onGenerationFinished(elapsedMs, tokenEvents, false);
                            }
                        });
                    }

                    @Override
                    public void onError(Throwable error) {
                        mainHandler.post(() -> {
                            generating.set(false);
                            emitMessages();
                            if (listener != null) {
                                listener.onGenerationError(safeMessage(error));
                            }
                        });
                    }
                }
        ));
    }

    private void notifyLoading() {
        if (listener != null) {
            listener.onModelLoading();
        }
    }

    private void emitMessages() {
        if (listener != null) {
            listener.onMessagesChanged(getMessages(), generating.get());
        }
    }

    private static String safeMessage(Throwable t) {
        return t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
    }
}
