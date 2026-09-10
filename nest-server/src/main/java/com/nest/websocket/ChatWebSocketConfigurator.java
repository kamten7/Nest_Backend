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

/** WebSocket 握手鉴权配置器。连接建立前校验 JWT，防止伪造 userId。 */
@Slf4j
public class ChatWebSocketConfigurator extends ServerEndpointConfig.Configurator {

    public static final String ATTR_USER_TYPE = "userType";
    public static final String ATTR_USER_ID = "userId";

    /** 握手鉴权：解析路径→提取 token→按类型选密钥→校验 userId→写入可信身份。 */
    @Override
    public void modifyHandshake(ServerEndpointConfig config, HandshakeRequest request, HandshakeResponse response) {
        String path = config.getPath();
        String requestUri = request.getRequestURI().toString();
        int qIdx = requestUri.indexOf('?');
        if (qIdx >= 0) {
            requestUri = requestUri.substring(0, qIdx);
        }
        String[] pathSegs = path.split("/");
        String[] uriSegs = requestUri.split("/");
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

        String queryString = request.getQueryString();
        String token = extractToken(request, queryString);

        if (token == null || token.isEmpty()) {
            log.warn("WebSocket 握手缺少 token: {} {}", pathUserType, pathUserId);
            throw new SecurityException("缺少认证 token");
        }

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

            if (userId == null || !String.valueOf(userId).equals(pathUserId)) {
                log.warn("WebSocket token 与路径 userId 不匹配: path={}, tokenUserId={}", pathUserId, userId);
                throw new SecurityException("token 与用户不匹配");
            }

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

    /** 按优先级从 ParameterMap / Header / queryString 提取 token。 */
    private String extractToken(HandshakeRequest request, String queryString) {
        Map<String, List<String>> params = request.getParameterMap();
        if (params.containsKey("token") && params.get("token") != null && !params.get("token").isEmpty()) {
            return params.get("token").get(0);
        }

        List<String> userHeader = request.getHeaders().get(JwtConstant.USER_TOKEN_NAME);
        if (userHeader != null && !userHeader.isEmpty() && !userHeader.get(0).isEmpty()) {
            return userHeader.get(0);
        }
        List<String> adminHeader = request.getHeaders().get(JwtConstant.ADMIN_TOKEN_NAME);
        if (adminHeader != null && !adminHeader.isEmpty() && !adminHeader.get(0).isEmpty()) {
            return adminHeader.get(0);
        }

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
