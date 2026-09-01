package com.skillplanet.localagent.chat;

/**
 * 本地推理采样 / 负载参数。
 *
 * <p>默认值针对手机端 0.5B 级 Instruct 模型（如 Qwen2.5-0.5B）调过：
 * 略高的 repeatPenalty 可缓解小模型复读；maxTokens 控制延迟与耗电。</p>
 *
 * <p>本 sample 通过「预设」切换整组参数，避免把复杂滑条一股脑塞给初学者；
 * 需要精细调参时可改 {@link #balanced()} 等方法或直接 new。</p>
 */
public final class GenerationSettings {

    /** 预设名称，用于 UI 展示与 SharedPreferences。 */
    public enum Preset {
        /** 默认：稳、短、适合演示。 */
        BALANCED,
        /** 更随机，适合头脑风暴。 */
        CREATIVE,
        /** 更收敛，适合事实问答。 */
        PRECISE
    }

    public final float temperature;
    public final int topK;
    public final float topP;
    public final float minP;
    public final float repeatPenalty;
    public final int repeatLastN;
    public final int maxTokens;
    /** llama.cpp 计算线程数；过大可能反而变慢（大小核争抢）。 */
    public final int threads;
    /** KV / 上下文窗口（token）。手机端建议 1024~2048。 */
    public final int contextSize;
    public final Preset preset;

    public GenerationSettings(
            float temperature,
            int topK,
            float topP,
            float minP,
            float repeatPenalty,
            int repeatLastN,
            int maxTokens,
            int threads,
            int contextSize,
            Preset preset
    ) {
        this.temperature = temperature;
        this.topK = topK;
        this.topP = topP;
        this.minP = minP;
        this.repeatPenalty = repeatPenalty;
        this.repeatLastN = repeatLastN;
        this.maxTokens = maxTokens;
        this.threads = threads;
        this.contextSize = contextSize;
        this.preset = preset;
    }

    public static GenerationSettings balanced() {
        return new GenerationSettings(0.7f, 40, 0.95f, 0.05f, 1.1f, 64, 256, 4, 2048, Preset.BALANCED);
    }

    public static GenerationSettings creative() {
        return new GenerationSettings(0.95f, 60, 0.98f, 0.02f, 1.05f, 64, 320, 4, 2048, Preset.CREATIVE);
    }

    public static GenerationSettings precise() {
        return new GenerationSettings(0.3f, 20, 0.85f, 0.1f, 1.15f, 80, 192, 4, 2048, Preset.PRECISE);
    }

    public static GenerationSettings fromPreset(Preset preset) {
        if (preset == Preset.CREATIVE) {
            return creative();
        }
        if (preset == Preset.PRECISE) {
            return precise();
        }
        return balanced();
    }

    /** 中文短标签，给 OptionSheet 用。 */
    public String presetLabel() {
        switch (preset) {
            case CREATIVE:
                return "创意 · Creative";
            case PRECISE:
                return "精确 · Precise";
            case BALANCED:
            default:
                return "均衡 · Balanced";
        }
    }

    public String summary() {
        return presetLabel()
                + " · temp=" + temperature
                + " · max=" + maxTokens
                + " · thr=" + threads
                + " · ctx=" + contextSize;
    }
}
