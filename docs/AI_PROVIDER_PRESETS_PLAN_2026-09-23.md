# AI 供应商预设与连接检测实施计划

## 目标

在现有“AI 转换学校课表”页面，用户可直接选供应商、填 API Key 并测试连接，再用原流程转换课表。预设至少包括 OpenAI、DeepSeek、智谱 GLM、阿里云百炼/千问；保留“自定义兼容接口”，供其他供应商、区域或特殊套餐使用。预设自带完整 Chat Completions HTTPS URL 和默认模型，通常无需用户手填链接与模型；高级设置可改模型，必要时切到自定义接口。供应商、模型及自定义 URL 可保存；API Key 仅在用户明确选择保存时使用 Android Keystore 加密保存在本机，并提供删除已保存 Key 的操作。

## 预设依据（2026-09-23 查阅官方文档）

| 供应商 | 完整请求 URL | 默认模型 | 依据 |
|---|---|---|---|
| OpenAI | `https://api.openai.com/v1/chat/completions` | `gpt-4.1-mini` | [Chat Completions](https://platform.openai.com/docs/api-reference/chat/create)、[模型端点支持](https://platform.openai.com/docs/models/default-usage-policies-by-endpoint) |
| DeepSeek | `https://api.deepseek.com/chat/completions` | `deepseek-flash` | [接入指南](https://api-docs.deepseek.com/quick_start/pricing/)、[对话补全](https://api-docs.deepseek.com/api/create-chat-completion/) |
| 智谱 GLM | `https://open.bigmodel.cn/api/paas/v4/chat/completions` | `glm-4.5` | [官方兼容 API 示例](https://docs.bigmodel.cn/cn/best-practice/case/ai-search-engine)、[模型文档](https://docs.bigmodel.cn/api-reference/%E5%8A%A9%E7%90%86-api/%E5%8A%A9%E6%89%8B%E5%AF%B9%E8%AF%9D) |
| 阿里云百炼/千问 | `https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions` | `qwen-plus` | [官方兼容 API 示例](https://help.aliyun.com/zh/model-studio/model-calling-in-sub-workspace) |

这些是普通标准 API 路径和参考模型，不保证所有账号都有调用权限；地区、子业务空间与套餐可能需要不同 URL 或模型。预设不在后台自动探测，也不偷偷请求真实课表。

## 实施要求（GPT-6 Luna）

1. 将供应商预设定义为稳定 ID + 显示名称资源 ID + URL + 默认模型的数据对象，避免 UI 硬编码到处散落。选择预设自动填充 URL、默认模型，隐藏常规 URL 输入；在高级设置中允许修改模型；自定义选项显示 URL 与模型输入。切换供应商时分别保留其本次编辑值，不混用 Key。
2. 保存供应商配置：选中的预设、每个预设的模型（及自定义 URL/模型）持久化。API Key 的保存必须由用户明确勾选或点击；使用 Android Keystore AES-GCM 在应用私有、非备份目录加密存储，绝不以明文写入 `preferences.json`、日志、备份、Bundle 或构建常量。支持读取后自动填入当前供应商并可删除；密钥失效或文件损坏时安全清空并提示重新填写。不能改变现有 `tables.json` / `preferences.json` 的 schemaVersion 4、包名与签名。
3. “检测连接”应使用当前 URL、模型和 Key 对 **Chat Completions** 发一个不包含课表、不含用户文件内容的最小请求，只有点击时才联网。成功须同时验证 HTTP 成功、有效兼容响应和非空内容，状态提示“连接成功”；401/403、404/模型不存在、429、超时/网络异常、响应格式错误等给不同的非敏感失败提示。明确说明检测可能消耗少量模型额度。检测中可取消，任何字段/供应商修改都使旧成功状态失效；检测成功不应成为转换的强制门槛。
4. 转换仍沿用原有隐私确认、输入限额、JSON 严格校验、Excel 模板回读与导入确认。无 Key 时不能请求；测试连接不传课表。URL 验证、禁止重定向/私网、响应大小上限和超时继续生效。保存/删除配置的用户可见状态明确。
5. 测试：预设 ID 与 URL/模型映射、配置切换隔离、持久化读写与加密存储（密钥明文不得出现在文件）、删除、连接检测成功及典型 HTTP/响应失败分类、UI 选择/保存/状态失效。无需真实用户 Key；全量 JVM/API34、Release Lint/构建、原签名升级验证。版本从 3.4.34 / 154 推进到 3.4.35 / 155，文档同步更新，本地签名交付，不自动发布 GitHub。

## 用户边界

“连接成功”只表示当前 Key、URL 与模型完成了一次最小兼容请求，不能证明其课表识别正确；识别结果仍由用户在导入确认页核对。已保存 Key 只在本设备可用，不进入应用备份或换机数据。任何服务商用量和费用由服务商账号决定。
