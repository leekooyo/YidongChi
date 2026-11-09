# 明日异动池 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** 全沪深 A 股下一交易日严重异动条件筛选，可继续扫描并与计算页一致。

**Architecture:** core 增加明日池纯计算；app 分离证券清单、共享行情缓存和扫描状态，UI 复用原生 Compose。首次扫描逐股取日线，按基准日期冻结指数并持久化进度。

**Tech Stack:** 现有 Kotlin/Compose/协程/JSONObject，无新增运行库或服务器。

**Spec:** `docs/superpowers/specs/2026-11-09-tomorrow-pool-design.md`

## Global Constraints

- 全部沪深 A 股，不用热门榜或自选替代；指数不变，10/30日上下行。
- 复用精确有理数及合法窗口引擎、公告校准，不增加无关基线核查。
- 开发目录 D:/Projects/YidongChi，D盘SDK/Gradle；保留C盘回退。
- 不擅自增加付费行情或服务器；完整和部分扫描明确区分。

## Review Focus

- 分页重复或中途空页：失败不能算覆盖全部。
- 滚动过期窗口：明日命中必须用目标交易日重算。
- 日期变化与恢复：不复用另一批次指数或成功状态。
- 后台取消：保留已完成项目、继续时不重复下载。
- 多指标或零变动命中：同股去重、正负方向及排序正确。

## Task 1: 纯计算与列表筛选

Files: `core/.../TomorrowPool.kt`, `core/.../TomorrowPoolTest.kt`。

Interfaces: `TomorrowPool.calculate(stock,bars,index,resets):PoolStock`，包含目标日、现价、命中PriceLevel；`PoolStock` 记录证券和10/30临界条件及所需比例。无资格项目返回原因；日期不足抛出有意义的异常给扫描器。

- [x] 先写上下行临界边界、滚动、节假日、重置和无资格测试，运行 :core:test 确认缺少功能失败。
- [x] 实现下一日 Projection.project，取严重指标 satisfied 或合法价格可达条件。
- [x] 正确计算正负所需变动；已满足用0。筛选/排序纯函数可测试。
- [x] 运行 core 全测试并提交。

## Task 2: 完整证券清单、缓存与扫描器

Files: `app/.../PoolRepository.kt`, `PoolScanner.kt`, `PoolCodec.kt`, `MarketRepository.kt`及相应测试。

Interfaces: UniversePage(total,stocks)分页解析；PoolRepository.universe()/indices()/load(stock,indices)，共享指数、行情请求及按日期日线缓存；PoolBatch 包含完整清单、基准和结果/失败/排除；PoolScanner.scan(batch,onProgress)，同批只处理待完成项，失败重试显式触发。JSONObject读写批次。

- [x] 先测试分页不足/去重和状态计数、序列化、恢复、失败重试；运行 app 单测确认缺少功能失败。
- [x] 实现完整分页清单与证券范围，验证实际总数；每次指数基准固定、日线缓存按日期来源共享。
- [x] 实现3并发扫描、取消/继续和定期原子保存；每股只计算不调事件接口。
- [x] 使用截图四股夹具确认池与计算页完全一致；运行单测并提交。

## Task 3: 安卓状态及明日池界面

Files: `AppState.kt`, `ui/App.kt`, `ui/WatchScreen.kt`, `ui/PoolScreen.kt`, `ui/CalculationScreen.kt`，`androidTest/.../PoolScreenTest.kt`。

Interfaces: AppState公开pool状态和startPool/retryPool/pausePool；持久化读取后台IO，更新Compose状态主线程。计算页增加pool打开时的次日/指数0指令。

- [x] 先写仪器测试，使用确定池夹具断言筛选、加自选、详情次日及暂停；运行证明确实缺入口失败。
- [x] 自选页增加二级切换，短说明、清晰边界；呈现进度、上次结果、筛选和未计算详情。
- [x] 生命周期后台暂停，前台不自动重复全量，恢复按钮继续；新版0.2.0/code3。
- [x] 运行所有单测、安卓测试和构建，提交。

## Task 4: 全量实测、复核与交付

Files: `docs/明日异动池验收.md`, README, APK/源码输出。

- [x] 实跑完整沪深清单并记录覆盖、失败、命中及耗时；通过扫描器实际数据路径验证共享缓存。
- [x] 全分支独立复核并修复重要问题，重跑对应测试。
- [x] D盘打包APK和源码，将交付副本放outputs；同步GitHub并用远端SHA核对。

执行采用当前会话直接实现，任务结束进行一次独立复核；用户已明确要求按批准方案开发，不再次询问是否开始。
