package com.nest.chat.jsr356;

import com.nest.constant.JwtConstant;
import com.nest.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.websocket.HandshakeResponse;
import jakarta.websocket.server.HandshakeRequest;
import jakarta.websocket.server.ServerEndpointConfig;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

/** 握手鉴权：校验 JWT 并写入可信身份，防止伪造 userId。 */
@Slf4j
public class ChatWebSocketConfigurator extends ServerEndpointConfig.Configurator {

    public static final String ATTR_USER_TYPE = "userType";
    public static final String ATTR_USER_ID = "userId";

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
        String pathUserType = null;
        String pathUserId = null;
        for (int i = 0; i < pathSegs.length && i < uriSegs.length; i++) {
            String seg = pathSegs[pathSegs.length - 1 - i];
            String actual = uriSegs[uriSegs.length - 1 - i];
            if ("{userType}".equals(seg)) {
                pathUserType = actual;
            } else if ("{userId}".equals(seg)) {
                pathUserId = actual;
            }
        }
        if (pathUserType == null || pathUserId == null) {
            log.warn("WebSocket 握手无法解析路径: template={}, uri={}", path, requestUri);
            throw new SecurityException("非法连接路径");
        }

        String token = extractToken(request);
        if (token == null || token.isEmpty()) {
            log.warn("WebSocket 握手缺少 token: {} {}", pathUserType, pathUserId);
            throw new SecurityException("缺少认证 token");
        }

        String secretKey;
        if (JwtConstant.TYPE_TENANT.equals(pathUserType)) {
            secretKey = JwtConstant.userSecretKey();
        } else if (JwtConstant.TYPE_LANDLORD.equals(pathUserType)) {
            secretKey = JwtConstant.adminSecretKey();
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

    /** 依次从 ParameterMap / Header / queryString 取 token。 */
    private String extractToken(HandshakeRequest request) {
        Map<String, List<String>> params = request.getParameterMap();
        List<String> tokenParam = params.get("token");
        if (tokenParam != null && !tokenParam.isEmpty() && !tokenParam.get(0).isEmpty()) {
            return tokenParam.get(0);
        }

        for (String headerName : List.of(JwtConstant.USER_TOKEN_NAME, JwtConstant.ADMIN_TOKEN_NAME)) {
            List<String> values = request.getHeaders().get(headerName);
            if (values != null && !values.isEmpty() && !values.get(0).isEmpty()) {
                return values.get(0);
            }
        }
        return null;
    }
}
