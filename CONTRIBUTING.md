# Contributing

感谢关注 **Android Local Agent** Sample。

## 开发约定

1. **UI**：继续使用 `planet-components` Android View（Java）。不要引入 Compose / AppCompat，除非有充分理由并更新文档。
2. **推理**：默认 CPU GGUF；改 GPU 路径请在 README / NOTICE 写清设备要求。
3. **注释**：公共类与「为什么这样做」的取舍请写清楚，方便后来者学习。
4. **隐私**：新增网络 / 持久化能力时，同步更新 Manifest、关于页与 NOTICE。

## 本地构建

```bash
cp local.properties.example local.properties   # 填入 sdk.dir
./gradlew :app:assembleDebug
```

## 提交流程

1. Fork / 开分支。
2. 保持改动聚焦；Sample 以可读性优先。
3. 至少保证 `:app:assembleDebug` 通过。
4. PR 描述写清动机与验证方式。

## 行为准则

请友善讨论。不接受在 Sample 中夹带未声明的跟踪 SDK 或强制联网逻辑。
