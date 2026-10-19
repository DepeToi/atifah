# 业务域与服务边界

> 读这份文档的时机：新增业务功能、判断代码该放哪个服务、设计跨服务调用或 MQ 事件。
> 总体架构、核心链路与 Topic 规划见 [`architecture.md`](architecture.md)；表结构见 [`db.md`](db.md)；接口规则见 [`contracts/api-conventions.md`](contracts/api-conventions.md)。

## 1. C 端功能域

| 域 | 功能 |
| --- | --- |
| 账号域 | 手机号/三方登录、Token、设备管理、隐私设置 |
| 情绪域 | 心情打卡、情绪日记、标签、语音/图片附件、日历视图 |
| 分析域 | 情绪趋势、连续打卡、周报/月报、心理量表 |
| 社区域 | 树洞/动态、匿名发布、评论、点赞、关注、举报、Feed |
| 内容域 | 冥想音频、文章、课程、每日推荐、播放进度 |
| 消息域 | 站内信、WebSocket 实时消息、App 推送、打卡提醒 |
| 增长域 | 签到、任务、积分、成就、活动 |
| 运营域 | 管理后台 RBAC、内容审核、运营配置、统计报表 |

## 2. 服务归属

| 模块 | 服务名 | 职责 | 数据归属 |
| --- | --- | --- | --- |
| `atifah-gateway` | `atifah-gateway` | 路由、鉴权、限流、灰度、TraceId | 无 |
| `auth-service` | `atifah-auth` | 登录注册、验证码、三方登录、Token 签发/注销 | `atifah_auth` |
| `user-service` | `atifah-user` | 用户资料、隐私设置、黑名单 | `atifah_user` |
| `mood-service` | `atifah-mood` | 打卡、日记、标签、附件、日历查询 | `atifah_mood` |
| `analysis-service` | `atifah-analysis` | 趋势统计、连续打卡、量表、周报月报、AI 摘要 | `atifah_analysis` |
| `community-service` | `atifah-community` | 树洞、动态、评论、点赞、关注、举报、Feed | `atifah_community` |
| `content-service` | `atifah-content` | 音频/文章/课程、推荐、播放进度 | `atifah_content` |
| `message-service` | `atifah-message` | 站内信、WebSocket、推送、提醒 | `atifah_message` |
| `growth-service` | `atifah-growth` | 签到、任务、积分、成就 | `atifah_growth` |
| `stats-service` | `atifah-stats` | 埋点消费、运营聚合报表 | `atifah_stats` |
| `admin-service` | `atifah-admin` | 管理后台 BFF、RBAC、审核工作台、运营配置 | `atifah_admin` |

## 3. 领域边界规则

- 数据库边界与跨服务调用方式的硬约束见 [`rules/database.md`](rules/database.md) DB-01；本文件只补充领域归属判断。
- 匿名身份映射只存在社区/账号域，反查需专项授权并留审计痕迹。
- 情绪日记、量表结果属于情绪/分析域；其他服务只保存必要的 ID 或聚合结果，不复制正文。
- 统计、积分、通知、审核、报告生成一律异步；主链路只做核心落库。
- 初期可合并部署（`auth` 并入 `user`、`growth` 并入 `user`），但代码边界与库边界保持不变。

## 4. 核心链路

| 链路 | 主服务 | 异步派生 |
| --- | --- | --- |
| 心情打卡 | `mood-service` 落库 + Redis 当日状态 | `mood-recorded` → stats / analysis / growth / message |
| 情绪分析与报告 | `analysis-service` 预计算日统计 | 周报/月报由定时任务生成，完成后发通知 |
| 社区 Feed | `community-service` 发布（先审后发） | `post-published` → 审核 / 粉丝扩散 / 统计 |
| 消息与实时通信 | `message-service` WebSocket + 站内信 | `notification` 统一出口，延时消息做提醒 |

> 时序图、缓存与一致性细节见 [`architecture.md`](architecture.md) §4–§5。

## 5. 新增服务的判断

- 先问：是否只是现有域的一个功能？是则加到现有服务，不新建服务。
- 只有同时满足「独立数据归属 + 独立伸缩需求 + 清晰边界」才拆新服务。
- 新服务必须：分配端口、登记路由、建独立库、加入 `atifah-services/pom.xml`、补 `docs/contracts/openapi/<service>.yaml` 与端口表。
