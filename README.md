# NomiPetExtras

NomiPetExtras 1.0.0 独立提供 MyPet 基础技能路线重置，不依赖 MenuReturnBridge。只处理本人当前宠物；使用 MyPet 4.0.4 原生路线选择菜单、确认菜单 API 和技能重算事件，不执行等级修改命令。

必须安装 MyPet 4.0.4、Vault（包括使用 Vault 插件名的 VaultUnlocked）及经济服务提供者。缺少硬依赖时由插件加载器停止加载；没有经济提供者或 API 不兼容时明确停用。依赖 Purpur 26.3 的 Paper/Folia API；玩家变更与延迟菜单操作均在玩家实体调度器上执行。

`/nomipetreset` 为本人当前宠物选择另一条基础路线。权限 `nomipetextras.reset` 默认允许；同时要求 `MyPet.command.chooseskilltree`，并重新检查路线自己的原生条件与权限。控制台执行此命令只显示只读状态。

`config.yml` 的 `reset-cost` 默认 500，非有限数或负值回退为 500。确认菜单锁定当次价格。扣费仅由本插件执行，每次确认终结后不能重试扣费，需重新选择。当前宠物、原路线、目标注册、等级、类型、继承条件、权限、背包和当前菜单 holder 均会再次检查。背包检查读取全部持久化槽，而非仅当前容量。

取消只返回原生父菜单；父栈为空时重新显示路线选择。取消、旧菜单、切换菜单和玩家退出使旧确认失效。付款成功后会再次核对同一流程、同一确认 holder、有效 ticket、在线状态及宠物条件。仅在尚未调用 MyPet 修改接口、确认未应用时自动退款。MyPet 修改接口一旦进入，返回 false、抛异常或路线/等级/经验后置检查失败均视为应用结果不明，不自动退款、不尝试覆盖其他插件的合法变更；需管理员核对路线、等级经验、技能状态和经济账目。旧确认仅清理自己的流程，不关闭重入建立的新菜单。退款失败、应用结果不明或支付结果不明均会记录 SEVERE 日志，含交易号、玩家 UUID、宠物 UUID、金额及路线，玩家会获得对应交易号。只有经济接口确认退款成功才提示已退款。此日志需由管理员保留用于核查；该流程不是跨服务 ACID：没有持久化交易日志或自动恢复协议，进程崩溃、服务超时、第三方同步事件及技能重算的跨插件边界仍需人工核对；支付接口抛异常时不会盲目退款。MyPet 返回成功且路线/等级/经验符合预期只证明这些可观测条件，不能独立证明第三方技能监听器完整执行。

使用 Python 3 和 PATH 中的 JDK 25。外部依赖目录包含 Purpur 26.3 build 2654 stable API、MyPet 4.0.4、Vault API JAR 及相应 Adventure、Guava、注解库。

```sh
python3 build.py --deps-dir .work/deps
# 也可通过 NOMI_DEPS_DIR 指定依赖目录
```

构建使用真实外部 JAR，执行支付/退款/安全条件/旧菜单、付款重入取消/新流程、应用副作用不明的生产状态机回归，以及真实 MyPet Skilltree 对象测试，输出 `build/NomiPetExtras-1.0.0.jar`。第三方 JAR 不会打入产物或公开源码。测试与源码可公开复现。

安装维护者需迁移 MyPet 菜单配置：在原生宠物主菜单 `PetMenuContext` 中保留一个槽类型 section，ID 为 `nuomi-reset`，配置其位置与图标；本插件在打开时显示实际价格并接管此按钮。无需为按钮再配置扣费或重置指令。本插件自行注册唯一的 `nuomi-reset-confirm` 菜单并附带确认资源。保留 MyPet 选择路线菜单的原生 `skilltrees` section；不要覆盖其选择逻辑。Bridge 的其他 `nuomi-*` 导航按钮可继续存在。

必须先移除仍包含 MyPetReset/ResetCharge 的旧 Bridge 实现，清理旧 Bridge 的 `mypet-skill-reset-cost` 配置及旧确认资源覆盖，将价格迁至本插件 `reset-cost`。检查 MyPet 的同名确认菜单 JSON 覆盖，避免其遮盖本插件资源；同一 MenuId 只能有一个注册者。这些配置清理由安装维护者操作，本项目构建不会修改其他插件配置。

已验证真实依赖编译和进程内 Java 逻辑；尚未进行游戏内路线选择/确认/取消、实际经济服务、原生技能升级与多玩家 Folia 体验验证。Folia 声明仅表示实现采用对应调度 API，支持范围以此验证记录为限。不建议热重载：MyPet GUI API 没有公开的 unregisterMenu 接口，应通过正常服务器生命周期安装或卸载。

新实现和测试未包含原源码或第三方二进制。外部 MyPet、Vault、Purpur 及其他参考的适用版权与许可仍归各自权利人，本仓库不为第三方授予许可证。
