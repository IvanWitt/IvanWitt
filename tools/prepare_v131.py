from pathlib import Path
import re
import subprocess

ROOT=Path('.')
subprocess.run(['python3','tools/prepare_v130.py'],check=True)

index=ROOT/'app/src/main/assets/index.html'
s=index.read_text('utf-8')
# v3 loads after v2 and replaces only the Calendar Round page hooks.
tag='    <script src="calendar-mechanism-v3.js"></script>\n'
s=s.replace(tag,'')
if '</body>' not in s:
    raise SystemExit('Missing </body>')
s=s.replace('</body>',tag+'</body>',1)
index.write_text(s,'utf-8')

gradle=ROOT/'app/build.gradle'
g=gradle.read_text('utf-8')
g=re.sub(r'versionCode\s+\d+','versionCode 11',g)
g=re.sub(r"versionName\s+'[^']+'","versionName '1.3.1'",g)
gradle.write_text(g,'utf-8')

print('Prepared v1.3.1: fixed-center meshed sandstone mechanism with nested rotors and rack drive.')
