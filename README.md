# NomiPetExtras

NomiPetExtras 1.1.0 负责 MyPet 4.0.4 的本人当前宠物基础路线选择与执行校验。使用原生路线选择 GUI 和 `setSkilltree(target, PLAYER_COMMAND)`，由 MyPet 的技能事件按当前等级重新计算技能，保留等级和经验。

依赖 MyPet 4.0.4、TrMenu，以及提供 Paper 实体调度与 `Server.isOwnedByCurrentRegion(Entity)` 的服务端。构建目标为 Purpur 26.3 build 2654 stable，Java 21 字节码。本插件不依赖 Vault，没有价格配置、支付、退款、交易日志或自有确认菜单。

`/nomipetreset` 只打开本人当前宠物的原生路线选择。要求 `nomipetextras.reset` 和 `MyPet.command.chooseskilltree`，无免费执行重置的命令。保留原生 `skilltrees` section 的选择逻辑。在 MyPet 主菜单的 `PetMenuContext` 中保留槽类型 section `nuomi-reset`；本插件接管该按钮，并显示“费用见确认菜单”。MenuReturnBridge 应继续忽略此按钮；本仓库不修改 Bridge 或 VIP。

选择后，原生 MyPet GUI 先关闭，本插件在下一 tick 通过 TrMenu 公共 API 打开 `PetResetConfirm`。适配 `me.arasple.mc.trmenu` 与 `trplugins.menu` 两命名空间的 `TrMenuAPI.INSTANCE.getMenuById` 及 `open(Player, Integer, MenuOpenEvent.Reason, Consumer)`。API 不兼容、菜单缺失、打开取消或出现其他菜单时清理选择；不回退执行命令。已只读核对真实 TrMenu 3.12.5 JAR 的签名和字节码打开顺序；完整运行时与实际网络窗口仍需服务端验收。

TrMenu 脚本从 Bukkit 插件管理器取得名称为 `NomiPetExtras` 的实例，在玩家实体线程同步调用以下公开实例方法：

```java
public boolean prepare(org.bukkit.entity.Player p)
public String apply(org.bukkit.entity.Player p)
public void cancel(org.bukkit.entity.Player p)
public String getTargetName(org.bukkit.entity.Player p)
```

`prepare` 复核当前选择、本人当前宠物、原路线、权限、目标注册身份、基础路线等级/类型/继承限制（原生条件回调后再次复核）、原生条件，以及宠物背包未打开且全部持久槽为空。原生 NoSkilltree 条件使用只屏蔽当前路线的只读 Pet 视图。成功只建立一次执行准备，不扣款、不修改宠物；重复准备返回 false。

`apply` 消耗一次准备并重新校验，严格返回：

| 返回值 | 含义 | 菜单处理 |
| --- | --- | --- |
| `SUCCESS` | MyPet 返回成功，且观察到目标路线和原等级经验 | 显示完成 |
| `REJECTED` | 确定没有调用 MyPet 修改接口 | 若菜单已明确扣款，可由脚本退款 |
| `UNCERTAIN` | 已调用 MyPet，返回 false、异常或后置条件不能证明成功 | 不自动退款或回滚，由维护者核查 |

所有价格、余额判断、扣款及明确未应用时的退款均由 TrMenu 脚本负责。建议单次点击同步执行：`prepare(p)` 成功 → 菜单按自己的价格扣款 → 扣款明确成功后 `apply(p)` → 按上述返回值处理。扣款失败或取消调用 `cancel(p)`。付款结果不明时不要调用 apply 或盲退款。脚本不得异步、延迟或重复付款，不应在 apply 前关闭或替换确认菜单；插件无法替菜单保证经济操作只执行一次。插件不提供经济脚本实现或价格来源。

`getTargetName` 只读返回当前有效确认目标的展示名，无有效目标时返回空字符串。目标名称在 TrMenu 渲染打开期间也可只读展示；确认打开完成后才可准备。不要在 TrMenu 的预打开脚本中执行 prepare/付款。脚本回调仅针对当前可见会话同步调用，不能缓存 Player 后为旧会话延迟调用这些接口。`cancel` 清理该玩家当前选择。错实体线程的 prepare/apply 分别返回 false/REJECTED，cancel 无副作用，名称返回空字符串。

每位玩家至多一个短期选择，60 秒后不可用，并在 1200 tick 超时回调中清理。取消、换菜单、退出及新的选择会废弃旧状态；合法的原生选择关闭与生存/创造默认背包的一 tick 衔接被单独允许。原生选择绑定 Bukkit 库存身份；TrMenu 确认通过 `Menu.open` 的 `Consumer<MenuSession>` 绑定真实 session id/menu/viewer，再核对本次 receptacle 与 `getViewingReceptacle`。默认 packet 模式无需 `InventoryOpenEvent`，static 模式也使用同一会话检查；不修改 TrMenu 全局设置。自然 packet 关闭走 receptacle `onClose`（未必触发 `MenuCloseEvent`），适配器在该回调中先废弃本次请求，再保留 TrMenu 原生清理；旧窗口回调只清理其捕获的 flow，不能关闭后来复用同一 session 的请求。旧选择的延迟回调和完成清理不能删除后来建立的选择。prepare/apply 之间换宠物、原路线、权限或背包变化都会拒绝。进入 MyPet 前移除选择，防止同步事件重入重复执行。

从 1.0.0 迁移时，由安装维护者移除旧 `reset-cost` 配置和 `nuomi-reset-confirm` 资源覆盖，创建 TrMenu `PetResetConfirm` 菜单及其付款脚本。不要再注册本插件旧 MyPet 确认菜单，也不要让两个插件接管 `nuomi-reset`。安装变更通过正常服务端生命周期完成。

使用 Python 3 和支持 `--release 21` 的 JDK，以外部、未修改的依赖目录离线构建：

```sh
python3 build.py --deps-dir /path/to/dependencies
```

目录须包含上述精确 Purpur API、MyPet 4.0.4 及 Adventure、Guava、注解等 API 依赖。完整测试另需私有 Gson 依赖目录，传入 `--test-deps-dir <private-test-jars>`，该目录仅加入测试 classpath。构建执行公开接口、原生路线、应用边界、选择衔接、隐藏持久槽、安全门、真实 Paper 错线程判断及桥接重入回归，输出 `build/NomiPetExtras-1.1.0.jar` 和 SHA-256。第三方 JAR、测试类及私有输入不会打入产物。

测试使用真实 MyPet/Paper 类型与故障适配器，真实 `BackpackImpl.applyState/getState` 覆盖全部持久槽；Material 空气判定由离线注册表适配。TrMenu 签名和调用顺序核对真实 3.12.5 JAR，可传入 `--trmenu-jar <TrMenu-3.12.5.jar>` 复核真实签名及字节码顺序，packet/static 行为由独立离线会话模型回归；没有运行游戏服务端或实际玩家联测。尚未验证运行服 TrMenu 的网络窗口与付款脚本、Folia 多玩家体验、数据库序列化和 MyPet 技能监听器在运行服上的完整效果。`SUCCESS` 只证明可观察的路线与等级经验条件，不能独立证明第三方监听器全部完成。Gson 只用于私有测试 classpath，第三方依赖不随产物分发。

本项目未包含第三方源码或二进制。MyPet、Purpur、TrMenu 及其他依赖的版权与许可归各自权利人；本仓库不为第三方授予许可证。

确认菜单使用 TrMenu 每次调用复制上下文的 JEXL，避开安装版 JS 共享玩家绑定；不在 Open/Close 事件付款或调用按玩家清理的 cancel，旧窗口关闭由捕获 flow 的桥回调处理。插件仍需与确认菜单一起安装，不能仅卸载插件后留下按钮。未部署、未测真人。
