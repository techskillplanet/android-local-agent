package com.skillplanet.localagent.chat;

/**
 * 一次对话气泡的不可变角色 + 可变正文。
 *
 * <p>助手消息在流式生成时会不断改写 {@link #text}，UI 通过同一个对象引用刷新气泡。
 * role 约定与 llama.cpp chat template 一致：{@code user} / {@code assistant}。</p>
 */
public final class ChatUiMessage {
    /** 与 GGUF chat template 对齐的角色名。 */
    public final String role;

    /** 气泡正文；助手消息可能从空字符串开始逐步追加。 */
    public String text;

    public ChatUiMessage(String role, String text) {
        this.role = role;
        this.text = text == null ? "" : text;
    }

    public boolean isUser() {
        return "user".equals(role);
    }

    public boolean isAssistant() {
        return "assistant".equals(role);
    }
}
