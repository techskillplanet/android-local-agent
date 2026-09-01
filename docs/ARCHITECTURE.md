# Architecture

面向想二次开发 / 提 PR 的读者。更偏「为什么这样写」，而不是类清单。

## 目标

做一个**可开源分享**的 Android 端侧小模型聊天 Sample：

1. UI 与技趣星球 `planet-components` **同栈**（Java + 传统 View，无 Compose）。
2. 推理用 **GGUF + llama.cpp**，可直接加载 Qwen2 / Qwen2.5 0.5B Instruct。
3. 隐私默认：无网络权限、对话不落盘、模型工作副本冷启动清理。

## 分层

```text
┌─────────────────────────────────────────────┐
│ MainActivity（Shell）                        │
│  - 只拼装 planet-components 控件             │
│  - 实现 ChatController.Listener 刷新 UI      │
└──────────────────┬──────────────────────────┘
                   │
┌──────────────────▼──────────────────────────┐
│ ChatController                               │
│  - 单线程 Executor 串行 load / generate      │
│  - Handler 切回主线程回调                    │
└──────────────────┬──────────────────────────┘
                   │
┌──────────────────▼──────────────────────────┐
│ GgufLlamaEngine + ModelFileStore             │
│  - SAF content:// → filesDir 真实路径        │
│  - LlamaModel.generateChat 流式 token        │
└─────────────────────────────────────────────┘
```

## 关键路径

1. 用户点「选择 GGUF」→ `ACTION_OPEN_DOCUMENT`。
2. `ModelFileStore.copyToInternal` 复制到 `filesDir/current-model.gguf`。
3. `GgufLlamaEngine.load`：`setGpuLayers(0)` CPU 推理。
4. 发送消息 → `generateChat` + chat template → UI 逐 token 更新气泡。
5. 长按气泡复制；「重新生成」丢弃最后助手回复再跑一轮。

## 与 MediaPipe 的边界

MediaPipe / LiteRT 只吃转换后的 `.task` / `.litertlm`，对「任意开源 GGUF」不友好。  
本 Sample **明确选择 llama.cpp**，换取模型生态与文件系统加载的自由度。

## 扩展建议

| 想法 | 入口 |
| --- | --- |
| GPU（Adreno OpenCL） | 换 `llama-android-opencl`，并在 Engine 里放开 `gpuLayers` |
| 多会话落盘 | 新增 `SessionStore`；注意隐私文案 |
| 工具调用 Agent | `LlamaModel.chatWithTools`（java-llama.cpp） |
| 换 UI 皮肤 | `BasicThemeManager` 的 color/style token |
