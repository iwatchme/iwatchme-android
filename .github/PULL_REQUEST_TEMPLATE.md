<!--
PR 模板。删掉无关章节即可。

> 改 `player/` 模块的人请重点看 §3 / §4——架构改动有硬性同步要求。
> 不改 player 的可以删掉那两节。
-->

## 1. 这个 PR 干什么

<!-- 一两句话说清楚做了什么、为什么。不要罗列每一行改动。 -->



## 2. 改动类型（勾选；可多选）

- [ ] **bug 修复**（行为不对）
- [ ] **新功能**（用户能感知的能力）
- [ ] **重构**（行为不变，结构更清楚）
- [ ] **架构变更** ← 改了 Scope / Service / Driver / Repository 拓扑或通信路径
- [ ] **文档**（仅 `*.md`）
- [ ] **构建 / CI**（gradle、workflow、依赖）
- [ ] **其它**：_______

---

## 3. Player 模块自检（仅当你改了 `player/` 才填）

> 规则源 → [`player/CLAUDE.md`](../player/CLAUDE.md)
> 现状源 → [`player/PROJECT_MAP.md`](../player/PROJECT_MAP.md)

### 3.1 是否触发"必须同步更新 PROJECT_MAP.md"

下面任意一项命中就**必须**改 PROJECT_MAP.md（不命中可跳过）：

- [ ] 加 / 删 / 改 `@Scope` 注解、Subcomponent
- [ ] 加 / 删 `@PageScope` / `@BizScope` / `@EpisodeScope` / `@MediaScope` 类（Service / Driver / Repository / Bootstrap）
- [ ] 改通信路径（谁调谁、谁订谁的 flow）
- [ ] 改 `@Binds` / `@IntoMap` 多绑定的解析器集合（共享 / 私有 parser）
- [ ] 引入新的 sealed 业务子类（如 `XxxDetail`）

### 3.2 反模式自检（CLAUDE.md §9）

- [ ] Fragment 里**没有** `repo.flow.collect { ... }` 或业务判断
- [ ] Repo 的 `init` 里**没有** `combine` / `collect` 别的 repo 的 flow
- [ ] Repo 是**无状态 IO**（如需状态机，state 已搬到 Driver）
- [ ] 离散事件用 `SharedFlow(replay=0)`，不是 StateFlow
- [ ] 没有 `trySend(player.xxxState)` / `trySend(currentValue)` 这种主动读热属性当事件发射的代码
- [ ] 没有 `if (detail.businessType == UGC) ... else ...`，业务分支走 sealed `when` 或 `@Binds` 多实现
- [ ] 新增"init 里订阅 flow"的 service 已挂进对应 `XxxBootstrap` 构造参数（否则 Dagger 不实例化）
- [ ] 没有 `GlobalScope` / `MainScope()`；协程都从 `@XxxCoroutineScope` 注入

### 3.3 PROJECT_MAP.md 同步内容

如果 §3.1 命中，下面也要勾：

- [ ] 在 PROJECT_MAP.md 对应章节（§2~§5 / §6 / §7）改了清单 / 通信路径 / 文件速查
- [ ] 没有引入新的行号引用（PROJECT_MAP.md 一律不写行号）

### 3.4 CLAUDE.md 是否需要更新

- [ ] 这个 PR 暴露了一个**新的反模式 / 踩坑** → 已在 CLAUDE.md §9 加了一条
- [ ] 引入了**新的硬性约束**（比如新的命名规约、新的禁止用法）→ 已在 CLAUDE.md 对应章节加了规则
- [ ] 都不涉及，CLAUDE.md 不需要改

---

## 4. 验证（自测怎么验过的）

<!--
具体步骤。"验证通过"这类话不算数。
例：
- 启动 demo，点击合集标题切到 OGV 合集，确认 BizInfo 卡片样式从粉色变蓝
- 让一集自然播完，确认自动跳下一集且日志里只看到一次 [EpisodeCompletedService] auto-next
- 横屏旋转 → 屏幕方向变化，列表正确隐藏
-->



## 5. 风险 / 已知问题

<!-- 可能出问题的地方、限制条件、未覆盖到的场景。没有就写"无"。 -->



## 6. 关联

<!-- Linear / Jira / issue 链接、相关 PR 链接。可选。 -->
