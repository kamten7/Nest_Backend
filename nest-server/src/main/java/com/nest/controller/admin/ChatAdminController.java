package com.nest.controller.admin;

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

/** 房东端聊天接口。 */
@Slf4j
@RestController
@RequestMapping("/admin/chat")
@RequiredArgsConstructor
@Tag(name = "房东端-聊天", description = "会话列表/历史消息")
public class ChatAdminController {

    private final ChatService chatService;

    /** 我的会话列表 */
    @GetMapping("/conversations")
    @Operation(summary = "会话列表", description = "当前房东的所有会话（含对方信息+未读数）")
    public Result<PageResult<ConversationVO>> conversations(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize
    ) {
        PageResult<ConversationVO> result = chatService.listConversations(
                JwtConstant.TYPE_LANDLORD,
                BaseContext.getCurrentId(),
                page,
                pageSize
        );
        return Result.success(result);
    }

    /** 历史消息（打开时标记已读） */
    @GetMapping("/messages/{conversationId}")
    @Operation(summary = "历史消息", description = "分页查询会话历史消息，打开时自动标记已读")
    public Result<PageResult<MessageVO>> messages(
            @PathVariable Long conversationId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "50") Integer pageSize
    ) {
        PageResult<MessageVO> result = chatService.getMessages(
                conversationId,
                JwtConstant.TYPE_LANDLORD,
                BaseContext.getCurrentId(),
                page,
                pageSize);
        return Result.success(result);
    }

    /** 总未读数 */
    @GetMapping("/unread")
    @Operation(summary = "总未读数", description = "当前房东所有会话未读消息总数（导航红点用）")
    public Result<Long> unread() {
        long count = chatService.getUnreadCount(
                JwtConstant.TYPE_LANDLORD, BaseContext.getCurrentId());
        return Result.success(count);
    }

    /** 找或创建与某租客的会话（房东从订单页主动联系租客的入口） */
    @PostMapping("/create")
    @Operation(summary = "创建会话", description = "找或创建与某租客的会话，返回会话 ID")
    public Result<Long> create(@RequestBody Map<String, Object> body) {
        Object otherId = body.get("otherId");
        if (otherId == null) {
            return Result.error("otherId 不能为空");
        }
        Long conversationId = chatService.getOrCreateConversationId(
                JwtConstant.TYPE_LANDLORD, BaseContext.getCurrentId(),
                JwtConstant.TYPE_TENANT, Long.valueOf(otherId.toString()));
        return Result.success(conversationId);
    }
}
