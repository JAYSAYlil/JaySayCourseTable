# v3.4.16 自定义配色修复与交付验证

日期：2026-09-07。版本：3.4.16 / versionCode 136。

## 修复

- 自定义预设色继续保存为稳定的调色板索引。
- 课程颜色映射新增 `Course.uniqueKey` 键，课表网格、月视图、课程详情均优先按唯一课程读取颜色。
- 同名课程不再互相覆盖自定义颜色；课程名键保留为兼容回退。

## 验证

- `:app:testDebugUnitTest --offline`：通过。
- `:app:lintRelease :app:assembleRelease --offline`：通过，0 个错误。
- APK 使用兼容升级密钥签名，v2/v3 验证通过。
- APK SHA-256：`344B0F62A26C68EC7AD53A5EEAF2A3D136CA0A7145C6C98FB7ED829A3815549E`。
