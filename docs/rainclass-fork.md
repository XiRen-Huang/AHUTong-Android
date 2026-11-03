# 雨课堂截止时间并入日历（自用薄 fork）

本分支只做只读的雨课堂截止时间同步：微信扫码登录、课程/章节拉取、截止时间进现有学习通日历、复用现有提醒、雨课堂任务详情。

## 不改的东西

- `Work`、`ReminderPlan`、`ReminderScheduler` 的算法零修改；`CalendarModel` 只新增雨课堂合并条，普通作业/自建日程仍走原来的跨度块。
- 不实现签到、PPT、作业提交、自动做题/代考。
- 不访问 `changjiang-exam.yuketang.cn`，不调用 `start_paper` / `show_paper` / `answer_problem` / `submit_paper`。
- 不新增 `.ahup` 插件扩展点；日历与提醒由宿主合并数据源。

## 新增模块

`:data:rainclass`，包名 `com.ahu.ahutong.data.rainclass`：

| 文件 | 职责 |
| --- | --- |
| `RainClassEndpoints.kt` | 主站 URL、请求头、节流参数；没有考试域地址 |
| `RainClassApi.kt` / `HttpRainClassApi.kt` | 课程列表、章节树、叶子详情（只读） |
| `RainClassQrLogin.kt` | 微信扫码 6 步状态机；关闭自动重定向，302 即成功 |
| `RainClassCookieJar.kt` | OkHttp CookieJar + `SecureStorage` 加密持久化 |
| `RainClassLocalStore.kt` | `ahutong_rc_prefs` 缓存课程/作业/上次同步 |
| `RainClassMapper.kt` | DTO → 领域模型、字段容错、去重排序 |
| `RainClassStatusMapper.kt` | `content_info.status` 的保守映射 |
| `RainClassLeafFilter.kt` | 排除课件/视频，只保留作业；规则见下方校准结果 |
| `RainClassRepository.kt` | 登录/同步/会话失效/状态补全 |
| `RainClassDebug.kt` | 只输出聚合数量，不输出标题、课程名、Cookie 或用户信息 |

宿主接缝：

- `settings.gradle.kts`、`app/build.gradle.kts`、`feature/xuexiaotong/build.gradle.kts`
- `feature/xuexiaotong/.../XuexiaotongScreen.kt`（收集 ViewModel、合并 `works`、详情分流、合并条渲染、右上角入口）
- `feature/xuexiaotong/.../RainClassToolbarAction.kt`（新增，雨课堂胶囊入口 + 底部弹层）
- `feature/xuexiaotong/.../CalendarModel.kt`（新增 `CalendarMergedBar`、分组与跨周标题逻辑）
- `data/chaoxing/.../Store.kt`（`cx_show_done` 默认改为 `false`：已完成任务默认不显示）
- `app/.../di/RainClassWiringModule.kt`（新增）
- `app/.../di/ChaoxingWiringModule.kt`（提醒存储改为合并实现）
- `app/.../ui/state/AppReminderStore.kt`（新增）
- `app/.../ui/state/DeviceDataReset.kt`（清除雨课堂会话）
- `app/src/test/.../ModuleBoundaryTest.kt`（R31）
- `background/src/test/.../ReminderPlanTest.kt`（仅测试）

## 首次真实同步后的校准

### `leaf_type`（2026-10-31 已用真实账号校准）

一次同步的分布：`leaf_type={7=18, 5=3, 8=21}`，共 42 个叶子，其中真正的作业 11 个。

| `leaf_type` | 含义 | 处理 |
| --- | --- | --- |
| `5` | 作业 | 保留 |
| `7` | 混合学习活动：计分作业和视频/课件都可能是 7 | 仅 `is_score=true` 保留 |
| `8` | 课件/PPT，`end_time` 是课程结束时间（如 2027-01-31） | 排除 |
| 其他 | 未见过 | 保留，避免漏掉新作业类型 |

课件会横跨整个学期，进日历后把每个月都铺满，因此必须排除；形态见 `RainClassLeafFilter.kt` 的注释。

### `content_info.status`

`leaf_type` 已校准；完成状态仍未知（详情接口返回的叶子多为 `完成状态未知`）。首次拿到可确认的字符串后补进 `RainClassStatusMapper`；未知状态按“未完成”参与 `onlyTodo` 提醒。

### 作业“已提交”状态（2026-10-31 实测：接口不提供）

章节树 `leaf` 里没有 `is_finished` / `finish_status` / `is_done` / `is_completed` / `submit_status` / `is_submitted` / `answer_status` / `finish_time` / `progress` 中的任何一个；`content_info.status` 的取值只有 `post` 或缺失，不是提交状态。只读接口拿不到“已提交”，所以：

- 详情弹窗提供「标记为已提交 / 撤销已提交标记」，状态只写本地 `rc_done_overrides`（`Map<id, Boolean>`），重新同步不会丢，也不回写服务端。
- 标记后 `done=true`，参与 `isDone` 判断：`查看已完成作业` 关闭（默认）时不出现在日历，也不会进提醒；打开后以淡色显示。
- 若将来找到可靠的提交状态接口，把结果写进 `RainClassStatusMapper` 即可，手动标记仍可覆盖。

## 日历合并条与雨课堂入口

- 雨课堂任务（`workId` 以 `rainclass:` 开头）不再按跨度各占一条泳道。`CalendarModel` 每周把互相重叠的雨课堂任务分成一组，整组只占一行 16dp，不随重叠数增高。
- 组内按天计算“当天活动任务集合”，集合相同的连续日期合并成一段（`MergedBarSegment`）。单任务段用任务自己的纯色；两个及以上任务重叠时，对颜色等权平均后再按重叠数混入中性深灰（越多越暗），重叠范围一眼可见。集合变化的日界画 1dp 半透明分隔线。
- 调色板 12 色按整月统一分配（`assignColors` 对当月全部雨课堂任务去重），同一任务跨周换行颜色不变，相邻的组也不会撞色。
- 标题按“起始列分槽”（`MergedBarTitle`）：每个任务只在本行第一次出现的起始列开始，槽的右边界是下一个任务起始列的前一格，或本任务结束列；同一起始列的多个任务合并成一个标题（最多两个名字，其余用 ` +N`），放不下时退化为 `名字 +N`。
- 跨周任务只在第一行显示标题，续行不重复；只有被自身范围末端截断的长标题才把剩余文字顺延到下一行，被后面任务起始列截断的部分直接截断。普通学习通作业的跨度块仍走原来的顺延逻辑。
- 已完成/已提交的任务默认不显示（`查看已完成作业` 默认关闭）；打开该开关后，已完成任务的颜色替换为灰色并降低透明度参与叠加。
- 点击合并条的单任务段直接打开该任务的雨课堂详情；点击多任务重叠段弹出当天任务列表，再选具体任务进入详情。
- 普通学习通作业和自建日程仍然走原来的跨度块和泳道，不受影响；两类在同周重叠时雨课堂组按矩形避让排到普通块下方或旁边的空位。
- 雨课堂状态不再占用日历上方的整块卡片：日历页右上角操作胶囊里新增学士帽图标，图标角标显示任务数。点开底部弹层可看到登录状态、上次同步、进度/错误，以及“同步”“退出”；未登录时是“登录雨课堂”入口，二维码弹窗和退出确认也在这个弹层里。

## 数据与安全

- 只使用主站 `changjiang.yuketang.cn` 的只读接口。
- Cookie 经 `SecureStorage`（AES-GCM + Android Keystore）保存，键 `rainclass_cookies_v1`。
- 会话 Cookie 也持久化，进程重启后无需重新扫码；服务端仍可能让会话失效，失效时提示重新登录。
- 扩展存储按插件隔离的机制不参与本方案；雨课堂数据只属于本 App。
- 日志只记录聚合数量。

## 构建与验证

当前工作区没有 Java/Gradle/adb，以下命令需在装有 Android 工具链的开发机执行：

```bash
cd /home/xiren/Projects/AHUTong-Android
./gradlew --write-locks
./gradlew :data:rainclass:testDebugUnitTest
./gradlew :feature:xuexiaotong:testDebugUnitTest
./gradlew :background:testDebugUnitTest
./gradlew :app:testDebugUnitTest --tests 'com.ahu.ahutong.architecture.ModuleBoundaryTest'
./gradlew :app:assembleDebug
```

首次加入新模块后必须提交生成的 `data/rainclass/gradle.lockfile` 以及受影响模块的锁文件。

设备验收：

1. 登录雨课堂 → 微信扫码 → 自动同步。
2. 日历出现雨课堂合并条（每组一行 16dp，单任务段纯色、重叠段叠加色，标题从各自起始列开始）；点单任务段直接进对应任务详情，点多任务段弹出当天任务列表。右上角学士帽角标显示任务数。
3. 打开现有提醒设置，确认雨课堂任务参与排期；退出/过期后旧提醒取消。
4. 杀进程重开仍保持登录；设置页“清除数据”后 Cookie、缓存、提醒全部清空。
5. 确认没有考试域请求：

```bash
grep -rn "changjiang-exam\|submit_paper\|start_paper\|show_paper\|answer_problem" \
  data/rainclass feature/xuexiaotong app/src/main/java || true
```

## 上游更新

雨课堂协议、登录、存储、解析都在 `data/rainclass/`，上游更新一般无需改它。合入上游后只需处理上面的接缝文件；`data/rainclass/` 通常无冲突。rebase 后重跑数据模块、feature、background 与边界测试。
