# 微信真实登录配置（B 方案）

> 目标：不用 mock，走微信 `jscode2session` 换取真实 openid。
> 适用：本地 IDEA `dev`、Docker `demo`、生产 `prod`。

## 1. 先确认三处 AppID 一致

| 位置 | 文件 |
|------|------|
| 小程序 | `miniapp/project.config.json` → `appid` |
| 后端 dev | `backend/nest-server/src/main/resources/application-dev.yml` → `nest.wechat.appid` |
| 后端 Docker | `backend/.env` → `NEST_WECHAT_APPID` |

三处必须是同一个小程序 AppID，例如：

```text
wxcf8208fdc6c87cfc
```

AppID 不一致，微信会返回 `40013 invalid appid` 或 `40125`。

## 2. 获取/重置 AppSecret

1. 登录 [微信公众平台](https://mp.weixin.qq.com)
2. 开发管理 → 开发设置 → AppSecret
3. 点击“重置”并保存新 Secret（只显示一次）
4. 不要使用其它小程序的 Secret

## 3. 本地 IDEA（dev profile）

编辑 `backend/nest-server/src/main/resources/application-dev.yml`：

```yaml
nest:
  wechat:
    appid: wxcf8208fdc6c87cfc
    secret: 你的32位AppSecret
    mock-enabled: false
    mock-openid: nest_dev_tenant
```

注意：

- **Spring Boot 不读 `backend/.env`**；`.env` 只给 Docker Compose 用
- `mock-enabled` 也可以写成 `${NEST_WECHAT_MOCK:false}`，然后在 IDEA Run Configuration 里设置 `NEST_WECHAT_MOCK=false`
- 改完必须重启 `NestApplication`

## 4. Docker Compose（demo profile）

编辑 `backend/.env`：

```text
NEST_WECHAT_APPID=wxcf8208fdc6c87cfc
NEST_WECHAT_SECRET=你的32位AppSecret
NEST_WECHAT_MOCK=false
```

`application-demo.yml` 使用 `${NEST_WECHAT_MOCK:true}`；`.env` 设为 `false` 后即走真实登录。

重建后端：

```bash
docker compose up -d --force-recreate backend
docker compose logs -f backend
```

## 5. 小程序端

1. 微信开发者工具导入 `miniapp/dist/build/mp-weixin`
2. 详情 → 本地设置 → 勾选“不校验合法域名、web-view、TLS 版本以及 HTTPS 证书”（仅本地）
3. 真机/正式环境必须在微信公众平台配置：
   - request 合法域名：`https://你的域名`
   - socket 合法域名：`wss://你的域名`
4. 清缓存 → 重新编译 → 点“微信一键登录”

本地开发时后端是 `http://localhost:8080`，只能在开发者工具里用；真机要用电脑局域网 IP（或 HTTPS 域名），并同步改 `miniapp/.env`：

```text
VITE_API_BASE_URL=http://192.168.1.100:8080
VITE_WS_BASE_URL=ws://192.168.1.100:8081
```

## 6. 验证是否真的走真实登录

后端日志出现：

```text
微信 code 换取 openid 成功: openid=xxxx****xxxx
```

说明成功。

如果看到：

```text
微信登录失败: errcode=40125, errmsg=invalid appsecret
```

说明 AppID/Secret 不匹配，继续检查第 1、2 步。

### 快速自检 Secret 是否有效

用一个明显无效的 `js_code` 请求：

```text
GET https://api.weixin.qq.com/sns/jscode2session
  ?appid=你的AppID
  &secret=你的AppSecret
  &js_code=diagnostic_invalid_code
  &grant_type=authorization_code
```

结果含义：

| 返回 | 含义 |
|------|------|
| `40029 invalid code` | AppID/Secret 已被微信接受，配置正确；真实 code 才会返回 openid |
| `40125 invalid appsecret` | Secret 不对/已重置/不是这个 AppID 的 |
| `40013 invalid appid` | AppID 不对 |
| `45011` | 频率限制，稍后再试 |

## 7. 常见错误

| errcode | 原因 | 处理 |
|---------|------|------|
| 40125 | AppSecret 不匹配/重置/复制错 | 重新生成，更新 dev 与 `.env`，重启 |
| 40029 | code 已使用/过期/伪造 | 小程序重新 `wx.login()`，不要复用 code |
| 40013 | AppID 不合法 | 三处 AppID 对齐 |
| 45011 | 调用太频繁 | 等待后重试 |
| 40226 | 账号风险 | 按微信提示处理 |
| `invalid appsecret` 但 Secret 看起来对 | 末尾空格/引号/换行；或复制了别的账号 | 重新复制，注意不要带空格 |
| 本地一直 40125 | IDEA 还在跑旧配置 | 重启后端；检查 `mock-enabled` 与运行环境变量 |

## 8. 安全

- `application-dev.yml`、`.env` 已在 `.gitignore`
- 不要把 AppSecret 提交到 Git、截图、聊天记录
- 如果 Secret 曾经泄露，立即在微信公众平台重置
- 生产使用 `prod` profile，`ProdGuard` 会强制关闭 mock

## 9. 没有 AppSecret 时

本地只想跑通预约/订单/钱包/聊天/RAG：

```yaml
mock-enabled: true
```

或 Docker `.env`：

```text
NEST_WECHAT_MOCK=true
```

mock 模式下 openid 固定为 `nest_dev_tenant` / `nest_demo_tenant`，不会请求微信。
