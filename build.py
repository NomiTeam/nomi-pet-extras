#!/usr/bin/env python3
"""Compile against external, unmodified dependencies; never download or vendor them."""

import argparse
import hashlib
import os
import pathlib
import shutil
import subprocess

ROOT = pathlib.Path(__file__).resolve().parent
TEST_SUITES = [
    "BridgeContractTest",
    "RoutesTest",
    "ApplicationTest",
    "SelectionTest",
    "SafetyTest",
    "ThreadTest",
    "BridgeSafetyTest",
    "TrMenuSessionTest",
    "NativeMenusTest",
]


def arguments():
    parser = argparse.ArgumentParser()
    parser.add_argument("--deps-dir", default=os.environ.get("NOMI_DEPS_DIR"))
    parser.add_argument("--test-deps-dir", help="private test dependencies, never packaged")
    parser.add_argument("--trmenu-jar", help="read-only TrMenu 3.12.5 bytecode test input")
    args = parser.parse_args()
    if not args.deps_dir:
        parser.error("supply --deps-dir or NOMI_DEPS_DIR with Purpur 26.3 and MyPet 4.0.4")
    return parser, args


def dependency_classpath(parser, directory):
    jars = sorted(pathlib.Path(directory).rglob("*.jar"))
    api = [
        jar for jar in jars
        if "purpur-api-26.3" in jar.name and "2654-stable" in jar.name
    ]
    if len(api) != 1:
        parser.error("exactly one Purpur 26.3 build 2654 stable API is required")
    # 不链接目录内较旧的 Bukkit API；第三方插件不进入编译或产物。
    jars = api + [
        jar for jar in jars
        if "purpur-api-" not in jar.name
        and ("libraries" in jar.parts or "MyPet" in jar.name)
    ]
    # Purpur 26.3 NamespacedKey 使用 Adventure 5.2 的 checkNamespace。
    current_key = [jar for jar in jars if jar.name == "adventure-key-5.2.0.jar"]
    return os.pathsep.join(map(str, current_key + [j for j in jars if j not in current_key]))


def empty_directory(path):
    if path.exists():
        shutil.rmtree(path)
    path.mkdir(parents=True)


def compile_java(source, output, classpath):
    sources = sorted(source.rglob("*.java"))
    subprocess.run([
        "javac", "--release", "21", "-encoding", "UTF-8",
        "-cp", classpath, "-d", str(output), *map(str, sources),
    ], check=True)


def main():
    parser, args = arguments()
    classpath = dependency_classpath(parser, args.deps_dir)
    output = ROOT / "build/NomiPetExtras-1.1.0.jar"
    output.unlink(missing_ok=True)
    classes = ROOT / "build/classes"
    empty_directory(classes)
    compile_java(ROOT / "src/main/java", classes, classpath)

    test_classpath = classpath
    if args.test_deps_dir:
        private_jars = sorted(pathlib.Path(args.test_deps_dir).rglob("*.jar"))
        test_classpath += os.pathsep + os.pathsep.join(map(str, private_jars))
    tests = ROOT / ".work/tests"
    empty_directory(tests)
    compile_java(ROOT / "tests", tests, str(classes) + os.pathsep + test_classpath)
    services = tests / "META-INF/services"
    services.mkdir(parents=True)
    (services / "io.papermc.paper.registry.RegistryAccess").write_text(
        "team.nomi.pet.TestRegistryAccess\n"
    )
    for suite in TEST_SUITES:
        subprocess.run([
            "java", "-ea", "-cp",
            os.pathsep.join([str(tests), str(classes), test_classpath]),
            "team.nomi.pet." + suite,
        ], check=True)
    if args.trmenu_jar:
        subprocess.run([
            "python3", str(ROOT / "tests/TrMenuApiTest.py"), args.trmenu_jar,
        ], check=True)
    subprocess.run([
        "jar", "--create", "--date=2026-01-01T00:00:00Z", "--file", str(output),
        "-C", str(classes), ".", "-C", str(ROOT / "src/main/resources"), ".",
    ], check=True)
    digest = hashlib.sha256(output.read_bytes()).hexdigest()
    print(f"{output.name} SHA-256 {digest}")


if __name__ == "__main__":
    main()
