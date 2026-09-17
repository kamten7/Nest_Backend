package com.nest.controller.user;

import com.nest.ai.service.AiUserService;
import com.nest.common.Result;
import com.nest.constant.MessageConstant;
import com.nest.vo.AiMessageVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 租客端 AI 找房接口 —— SSE 流式。
 */
@Slf4j
@RestController
@RequestMapping("/user/ai")
@RequiredArgsConstructor
@Tag(name = "租客端-AI找房", description = "自然语言找房，SSE 流式返回")
public class AiUserController {

    private final AiUserService aiUserService;

    /** 流式 AI 找房对话（SSE 长连接返回）。 */
    @PostMapping(value = "/chat/stream", produces = "text/event-stream")
    @Operation(summary = "AI 流式找房", description = "租客输入自然语言，AI 通过工具查真实房源并逐字返回")
    public void streamChat(@RequestBody(required = false) Map<String, String> body,
                           HttpServletRequest request,
                           HttpServletResponse response) {
        String message = body != null ? body.get("message") : null;

        AsyncContext asyncContext = request.startAsync();
        asyncContext.setTimeout(120000);

        aiUserService.streamChat(message, asyncContext);
    }

    /** 读取对话历史（进页面时恢复气泡用）；无历史返回空数组。 */
    @GetMapping("/memory")
    @Operation(summary = "AI 对话历史", description = "返回当前租客最近 20 条对话（仅 user / ai 两种 role）")
    public Result<List<AiMessageVO>> getMemory() {
        return Result.success(aiUserService.getHistory());
    }

    /** 清空对话记忆。 */
    @DeleteMapping("/memory")
    @Operation(summary = "清空 AI 对话", description = "删除当前租客的 AI 对话记忆，下次对话重新开始")
    public Result<Void> clearMemory() {
        aiUserService.clearMemory();
        return Result.successMsg(MessageConstant.AI_MEMORY_CLEARED);
    }
}
