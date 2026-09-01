package com.skillplanet.localagent.llm;

import com.skillplanet.localagent.chat.GenerationSettings;

import net.ladenthin.llama.LlamaModel;
import net.ladenthin.llama.parameters.InferenceParameters;
import net.ladenthin.llama.parameters.ModelParameters;
import net.ladenthin.llama.value.ChatMessage;
import net.ladenthin.llama.value.LlamaOutput;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * llama.cpp（via {@code net.ladenthin:llama-android}）的薄封装。
 *
 * <h3>设计取舍</h3>
 * <ul>
 *   <li><b>CPU only</b>（{@code setGpuLayers(0)}）：保证真机与 x86_64 模拟器都能跑，
 *       开源 sample 优先可复现，而不是峰值性能。</li>
 *   <li><b>阻塞流式 API</b>：调用方应在后台线程跑 {@link #generateChat}；
 *       通过 {@link TokenListener} 把 token 抛回 UI 线程。</li>
 *   <li><b>chat template</b>：走 {@code withMessages(List&lt;ChatMessage&gt;)}，
 *       让 Instruct GGUF 自带的 Jinja/chat 模板生效（Qwen2 / Qwen2.5 均适用）。</li>
 * </ul>
 *
 * <p>不支持直接加载 Hugging Face safetensors / MediaPipe {@code .task}；请使用 GGUF。</p>
 */
public final class GgufLlamaEngine {

    /**
     * 流式回调。除 error 外，{@link #onComplete(long, int)} 总会在结束时调用一次
     * （含用户取消）。
     */
    public interface TokenListener {
        void onToken(String token);

        /**
         * @param elapsedMs 从开始生成到结束的墙钟时间
         * @param tokenEvents 回调次数（近似 token 数，取决于后端合并策略）
         */
        void onComplete(long elapsedMs, int tokenEvents);

        void onError(Throwable error);
    }

    public static final class ChatTurn {
        public final String role;
        public final String text;

        public ChatTurn(String role, String text) {
            this.role = role;
            this.text = text;
        }
    }

    private LlamaModel model;
    private String loadedPath;
    private final AtomicBoolean cancelRequested = new AtomicBoolean(false);

    public synchronized boolean isReady() {
        return model != null;
    }

    public synchronized String getLoadedPath() {
        return loadedPath;
    }

    /**
     * 加载 GGUF。会先关闭旧模型，避免 native 内存泄漏。
     *
     * @param modelPath 真实文件系统路径（不是 content://）
     */
    public synchronized void load(String modelPath, GenerationSettings settings) {
        closeLocked();
        ModelParameters parameters = new ModelParameters()
                .setModel(modelPath)
                .setCtxSize(settings.contextSize)
                .setThreads(settings.threads)
                // 0 = 全部留在 CPU。OpenCL/Vulkan 变体可另接 llama-android-opencl。
                .setGpuLayers(0);
        model = new LlamaModel(parameters);
        loadedPath = modelPath;
    }

    public synchronized void unload() {
        closeLocked();
    }

    /** 协作式取消：在下一个 token 边界停下。 */
    public void requestCancel() {
        cancelRequested.set(true);
    }

    /**
     * 阻塞式流式聊天。必须在后台线程调用。
     */
    public void generateChat(
            String systemPrompt,
            List<ChatTurn> history,
            GenerationSettings settings,
            TokenListener listener
    ) {
        LlamaModel active;
        synchronized (this) {
            active = model;
        }
        if (active == null) {
            listener.onError(new IllegalStateException("模型尚未加载"));
            return;
        }

        cancelRequested.set(false);
        List<ChatMessage> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.trim().isEmpty()) {
            messages.add(new ChatMessage("system", systemPrompt));
        }
        for (ChatTurn turn : history) {
            messages.add(new ChatMessage(turn.role, turn.text));
        }

        InferenceParameters params = InferenceParameters.empty()
                .withMessages(messages)
                .withNPredict(settings.maxTokens)
                .withTemperature(settings.temperature)
                .withTopK(settings.topK)
                .withTopP(settings.topP)
                .withMinP(settings.minP)
                .withRepeatPenalty(settings.repeatPenalty)
                .withRepeatLastN(settings.repeatLastN);

        long start = System.currentTimeMillis();
        int events = 0;
        try {
            for (LlamaOutput output : active.generateChat(params)) {
                if (cancelRequested.get()) {
                    break;
                }
                if (output != null && output.text != null && !output.text.isEmpty()) {
                    events++;
                    listener.onToken(output.text);
                }
            }
            listener.onComplete(System.currentTimeMillis() - start, events);
        } catch (Throwable t) {
            listener.onError(t);
        }
    }

    private void closeLocked() {
        if (model != null) {
            try {
                model.close();
            } finally {
                model = null;
                loadedPath = null;
            }
        }
    }
}
