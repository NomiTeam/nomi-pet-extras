#!/usr/bin/env python3
"""Compile against externally supplied, unmodified dependencies; never download or vendor them."""
import argparse, os, pathlib, shutil, subprocess, hashlib
ROOT = pathlib.Path(__file__).resolve().parent
p = argparse.ArgumentParser()
p.add_argument('--deps-dir', default=os.environ.get('NOMI_DEPS_DIR'))
a = p.parse_args()
if not a.deps_dir:
    p.error('supply --deps-dir or NOMI_DEPS_DIR containing Purpur 26.3, MyPet 4.0.4 and API dependency jars')
deps = pathlib.Path(a.deps_dir)
jars = sorted(deps.rglob('*.jar'))
api = [j for j in jars if 'purpur-api-26.3' in j.name and '2654-stable' in j.name]
if len(api) != 1:
    p.error('exactly one Purpur 26.3 build 2654 stable API is required')
# Avoid accidental linkage to older Bukkit APIs bundled in the dependency directory.
jars = api + [j for j in jars if 'purpur-api-' not in j.name and ('libraries' in j.parts or 'MyPet' in j.name or 'Vault' in j.name)]
# Purpur 26.3 NamespacedKey requires Key.checkNamespace (Adventure 5.2).
# The supplied server snapshot also contains older plugin-era copies.
current_key = [j for j in jars if j.name == 'adventure-key-5.2.0.jar']
cp = os.pathsep.join(map(str, current_key + [j for j in jars if j not in current_key]))
output = ROOT / 'build/NomiPetExtras-1.0.0.jar'
output.unlink(missing_ok=True)
classes = ROOT / 'build/classes'
if classes.exists(): shutil.rmtree(classes)
classes.mkdir(parents=True)
subprocess.run(['javac', '--release', '21', '-encoding', 'UTF-8', '-cp', cp, '-d', str(classes), *map(str, sorted((ROOT/'src/main/java').rglob('*.java')))], check=True)
tests = ROOT / '.work/tests'
tests.mkdir(parents=True, exist_ok=True)
subprocess.run(['javac','--release','21','-encoding','UTF-8','-cp',str(classes)+os.pathsep+cp,'-d',str(tests),*map(str,sorted((ROOT/'tests').rglob('*.java')))],check=True)
services = tests / 'META-INF/services'
services.mkdir(parents=True, exist_ok=True)
(services / 'io.papermc.paper.registry.RegistryAccess').write_text('team.nomi.pet.TestRegistryAccess\n')
for test in ['team.nomi.pet.ResetTest','team.nomi.pet.RoutesTest','team.nomi.pet.ReentryTest','team.nomi.pet.ConfirmationTest','team.nomi.pet.ApplicationTest','team.nomi.pet.SelectionTransitionTest']:
    subprocess.run(['java','-ea','-cp',str(tests)+os.pathsep+str(classes)+os.pathsep+cp,test],check=True)
output = ROOT / 'build/NomiPetExtras-1.0.0.jar'
subprocess.run(['jar','--create','--date=2026-01-01T00:00:00Z','--file',str(output),'-C',str(classes),'.','-C',str(ROOT/'src/main/resources'),'.'],check=True)
print(output.name+' SHA-256 '+hashlib.sha256(output.read_bytes()).hexdigest())
