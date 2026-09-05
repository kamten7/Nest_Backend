package com.nest.websocket;

import com.nest.constant.JwtConstant;
import com.nest.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.websocket.HandshakeResponse;
import jakarta.websocket.server.HandshakeRequest;
import jakarta.websocket.server.ServerEndpointConfig;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

/**
 * WebSocket 握手鉴权配置器。
 *
 * 在 WebSocket 连接建立（握手）时校验 JWT，防止伪造 userId 连接。
 *
 *
 * 租客端：Header authentication 或 queryString ?token=xxx，用 USER_SECRET_KEY
 * 房东端：Header token 或 queryString ?token=xxx，用 ADMIN_SECRET_KEY
 *
 * 校验验通过的用户身份（userType+userId）存入 getUserProperties()，
 * 在 @OnOpen 里取出使用。
 */
@Slf4j
public class ChatWebSocketConfigurator extends ServerEndpointConfig.Configurator {

    /** 身份属性 key */
    public static final String ATTR_USER_TYPE = "userType";
    public static final String ATTR_USER_ID = "userId";

    /**
     * WebSocket 握手鉴权：连接建立前校验 JWT。
     *
     * 流程：解析路径中的 userType/userId → 提取 token（ParameterMap/Header/queryString）
     * → 按用户类型选密钥解析 JWT → 校验 token 内 userId 与路径一致（防冒充）→ 写入可信身份。
     *
     *  1. 从路径解析 userType/userId
     *  2. 从 queryString/Header 提取 token
     *  3. 按用户类型选密钥解析 JWT
     *  4. 校验 token 中的 userId 与路径一致（防冒充）
     *  5. 写入可信身份到 config.getUserProperties()
     *
     * 任何一步失败都抛 {@link SecurityException}，Tomcat 会以 500 拒绝该连接（未建立）。
     *
     * @param config   WebSocket 端点配置（写入可信身份，@OnOpen 里读取）
     * @param request  握手请求（含 URI、queryString、Header）
     * @param response 握手响应（本方法不需要改）
     */
    @Override
    public void modifyHandshake(
            ServerEndpointConfig config,// WebSocket 端点配置（写入可信身份，@OnOpen 里读取）
            HandshakeRequest request,
            HandshakeResponse response
    ) {
        // 从路径模板 {userType}/{userId} 定位：用 config.getPath() 匹配索引
        // 或从 request URI 取最后两段（/ws/chat/{userType}/{userId}）
        String path = config.getPath(); // 如 /ws/chat/{userType}/{userId}
        String requestUri = request.getRequestURI().toString(); // 注意：可能含 queryString，如 /ws/chat/tenant/1?token=xxx
        // 先去掉 query string（? 后面的部分），避免 userId 解析成 "1?token=xxx"
        int qIdx = requestUri.indexOf('?');
        if (qIdx >= 0) {
            requestUri = requestUri.substring(0, qIdx);
        }
        String[] pathSegs = path.split("/");
        String[] uriSegs = requestUri.split("/");
        // 找到模板中 userType 和 userId 在 uri 里的对应值（按末尾对齐）
        String pathUserType = null, pathUserId = null;
        int n = pathSegs.length, m = uriSegs.length;
        for (int i = 0; i < n && i < m; i++) {
            if (pathSegs[n - 1 - i].equals("{userType}")) pathUserType = uriSegs[m - 1 - i];
            if (pathSegs[n - 1 - i].equals("{userId}")) pathUserId = uriSegs[m - 1 - i];
        }
        if (pathUserType == null || pathUserId == null) {
            log.warn("WebSocket 握手无法解析路径: template={}, uri={}", path, requestUri);
            throw new SecurityException("非法连接路径");
        }

        // 从 queryString 或 Header 取 token
        String queryString = request.getQueryString(); // 如 token=xxx
        String token = extractToken(request, queryString);

        if (token == null || token.isEmpty()) {
            log.warn("WebSocket 握手缺少 token: {} {}", pathUserType, pathUserId);
            throw new SecurityException("缺少认证 token");
        }

        // 根据 userType 选择密钥解析
        String secretKey;
        if ("tenant".equals(pathUserType)) {
            secretKey = JwtConstant.USER_SECRET_KEY;
        } else if ("landlord".equals(pathUserType)) {
            secretKey = JwtConstant.ADMIN_SECRET_KEY;
        } else {
            throw new SecurityException("非法的用户类型");
        }

        try {
            Claims claims = JwtUtil.parseToken(secretKey, token);
            Long userId = claims.get("userId", Long.class);

            // 校验路径中的 userId 与 token 中的一致（防止冒充他人）
            if (userId == null || !String.valueOf(userId).equals(pathUserId)) {
                log.warn("WebSocket token 与路径 userId 不匹配: path={}, tokenUserId={}", pathUserId, userId);
                throw new SecurityException("token 与用户不匹配");
            }

            // 身份存入 properties，@OnOpen 里取
            config.getUserProperties().put(ATTR_USER_TYPE, pathUserType);
            config.getUserProperties().put(ATTR_USER_ID, userId);
            log.info("WebSocket 握手鉴权通过: {}/{}", pathUserType, userId);
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            log.warn("WebSocket 握手 token 无效: {}", e.getMessage());
            throw new SecurityException("token 无效或已过期");
        }
    }

    /**
     * 按优先级从三个来源提取 token。
     *
     * 优先级：ParameterMap（最可靠）→ Header → 手动解析 queryString（兜底）。
     * 为什么不用 getQueryString 直接解析？部分容器它可能返回 null，而 ParameterMap 一定有值。
     *
     * @param request     握手请求（提供已解析的 ParameterMap 与 Header）
     * @param queryString 原始 queryString（最后兜底来源）
     * @return token 字符串；所有来源都没有则返回 null
     */
    private String extractToken(HandshakeRequest request, String queryString) {
        // 1. 参数表（Tomcat 已解析的 queryString，最可靠，Web 房东端 ?token=xxx 走这里）
        Map<String, List<String>> params = request.getParameterMap();
        if (params.containsKey("token") && params.get("token") != null && !params.get("token").isEmpty()) {
            return params.get("token").get(0);
        }

        // 2. Header：租客端 authentication / 房东端 token
        List<String> userHeader = request.getHeaders().get(JwtConstant.USER_TOKEN_NAME);
        if (userHeader != null && !userHeader.isEmpty() && !userHeader.get(0).isEmpty()) {
            return userHeader.get(0);
        }
        List<String> adminHeader = request.getHeaders().get(JwtConstant.ADMIN_TOKEN_NAME);
        if (adminHeader != null && !adminHeader.isEmpty() && !adminHeader.get(0).isEmpty()) {
            return adminHeader.get(0);
        }

        // 3. 兜底：手动解析 queryString（个别容器 getParameterMap 为空时）
        if (queryString != null && queryString.contains("token=")) {
            for (String pair : queryString.split("&")) {
                if (pair.startsWith("token=")) {
                    return pair.substring("token=".length());
                }
            }
        }
        return null;
    }
}
