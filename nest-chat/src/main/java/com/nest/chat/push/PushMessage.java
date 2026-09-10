package com.nest.chat.push;

import com.alibaba.fastjson2.JSON;

import java.util.LinkedHashMap;
import java.util.Map;

/** 推送消息体，null 字段自动忽略。 */
public class PushMessage {

    private final Map<String, Object> body = new LinkedHashMap<>();

    private PushMessage(String type) {
        if (type != null) {
            body.put("type", type);
        }
    }

    public static PushMessage of(String type) {
        return new PushMessage(type);
    }

    public PushMessage put(String key, Object value) {
        if (value != null) {
            body.put(key, value);
        }
        return this;
    }

    public String toJson() {
        return JSON.toJSONString(body);
    }
}
