"""Build the single-jar distribution (frontend bundled into the Spring Boot jar).

Usage (from the repository root):
    python scripts/build_single_jar.py
    python scripts/build_single_jar.py --skip-frontend      # reuse frontend/dist
    python scripts/build_single_jar.py --mvn /path/to/mvn   # Maven not on PATH

Requires only the Python standard library, plus Node/npm for the frontend build
and Maven for the backend package step.
"""
import argparse
import os
import shutil
import subprocess
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FRONTEND = ROOT / "frontend"
BACKEND = ROOT / "backend"
INDEX = FRONTEND / "dist" / "index.html"
JAR = BACKEND / "target" / "minidesk.jar"
STATIC_ENTRY = "BOOT-INF/classes/static/index.html"


def windows_executable(path):
    """Maven Wrapper unpacks both `mvn` (sh) and `mvn.cmd`; Python cannot spawn the sh one."""
    if os.name != "nt":
        return str(path)
    batch = Path(str(path)).with_suffix(".cmd")
    return str(batch) if batch.is_file() else str(path)


def resolve_exec(explicit, name):
    if explicit:
        return explicit
    for candidate in (name, name + ".cmd") if os.name == "nt" else (name,):
        found = shutil.which(candidate)
        if found:
            return found
    return None


def resolve_mvn(explicit):
    found = resolve_exec(explicit or os.environ.get("MVN_PATH"), "mvn")
    if found:
        return found
    # Maven Wrapper distributions are commonly unpacked under ~/.m2/wrapper
    wrapper = Path.home() / ".m2" / "wrapper" / "dists"
    candidates = sorted(wrapper.glob("apache-maven-*/**/bin/mvn"), reverse=True)
    if candidates:
        return windows_executable(candidates[0])
    return None


def run(command, cwd):
    printable = " ".join(str(item) for item in command)
    print(f"\n$ {printable}   (cwd={cwd})", flush=True)
    if os.name == "nt" and str(command[0]).lower().endswith((".cmd", ".bat")):
        command = ["cmd.exe", "/c", str(command[0]), *[str(item) for item in command[1:]]]
    completed = subprocess.run(command, cwd=str(cwd), check=False)
    if completed.returncode != 0:
        raise SystemExit(f"command failed with exit code {completed.returncode}: {printable}")


def build_frontend(npm, skip):
    if skip:
        print("skip frontend build (--skip-frontend)")
    else:
        if npm is None:
            raise SystemExit("npm not found; pass --npm /path/to/npm or use --skip-frontend")
        if not (FRONTEND / "node_modules").is_dir():
            run([npm, "ci"], FRONTEND)
        run([npm, "run", "typecheck"], FRONTEND)
        run([npm, "run", "build"], FRONTEND)
    if not INDEX.is_file():
        raise SystemExit(f"frontend build output missing: {INDEX}")


def verify_jar():
    if not JAR.is_file():
        raise SystemExit(f"jar not produced: {JAR}")
    with zipfile.ZipFile(JAR) as archive:
        names = archive.namelist()
        if STATIC_ENTRY not in names:
            raise SystemExit(f"jar does not contain {STATIC_ENTRY}; was -Psingle-jar active?")
        assets = [name for name in names if name.startswith("BOOT-INF/classes/static/assets/")]
        print(f"bundled static files: {len([n for n in names if n.startswith('BOOT-INF/classes/static/')])}"
              f" (assets: {len(assets)})")
        return JAR.stat().st_size


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--skip-frontend", action="store_true", help="reuse existing frontend/dist")
    parser.add_argument("--npm", help="path to npm executable")
    parser.add_argument("--mvn", help="path to mvn executable (or set MVN_PATH)")
    parser.add_argument("--no-clean", action="store_true", help="keep previous target/classes")
    parser.add_argument("--offline", action="store_true", help="pass -o to Maven (no network)")
    args = parser.parse_args()

    build_frontend(resolve_exec(args.npm, "npm"), args.skip_frontend)
    mvn = resolve_mvn(args.mvn)
    if mvn is None:
        raise SystemExit("mvn not found; pass --mvn /path/to/mvn or set MVN_PATH")
    command = [mvn, "-Psingle-jar", "-DskipTests"]
    if args.offline:
        command.append("-o")
    if not args.no_clean:
        command.append("clean")
    command.append("package")
    run(command, BACKEND)

    size = verify_jar()
    print(f"\nSingle-jar build ready: {JAR} ({size / 1024 / 1024:.1f} MB)")
    print(f"Run it with:  java -jar {JAR} --server.port=18080")
    return 0


if __name__ == "__main__":
    sys.exit(main())
