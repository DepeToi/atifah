# 事件契约：<event-name>

> 复制为 `docs/contracts/events/<event-name>.md` 后填写。
> Topic / 消费组常量分别登记在 `MqTopics` / `MqConsumerGroups`，本文件与常量必须一一对应。
> 事件契约冻结后由 Claude 维护，CodeBuddy / Codex 只读。

## 1. 元数据

| 项 | 值 |
| --- | --- |
| 事件名 | `<event-name>`（kebab-case） |
| Topic | `MqTopics.<CONSTANT>`（实际字符串：`<topic>`） |
| 生产者 | `<service>` |
| 消费者 | `<service-a>`, `<service-b>` |
| 消息 key | `<userId / postId / ...>`，保证同一业务实体有序 |
| 顺序要求 | 同一 key 有序；跨 key 不保证 |
| 幂等键 | `msgId` + `<业务唯一键>` |
| 重试策略 | 16 次指数退避 → 死信队列 → 告警 |
| 契约版本 | `0.1.0` |
| 状态 | `draft` / `frozen` |

## 2. 触发时机

描述在什么业务动作成功后发送；是否事务消息；是否允许重复。

## 3. Payload

```json
{
  "msgId": "01J...",
  "eventType": "<event-name>",
  "occurredAt": "2026-10-19 10:00:00",
  "userId": "1234567890123456789",
  "payload": {}
}
```

## 4. 字段说明

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| msgId | string | 是 | 全局唯一消息 ID，消费端幂等键之一 |
| eventType | string | 是 | 事件名，与本文标题一致 |
| occurredAt | string | 是 | 事件发生时间，`yyyy-MM-dd HH:mm:ss`，Asia/Shanghai |
| userId | string | 是 | 业务 key / 分片键 |
| payload | object | 是 | 业务负载，禁止包含敏感明文 |

## 5. 兼容性

- 新增可选字段 = 向后兼容，可保持版本不变。
- 删除/重命名字段、改变语义、收紧必填 = 破坏性变更，必须升级事件版本（新事件名或 `version` 字段）。
- 消费端必须忽略未知字段，不得因新增字段反序列化失败。

## 6. 消费端检查清单

- [ ] 使用 `msgId` + 业务唯一键做幂等，重复消费无副作用。
- [ ] 有状态机或业务前置校验，避免乱序导致错误状态。
- [ ] 异常不吞；重试耗尽后进入死信并告警。
- [ ] 消费者组已登记 `MqConsumerGroups`。
- [ ] 不依赖跨 key 的到达顺序。
- [ ] 日志不打印敏感 payload。
