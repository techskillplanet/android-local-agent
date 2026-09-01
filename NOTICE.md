# Third-Party Notices

本 Sample（`android-local-agent`）本身以 **MIT** 许可开源，见 [LICENSE](./LICENSE)。

运行时还会链接下列第三方组件；**请在分发 APK / 衍生作品时一并保留其许可声明**。

## UI

| 组件 | 坐标 / 来源 | 许可 |
| --- | --- | --- |
| Planet Components Android View | `io.github.techskillplanet:planet-components-android`（本仓库 `../planet-components/android/library`） | MIT |

## 本地推理

| 组件 | 坐标 / 来源 | 许可 |
| --- | --- | --- |
| java-llama.cpp / llama-android | `net.ladenthin:llama-android` | MIT |
| llama.cpp（native） | [ggml-org/llama.cpp](https://github.com/ggml-org/llama.cpp) | MIT |

## 推荐模型（需用户自行下载，不随本仓库分发）

| 模型 | 许可 |
| --- | --- |
| [Qwen2-0.5B-Instruct](https://huggingface.co/Qwen/Qwen2-0.5B-Instruct) | Apache-2.0 |
| [Qwen2.5-0.5B-Instruct](https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct) | Apache-2.0 |

GGUF 量化文件请从可信来源（如 Hugging Face 社区 GGUF 仓库）获取，并遵守对应模型卡与量化发布者的条款。

## 不包含 / 刻意避开

- **MediaPipe LLM Inference / LiteRT `.task`**：不直接支持任意开源 GGUF；本 Sample 走 llama.cpp 路线。
- **网络下载模型**：App 不声明 `INTERNET` 权限，避免「静默拉模型」的隐私疑虑。
