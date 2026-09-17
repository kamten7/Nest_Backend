package com.nest.ai.service;

import com.nest.vo.AiMessageVO;
import jakarta.servlet.AsyncContext;

import java.util.List;

/** AI 找房服务接口。 */
public interface AiUserService {

    void streamChat(String message, AsyncContext asyncContext);

    /** 读取当前登录租客的对话历史（store 中最多 20 条，已过滤工具调用的中间消息）。 */
    List<AiMessageVO> getHistory();

    /** 清空当前登录租客的对话记忆（删 Redis key；下一条消息自动重建空白记忆）。 */
    void clearMemory();
}
