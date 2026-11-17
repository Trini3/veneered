#!/bin/bash
# Build the jar, check it matches the source resources, then copy it to project files.
set -e
cd "$(dirname "$0")/.."
export JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64
./gradlew build -q 2>&1 | grep -v JAVA_TOOL || true
python3 - <<'PY'
import zipfile, os, sys
z = zipfile.ZipFile('build/libs/veneer-0.1.0.jar')
bad = []
for root in ['src/main/resources', 'src/client/resources']:
    for d, _, fs in os.walk(root):
        for f in fs:
            p = os.path.join(d, f); rel = os.path.relpath(p, root)
            if rel == 'fabric.mod.json':
                continue
            try:
                if z.read(rel) != open(p, 'rb').read(): bad.append(rel)
            except KeyError:
                bad.append(rel + ' (missing)')
if bad:
    sys.exit('jar out of date: ' + ', '.join(bad[:5]))
print('jar matches source resources')
PY
cp build/libs/veneer-0.1.0.jar /mnt/project-files/veneer-0.1.0.jar
cd build/libs && python3 -c "
import zipfile; z=zipfile.ZipFile('/mnt/project-files/veneer-download/veneer-0.1.0.zip','w',zipfile.ZIP_DEFLATED); z.write('veneer-0.1.0.jar'); z.close()"
unzip -p /mnt/project-files/veneer-download/veneer-0.1.0.zip veneer-0.1.0.jar > /tmp/claude-0/z.jar && unzip -p /tmp/claude-0/z.jar data/veneer/recipe/frame_pane.json | grep category
