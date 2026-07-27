from pathlib import Path
import re
import subprocess

ROOT=Path('.')
subprocess.run(['python3','tools/prepare_v131.py'],check=True)

index=ROOT/'app/src/main/assets/index.html'
s=index.read_text('utf-8')
tag='    <script src="events-table-v2.js"></script>\n'
s=s.replace(tag,'')
if '</body>' not in s:
    raise SystemExit('Missing </body>')
s=s.replace('</body>',tag+'</body>',1)
index.write_text(s,'utf-8')

gradle=ROOT/'app/build.gradle'
g=gradle.read_text('utf-8')
g=re.sub(r'versionCode\s+\d+','versionCode 12',g)
g=re.sub(r"versionName\s+'[^']+'","versionName '1.3.2'",g)
gradle.write_text(g,'utf-8')

print('Prepared v1.3.2: full-width events table with row color highlighting and compact color cells.')
