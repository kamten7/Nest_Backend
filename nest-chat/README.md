# nest-chat · 聊天模块

Nest 的**聊天与实时通知层**：统一管理 WebSocket 传输（Netty / JSR-356）与消息推送，业务模块只调用 `PushService`，不感知底层实现。

```
nest-server ──依赖──▶ nest-chat ──依赖──▶ nest-common
     ▲                     │
     └──── 实现 SPI ────────┘   MessageListener（入站消息回调）
```

## 目录结构

```
nest-chat/src/main/java/com/nest/chat/
├── config/
│   ├── ChatConfiguration.java       # 装配传输层 Bean + 注册 @ServerEndpoint
│   ├── ChatProperties.java          # nest.websocket.* 配置
│   └── ApplicationContextHolder.java# 静态取 Bean（容器自建对象用）
├── transport/
│   ├── MessageTransport.java        # 传输层抽象
│   ├── Jsr356Transport.java         # 内嵌 Tomcat WebSocket 实现
│   └── NettyTransport.java          # Netty 实现
├── netty/
│   ├── NettyWebSocketServer.java    # Netty 服务端：启动 / 鉴权 / Channel 池
│   └── NettyChannelHandler.java     # 通道处理器
├── jsr356/
│   ├── ChatWebSocketServer.java     # 端点 /ws/chat/{userType}/{userId}
│   └── ChatWebSocketConfigurator.java # 握手 JWT 鉴权
├── core/
│   ├── MessageListener.java         # 入站消息 SPI（由 nest-server 实现）
│   └── MessageDispatcher.java       # 入站路由 chat/read_receipt/typing/heartbeat
└── push/
    ├── PushService.java             # 对外推送 API
    └── PushMessage.java             # 消息体（null 字段自动忽略）
```

## 一、出站：业务侧只注入 `PushService`

| 方法 | 用途 |
|------|------|
| `pushChat(toType, toId, conversationId, msgId, fromType, fromId, senderName, content, msgType)` | 聊天消息 |
| `pushReadReceipt(toType, toId, conversationId, readerType, readerId, lastReadMsgId)` | 已读回执 |
| `pushNotice(toType, toId, type, title, content)` | 通用通知（预约 / 公告 / 系统） |
| `pushComment(toType, toId, title, content, houseId, reviewId)` | 评论通知 |
| `pushLike(toType, toId, title, content, commentId)` | 点赞通知 |
| `pushRaw(toType, toId, payload)` | 透传原始报文（`typing` 等） |
| `isOnline(userType, userId)` | 用户是否在线 |

```java
// nest-server 里新增「评论被回复」推送，只需一行
pushService.pushComment("tenant", review.getTenantId(),
        "您的评论收到新回复", content, houseId, reviewId);

// 点赞同理
pushService.pushLike("tenant", comment.getUserId(), "有人赞了你的评论", content, commentId);
```

## 二、入站：业务侧实现 `MessageListener`

聊天模块收到客户端消息后只做路由，落库等业务由 `nest-server` 实现：

```java
@Component
@RequiredArgsConstructor
public class ChatMessageListener implements MessageListener {

    private final ChatService chatService;   // nest-server 的业务服务

    @Override
    public Long onChat(String fromType, Long fromId, String toType, Long toId,
                       String content, String msgType, String clientMsgId) {
        return chatService.send(fromType, fromId, toType, toId, content, msgType, clientMsgId);
    }

    @Override
    public void onReadReceipt(Long conversationId, String readerType, Long readerId, Long lastReadMsgId) {
        chatService.markReadByReceipt(conversationId, readerType, readerId, lastReadMsgId);
    }
}
```

`MessageListener` 通过 `ObjectProvider` 懒加载，**没有实现类时模块仍可独立启动**（入站消息只记日志）。

## 三、消息协议

| 方向 | type | 关键字段 |
|------|------|----------|
| 客户端 → 服务端 | `chat` | toType, toId, content, msgType, clientMsgId |
| 客户端 → 服务端 | `read_receipt` | conversationId, lastReadMsgId |
| 客户端 → 服务端 | `typing` | toType, toId（服务端原样转发） |
| 客户端 → 服务端 | `heartbeat` | — |
| 服务端 → 客户端 | `chat` / `read_receipt` / `appointment` / `comment` / `like` | 由 `PushService` 组装 |

## 四、切换传输层

```yaml
nest:
  websocket:
    implementation: jsr356      # jsr356 | netty
    netty:
      port: 8081
      path: /ws/chat
      boss-threads: 1
      worker-threads: 0         # 0 = 2 * CPU
      idle-timeout-seconds: 60
```

`ChatConfiguration` 按 `implementation` 装配 `MessageTransport`；`NettyWebSocketServer` 仅在 `implementation=netty` 时启动，避免无用端口占用。

## 五、已知限制

- **默认实现是 JSR-356**（客户端连 `ws://localhost:8080/ws/chat/...`）；Netty 监听 8081，切换后前端需同步改地址。
- Netty 链路的握手鉴权方法 `NettyWebSocketServer.authenticate(...)` 已就绪，但**尚未接入 pipeline**，因此切换前需补一个解析 `?token=` 的握手 Handler。
- 推送为「在线直投」：用户离线时消息丢弃，历史消息由 HTTP 接口补齐（与拆分前行为一致）。
- 单机内存维护在线连接（`ConcurrentHashMap`），多实例部署需换成 Redis 广播。

## 六、构建

```bash
cd backend
mvn -pl nest-chat -am clean install -DskipTests   # 只构建本模块及其依赖
mvn clean package -DskipTests                      # 全量构建
```

> 若 `mvn` 报 `AccessDeniedException` 读取本地仓库，请在沙箱外执行或指定 `-Dmaven.repo.local=<你的 .m2 路径>`。
