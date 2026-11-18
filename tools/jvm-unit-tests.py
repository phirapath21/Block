#!/usr/bin/env python3
"""Runs Block's unit tests on the plain JVM, without the Android SDK or Google Maven.

Usage: python3 tools/jvm-unit-tests.py   (needs Gradle and JDK 21 on the PATH)

For machines that can't build the app (no SDK, no Google Maven), as a check before pushing.
CI's ./gradlew testDebugUnitTest stays the real check: this skips tests whose code uses
AndroidX/Compose UI, and it lists the tests it ran.

Copies every main/test Kotlin file without android/androidx/compose imports into a small
Gradle project, compiles, drops files that fail to compile (they lean on Android code),
and repeats until it builds, then runs the tests. Prints which tests ran and the result.
"""
import os, re, shutil, subprocess, sys
HERE = os.path.dirname(os.path.abspath(__file__))
REPO = sys.argv[1] if len(sys.argv) > 1 else os.path.dirname(HERE)
# Built under the git-ignored build/ folder.
PROJ = os.path.join(REPO, 'build', 'jvm-unit-tests')
BAD = re.compile(r'^import (androidx\.(?!compose\.runtime\.)|coil3|com\.google|kotlinx\.coroutines\.android|org\.junit\.runner\.RunWith)|androidx\.(?!compose\.runtime\.)|coil3\.', re.M)
def collect(sub):
    out = []
    for root, _, files in os.walk(os.path.join(REPO, 'app/src', sub, 'java')):
        for f in files:
            if f.endswith('.kt'):
                p = os.path.join(root, f)
                if not BAD.search(open(p, encoding='utf-8').read()): out.append(p)
    return out
main, test = collect('main'), collect('test')
shutil.rmtree(PROJ, ignore_errors=True)
for d in ('src/main/kotlin', 'src/test/kotlin'): os.makedirs(os.path.join(PROJ, d))
def place(files, d):
    for p in files:
        rel = os.path.relpath(p, os.path.join(REPO, 'app/src'))
        dst = os.path.join(PROJ, d, rel.replace(os.sep, '_'))
        shutil.copy(p, dst)
place(main, 'src/main/kotlin'); place(test, 'src/test/kotlin')
res = os.path.join(REPO, 'app/src/test/resources')
if os.path.isdir(res): shutil.copytree(res, os.path.join(PROJ, 'src/test/resources'))
open(os.path.join(PROJ, 'settings.gradle.kts'), 'w').write('rootProject.name="jvmtests"\n')
open(os.path.join(PROJ, 'build.gradle.kts'), 'w').write('''plugins { kotlin("jvm") version "2.3.20" }
repositories { maven("https://maven-central.storage-download.googleapis.com/maven2/"); mavenCentral() }
dependencies {
  implementation("org.json:json:20240303")
  implementation("org.jetbrains.compose.runtime:runtime-desktop:1.7.3")
  implementation("com.squareup.okhttp3:okhttp:5.3.2")
  implementation("fr.acinq.secp256k1:secp256k1-kmp:0.24.0")
  implementation("fr.acinq.secp256k1:secp256k1-kmp-jni-jvm:0.24.0")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
  implementation("com.google.zxing:core:3.5.3")
  // The Android framework, to compile against only (as CI's android.jar): tests can't call it.
  compileOnly("org.robolectric:android-all:14-robolectric-10818077")
  testCompileOnly("org.robolectric:android-all:14-robolectric-10818077")
  testImplementation("junit:junit:4.13.2")
  testImplementation("org.xerial:sqlite-jdbc:3.46.1.3")
  testImplementation("com.squareup.okhttp3:mockwebserver3:5.3.2")
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}
kotlin { jvmToolchain(21) }
// Compose's androidx pieces live on Google Maven, which this machine can't reach; the code only needs @Immutable.
configurations.all { exclude(group = "androidx.annotation"); exclude(group = "androidx.collection"); exclude(group = "androidx.lifecycle"); exclude(group = "androidx.arch.core") }
tasks.test { testLogging { events("failed"); exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL } }
''')
def gradle(task):
    return subprocess.run(['gradle', '-q', task], cwd=PROJ, capture_output=True, text=True)
for _ in range(30):
    r = gradle('compileTestKotlin')
    if r.returncode == 0: break
    bad = set(re.findall(r'^e: file://(\S+?\.kt):', r.stdout + r.stderr, re.M))
    if not bad: print(r.stdout[-3000:], r.stderr[-3000:]); sys.exit('build failed without per-file errors')
    first = re.findall(r'^e: file://\S+?/([^/]+?\.kt):\d+:\d+ (.*)$', r.stdout + r.stderr, re.M)
    if os.environ.get('VERBOSE'):
        for f, m in first[:400]:
            if any(k in f for k in ('AppPrefs', 'PriceData', 'BlockclockDisplay', 'PriceSources')): print('  ', f, m[:150])
        print('dropping', sorted(os.path.basename(b) for b in bad))
    for b in bad:
        if os.path.exists(b): os.remove(b)
else:
    sys.exit('gave up after 30 rounds')
r = gradle('test')
ran = sorted(f[:-3].split('_')[-1] for f in os.listdir(os.path.join(PROJ, 'src/test/kotlin')))
print('tests run:', ', '.join(ran))
import glob
tot = fail = 0
for x in glob.glob(os.path.join(PROJ, 'build/test-results/test/*.xml')):
    s = open(x).read()
    m = re.search(r'tests="(\d+)" skipped="\d+" failures="(\d+)" errors="(\d+)"', s)
    tot += int(m.group(1)); fail += int(m.group(2)) + int(m.group(3))
    for msg in re.findall(r'<failure message="([^"]*)"', s): print('FAIL', os.path.basename(x), msg[:300])
print(f'{tot} tests, {fail} failed')
sys.exit(1 if r.returncode or fail else 0)
