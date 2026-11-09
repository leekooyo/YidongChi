# Independent Workspaces Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans，当前会话直接执行，最后一次独立复核。

**Goal:** 解开三页共享选股状态，并支持预警全自选。
**Architecture:** StockWorkspaces持有三个页面状态，AppState共享股票数据；输入使用可观察且持久化的字段，事件状态按代码保存。
**Tech Stack:** 现有Kotlin、Compose、协程、SharedPreferences；无新增库。
**Spec:** docs/superpowers/specs/2026-11-10-independent-workspaces-design.md

## Global Constraints

- 不更改既有异动算法和已发布0.2.0附件。
- 页面选择与输入分离，证券数据及公告校准共用。
- 只验证有关功能，不新增无关安全基线或重扫全市场。

## Review Focus

- 异步搜索晚到，不得更新另一页或覆盖新查询。
- 旧selected迁移后必须真正独立，并在重启恢复。
- 补录事件不能仍使用计算页代码。
- 全自选局部失败不得用其他股票快照顶替。
- 明日池深链不得修改推演/预警的股票与输入。

## Task 1: 工作区状态

Files: app/.../StockWorkspaces.kt；app/src/test/.../StockWorkspacesTest.kt。
Interfaces: StockWorkspaces.calculation/forecast/warnings；StockWorkspace.select/query/acceptResults/text/flag；每个工作区使用独立存储键。

- [ ] 写迁移、独立选择、历史搜索、晚到结果、输入持久化和预警范围测试；执行确认失败。
- [ ] 实现最小可观察状态及持久化字段，重跑测试并提交。

## Task 2: 页面及数据请求解耦

Files: AppState.kt、ui/App.kt、CalculationScreen.kt、ForecastScreen.kt、EventsRulesScreens.kt、ForecastModeTest.kt、新的WorkspaceScreenTest.kt。
Interfaces: select/search增加目标工作区；refresh覆盖三页和自选；loadEvents/addEvent显式股票代码；Events按股票索引状态。

- [ ] 写安卓三股切换、预警全自选及事件归属回归，先验证旧版失败。
- [ ] 三页各自搜索框；计算/推演输入绑定各自状态；预警当前/全部自选LazyColumn和后台复算；修正深链及历史清理。
- [ ] 运行单测、安卓功能回归和构建，提交。

## Task 3: 独立复核与交付

- [ ] 独立复核并修复重要问题，按需回归。
- [ ] 版本0.2.1/code4；更新README、发布说明和验证证据，交付APK/源码/校验文件。
- [ ] 同步GitHub并发布新测试版，保留0.2.0不变。
