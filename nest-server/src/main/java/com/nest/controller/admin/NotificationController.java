package com.nest.controller.admin;

import com.nest.chat.push.PushService;
import com.nest.common.BaseContext;
import com.nest.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 通知演示控制器 —— 模拟 WebSocket 推送（测试用）。 */
@Slf4j
@RestController
@RequestMapping("/admin/notification")
@RequiredArgsConstructor
@Tag(name = "房东端-通知演示", description = "模拟租客预约/来消息的 WebSocket 推送（测试用）")
public class NotificationController {

    private final PushService pushService;

    /** 推送演示通知到当前登录房东。 */
    @PostMapping("/demo")
    @Operation(summary = "推送演示通知")
    public Result<Void> demo(@RequestBody(required = false) Map<String, String> body) {
        Long landlordId = BaseContext.getCurrentId();

        String type = body != null && body.get("type") != null ? body.get("type") : "appointment";
        String title = body != null && body.get("title") != null ? body.get("title") : "有新的预约看房请求";
        String content = body != null && body.get("content") != null
                ? body.get("content") : "租客小明预约了看房，点击查看详情";

        boolean online = pushService.isOnline("landlord", landlordId);
        log.info("演示通知推送: landlordId={}, online={}, type={}", landlordId, online, type);

        pushService.pushNotice("landlord", landlordId, type, title, content);
        return Result.successMsg(online ? "演示通知已推送" : "推送成功（房东当前不在线，消息将丢失）");
    }
}
