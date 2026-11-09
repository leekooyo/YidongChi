# After-close Incremental Pool Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans，当前会话逐任务实现并在末尾独立复核。

**Goal:** 复用已有扫描缓存，连续收盘日用批量报价增量更新明日池。
**Architecture:** 纯增量判断与更新编排、本地SQLite持久层、复用旧行情回补适配器；主页面保留最后完整结果并显示更新阶段。
**Tech Stack:** Kotlin、现有协程/JSON、Android内置SQLite；不新增运行依赖或服务器。
**Spec:** docs/superpowers/specs/2026-11-10-after-close-incremental-design.md

## Global Constraints

- 用户只在收盘后更新一次；不增加后台定时任务或盘中全市场轮询。
- 导入已有缓存；连续正常股票不重下整段历史；不拼接不一致的复权数据。
- 部分失败保留最后完整结果；本地计算仍覆盖全清单，不仅昨日候选。
- 不修改core既有异动公式；0.2.1 Release不可覆盖。目标0.3.0/code5。

## Review Focus

- 旧文件名日期不等于原始日线最后日期。
- 同日重入、重复同步不能重复插入；正常重复点击无全市场网络请求。
- 断更多日、IPO、停牌与参考前收不衔接须定向回补。
- 更新取消后，已存的批量报价和已计算记录可继续。
- 今日部分新数据不能替换昨日完整候选结果。

## Task 1: 纯增量判断与存储接口

Files: app/.../IncrementalHistory.kt、PoolHistoryStore.kt及IncrementalHistoryTest.kt。
Interfaces: HistorySeries(stock,provider,bars)；IncrementalHistory.plan(series,quote,date)返回Reuse/Append/Repair/Excluded；HistoryWrite(series,replace)描述事务写入。

- [ ] 先写连续日、同日替换、缺口、参考前收变化、停牌及资格测试并观察失败。
- [ ] 实现只追加必要日记录的判定，保持来源与元数据；通过后提交。

## Task 2: 本地库、导入与批量来源

Files: SQLitePoolHistory.kt、LegacyPoolImport.kt、PoolRepository.kt、AfterCloseSource.kt及对应JVM/安卓测试。
Interfaces: PoolHistoryStore提供history/write、quote staging、pending/begin/commit/finish和metadata；SQLite实现按证券/来源/日保存；旧缓存按最后可用来源导入。AfterCloseSource提供indexQuotes/universe/quotes/repair与requestCount，批量100只。

- [ ] 测试已有缓存迁移、数据日期、来源成对、SQLite事务及持久化、报价100只覆盖。
- [ ] 旧仓储暴露可取消请求与回补入口，回补可复用已获取的报价，不重复逐股报价。
- [ ] 运行相关测试并提交。

## Task 3: 更新编排与界面

Files: AfterCloseUpdater.kt、AppState.kt、ui/PoolScreen.kt及AfterCloseUpdaterTest/AfterCloseScreenTest。

- [ ] 假源先验证正常次日0个股历史请求、同日只探测指数、缺口回补、失败保留结果及取消恢复，观察失败。
- [ ] 实现导入→探测→证券清单→分批报价→定向补齐→本地重算；每100只事务保存结果。已缓存报价在恢复时不重取。
- [ ] 主按钮更新收盘数据，阶段/请求数/回补数可见；更多操作保留强制同步与历史重建。
- [ ] 完整回归构建和安卓界面，保留独立工作区功能；提交。

## Task 4: 验收与发布

- [ ] 使用既有完整扫描缓存做迁移与真实后续更新样例；记录请求数/回补量/耗时，不重复无必要冷扫描。
- [ ] 单次独立代码复核，修复重要问题，按需回归。
- [ ] 更新README、验收与0.3.0发布说明，交付APK/源码/校验，发布GitHub预发布版本。
