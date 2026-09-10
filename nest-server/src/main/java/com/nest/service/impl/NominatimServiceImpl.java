package com.nest.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nest.constant.MessageConstant;
import com.nest.service.NominatimService;
import com.nest.vo.GeocodeVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

/** Nominatim 地理编码实现。结果缓存 Redis 30 天，内置 1 req/s 速率限制。 */
@Slf4j
@Service
public class NominatimServiceImpl implements NominatimService {

    private static final String FORWARD_URL =
            "https://nominatim.openstreetmap.org/search?q=%s&format=json&limit=1";
    private static final String REVERSE_URL =
            "https://nominatim.openstreetmap.org/reverse?lat=%s&lon=%s&format=json";
    private static final String CACHE_KEY_PREFIX = "geo:address:";
    private static final String REVERSE_CACHE_PREFIX = "geo:reverse:";
    private static final Duration CACHE_TTL = Duration.ofDays(30);

    private final RestTemplate restTemplate;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    private final Object rateLimitLock = new Object();
    private long lastRequestTime = 0;

    public NominatimServiceImpl(RestTemplate restTemplate,
                                @Qualifier("redisTemplate") RedisTemplate<String, Object> redisTemplate,
                                ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /** 正向地理编码：地址→经纬度。先查缓存，未命中调 Nominatim。 */
    @Override
    public GeocodeVO geocode(String address) {
        if (address == null || address.isBlank()) {
            return null;
        }

        String cacheKey = CACHE_KEY_PREFIX + md5(address);

        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            log.debug("地理编码缓存命中: address='{}'", address);
            if (cached instanceof GeocodeVO vo) {
                return vo;
            }
            return objectMapper.convertValue(cached, GeocodeVO.class);
        }

        try {
            rateLimit();
            String url = String.format(FORWARD_URL, URLEncoder.encode(address, StandardCharsets.UTF_8));
            log.info("Nominatim 正向地理编码: address='{}'", address);
            String json = restTemplate.getForObject(url, String.class);

            JsonNode root = objectMapper.readTree(json);
            if (root.isArray() && !root.isEmpty()) {
                JsonNode first = root.get(0);
                GeocodeVO vo = GeocodeVO.builder()
                        .latitude(Double.parseDouble(first.get("lat").asText()))
                        .longitude(Double.parseDouble(first.get("lon").asText()))
                        .displayName(first.get("display_name").asText())
                        .build();

                redisTemplate.opsForValue().set(cacheKey, vo, CACHE_TTL);
                return vo;
            }
            log.warn("Nominatim 无结果: address='{}'", address);
            return null;
        } catch (Exception e) {
            log.error("地理编码失败: address='{}'", address, e);
            throw new RuntimeException(MessageConstant.GEOCODE_FAILED, e);
        }
    }

    /** 反向地理编码：经纬度→地址。先查缓存，未命中调 Nominatim。 */
    @Override
    public GeocodeVO reverseGeocode(double lat, double lng) {
        String cacheKey = REVERSE_CACHE_PREFIX + String.format("%.6f,%.6f", lat, lng);

        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            log.debug("反向地理编码缓存命中: lat={}, lng={}", lat, lng);
            if (cached instanceof GeocodeVO vo) {
                return vo;
            }
            return objectMapper.convertValue(cached, GeocodeVO.class);
        }

        try {
            rateLimit();
            String url = String.format(REVERSE_URL, lat, lng);
            log.info("Nominatim 反向地理编码: lat={}, lng={}", lat, lng);
            String json = restTemplate.getForObject(url, String.class);

            JsonNode root = objectMapper.readTree(json);
            if (root.has("lat") && root.has("lon")) {
                GeocodeVO vo = GeocodeVO.builder()
                        .latitude(Double.parseDouble(root.get("lat").asText()))
                        .longitude(Double.parseDouble(root.get("lon").asText()))
                        .displayName(root.has("display_name") ? root.get("display_name").asText() : "")
                        .build();

                redisTemplate.opsForValue().set(cacheKey, vo, CACHE_TTL);
                return vo;
            }
            return null;
        } catch (Exception e) {
            log.error("反向地理编码失败: lat={}, lng={}", lat, lng, e);
            throw new RuntimeException(MessageConstant.GEOCODE_FAILED, e);
        }
    }

    /** 速率限制：保证两次 Nominatim 请求间隔至少 1 秒。 */
    private void rateLimit() {
        synchronized (rateLimitLock) {
            long now = System.currentTimeMillis();
            long elapsed = now - lastRequestTime;
            if (elapsed < 1000) {
                try {
                    Thread.sleep(1000 - elapsed);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            lastRequestTime = System.currentTimeMillis();
        }
    }

    /** MD5 哈希（用于 Redis key）。 */
    private static String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
