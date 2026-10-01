#!/usr/bin/env python3
"""Build Item Blacklist and run it on real Fabric and NeoForge servers.

One server per loader and Minecraft release, each installed by its loader's
own installer, so the jar a line builds is booted on every release it claims:

    run.py                     # devVersion, from gradle.properties
    run.py 1.21.1
    run.py all                 # smoke-boot every release on every loader
    run.py all --gametest      # run the GameTests on every release and loader
    run.py --list              # the boot list, its pins and what is installed

Each server lives in .run/<loader>-<release>/ with its world, config and logs;
deleting that folder resets just that server. The installers and mod jars the
servers need are downloaded once into the paper-scaffold home, shared by every
project, each checked against the sha256 pinned below (RELEASES and
FABRIC_INSTALLER); the Mojang server jar the Fabric installer fetches, against
its sha1. How a server's server.properties is layered: dev-server/README.md.

Standard library only, and no assumptions about what is on PATH: the build
runs through gw.cmd or gw.sh, which find its JDK, and each server's Java is
looked up as find_java describes.
"""

from __future__ import annotations

import argparse
import atexit
import hashlib
import os
import re
import shutil
import signal
import socket
import subprocess
import sys
import threading
import urllib.error
import urllib.request
import xml.etree.ElementTree as ElementTree
from collections.abc import Callable, Iterator
from contextlib import contextmanager
from pathlib import Path
from typing import NamedTuple

PROJECT = Path(__file__).resolve().parent.parent
RUN_ROOT = PROJECT / ".run"


def read_properties(file: Path) -> dict[str, str]:
    """Parse the key=value lines of a .properties file; every other line is dropped."""
    values: dict[str, str] = {}
    if not file.exists():
        return values
    for line in file.read_text(encoding="utf-8", errors="replace").splitlines():
        line = line.strip()
        if not line or line.startswith(("#", "!")) or "=" not in line:
            continue
        key, _, value = line.partition("=")
        values[key.strip()] = value.strip()
    return values


def gradle_property(key: str) -> str:
    """A setting of gradle.properties, which the build reads too."""
    file = PROJECT / "gradle.properties"
    if not file.is_file():
        raise SystemExit(f"{file} not found")
    value = read_properties(file).get(key)
    if value is None:
        raise SystemExit(f"{file} has no line of the form {key}=<value>")
    if not value:
        raise SystemExit(f"{file} has no value for {key}")
    return value


# ---------------------------------------------------------------------------
# The mod and its boot list, written when the project was made
# ---------------------------------------------------------------------------

# Every log line of the mod starts with its name and goes through the logger
# named by its mod id; LogSieve and the verdicts key on both.
NAME = "Item Blacklist"
MOD_ID = "item_blacklist"
TEST_MOD_ID = "item_blacklist_gametest"
PACKAGE = "net.sievert.item_blacklist"
ARTIFACT = "item-blacklist"

# The loaders the project builds for, each in its part folder <artifact>-<loader>.
LOADERS: tuple[str, ...] = ("fabric", "neoforge")
LOADERS_TEXT = "Fabric and NeoForge"

LOADER_NAMES = {"fabric": "Fabric", "neoforge": "NeoForge"}


class FabricPins(NamedTuple):
    """What a release's Fabric server runs besides the mod."""
    loader: str
    api: str
    api_sha256: str
    # fabric-gametest-api-v1, the version this Fabric API build's POM names. The
    # Fabric API jar does not bundle it; GameTest runs add it to mods/.
    gametest: str
    gametest_sha256: str


class NeoForgePins(NamedTuple):
    """The NeoForge build a release's server runs: its newest stable, else its newest beta."""
    version: str
    beta: bool
    installer_sha256: str


class Release(NamedTuple):
    """A release of the boot list: its server's Java, and the line module whose jars serve it."""
    java: int
    line_module: str
    server_sha1: str
    fabric: FabricPins | None
    neoforge: NeoForgePins | None


class FabricInstaller(NamedTuple):
    version: str
    sha256: str


# The boot list: every release this script boots and tests, oldest first,
# because port_for numbers ports by position. Each pin was resolved when the
# project was made, and each file downloaded for it is checked against the
# sha256 its repository published then (README.md, "Testing across versions").
# Booting another release: CLAUDE.md, "Booting another release".
RELEASES: dict[str, Release] = {
    "1.21.1": Release(
        java=21,
        line_module="l1_21",
        server_sha1="59353fb40c36d304f2035d51e7d6e6baa98dc05c",
        fabric=FabricPins(
            loader="0.19.5",
            api="0.116.17+1.21.1",
            api_sha256="79ac44b40780acbd884b34c50be1e39af682847e5f5cb3b1fddeeaa768dce800",
            gametest="2.0.5+6fc22b9919",
            gametest_sha256="1844602aba201ca58a2d02c4dbb51435fa33d6c6b25ace2d158db44ab1688125",
        ),
        neoforge=NeoForgePins(
            version="21.1.252",
            beta=False,
            installer_sha256="d0345e2a104ce4633065f572310ba6e0dd428bb6a73364cf0c2443ed9f8950ad",
        ),
    ),
    "1.21.2": Release(
        java=21,
        line_module="l1_21",
        server_sha1="7bf95409b0d9b5388bfea3704ec92012d273c14c",
        fabric=FabricPins(
            loader="0.19.5",
            api="0.106.1+1.21.2",
            api_sha256="d306e1cf9348bc13292bbdcec19a417a50d6dc538121eec02d10704e6b9e2ee8",
            gametest="2.0.13+c47b9d4373",
            gametest_sha256="a8820370b7896e8f7f01e0beb0047c937f795002e7488d2651601cfb748135c9",
        ),
        neoforge=NeoForgePins(
            version="21.2.1-beta",
            beta=True,
            installer_sha256="e4b6e64c9192e3baacb6223bfaa65ce2d677b4f4ad74ac051335681fd6a70e34",
        ),
    ),
    "1.21.3": Release(
        java=21,
        line_module="l1_21",
        server_sha1="45810d238246d90e811d896f87b14695b7fb6839",
        fabric=FabricPins(
            loader="0.19.5",
            api="0.114.1+1.21.3",
            api_sha256="357a4fe24c7a7248320d2099b8567b65fd2f4271a3faf9c7f369dd2c18ab4157",
            gametest="2.0.15+fd37071f40",
            gametest_sha256="9a96e99b9537587bbabcfbff94b72b7161fef386f2406364253b2046e5d74305",
        ),
        neoforge=NeoForgePins(
            version="21.3.97",
            beta=False,
            installer_sha256="86bac20916c03344848ee42f18e4beaa99c113f358d90f9e4731abeea5fad6eb",
        ),
    ),
    "1.21.4": Release(
        java=21,
        line_module="l1_21",
        server_sha1="4707d00eb834b446575d89a61a11b5d548d8c001",
        fabric=FabricPins(
            loader="0.19.5",
            api="0.119.4+1.21.4",
            api_sha256="d183bacb845167f09264c2f90322b7ecffe8826debda6f60e597889264bef4af",
            gametest="2.0.26+7feeb73304",
            gametest_sha256="1d0598ed6e33b2aaabc72fa626ed9dae85c18c138cf8f787f712f2bebb1d0b6b",
        ),
        neoforge=NeoForgePins(
            version="21.4.158",
            beta=False,
            installer_sha256="f392445f5f5c4523d5ba8f6481c14e83a6167d7ea16438d92ea7bfcd5a0e125a",
        ),
    ),
    "1.21.5": Release(
        java=21,
        line_module="l1_21",
        server_sha1="e6ec2f64e6080b9b5d9b471b291c33cc7f509733",
        fabric=FabricPins(
            loader="0.19.5",
            api="0.128.2+1.21.5",
            api_sha256="a82fd00827206e911936ed1e0ceaec6eb55d061ca5d3c5d63c7f0031426d29ae",
            gametest="3.1.3+2a6ec84b49",
            gametest_sha256="ec7352826bcccaa831ce7ff69b4f364ccd2fa5565a0da1cd6324ff48ad130727",
        ),
        neoforge=NeoForgePins(
            version="21.5.98",
            beta=False,
            installer_sha256="da85958099ea79b6f69561c3e3ee1420032e541aa33cef8639416ccf512ec42b",
        ),
    ),
    "1.21.6": Release(
        java=21,
        line_module="l1_21",
        server_sha1="6e64dcabba3c01a7271b4fa6bd898483b794c59b",
        fabric=FabricPins(
            loader="0.19.5",
            api="0.128.2+1.21.6",
            api_sha256="6e1d4547d7debd18da80532e561f02f167545219516af5b63c8a803a8f09822f",
            gametest="3.1.9+39ce47f596",
            gametest_sha256="5b3da19d0d4b074c58dcac31500d5fa73cdc4df5db027195332ab4afb8e57ff4",
        ),
        neoforge=NeoForgePins(
            version="21.6.20-beta",
            beta=True,
            installer_sha256="e85b8ed2614f2ab3f10d2d179064e3e81f5836519a074d22b8a8460f2e91b5d5",
        ),
    ),
    "1.21.7": Release(
        java=21,
        line_module="l1_21",
        server_sha1="05e4b48fbc01f0385adb74bcff9751d34552486c",
        fabric=FabricPins(
            loader="0.19.5",
            api="0.129.0+1.21.7",
            api_sha256="db5f16fe1aa577f46d363bf8d7a97054249f0962d82d0ef18d19ca4d5e162768",
            gametest="3.1.9+39ce47f56c",
            gametest_sha256="494ea4e9347b9c0028dbf054aaa341531b7e45ad08c4fbac72e0029dff3fcc73",
        ),
        neoforge=NeoForgePins(
            version="21.7.25-beta",
            beta=True,
            installer_sha256="1ceecf4fad699ec5ff1f02dbfd9b08b49069ac158adb1f1b9133f9db524f0fe1",
        ),
    ),
    "1.21.8": Release(
        java=21,
        line_module="l1_21",
        server_sha1="6bce4ef400e4efaa63a13d5e6f6b500be969ef81",
        fabric=FabricPins(
            loader="0.19.5",
            api="0.136.1+1.21.8",
            api_sha256="24c2c9c7fa1f969982ddf142a5f17b356b88a5eb1cb1ece9c287a588f3451233",
            gametest="3.1.10+39ce47f52c",
            gametest_sha256="78042353cb269c098a89c718dbd79e054bd5aff03ed0609b67a8df9186daa3f8",
        ),
        neoforge=NeoForgePins(
            version="21.8.54",
            beta=False,
            installer_sha256="8771de3b3df9d5382103245dcdcf6cd6ebe84a8c3b26a2b21d43153613ff191b",
        ),
    ),
    "1.21.9": Release(
        java=21,
        line_module="l1_21",
        server_sha1="11e54c2081420a4d49db3007e66c80a22579ff2a",
        fabric=FabricPins(
            loader="0.19.5",
            api="0.134.1+1.21.9",
            api_sha256="43e7bf24789b5d841690d942efc274478a50fbfd943e8838f8fbc4de8162a970",
            gametest="3.1.18+33df5e6e7d",
            gametest_sha256="d8d4454435aedb9d15e5d17fcc117c814f87ba54ef3bfeb688ed8a3c5126b2d5",
        ),
        neoforge=NeoForgePins(
            version="21.9.16-beta",
            beta=True,
            installer_sha256="fde025c65f042a39308e5313b6023db9f17f3d77c35a27c66d9065115759a78e",
        ),
    ),
    "1.21.10": Release(
        java=21,
        line_module="l1_21",
        server_sha1="95495a7f485eedd84ce928cef5e223b757d2f764",
        fabric=FabricPins(
            loader="0.19.5",
            api="0.138.4+1.21.10",
            api_sha256="4dd5d07067d5cf3e874d2780806c3da223819d39b28d6296c1005a36f2d0a5e4",
            gametest="3.1.21+33df5e6e6f",
            gametest_sha256="e92e19d2c4e53401be10925bc3d602f4c3e8c23eb0065bc1494ad212eefe77ca",
        ),
        neoforge=NeoForgePins(
            version="21.10.64",
            beta=False,
            installer_sha256="6adb1f063358b22b050834323a4f68df47c93d082bc49a8a0a97aec22b789427",
        ),
    ),
    "1.21.11": Release(
        java=21,
        line_module="l1_21",
        server_sha1="64bb6d763bed0a9f1d632ec347938594144943ed",
        fabric=FabricPins(
            loader="0.19.5",
            api="0.141.6+1.21.11",
            api_sha256="bdff7fd7e220085cfad2ff9b1f40dde6534ae0b96cf378f97a374bc54cb9ed0f",
            gametest="3.1.27+4fc5413f3e",
            gametest_sha256="c9d7127eb43bd0f1d1455a98944f8f60cf82e04e2d374708f10ed84d98de3897",
        ),
        neoforge=NeoForgePins(
            version="21.11.45",
            beta=False,
            installer_sha256="e54350cc68d7dec6cb0523a0597a6a2fa664c1acd878b456af4564ee126da00a",
        ),
    ),
    "26.1.2": Release(
        java=25,
        line_module="l26",
        server_sha1="97ccd4c0ed3f81bbb7bfacddd1090b0c56f9bc51",
        fabric=FabricPins(
            loader="0.19.5",
            api="0.155.3+26.1.2",
            api_sha256="7fe7bea3dbb7b2e9ff998ab583130cd62fab22ab8a5229656ff14b2e648c91dd",
            gametest="4.0.18+867b87624c",
            gametest_sha256="a204f0f5eb9bb9ef2050008a03d833219eb61851c0f8a668196ba2c14ce9f219",
        ),
        neoforge=NeoForgePins(
            version="26.1.2.112",
            beta=False,
            installer_sha256="7df28ad0522341630ac30132e56f5c2a5f93df2ca9435ddf55151f4eaf72a257",
        ),
    ),
    "26.2": Release(
        java=25,
        line_module="l26",
        server_sha1="823e2250d24b3ddac457a60c92a6a941943fcd6a",
        fabric=FabricPins(
            loader="0.19.5",
            api="0.161.0+26.2",
            api_sha256="e5b858ceb13290c274e31cb888ff5a1a40cb91067e7ffab0b5b775aec51e216a",
            gametest="4.0.22+4a7fa0819e",
            gametest_sha256="b4cc93c8de8c3546bf725939e09a85e43f4716775dd99076940a1b9bb9d7d702",
        ),
        neoforge=NeoForgePins(
            version="26.2.0.88",
            beta=False,
            installer_sha256="c268d5a9c33ff258e559f236b72b66bbd6c73530ce94f1e665160550fff45175",
        ),
    ),
}

FABRIC_INSTALLER: FabricInstaller | None = FabricInstaller(
    version="1.1.2",
    sha256="61e035bf7bf70153e127440ce34de47c9036f0a2d0c65d1529454bd35ceefe4f",
)

# The release booted when none is named: devVersion, read from gradle.properties.
DEV_VERSION = gradle_property("devVersion")

# What every paper-scaffold project on this machine shares: its JDKs, its
# downloads and the shared settings. The generator, gw.cmd and gw.sh read the
# same variable, so they all agree on where that is.
SCAFFOLD_HOME = Path(os.environ.get("PAPER_SCAFFOLD_HOME") or Path.home() / ".paper-scaffold")

# The generator's own archive cache. The installers and the Fabric jars are
# downloaded into it once, for every project: for a boot list of a dozen
# releases on both loaders they add up to over 100 MB.
DOWNLOADS = SCAFFOLD_HOME / "downloads"

# server.properties overrides shared by every dev server on this machine.
GLOBAL_PROPERTIES = SCAFFOLD_HOME / "server.properties"

# Copied over every server this script starts; dev-server/README.md has the rules.
DEV_SERVER = PROJECT / "dev-server"

FABRIC_MAVEN = "https://maven.fabricmc.net/net/fabricmc"
NEOFORGE_MAVEN = "https://maven.neoforged.net/releases/net/neoforged"
USER_AGENT = "item-blacklist-devtools/1.0 (internal mod tooling)"

# The build wrapper as a printed command names it: gw.cmd in Windows' own
# shells, gw.sh elsewhere, Git Bash included.
GW = r".\gw.cmd" if os.name == "nt" and not os.environ.get("MSYSTEM") else "./gw.sh"

DEFAULT_PORT = 25565
DEFAULT_MEMORY = "2G"
DEBUG_PORT = 5005

# For the test servers, which live half a minute: the C1 compiler only.
# Start-up is where such a JVM spends its life, and C1 gets through it sooner;
# an interactive server keeps the full JIT, since it runs long enough for it to
# pay off.
TEST_SERVER_JVM_FLAGS = ["-XX:TieredStopAtLevel=1"]

# Every test run's time limit, and the cap on its console output. A stuck
# GameTest ticks without pause and logs a progress line every 20 ticks, some
# 8,000 lines a second, so the cap is what ends most hangs. The server's own
# logs/latest.log and debug.log grow to about 1.6 times the cap, so a run
# killed at the cap also clears its folder's logs/.
TIME_LIMIT = 300
SMOKE_CAP = 10 * 1024 * 1024
GAMETEST_CAP = 25 * 1024 * 1024

# An installer downloads the release's server and libraries as it runs.
INSTALL_TIME_LIMIT = 1200

# Each test run plays in a universe folder of its own, deleted before and after,
# so the server folder's own world, the one an interactive boot plays in, is
# never touched. Fabric's GameTest server and NeoForge 21.1 to 21.4's also take
# the world's name.
SMOKE_UNIVERSE = "smoke-universe"
GAMETEST_UNIVERSE = "gametest-universe"
GAMETEST_WORLD = "gametestworld"

# The JUnit report of a GameTest run, in its server folder. Every path on a
# server's command line is relative to its folder, where the server runs:
# java.exe on Windows reads its command line in the system code page, which
# may not spell the project's own folder.
REPORT = "gametest-report.xml"

# Where a release's GameTests run in production, by era and FML generation
# (README.md, "Testing across versions"). Fabric, every release:
# -Dfabric-api.gametest with fabric-gametest-api-v1 in mods/; its 2.x, up to
# 1.21.4, has no filter and runs every registered test, the 3.x of 1.21.5 on
# takes one.
FABRIC_FILTER_FROM = "1.21.5"
# NeoForge 21.1 to 21.4 (FML 4 to 6) run them through the server's own Main
# with -Dneoforge.gameTestServer=true, after its EULA check, the report coming
# from the test mod's property. NeoForge 21.5 to 21.8 (FML 7 to 9) have no
# production GameTest entry point: the plain server runs them through vanilla's
# /test command, which 1.21.5 registers on every server and which writes no
# report, so the console gives the verdict.
NEOFORGE_TEST_COMMAND_FROM = "1.21.5"
# NeoForge 21.9 on (FML 10) and 26.x (FML 11): FML's own GameTestServer, started
# through a copy of the installed args file with the main class swapped. It
# checks no EULA and hands --tests and --report on to vanilla's GameTest server.
NEOFORGE_GAMETEST_SERVER_FROM = "1.21.9"
GAMETEST_ARGS = "gametest-args.txt"

# The world of a smoke run and of a /test run, written into server.properties
# for the run only: vanilla's own GameTest world, flat, nothing spawning,
# nothing generated, no nether. The layers are vanilla's default ones spelt
# out, because a flat world with no settings is an error in the log before it
# falls back to exactly these.
TEST_WORLD_PROPERTIES = {
    "level-type": "minecraft\\:flat",
    "generator-settings": '{"biome":"minecraft:plains","layers":['
    '{"block":"minecraft:bedrock","height":1},'
    '{"block":"minecraft:dirt","height":2},'
    '{"block":"minecraft:grass_block","height":1}]}',
    "spawn-monsters": "false",
    "generate-structures": "false",
    "allow-nether": "false",
}


# ---------------------------------------------------------------------------
# Releases, folders and ports
# ---------------------------------------------------------------------------


def version_key(release: str) -> tuple[int, ...]:
    """A release's numbers, compared as numbers: 1.21.10 is above 1.21.9."""
    return tuple(int(part) for part in release.split("."))


def at_least(release: str, bound: str) -> bool:
    return version_key(release) >= version_key(bound)


def port_for(release: str, loader: str) -> int:
    """A stable, distinct port per release and loader, so several servers can run at once."""
    return DEFAULT_PORT + 2 * list(RELEASES).index(release) + list(LOADER_NAMES).index(loader)


def server_folder(loader: str, release: str) -> Path:
    return RUN_ROOT / f"{loader}-{release}"


def neoforge_args(release: str) -> str:
    """The args file NeoForge's installer wrote, relative to the server folder."""
    name = "win_args.txt" if os.name == "nt" else "unix_args.txt"
    return f"libraries/net/neoforged/neoforge/{RELEASES[release].neoforge.version}/{name}"


def remove(path: Path) -> None:
    if path.is_dir():
        shutil.rmtree(path, ignore_errors=True)
    else:
        path.unlink(missing_ok=True)


# ---------------------------------------------------------------------------
# Downloads
# ---------------------------------------------------------------------------


def fetch(url: str) -> bytes:
    """GET a URL, or stop the script saying why not."""
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    try:
        with urllib.request.urlopen(request, timeout=60) as response:
            return response.read()
    except urllib.error.HTTPError as failure:
        raise SystemExit(f"{url} returned HTTP {failure.code}") from failure
    except OSError as failure:  # URLError, or a timeout or reset while reading
        reason = getattr(failure, "reason", failure)
        raise SystemExit(f"could not reach {url}: {reason}") from failure


def intact(path: Path, sha256: str) -> bool:
    """Whether a file exists and has the given sha256."""
    if not path.is_file():
        return False
    hashed = hashlib.sha256()
    with path.open("rb") as handle:
        while chunk := handle.read(1 << 20):
            hashed.update(chunk)
    return hashed.hexdigest() == sha256


def _write_whole(destination: Path, payload: bytes) -> None:
    """Write a file so that, wherever it exists, it is a whole one.

    The bytes are written aside and renamed into place. The file aside is named
    for this process and thread, so another write of the same file at the same
    time, from another project's run, neither writes into it nor renames it away.
    """
    destination.parent.mkdir(parents=True, exist_ok=True)
    temporary = destination.with_name(
        f"{destination.name}.{os.getpid()}-{threading.get_ident()}.part"
    )
    try:
        temporary.write_bytes(payload)
        temporary.replace(destination)
    finally:
        temporary.unlink(missing_ok=True)


def fetch_pinned(url: str, sha256: str) -> Path:
    """A file from DOWNLOADS, downloaded first when it is missing or damaged.

    What arrives must have the sha256 pinned for it when the project was made;
    anything else stops the run and is not kept. The file is named as in its
    URL: the generator reads each Fabric API jar's declared range from the same
    file, so a project made on this machine finds those already there.
    """
    destination = DOWNLOADS / url.rpartition("/")[2]
    if intact(destination, sha256):
        return destination
    destination.unlink(missing_ok=True)
    print(f"  downloading {destination.name}")
    payload = fetch(url)
    actual = hashlib.sha256(payload).hexdigest()
    if actual != sha256:
        raise SystemExit(
            f"{url} has sha256 {actual}, not the {sha256} scripts/run.py pins; not kept"
        )
    _write_whole(destination, payload)
    return destination


def fabric_installer_url(version: str) -> str:
    return f"{FABRIC_MAVEN}/fabric-installer/{version}/fabric-installer-{version}.jar"


def fabric_api_url(version: str) -> str:
    return f"{FABRIC_MAVEN}/fabric-api/fabric-api/{version}/fabric-api-{version}.jar"


def fabric_gametest_url(version: str) -> str:
    module = "fabric-gametest-api-v1"
    return f"{FABRIC_MAVEN}/fabric-api/{module}/{version}/{module}-{version}.jar"


def neoforge_installer_url(version: str) -> str:
    return f"{NEOFORGE_MAVEN}/neoforge/{version}/neoforge-{version}-installer.jar"


# ---------------------------------------------------------------------------
# Java
# ---------------------------------------------------------------------------


def java_feature(binary: Path) -> int | None:
    """The feature release a java binary reports in its -version output, or None."""
    # Only the version's digits are read. A byte that is not UTF-8, such as one
    # of a path in a JAVA_TOOL_OPTIONS notice, decodes as U+FFFD.
    try:
        result = subprocess.run(
            [str(binary), "-version"], capture_output=True, encoding="utf-8", errors="replace",
            timeout=20, check=False,
        )
    except (OSError, subprocess.SubprocessError):
        return None

    text = f"{result.stdout} {result.stderr}"
    for token in text.split():
        cleaned = token.strip('"')
        if cleaned and cleaned[0].isdigit():
            head = cleaned.split(".")[0]
            if head.isdigit():
                return int(head)
    return None


_JAVAS: dict[int, Path] = {}


def find_java(feature: int) -> Path:
    """Locate a Java of the given feature release for a server.

    The candidates, in order: the JDKs actions/setup-java installed, which it
    names in JAVA_HOME_<feature>_X64 or _ARM64 and keeps there when a later step
    takes JAVA_HOME for another release; the JDK paper-scaffold installed;
    JAVA_HOME; PATH. Only one whose java reports exactly that feature release
    counts: each release's server needs its own.
    """
    if feature in _JAVAS:
        return _JAVAS[feature]
    candidates: list[Path] = []
    for name in (f"JAVA_HOME_{feature}_X64", f"JAVA_HOME_{feature}_ARM64"):
        if os.environ.get(name):
            candidates.append(Path(os.environ[name]))

    # The marker names the JDK home relative to its own folder. An absolute
    # path, which older markers hold, joins to itself.
    marker = SCAFFOLD_HOME / "jdks" / f"temurin-{feature}" / ".complete"
    if marker.is_file():
        candidates.append(marker.parent / marker.read_text(encoding="utf-8").strip())

    if java_home := os.environ.get("JAVA_HOME"):
        candidates.append(Path(java_home))
    if located := shutil.which("java"):
        candidates.append(Path(located).resolve().parent.parent)

    java = "java.exe" if os.name == "nt" else "java"
    for home in candidates:
        if java_feature(home / "bin" / java) == feature:
            _JAVAS[feature] = home / "bin" / java
            return _JAVAS[feature]

    needed_by = ", ".join(release for release, info in RELEASES.items() if info.java == feature)
    raise SystemExit(
        f"No Java {feature} found. Install it from "
        f"https://adoptium.net/temurin/releases/?version={feature}\n"
        f"and point JAVA_HOME at it, or PATH if JAVA_HOME already names another Java.\n"
        f"Needed by Minecraft {needed_by}."
    )


# ---------------------------------------------------------------------------
# Server folders
# ---------------------------------------------------------------------------


def installed(loader: str, release: str) -> bool:
    """Whether a release's server of a loader is installed, at the loader build pinned."""
    folder = server_folder(loader, release)
    if loader == "fabric":
        version = RELEASES[release].fabric.loader
        library = folder / "libraries/net/fabricmc/fabric-loader" / version
        return (folder / "fabric-server-launch.jar").is_file() and (
            library / f"fabric-loader-{version}.jar"
        ).is_file()
    return (folder / neoforge_args(release)).is_file()


def install(loader: str, release: str, folder: Path, java: Path) -> None:
    """Install a release's server with its loader's own installer, on the release's Java.

    Neither installer asks the EULA or creates mods/; prepare_server sees to both.
    The Fabric installer takes the server jar from Mojang, and it must have the
    sha1 Mojang publishes for the release.
    """
    info = RELEASES[release]
    if loader == "fabric":
        if FABRIC_INSTALLER is None:
            raise SystemExit(
                "FABRIC_INSTALLER is not set: take it from a throwaway project with Fabric "
                '(CLAUDE.md, "Adding a second loader")'
            )
        installer = fetch_pinned(
            fabric_installer_url(FABRIC_INSTALLER.version), FABRIC_INSTALLER.sha256
        )
        arguments = [
            "server", "-dir", ".", "-mcversion", release,
            "-loader", info.fabric.loader, "-downloadMinecraft",
        ]
        what = f"Fabric Loader {info.fabric.loader}"
    else:
        installer = fetch_pinned(
            neoforge_installer_url(info.neoforge.version), info.neoforge.installer_sha256
        )
        arguments = ["--installServer", "."]
        what = f"NeoForge {info.neoforge.version}"

    log = folder / "install.log"
    print(f"  installing {what} for Minecraft {release} (log: {log})")
    # UTF-8, whatever the platform, like every file this script leaves for CI.
    command = [
        str(java), "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8",
        "-jar", str(installer), *arguments,
    ]
    with log.open("wb") as output:
        process = launch(
            command, folder, stdin=subprocess.DEVNULL, stdout=output, stderr=subprocess.STDOUT
        )
        try:
            status = process.wait(timeout=INSTALL_TIME_LIMIT)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait()
            raise SystemExit(
                f"the {what} installer did not finish within {INSTALL_TIME_LIMIT} s; see {log}"
            ) from None
    if status != 0 or not installed(loader, release):
        raise SystemExit(f"the {what} installer failed (exit {status}); see {log}")

    if loader == "fabric":
        server = folder / "server.jar"
        sha1 = hashlib.sha1(server.read_bytes()).hexdigest() if server.is_file() else "missing"
        if sha1 != info.server_sha1:
            # Not installed, so the next run installs it again.
            (folder / "fabric-server-launch.jar").unlink(missing_ok=True)
            raise SystemExit(
                f"{server} has sha1 {sha1}, not the {info.server_sha1} Mojang publishes "
                f"for Minecraft {release}"
            )


def server_settings(
    folder: Path, loader: str, release: str, overrides: dict[str, str] | None = None
) -> dict[str, str]:
    """A server's server.properties, layered.

    Its own settings come first, or on the first boot the defaults below; then
    the machine-wide file; then a run's overrides. The port is always this
    server's own: no layer can change it.
    """
    settings = read_properties(folder / "server.properties") or {
        # This machine only: in offline mode, anyone who can reach the port can
        # join under an op's name.
        "server-ip": "127.0.0.1",
        "online-mode": "false",
        "motd": f"{NAME} dev ({LOADER_NAMES[loader]} {release})",
        "max-players": "5",
        "spawn-protection": "0",
        "view-distance": "6",
        "simulation-distance": "4",
        "sync-chunk-writes": "false",
    }
    settings.update(read_properties(GLOBAL_PROPERTIES))
    settings.update(overrides or {})
    settings["server-port"] = str(port_for(release, loader))
    return settings


def write_properties(
    folder: Path, loader: str, release: str, overrides: dict[str, str] | None = None
) -> None:
    """Write a server's server.properties from its layers (server_settings)."""
    settings = server_settings(folder, loader, release, overrides)
    (folder / "server.properties").write_text(
        "# Local development server - not for public use.\n"
        f"# Machine-wide overrides: {GLOBAL_PROPERTIES}\n"
        + "".join(f"{key}={value}\n" for key, value in settings.items()),
        encoding="utf-8",
    )


def prepare_server(loader: str, release: str, java: Path) -> Path:
    """Install a release's server of a loader when it is not yet, and lay out its folder.

    The dev-server overlay is copied first, so what is written after it wins:
    the EULA, the layered server.properties and, on Fabric, the release's
    Fabric API, the one Fabric API in mods/.
    """
    folder = server_folder(loader, release)
    folder.mkdir(parents=True, exist_ok=True)
    if not installed(loader, release):
        install(loader, release, folder, java)

    if DEV_SERVER.is_dir():
        # Its README.md is for people. A server.properties here is ignored: write_properties
        # writes each server's own from its layers (dev-server/README.md).
        left_out = {"server.properties", "README.md"}
        shutil.copytree(
            DEV_SERVER,
            folder,
            dirs_exist_ok=True,
            ignore=lambda parent, names: left_out if Path(parent) == DEV_SERVER else set(),
        )

    # Accepting the EULA here is the developer accepting it for their own local
    # test server; it is not shipped anywhere.
    (folder / "eula.txt").write_text("eula=true\n", encoding="utf-8")
    write_properties(folder, loader, release)

    mods = folder / "mods"
    mods.mkdir(exist_ok=True)
    if loader == "fabric":
        pins = RELEASES[release].fabric
        api = fetch_pinned(fabric_api_url(pins.api), pins.api_sha256)
        for jar in mods.glob("fabric-api-*.jar"):
            if jar.name != api.name and _FABRIC_API_JAR.match(jar.name):
                jar.unlink()
        if not (mods / api.name).is_file():
            shutil.copy2(api, mods / api.name)
    return folder


# A Fabric API jar, and not one of another mod whose name merely starts the same.
_FABRIC_API_JAR = re.compile(r"fabric-api-\d[\w.+-]*\.jar")


def built_jar(loader: str, release: str, *, test: bool = False) -> Path:
    """The release jar, or the test jar, the line serving a release built for a loader."""
    module = RELEASES[release].line_module
    prefix = TEST_MOD_ID if test else MOD_ID
    name = f"{prefix}-{loader}-{module}-{gradle_property('version')}.jar"
    jar = PROJECT / f"{ARTIFACT}-{loader}" / module / "build" / "libs" / name
    if not jar.is_file():
        raise SystemExit(
            f"No {jar.relative_to(PROJECT)}. Run without --no-build, or build first with:\n"
            f"  {GW} assemble"
        )
    return jar


@contextmanager
def mods_for_run(
    folder: Path, loader: str, release: str, *, test: bool = False
) -> Iterator[tuple[str, ...]]:
    """Put the line's jar into a server's mods/ in place of any earlier jar of
    this mod, and for a GameTest run the test jar and, on Fabric, the GameTest
    module, which are removed afterwards.

    The jars of this mod and its test mod for the loader go first, and only
    those (<mod id>-<loader>-* and <test mod id>-<loader>-*), so the line's
    release jar is the only one with the mod id: with two, Fabric Loader
    silently boots the one whose dependencies resolve. A GameTest run adds the
    test jar and, on Fabric, the GameTest module, each only when no file of that
    name is there, and removes afterwards only what it added. Answers the file
    names of the mod's own jars in mods/, which LogSieve counts as the mod's.
    """
    jar = built_jar(loader, release)
    ours = [jar.name]
    extras: list[Path] = []
    if test:
        extras.append(built_jar(loader, release, test=True))
        ours.append(extras[0].name)
        if loader == "fabric":
            pins = RELEASES[release].fabric
            extras.append(fetch_pinned(fabric_gametest_url(pins.gametest), pins.gametest_sha256))

    mods = folder / "mods"
    own = (f"{MOD_ID}-{loader}-", f"{TEST_MOD_ID}-{loader}-")
    for stale in mods.iterdir():
        if stale.is_file() and stale.name.startswith(own):
            stale.unlink()
    shutil.copy2(jar, mods / jar.name)

    copied: list[Path] = []
    try:
        for extra in extras:
            target = mods / extra.name
            if not target.exists():
                shutil.copy2(extra, target)
                copied.append(target)
        yield tuple(ours)
    finally:
        for target in copied:
            target.unlink(missing_ok=True)


@contextmanager
def run_properties(folder: Path, loader: str, release: str) -> Iterator[None]:
    """Give a test run the test world in server.properties, and put the file back afterwards.

    Put back byte for byte, so an interactive boot finds its own world's settings.
    """
    file = folder / "server.properties"
    kept = file.read_bytes()
    write_properties(folder, loader, release, TEST_WORLD_PROPERTIES)
    try:
        yield
    finally:
        file.write_bytes(kept)


# ---------------------------------------------------------------------------
# Server processes
# ---------------------------------------------------------------------------

# Every process started through launch(), so the exit handler can reach them all.
_CHILDREN: list[subprocess.Popen] = []

# launch()'s options for a server whose output this script reads, line by line.
# UTF-8 is what java_command has the JVM write, whatever the locale or Python's
# UTF-8 mode. A stray byte becomes U+FFFD instead of ending the thread that
# reads it.
SERVER_OUTPUT = {
    "stdout": subprocess.PIPE,
    "stderr": subprocess.STDOUT,
    "encoding": "utf-8",
    "errors": "replace",
    "bufsize": 1,
}


def launch(command: list[str], cwd: Path, **popen_kwargs) -> subprocess.Popen:
    """Start a process whose lifetime is bound to this script's.

    An IDE or terminal that kills the script must not orphan a server on its
    port: the next run would fail to bind, and nothing on screen would say why.
    On Windows the child is put into a Job Object that terminates everything in
    it once the script's handle closes, which includes the script being killed
    outright. Elsewhere the child gets its own session, so its whole process
    group can be taken down as one by the exit and signal handlers below.
    """
    if os.name == "nt":
        process = subprocess.Popen(command, cwd=cwd, **popen_kwargs)
        _bind_to_job(process)  # ctypes plumbing: the Windows Job Object section, below
    else:
        process = subprocess.Popen(command, cwd=cwd, start_new_session=True, **popen_kwargs)
    _CHILDREN.append(process)
    return process


def _kill_children(*_: object) -> None:
    """Take down every server the script started that is still running.

    Outside Windows, SIGTERM first, so the JVM runs its shutdown hooks and the
    world is saved, then SIGKILL after 10 s, because the port has to be free for
    the next run. On Windows the server is terminated outright. The Job Object
    covers the other case: the script killed before this handler can run.
    """
    for process in _CHILDREN:
        if process.poll() is not None:
            continue
        try:
            if os.name == "nt":
                process.kill()
            else:
                os.killpg(process.pid, signal.SIGTERM)
                try:
                    process.wait(timeout=10)
                except subprocess.TimeoutExpired:
                    os.killpg(process.pid, signal.SIGKILL)
            process.wait()
        except OSError:
            pass  # already gone


def _exit_on_signal(signum: int, _frame: object) -> None:
    """Turn a terminating signal into an ordinary exit, so the exit handler runs.

    The default action for SIGTERM and SIGHUP ends the interpreter without
    running atexit, which is exactly when the servers would be orphaned.
    """
    raise SystemExit(128 + signum)


atexit.register(_kill_children)
if os.name != "nt":
    signal.signal(signal.SIGTERM, _exit_on_signal)
    signal.signal(signal.SIGHUP, _exit_on_signal)


def send(process: subprocess.Popen, command: str) -> None:
    """Type a command into a server's console; a server already on its way out is left to it."""
    try:
        if process.stdin:
            process.stdin.write(command + "\n")
            process.stdin.flush()
    except (OSError, ValueError):
        pass


def forward_stdin(process: subprocess.Popen) -> None:
    """Forward this script's console input to a server, without ever closing it.

    An IDE runs the script with no interactive input at all, so this input can
    end the moment the server starts. On end-of-file the forwarding simply
    stops; the pipe stays open, and the server keeps its console, and runs,
    until it is told to stop.
    """
    def pump() -> None:
        assert process.stdin is not None
        if sys.stdin is None:
            return
        try:
            for line in sys.stdin:
                process.stdin.write(line)
                process.stdin.flush()
        except (OSError, ValueError):
            pass  # the server went away, or our own stdin was closed under us

    threading.Thread(target=pump, daemon=True).start()


def follow_output(
    process: subprocess.Popen, on_line: Callable[[str], object], timeout: float, cap: int
) -> tuple[str, str]:
    """Hand each line a server prints to on_line until it exits, and say why it was killed.

    The output is read on a daemon thread, because a read on the calling thread
    only returns when the server closes its output, which is when it exits: a
    server hanging while it starts or stops would hold the run forever, and no
    time limit checked after that read could ever fire.

    Answers (reason, text): ("", "") when the server exited by itself.
    Otherwise it was killed, and reason says what killed it, "timeout" for the
    time limit of timeout seconds or "cap" for the cap of cap bytes of console
    output, and text says it in words. A killed server is waited for, so that
    it has let go of its port and its files before anything else touches them,
    and so is the reader, so that on_line has seen every line it was handed.
    """
    capped = threading.Event()
    ended = threading.Event()

    def pump() -> None:
        assert process.stdout is not None
        written = 0
        try:
            for line in process.stdout:
                if capped.is_set():
                    continue  # read on, so the killed server's pipe drains and closes
                written += len(line.encode("utf-8", "replace"))
                if written > cap:
                    capped.set()
                    process.kill()
                    continue
                on_line(line)
        finally:
            ended.set()

    threading.Thread(target=pump, daemon=True).start()
    try:
        process.wait(timeout=timeout)
    except subprocess.TimeoutExpired:
        process.kill()
        process.wait()
        ended.wait(10)
        return "timeout", f"the time limit of {timeout:.0f} s"
    ended.wait(10)
    if capped.is_set():
        return "cap", f"the cap of {cap // 1048576} MB of console output"
    return "", ""


def stop_server(process: subprocess.Popen) -> bool:
    """Ask an interactive server to stop, and kill it if it has not within two minutes.

    Answers whether it stopped on its own. The kill is waited for, so that the
    server has let go of its port and its files before the script exits.
    """
    send(process, "stop")
    try:
        process.wait(timeout=120)
        return True
    except subprocess.TimeoutExpired:
        process.kill()
        process.wait()
        return False


# ---------------------------------------------------------------------------
# Windows Job Object: the kill-on-close binding launch() gives its children
# ---------------------------------------------------------------------------

# The Job Object the servers are assigned to. Module-level so the handle stays
# open for as long as the script runs: closing it is what kills the job, so it
# must never be closed early.
_JOB: int | None = None
_JOB_UNAVAILABLE = False


def _bind_to_job(process: subprocess.Popen) -> None:
    """Assign a Windows child to a kill-on-close Job Object.

    Standard library only, so the Win32 structures are spelled out with ctypes.
    If any call fails the script carries on without the binding and says so once.
    """
    global _JOB, _JOB_UNAVAILABLE
    if _JOB_UNAVAILABLE:
        return

    import ctypes

    JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE = 0x2000
    JobObjectExtendedLimitInformation = 9
    PROCESS_SET_QUOTA = 0x0100
    PROCESS_TERMINATE = 0x0001

    class IoCounters(ctypes.Structure):
        _fields_ = [
            (name, ctypes.c_uint64)
            for name in (
                "ReadOperationCount", "WriteOperationCount", "OtherOperationCount",
                "ReadTransferCount", "WriteTransferCount", "OtherTransferCount",
            )
        ]

    class BasicLimitInformation(ctypes.Structure):
        _fields_ = [
            ("PerProcessUserTimeLimit", ctypes.c_int64),
            ("PerJobUserTimeLimit", ctypes.c_int64),
            ("LimitFlags", ctypes.c_uint32),
            ("MinimumWorkingSetSize", ctypes.c_size_t),
            ("MaximumWorkingSetSize", ctypes.c_size_t),
            ("ActiveProcessLimit", ctypes.c_uint32),
            ("Affinity", ctypes.c_size_t),
            ("PriorityClass", ctypes.c_uint32),
            ("SchedulingClass", ctypes.c_uint32),
        ]

    class ExtendedLimitInformation(ctypes.Structure):
        _fields_ = [
            ("BasicLimitInformation", BasicLimitInformation),
            ("IoInfo", IoCounters),
            ("ProcessMemoryLimit", ctypes.c_size_t),
            ("JobMemoryLimit", ctypes.c_size_t),
            ("PeakProcessMemoryUsed", ctypes.c_size_t),
            ("PeakJobMemoryUsed", ctypes.c_size_t),
        ]

    kernel32 = ctypes.WinDLL("kernel32", use_last_error=True)
    kernel32.CreateJobObjectW.restype = ctypes.c_void_p
    kernel32.CreateJobObjectW.argtypes = [ctypes.c_void_p, ctypes.c_wchar_p]
    kernel32.SetInformationJobObject.restype = ctypes.c_int
    kernel32.SetInformationJobObject.argtypes = [
        ctypes.c_void_p, ctypes.c_int, ctypes.c_void_p, ctypes.c_uint32,
    ]
    kernel32.OpenProcess.restype = ctypes.c_void_p
    kernel32.OpenProcess.argtypes = [ctypes.c_uint32, ctypes.c_int, ctypes.c_uint32]
    kernel32.AssignProcessToJobObject.restype = ctypes.c_int
    kernel32.AssignProcessToJobObject.argtypes = [ctypes.c_void_p, ctypes.c_void_p]
    kernel32.CloseHandle.restype = ctypes.c_int
    kernel32.CloseHandle.argtypes = [ctypes.c_void_p]

    def checked(result):
        if not result:
            raise ctypes.WinError(ctypes.get_last_error())
        return result

    try:
        if _JOB is None:
            job = checked(kernel32.CreateJobObjectW(None, None))
            limits = ExtendedLimitInformation()
            limits.BasicLimitInformation.LimitFlags = JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE
            checked(kernel32.SetInformationJobObject(
                job, JobObjectExtendedLimitInformation, ctypes.byref(limits), ctypes.sizeof(limits),
            ))
            _JOB = job
        handle = checked(
            kernel32.OpenProcess(PROCESS_SET_QUOTA | PROCESS_TERMINATE, False, process.pid)
        )
        try:
            checked(kernel32.AssignProcessToJobObject(_JOB, handle))
        finally:
            kernel32.CloseHandle(handle)
    except OSError as exc:
        _JOB_UNAVAILABLE = True
        print(f"  note: server lifetime not bound to this script's ({exc.strerror or exc})")


# ---------------------------------------------------------------------------
# Server output
# ---------------------------------------------------------------------------

# The line a server prints when it has finished starting, and the one a GameTest
# server prints instead. Everything before either is the boot.
_DONE = re.compile(r"\bDone \([^)]*\)! For help, type")
_BOOTED = re.compile(_DONE.pattern + r"|\bStarted game test server\b")

# The prefix of a logged line: its time, its thread and level, and on NeoForge
# the logger and marker, as in "[12:00:00] [main/INFO] [<mod id>/]: ". Fabric's
# layout names no logger.
_PREFIX = re.compile(
    r"^\[[^\]]*\] \[[^\]]*/(?P<level>[A-Z]+)\](?: \[(?P<logger>[^\]/]*)/[^\]]*\])?: "
)

# A line that continues the exception block above it: a stack frame, the cause
# under it, or the "... N more" standing for frames already listed.
_CONTINUED = re.compile(r"^(?:\s+at |\s+\.\.\. \d+ more|\s*Caused by: |\s*Suppressed: )")

# A line that opens one: a thrown type, the JVM's line for an uncaught one, or
# a cause with nothing above it. A thrown type is printed with its package,
# which keeps out a line of prose that starts with "Error".
_THROWN = re.compile(
    r"^(?:(?:[\w$]+\.)+[\w$]*(?:Exception|Error|Throwable)\b"
    r"|Exception in thread |Caused by: |Suppressed: )"
)

# A class of this project: its package where a name starts, followed by a dot,
# so a short package such as a.b matches neither java.base nor a.bc.
_OWN_CLASS = re.compile(r"(?<![\w.$])" + re.escape(PACKAGE) + r"\.")

# The mod's mixin config, named where a name starts. Mixin's word for a target
# class that is missing names it; on NeoForge's Mixin that is only a warning,
# which the mod's mixin plugin then turns into a failure.
_MIXIN_CONFIG = re.compile(r"(?<![\w.-])" + re.escape(f"{MOD_ID}.mixins.json"))

# The line a server stopped by its EULA check logs. This script writes an
# accepted eula.txt, so a run that logs it is not the run it meant to start.
_EULA = "You need to agree to the EULA"

# Vanilla's line for a failed required test of the test mod (LogTestReporter):
# its id, where it stood and its message. The /test run of NeoForge 21.5 to
# 21.8 writes no report, so this line is all it says of a failure.
_FAILED_TEST = re.compile(
    r"^(" + re.escape(TEST_MOD_ID) + r":\S+) failed at -?\d+, -?\d+, -?\d+! (.*)$"
)


class LogSieve:
    """The console view of a test server's log: this mod's lines and no others.

    Ownership decides, not the log level. A line is the mod's when its logger
    is the mod id or the test mod id, when its message starts with the mod's
    name, or when it names one of the mod's classes or jars; it is printed. An
    exception block is the mod's when any of its lines names one of the mod's
    classes. Every other line is counted against whoever logged it and reported
    in one line at the end: the logger, with NeoForge's own abbreviated class
    loggers under "neoforge", "jvm" for the JVM's bare WARNING lines, "server"
    for lines with no logger. Until the server is done starting, every line is
    printed, since the boot is the progress a developer waits on. The log file
    on disk is never filtered.

    What fails a run is kept in ``faults``, the first line of each: a line of
    the mod's at ERROR or FATAL (the mixin audit's ERROR line at server start
    is one), an exception block of the mod's, a FATAL line from anyone (FML
    logs a declared range it refuses at FATAL, drops the mod and starts
    anyway), a missing target of the mod's mixin config, and the EULA check
    stopping the server. The level alone decides nothing else:
    NeoForge logs failed GameTests at ERROR on some releases and INFO on others.
    Vanilla's line for a failed test of the test mod is printed, as no fault.
    """

    def __init__(self, jars: tuple[str, ...] = (), *, quiet: bool = False) -> None:
        # The file names of the mod's jars in mods/: a line naming one is the mod's.
        self.marks = tuple(jars)
        self.left_out: dict[str, int] = {}
        self.faults: list[str] = []
        # A quiet sieve prints nothing, so it has no boot to print either: it
        # sorts every line it is fed, for a caller that prints them itself.
        self._quiet = quiet
        self._booting = not quiet
        # Who logged the last line with a prefix; the lines under it without
        # one, such as NeoForge's mod list, are counted against it too.
        self._source = "server"
        self._block: list[tuple[str, bool]] = []

    def feed(self, line: str) -> None:
        """Print, buffer or count one line of server output."""
        shown = self._booting
        if shown:
            self._print(line)
            if _BOOTED.search(line):
                self._booting = False

        text = line.rstrip("\r\n")
        if self._block and _CONTINUED.match(text):
            self._block.append((line, shown))
            return
        self._settle()

        prefix = _PREFIX.match(text)
        message = text[prefix.end():] if prefix else text
        if prefix:
            self._source = self._source_of(prefix)
        elif text.startswith("WARNING: "):
            self._source = "jvm"
        missing_target = (
            "@Mixin target" in message
            and "was not found" in message
            and _MIXIN_CONFIG.search(message) is not None
        )
        if (prefix and prefix.group("level") == "FATAL") or _EULA in message or missing_target:
            self._fault(text)

        if _THROWN.match(message):
            # Whose it is shows only once its last line is in: _settle decides.
            self._block.append((line, shown))
        elif _FAILED_TEST.match(message):
            # Shown, and no fault at its ERROR level: the tests' verdict decides.
            if not shown:
                self._print(line)
        elif self._is_ours(text, prefix):
            if prefix and prefix.group("level") in ("ERROR", "FATAL"):
                self._fault(text)
            if not shown:
                self._print(line)
        elif not shown:
            self._count(self._source, 1)

    def close(self) -> None:
        """Decide the exception block still buffered, once the output has ended."""
        self._settle()

    def report(self, log: Path) -> None:
        """Say in one line what was left out, by source, and where the full server
        log is, once the run is over.

        A run killed at the cap has had its logs cleared, and then neither points
        at them.
        """
        self.close()
        kept = log.is_file()
        if self.left_out:
            counted = sorted(self.left_out.items(), key=lambda entry: (-entry[1], entry[0]))
            listed = ", ".join(
                f"{source} {lines} line{'' if lines == 1 else 's'}" for source, lines in counted
            )
            see = " (see the full server log)" if kept else ""
            print(f"  left out of this log: {listed}{see}")
        if kept:
            print(f"  full server log: {log}")

    def _is_ours(self, text: str, prefix: re.Match[str] | None) -> bool:
        if prefix and prefix.group("logger") in (MOD_ID, TEST_MOD_ID):
            return True
        message = text[prefix.end():] if prefix else text
        if message.startswith(NAME + " "):
            return True
        return _OWN_CLASS.search(text) is not None or any(mark in text for mark in self.marks)

    @staticmethod
    def _source_of(prefix: re.Match[str]) -> str:
        logger = prefix.group("logger")
        if not logger:
            return "server"
        return "neoforge" if "." in logger else logger

    def _fault(self, text: str) -> None:
        fault = text.strip()
        if fault not in self.faults:
            self.faults.append(fault)

    def _print(self, line: str) -> None:
        if not self._quiet:
            sys.stdout.write("  " + line)

    def _count(self, source: str, lines: int) -> None:
        self.left_out[source] = self.left_out.get(source, 0) + lines

    def _settle(self) -> None:
        """Decide a buffered exception block, now that its last line has been seen."""
        if not self._block:
            return
        block, self._block = self._block, []
        first = block[0][0].rstrip("\r\n")
        prefix = _PREFIX.match(first)
        names_us = any(_OWN_CLASS.search(line) for line, _ in block)
        ours = names_us or self._is_ours(first, prefix)
        if names_us or (ours and prefix and prefix.group("level") in ("ERROR", "FATAL")):
            self._fault(first)
        for line, shown in block:
            if shown:
                continue
            if ours:
                self._print(line)
            else:
                self._count(self._source, 1)


def exit_text(status: int) -> str:
    """A server's exit code as Java set it: Windows reports -1 as 4294967295."""
    return str(status - (1 << 32) if status >= 1 << 31 else status)


def report_verdict(
    report: Path, loader: str, release: str, *, status: int = 0, fault: str | None = None
) -> int:
    """Print the verdict of a GameTest run's JUnit report, and answer the exit status.

    Both loaders write vanilla's report: <testsuite>s nested in a <testsuite>,
    a <testcase> per test at any depth, with a <failure> for a failed required
    test and a <skipped> for a failed optional one. Each failure is printed
    with the report's message, which names more than the console does. A run
    with no report or no test in it fails, and so does one that ``fault`` names
    (LogSieve) or whose server exited non-zero (``status``), whatever the
    report says: the exit code counts the failed required tests, and is -1 when
    the selector matched nothing.
    """
    where = f"{LOADER_NAMES[loader]} {release}"
    if not report.is_file():
        # A server that dies before the tests run, on a missing mixin target say,
        # writes no report, and its fault says why.
        verdict = fault or f"no report written on {where} - the GameTests did not run to completion"
        print(f"  FAILED: {verdict}")
        return 1
    try:
        cases = list(ElementTree.parse(report).getroot().iter("testcase"))
    except ElementTree.ParseError as failure:
        print(f"  FAILED: {report} is not a whole report ({failure})")
        return 1

    failed = skipped = 0
    for case in cases:
        problem = case.find("failure")
        if problem is not None:
            failed += 1
            print(f"  FAIL  {case.get('name', '?')}")
            message = (problem.get("message") or problem.text or "").strip()
            if message:
                print(f"        {message}")
        elif case.find("skipped") is not None:
            skipped += 1
            print(f"  optional test failed: {case.get('name', '?')}")

    if not cases:
        verdict = "the report holds no test - nothing ran"
    elif failed:
        verdict = f"{failed} of {len(cases)} GameTest(s) failed on {where}"
    elif fault:
        verdict = fault
    elif status != 0:
        verdict = f"the GameTest server exited with {exit_text(status)}"
    else:
        optional = f", {skipped} optional test(s) failed" if skipped else ""
        print(f"  OK - {len(cases) - skipped} GameTest(s) passed on {where}{optional}")
        return 0
    print(f"  FAILED: {verdict}")
    return 1


# ---------------------------------------------------------------------------
# Actions
# ---------------------------------------------------------------------------


def banner(kind: str, version: str) -> None:
    """Open an action's output with a rule naming it, always 60 columns wide."""
    print()
    head = f"== {kind} {version} "
    print(head + "=" * max(0, 60 - len(head)))


def gradle_build(releases: list[str], loaders: list[str]) -> None:
    """Assemble the jars of the lines a run's releases need, on its loaders only."""
    # The project's own wrapper, so a build from here runs on the same JDK, and in
    # the same Gradle daemon, as gw from the command line. Through sh outside
    # Windows, so a gw.sh that lost its execute bit still runs.
    wrapper = [str(PROJECT / "gw.cmd")] if os.name == "nt" else ["sh", str(PROJECT / "gw.sh")]
    tasks = sorted({
        f":{ARTIFACT}-{loader}:{RELEASES[release].line_module}:assemble"
        for release in releases
        for loader in loaders
    })
    # --quiet hides the one-time Minecraft setup of every module, which makes a
    # first build long (README.md, "Quick start").
    print("  building (a first build sets up each release's Minecraft; that can take long)...")
    result = subprocess.run([*wrapper, *tasks, "--quiet"], cwd=PROJECT, check=False)
    if result.returncode != 0:
        raise SystemExit(f"build failed; rerun {GW} assemble without --quiet for the full output")


def check_port(folder: Path, loader: str, release: str) -> None:
    """Stop the run before anything is touched when the server's port is taken.

    A server of this release and loader still running, from the IDE or another
    run.py, holds its jars and its server.properties; another project's server
    may hold the port too, since ports go by position. The probe binds where
    the server will, its server-ip or every address. socket.create_server sets
    SO_REUSEADDR outside Windows, as the JVM does there, so a port that a
    server which just stopped left in TIME_WAIT is free, as it is for the server.
    """
    port = port_for(release, loader)
    host = server_settings(folder, loader, release).get("server-ip", "")
    try:
        socket.create_server((host, port)).close()
    except OSError:
        raise SystemExit(
            f"port {port} of the {LOADER_NAMES[loader]} {release} server is in use: stop "
            "what holds it (that server still running from the IDE or another run.py, or "
            "another project's server) and run again"
        ) from None


def provision(loader: str, release: str) -> tuple[Path, Path]:
    """A release's server folder for a loader, installed and laid out, and its Java.

    A port in use stops the run first (check_port).
    """
    check_port(server_folder(loader, release), loader, release)
    java = find_java(RELEASES[release].java)
    return prepare_server(loader, release, java), java


def java_command(java: Path, memory: str, *flags: str, piped: bool = True) -> list[str]:
    """The start of a server's command line: its Java, its heap and its JVM flags.

    ``piped`` is for a server whose output this script reads, as UTF-8
    (SERVER_OUTPUT). The JVM writes a pipe in the system code page unless told
    otherwise. A server that writes to this script's own output keeps the JVM's
    default, which suits a console.
    """
    encoding = ["-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8"] if piped else []
    return [str(java), f"-Xms{memory}", f"-Xmx{memory}", *encoding, *flags]


def server_command(
    loader: str, release: str, java: Path, memory: str, *flags: str, piped: bool = True
) -> list[str]:
    """The command line that starts a release's plain server of a loader, up to its arguments."""
    start = java_command(java, memory, *flags, piped=piped)
    if loader == "fabric":
        return [*start, "-jar", "fabric-server-launch.jar"]
    return [*start, "@user_jvm_args.txt", f"@{neoforge_args(release)}"]


def run_test_server(
    folder: Path,
    command: list[str],
    universe: str,
    cap: int,
    on_line: Callable[[subprocess.Popen, str], object],
    *,
    console: bool = False,
) -> tuple[str, int]:
    """Run a test server to its end in a fresh universe, and answer why it was killed, and its exit.

    command is the server's command line without --universe: this adds the
    universe it deletes before and after. ``console`` gives the server a
    console for on_line to type into. A server killed at the cap has its logs/
    cleared, since they grow past the cap. An interrupt kills the server before
    anything is cleaned up.
    """
    remove(folder / universe)
    try:
        process = launch(
            [*command, "--universe", universe],
            folder,
            stdin=subprocess.PIPE if console else subprocess.DEVNULL,
            **SERVER_OUTPUT,
        )
        try:
            reason, killed = follow_output(
                process, lambda line: on_line(process, line), TIME_LIMIT, cap
            )
        except BaseException:
            # Ctrl+C or a signal: the server goes before anything touches what it holds,
            # its universe below and the mods/ and server.properties of the with-blocks
            # around this call.
            process.kill()
            process.wait()
            raise
    finally:
        remove(folder / universe)
    if reason == "cap":
        shutil.rmtree(folder / "logs", ignore_errors=True)
        print(f"  server logs cleared: they grow past the cap ({folder / 'logs'})")
    return killed, process.returncode


def smoke(release: str, loader: str, *, memory: str) -> int:
    """Boot a release's server with the line's jar, confirm the mod loaded, and stop it.

    This is the check that validates the multi-version claim. The build proves
    each module compiles against its own release, and the binary check that
    the line's classes link there; only a boot shows the jar loading on this
    release with the backends it picks, and some requirements a release adds
    fail nowhere else.

    The verdict is this mod's alone. The server must finish starting, the mod
    must log its initialising line on this loader, nothing may fault
    (LogSieve; the mixin audit's ERROR line at server start is one), and the
    server must exit 0 once it is told to stop.
    """
    label = LOADER_NAMES[loader]
    banner(f"smoke {label}", release)
    folder, java = provision(loader, release)
    started = initialised = False

    with mods_for_run(folder, loader, release) as jars, run_properties(folder, loader, release):
        # Fed every line: the mod's own errors show up most often while it loads.
        sieve = LogSieve(jars, quiet=True)

        def watch(process: subprocess.Popen, line: str) -> None:
            nonlocal started, initialised
            # Every line, the stop included, not a LogSieve's view: the whole
            # run is the boot check.
            sys.stdout.write("  " + line)
            sieve.feed(line)
            if f"{NAME} initialising on {label}" in line:
                initialised = True
            if not started and _DONE.search(line):
                started = True
                send(process, "stop")

        command = server_command(loader, release, java, memory, *TEST_SERVER_JVM_FLAGS)
        killed, status = run_test_server(
            folder, [*command, "nogui"], SMOKE_UNIVERSE, SMOKE_CAP, watch, console=True
        )
        sieve.close()

    if killed:
        verdict = f"killed at {killed}"
    elif sieve.faults:
        verdict = sieve.faults[0]
    elif not started:
        verdict = "the server never finished starting"
    elif not initialised:
        verdict = f"{NAME} did not log its initialising line on {label}"
    elif status != 0:
        verdict = f"the server exited with {exit_text(status)} after the stop"
    else:
        print(f"  OK - {NAME} loaded on {label} {release}")
        return 0
    print(f"  FAILED: {verdict}")
    return 1


def boot(release: str, loader: str, *, debug: bool, memory: str, extra: list[str]) -> int:
    """Boot a release's server of a loader with the line's jar, and hand over its console.

    It plays in the server folder's own world, which stays between boots.
    """
    label = LOADER_NAMES[loader]
    banner(f"Minecraft {label}", release)
    folder, java = provision(loader, release)
    with mods_for_run(folder, loader, release) as jars:
        print(f"  mod    : {jars[0]}")
        print(f"  java   : {java}")
        print(f"  dir    : {folder}")
        print(f"  connect: localhost:{port_for(release, loader)}")
        if debug:
            print(f"  debug  : attach a JVM debugger to 127.0.0.1:{DEBUG_PORT}")
        print()

        debugger = ["-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,"
                    f"address=127.0.0.1:{DEBUG_PORT}"]
        command = server_command(
            loader, release, java, memory, *(debugger if debug else []), piped=False
        )
        # The server reads its console as UTF-8 whatever the locale; errors="replace", as in
        # SERVER_OUTPUT, so a stray character cannot end forward_stdin.
        process = launch(
            [*command, "nogui", *extra], folder, stdin=subprocess.PIPE,
            encoding="utf-8", errors="replace",
        )
        forward_stdin(process)
        try:
            return process.wait()
        except KeyboardInterrupt:
            print("\n  stopping server")
            if not stop_server(process):
                print(
                    "  the server did not stop within 2 minutes and was killed; "
                    "its world was not saved"
                )
            return process.returncode


def gametest(release: str, loader: str, *, memory: str) -> int:
    """Run the GameTests on a release's server of a loader, with the line's jars.

    The test mod's jar goes into mods/ for the run, and on Fabric the GameTest
    module with it. The path the tests take depends on the loader and, on
    NeoForge, on the FML generation; the constants above name them. A fault
    fails the run whatever the tests report (LogSieve; the mixin audit's ERROR
    line at server start is one).
    """
    label = LOADER_NAMES[loader]
    banner(f"GameTest {label}", release)
    folder, java = provision(loader, release)
    if (
        loader == "neoforge"
        and at_least(release, NEOFORGE_TEST_COMMAND_FROM)
        and not at_least(release, NEOFORGE_GAMETEST_SERVER_FROM)
    ):
        return test_command_run(release, folder, java, memory=memory)

    report = folder / REPORT
    remove(report)
    # Vanilla's reporter does not create the report's folder, and the server
    # crashes at the end of the run without it.
    report.parent.mkdir(parents=True, exist_ok=True)
    flags = [*TEST_SERVER_JVM_FLAGS]
    if loader == "fabric":
        # Fabric API creates the report's folder before saving it, taken from the
        # path's parent: a bare file name has none and the server dies with a
        # NullPointerException after the tests, so "./" gives it one.
        flags += ["-Dfabric-api.gametest", f"-Dfabric-api.gametest.report-file=./{REPORT}"]
        if at_least(release, FABRIC_FILTER_FROM):
            flags.append(f"-Dfabric-api.gametest.filter={TEST_MOD_ID}:*")
        command = [
            *server_command(loader, release, java, memory, *flags),
            "nogui", "--world", GAMETEST_WORLD,
        ]
    elif not at_least(release, NEOFORGE_TEST_COMMAND_FROM):
        flags += ["-Dneoforge.gameTestServer=true", f"-D{TEST_MOD_ID}.report-file={REPORT}"]
        command = [
            *server_command(loader, release, java, memory, *flags),
            "--world", GAMETEST_WORLD,
        ]
    else:
        # Derived from the installed args file at every run, never from a copy
        # another run left behind.
        installed_args = (folder / neoforge_args(release)).read_text(encoding="utf-8")
        swapped, found = re.subn(
            r"^net\.neoforged\.fml\.startup\.Server$",
            "net.neoforged.fml.startup.GameTestServer",
            installed_args,
            flags=re.MULTILINE,
        )
        if found != 1:
            raise SystemExit(
                f"{neoforge_args(release)} names no net.neoforged.fml.startup.Server line"
            )
        (folder / GAMETEST_ARGS).write_text(swapped, encoding="utf-8", newline="\n")
        command = [
            *java_command(java, memory, *flags),
            "@user_jvm_args.txt", f"@{GAMETEST_ARGS}",
            "--tests", f"{TEST_MOD_ID}:*", "--report", REPORT,
        ]

    with mods_for_run(folder, loader, release, test=True) as jars:
        sieve = LogSieve(jars)
        killed, status = run_test_server(
            folder, command, GAMETEST_UNIVERSE, GAMETEST_CAP, lambda _, line: sieve.feed(line)
        )
        # On a kill too: the exception block still buffered may be the cause.
        sieve.report(folder / "logs" / "latest.log")
    if killed:
        print(f"  FAILED: killed at {killed}")
        return 1
    return report_verdict(
        report, loader, release, status=status, fault=sieve.faults[0] if sieve.faults else None
    )


# NeoForge 21.5 to 21.8's /test run as the console reports it (vanilla's
# TestCommand): a line once every test has finished, and the verdict. Each
# failed test has a _FAILED_TEST line of its own.
_TESTS_COMPLETE = re.compile(r"Game Test complete! (\d+) test\(s\) were run")
_TESTS_PASSED = "All required tests passed"
_TESTS_FAILED = re.compile(r"\d+ required tests?(?:\(s\))? failed")


def test_command_run(release: str, folder: Path, java: Path, *, memory: str) -> int:
    """Run the GameTests of NeoForge 21.5 to 21.8 through vanilla's /test command.

    The plain server starts in the test world; once it is done, the script types
    "test run <test mod id>:*" into its console, and once the tests are complete,
    "stop". The test mod's tick shim advances the tests, which NeoForge does
    not in production. The command writes no report, so the console's summary
    is the verdict, with a line for each failed test.
    """
    started = passed = False
    complete = failed = ""
    failures: list[tuple[str, str]] = []

    with mods_for_run(folder, "neoforge", release, test=True) as jars, run_properties(
        folder, "neoforge", release
    ):
        sieve = LogSieve(jars)

        def watch(process: subprocess.Popen, line: str) -> None:
            nonlocal started, passed, complete, failed
            sieve.feed(line)
            prefix = _PREFIX.match(line)
            message = line[prefix.end():] if prefix else line
            if not started and _DONE.search(line):
                started = True
                send(process, f"test run {TEST_MOD_ID}:*")
            if (count := _TESTS_COMPLETE.search(line)) and not complete:
                complete = count.group(1)
                send(process, "stop")
            if _TESTS_PASSED in line:
                passed = True
            if _TESTS_FAILED.search(line):
                failed = message.strip()
            if test := _FAILED_TEST.match(message.rstrip("\r\n")):
                failures.append((test.group(1), test.group(2)))

        command = server_command("neoforge", release, java, memory, *TEST_SERVER_JVM_FLAGS)
        killed, status = run_test_server(
            folder, [*command, "nogui"], GAMETEST_UNIVERSE, GAMETEST_CAP, watch, console=True
        )
        sieve.report(folder / "logs" / "latest.log")

    for name, message in failures:
        print(f"  FAIL  {name}")
        print(f"        {message}")
    if killed:
        verdict = f"killed at {killed}"
    elif failed:
        verdict = failed
    elif sieve.faults:
        verdict = sieve.faults[0]
    elif not started:
        verdict = "the server never finished starting"
    elif not complete:
        verdict = "the tests never completed"
    elif not passed:
        verdict = "the console reported no verdict for the tests"
    elif status != 0:
        verdict = f"the server exited with {exit_text(status)} after the stop"
    else:
        print(f"  OK - {complete} GameTest(s) passed on NeoForge {release}")
        return 0
    print(f"  FAILED: {verdict}")
    return 1


def pins_text(loader: str, info: Release) -> str:
    if loader == "fabric":
        return f"Loader {info.fabric.loader}, API {info.fabric.api}"
    return info.neoforge.version + (" (beta)" if info.neoforge.beta else "")


def list_releases() -> None:
    print(
        f"\nThe boot list of {NAME} holds {len(RELEASES)} Minecraft release(s), "
        f"each on {LOADERS_TEXT}:\n"
    )
    for release, info in RELEASES.items():
        for index, loader in enumerate(LOADERS):
            head = f"{release:<9} Java {info.java:<3} {info.line_module:<6}" if index == 0 else ""
            state = "installed" if installed(loader, release) else "not installed"
            marker = " (default)" if release == DEV_VERSION and index == 0 else ""
            print(
                f"  {head:<26}{LOADER_NAMES[loader]:<9} {pins_text(loader, info):<38} "
                f"port {port_for(release, loader):<6} {state}{marker}"
            )
    state = "" if GLOBAL_PROPERTIES.exists() else "  (none - create it to set one)"
    print(f"\nDefault version:  {DEV_VERSION} (devVersion in gradle.properties)")
    if DEV_VERSION not in RELEASES:
        print("                  not in the boot list, so run.py refuses it")
    print(f"Server folders:   {RUN_ROOT}")
    print(f"Downloads:        {DOWNLOADS} (shared by every project)")
    print(f"Shared overrides: {GLOBAL_PROPERTIES}{state}")


def clean(release: str | None, loaders: list[str]) -> None:
    """Delete a release's server folders, or, with none, every server folder of
    the loaders, a release dropped from RELEASES included.

    Nothing outside RUN_ROOT is deleted: a target that resolves elsewhere,
    through a link or a hand-edited RELEASES entry, stops the clean before
    anything is deleted.
    """
    if release:
        targets = [server_folder(loader, release) for loader in loaders]
    else:
        targets = [
            path
            for loader in loaders
            for path in sorted(RUN_ROOT.glob(f"{loader}-*"))
            if path.is_dir()
        ]

    root = RUN_ROOT.resolve()
    for target in targets:
        resolved = target.resolve()
        if root not in resolved.parents:
            raise SystemExit(f"refusing to delete {resolved}: it is outside {RUN_ROOT}")

    for target in targets:
        if target.exists():
            shutil.rmtree(target, ignore_errors=True)
            if target.exists():
                print(f"  could not remove all of {target}; is its server still running?")
            else:
                print(f"  removed {target}")

    print(f"Downloaded installers and mod jars are kept, shared by every project: {DOWNLOADS}")


def run_each(
    action: Callable[..., int], releases: list[str], loaders: list[str], **options
) -> int:
    """Run an action on every release and loader in turn, and sum the runs up in one line."""
    failures = [
        f"{loader} {release}"
        for release in releases
        for loader in loaders
        if action(release, loader, **options)
    ]
    if failures:
        print(f"\nFAILED on: {', '.join(failures)}")
        return 1
    print(f"\nAll {len(releases) * len(loaders)} run(s) passed.")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(
        description=f"Build {NAME} and run it on {LOADERS_TEXT} servers."
    )
    parser.add_argument(
        "version",
        nargs="?",
        help=f"Minecraft release, or 'all'. Default: devVersion in gradle.properties, "
        f"{DEV_VERSION}",
    )
    parser.add_argument(
        "--loader",
        choices=LOADERS,
        help=f"the loader to run on. Default: {LOADERS[0]} for an interactive boot, "
        "every loader for --smoke, --gametest, --clean and all",
    )
    parser.add_argument("--no-build", action="store_true", help="skip the Gradle build")
    parser.add_argument(
        "--debug", action="store_true", help="expose a JDWP debug port on an interactive boot"
    )
    parser.add_argument(
        "--memory", default=DEFAULT_MEMORY, help="heap size (default %(default)s)"
    )
    # One run does one thing, and two of these together would quietly do only one.
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument(
        "--smoke",
        action="store_true",
        help="boot, verify the mod loads, stop, and exit non-zero on failure",
    )
    mode.add_argument(
        "--gametest", action="store_true", help="run the GameTests and exit non-zero on failure"
    )
    mode.add_argument(
        "--list", action="store_true", help="show the boot list, its pins and what is installed"
    )
    mode.add_argument(
        "--clean",
        action="store_true",
        help="delete the release's server folders ('all': every release's); downloads are kept",
    )
    parser.add_argument(
        "server_args",
        nargs="*",
        help="extra args for an interactive server, after the release and --, as in: "
        f"run.py {DEV_VERSION} -- --forceUpgrade",
    )
    arguments = parser.parse_args()
    version = arguments.version or DEV_VERSION
    # 'all' is never an interactive boot: booting several servers interactively
    # would just block on the first one.
    interactive = not (
        arguments.smoke or arguments.gametest or arguments.list or arguments.clean
        or version == "all"
    )
    if not interactive and (arguments.debug or arguments.server_args):
        parser.error("--debug and server args apply to an interactive boot only")

    if arguments.list:
        list_releases()
        return 0

    # Before --clean too, which deletes the folders named after the release.
    if version != "all" and version not in RELEASES:
        # The IDE's runs pass devVersion too, so its value may be one never typed.
        source = " (devVersion in gradle.properties)" if version == DEV_VERSION else ""
        parser.error(
            f"'{version}'{source} is not in the boot list: {', '.join(RELEASES)}. "
            'Booting another release: CLAUDE.md, "Booting another release".'
        )

    loaders = [arguments.loader] if arguments.loader else list(LOADERS)
    if arguments.clean:
        clean(None if version == "all" else version, loaders)
        return 0

    # Settled before the build, which assembles only what the run boots.
    releases = list(RELEASES) if version == "all" else [version]
    if interactive:
        loaders = [arguments.loader or LOADERS[0]]
    if not arguments.no_build:
        gradle_build(releases, loaders)

    memory = arguments.memory
    if arguments.gametest:
        return run_each(gametest, releases, loaders, memory=memory)
    if not interactive:
        return run_each(smoke, releases, loaders, memory=memory)
    return boot(
        version, loaders[0], debug=arguments.debug, memory=memory, extra=arguments.server_args
    )


if __name__ == "__main__":
    if sys.version_info < (3, 10):
        raise SystemExit("Python 3.10+ required")
    # Piped output, as the IDE's run tasks read it, is encoded in the system
    # code page on Windows, which cannot spell every path; an escape is printed
    # in place of a crash. Piped output, CI's too, is also block-buffered unless
    # told otherwise: a line this script prints would show only after the output
    # of the server or Gradle it starts next, which writes to the pipe directly.
    sys.stdout.reconfigure(errors="backslashreplace", line_buffering=True)
    # Git Bash's own terminal, mintty, hands Python a pipe that it fills with
    # UTF-8, which Python would read in the system code page; forward_stdin
    # passes what is typed there on to the server.
    if os.environ.get("MSYSTEM") and sys.stdin and not sys.stdin.isatty():
        sys.stdin.reconfigure(encoding="utf-8")
    try:
        raise SystemExit(main())
    except KeyboardInterrupt:
        # One line, not a traceback: a test server was killed on the way out
        # (run_test_server), and _kill_children takes any other child.
        print("\n  interrupted")
        raise SystemExit(130) from None
