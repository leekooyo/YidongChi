# 异动尺 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** 构建自动获取沪深行情的原生安卓异动价格计算器，提供自选、近期事件和未来十交易日情景推演。

**Architecture:** 安卓 Compose 客户端直接读取公开行情，本地保存自选、设置和快照。独立纯 Kotlin core 模块处理精确价格、滚动窗口、情景和事件，规则原文目录随包分发。

**Tech Stack:** Kotlin 2.1.20、AGP 8.9.2、Gradle 8.11.1、JDK17、Android API35/min26、Compose BOM2025.04.01、JUnit4。固定已发布且互相兼容的版本，避免为追新引入迁移。

**Spec:** ../specs/2026-11-09-design.md；用户2026-11-09明确批准并授权开发，额外要求减少过度防御和无关安全基线核查。

## Global Constraints

- 原生安卓 Kotlin，中文界面，免费数据，无账号，无自建服务器。
- 使用 BigDecimal 比较与反解，显示取整不得改变触发判断。
- 正式事件和情景触发隔离；盘中结果不是正式公告。
- 前台刷新，退出前台停止，不承诺后台持续提醒。
- 只在数据边界做必要检查；不加通用策略框架、反复兜底、无关安全扫描。
- B股/历史规则及未证实口径不伪称完整支持；规则目录必须解释范围。

## Review Focus

- 源端时间为过去交易日：离线/休市状态不能以本机时间伪装最新。
- 股票和指数交易日错位：按日期相交，缺少基期不得造价。
- 下一日/未来路径：目标日昨收必须递进，历史极值移出后阈值应变化。
- 重叠窗口/公告：不得将多个命中窗口当成多次官方异动。
- 缺少除权或正式重置资料：保留条件估算，不能显示监管状态已核实。

## 文件职责与接口

- `core/.../Models.kt`：证券、日线、规则、窗口、事件、情景数据类型。
- `core/.../PriceEngine.kt`：`analyze(stock: Stock, bars: List<Bar>, index: List<Bar>, targetDate: LocalDate, indexPrice: BigDecimal, resets: ResetDates): Analysis`，用实际价格单位反解所有窗口。
- `core/.../Projection.kt`：`project(..., scenario: Scenario): List<ProjectionDay>`，未来十交易日情景；`recentEvents(events, calendar, asOf)`。
- `core/.../Watchlist.kt`：搜索代码识别、分组去重、预警去重。
- `app/.../MarketRepository.kt`：腾讯/Eastmoney读取与字段解析、日期对齐、缓存，不伪造官方事件。
- `app/.../LocalStore.kt`：自选/设置/快照、JSON备份。
- `app/.../AppState.kt`：前台刷新、计算状态、事件信息。
- `app/.../MainActivity.kt`、`ui/*.kt`：自选、计算、推演、事件、规则、设置；系统文件选择器导入导出。
- `app/src/main/assets/rules.json`：研究者收集的官方规则索引。

## Task 1: 可复算的精确计算核心

- [x] 建立 core JVM 模块与安卓工程构建文件，先写 `PriceEngineTest`。
- [x] RED：`./gradlew :core:test`，因尚未实现领域接口失败。
- [x] 实现区间偏离、最小价格单位向上/下取整、正向复核、3/10/30日所有合法窗口、独立重置与不可达状态。
- [x] GREEN：`./gradlew :core:test`。字面样本：P0=10、I0=100、I=101、阈值20% → 12.10；12.09不满足；负向 -20% → 8.10；B股用其独立tick。
- [x] 覆盖指数日期缺失、1/2日触发、涨跌停取整、除权质量标识、停牌语义。
- [x] 保存结果与提交。

## Task 2: 未来十日、事件与自选行为

- [x] 先写 `ProjectionTest`、`WatchlistTest`。RED：任务1的实现不存在未来情景和去重逻辑。
- [x] 实现固定/逐日/涨跌停路径、目标日递进、交易日历、近期10交易日事件、未来重置假设标注。
- [x] 自选按exchange+code合并、排序和分组；预警按证券/规则/方向/目标日/窗口/情景合并。
- [x] GREEN：`:core:test`，校验路径10→11→12.10、窗口基期移出、节假日排除和重复窗口不生成多次官方事件。
- [x] 保存结果与提交。

## Task 3: 行情和本地数据

- [x] 采集真实响应测试样本，先写解析测试，RED验证字段与日期的错误映射。
- [x] 接入腾讯行情、未复权日线及基准指数；Eastmoney仅在整个时间序列口径一致时备用；用网络边界统一超时和一次错误提示，不叠加重试层。
- [x] 用真实除权参考数据或声明条件估算；正式事件支持读取官方来源与人工按原公告校准，来源不足不伪造。
- [x] LocalStore保存自选、情景、主题、预警状态和历史输入快照；schemaVersion=1备份合并和恢复副本。
- [x] GREEN解析/备份测试及开发机真实请求，记录来源时间。
- [x] 保存结果与提交。

## Task 4: 安卓交互与体验

- [x] Compose实现上述功能：自选分组/置顶/排序，搜索/详情/复制，历史事件与来源校准，未来十日及双情景比较，历史回放，规则全文链接和过滤，设置/帮助/主题/大字/备份。
- [x] 从领域模型消费结果，页面不重复实现公式。
- [x] 使用生命周期前台15秒刷新与应用内提醒；缓存和未核实状态同时可见。
- [x] 运行 `:app:assembleDebug :app:testDebugUnitTest :core:test`，修复实际编译/测试错误。
- [x] 可用设备上安装、启动、搜索、联网计算、自选、推演、断网及恢复，记录截图和日志。
- [x] 保存结果与提交。

## Task 5: 规则覆盖、独立审查与交付

- [ ] 已合并官方规则与真实公告事件；官方精确数值案例复算仍未完整完成，详见验收报告。
- [x] 一次独立代码审查，限定金融正确性、功能和实际故障，不做无关安全基线。
- [x] 重要问题以复现测试修正，运行相关测试及完整构建。
- [x] 交付APK、源码ZIP、README、规则目录与验收报告，准确区分编译、模拟器和真机验证。

## 执行裁定

用户已明确“按照这版开发”，按授权直接执行本计划，不重复请求计划确认。当前为空目录新项目，在 outputs/YidongChi 初始化独立 develop 仓库，不创建多余工作树，也不存在需要跑的旧基线测试。技能的 Bash 辅助脚本在 Windows 以等价账本/命令记录替代，保留每个任务的失败/通过证据。

## 交付状态

2026-11-09：可安装测试版已构建，33项单测和4项安卓仪器测试通过。正式事件自动覆盖、科创累计解释、历史停牌/除权/版本边界及官方精确数值案例仍是明确研究缺口，不据此声称全量监管结果复现完成。
