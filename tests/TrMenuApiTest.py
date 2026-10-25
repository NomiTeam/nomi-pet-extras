#!/usr/bin/env python3
"""Read-only checks against the supplied TrMenu 3.12.5; no runtime execution."""

import hashlib
import pathlib
import subprocess
import sys

jar = pathlib.Path(sys.argv[1])
expected_sha = "939362c90c48d54ceb4e5b0f6757e7407be27c95dbfcac4b69526412088ee586"
assert hashlib.sha256(jar.read_bytes()).hexdigest() == expected_sha, "unexpected TrMenu input"
prefix = "me.arasple.mc.trmenu."


def inspect(name, *options):
    return subprocess.check_output([
        "javap", *options, "-classpath", str(jar), prefix + name,
    ], text=True)


menu = inspect("module.display.Menu", "-p", "-c")
session = inspect("module.display.MenuSession", "-c")
window = inspect("api.receptacle.vanilla.window.WindowReceptacle", "-c")
assert "java.util.function.Consumer<me.arasple.mc.trmenu.module.display.MenuSession>" in menu
for method in ["getId()", "getMenu()", "getViewer()", "getReceptacle()"]:
    assert method in session, method
assert "ConcurrentHashMap<java.util.UUID, me.arasple.mc.trmenu.module.display.MenuSession> SESSIONS" in session
assert "getSession(org.bukkit.entity.Player)" in inspect("module.display.MenuSession$Companion")
assert "getSession()" in inspect("api.event.MenuCloseEvent")
assert "getMenuById(java.lang.String)" in inspect("api.TrMenuAPI")
assert "getViewingReceptacle(org.bukkit.entity.Player)" in inspect("api.receptacle.ReceptacleAPIKt")

start = menu.index("kotlin210.jvm.functions.Function1<? super")
block = menu[start:menu.index("public static void open$default", start)]
assert (
    block.index("MenuSession.setMenu:")
    < block.index("Function1.invoke:")
    < block.index("getOpenEvent:")
    < block.index("Method openWindow:")
)
close = session[
    session.index("public final void close(boolean, boolean)"):
    session.index("public final me.arasple.mc.trmenu.module.display.icon.Icon")
]
assert close.index("MenuCloseEvent.call:") < close.index("WindowReceptacle.close:")
opened = window[
    window.index("public void open(org.bukkit.entity.Player)"):
    window.index("public void close(boolean)")
]
assert opened.index("setViewingReceptacle:") < opened.index("Function2.invoke:")
assert "getOnClose()" in window
assert "setOnClose(kotlin210.jvm.functions.Function2" in window

# 安装版 JS 共享绑定存在已知串绑风险；JEXL 每次复制 Map 后才放入玩家。
jexl = inspect("module.internal.script.jexl.JexlAgent", "-p", "-c")
evaluation = jexl[jexl.index("public final java.lang.Object eval-"):]
assert evaluation.index("Maps.newHashMap:") < evaluation.index("String player")
assert evaluation.index("String player") < evaluation.index("JexlCompiledScript.eval:")
assert "JexlAgent" in inspect("api.action.impl.script.Jexl", "-c")
# 原生 take-money 丢弃 EconomyResponse；需要回执时只在菜单内使用现有 Economy API。
vault = inspect("module.internal.hook.impl.HookVault", "-p", "-c")
assert "public final void takeMoney(org.bukkit.OfflinePlayer, double)" in vault
print(
    "Real TrMenu 3.12.5 SHA/API, open/close ordering, JEXL context copy and "
    "native money receipt boundary passed (bytecode only)"
)
