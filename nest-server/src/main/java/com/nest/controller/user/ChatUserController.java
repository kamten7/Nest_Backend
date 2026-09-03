package com.nest.controller.user;

import com.nest.common.BaseContext;
import com.nest.common.PageResult;
import com.nest.common.Result;
import com.nest.constant.JwtConstant;
import com.nest.service.ChatService;
import com.nest.vo.ConversationVO;
import com.nest.vo.MessageVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 租客端聊天接口。
 */
@Slf4j
@RestController
@RequestMapping("/user/chat")
@RequiredArgsConstructor
@Tag(name = "租客端-聊天", description = "会话列表/历史消息/未读数")
public class ChatUserController {

    private final ChatService chatService;

    /** 我的会话列表 */
    @GetMapping("/conversations")
    @Operation(summary = "会话列表", description = "当前租客的所有会话（含对方信息+未读数）")
    public Result<PageResult<ConversationVO>> conversations(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize
    ) {
        PageResult<ConversationVO> result = chatService.listConversations(
                JwtConstant.TYPE_TENANT, BaseContext.getCurrentId(), page, pageSize);
        return Result.success(result);
    }

    /** 历史消息（打开时标记已读） */
    @GetMapping("/messages/{conversationId}")
    @Operation(summary = "历史消息", description = "分页查询会话历史消息，打开时自动标记已读")
    public Result<PageResult<MessageVO>> messages(
            @PathVariable Long conversationId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "50") Integer pageSize) {
        PageResult<MessageVO> result = chatService.getMessages(
                conversationId, JwtConstant.TYPE_TENANT, BaseContext.getCurrentId(), page, pageSize);
        return Result.success(result);
    }

    /** 总未读数 */
    @GetMapping("/unread")
    @Operation(summary = "总未读数", description = "所有会话未读消息总数")
    public Result<Long> unread() {
        long count = chatService.getUnreadCount(JwtConstant.TYPE_TENANT, BaseContext.getCurrentId());
        return Result.success(count);
    }

    /** 找或创建与房东的会话（联系房东入口） */
    @PostMapping("/create")
    @Operation(summary = "创建会话", description = "找或创建与某房东的会话，返回会话 ID")
    public Result<Long> create(
            @RequestBody Map<String, Object> body
    ) {
        Object otherId = body.get("otherId");
        if (otherId == null) {
            return Result.error("otherId 不能为空");
        }
        Long conversationId = chatService.getOrCreateConversationId(
                JwtConstant.TYPE_TENANT, BaseContext.getCurrentId(),
                JwtConstant.TYPE_LANDLORD, Long.valueOf(otherId.toString()));
        return Result.success(conversationId);
    }
}
