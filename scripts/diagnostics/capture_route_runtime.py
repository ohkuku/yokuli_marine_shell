"""Export a bounded immutable nav fixture and the already compiled host adapter.
The tiny SQLite contains only the exact source identity. A raw-facts fallback
therefore fails explicitly; it never substitutes empty facts for chart data.
"""
from pathlib import Path
import json, sqlite3, shutil, struct, subprocess
root=Path('/tmp/yokuli-route-probe')
repro=root/'repro'
repro.mkdir(exist_ok=True)
build=Path('tools/maritime-compiler/build.gradle.kts')
with build.open('a') as f:
    f.write('''
tasks.register("exportProbeRuntime") {
    dependsOn(tasks.named("classes"))
    doLast {
        val target = file("/tmp/yokuli-route-probe/repro")
        sourceSets.main.get().runtimeClasspath.forEach { entry ->
            copy {
                from(entry)
                into(java.io.File(target, if(entry.isDirectory) "classes" else "lib"))
            }
        }
    }
}
''')
subprocess.run(['./gradlew','--no-daemon',':tools:maritime-compiler:exportProbeRuntime','--console=plain'],check=True)
fixture=repro/'fixture';nav=fixture/'runtime/navigation';nav.mkdir(parents=True,exist_ok=True)
count=0
for path in (root/'runtime/navigation').glob('*.nav'):
    with path.open('rb') as f:
        magic,version,n=struct.unpack('>iii',f.read(12));assert magic==0x594b4e31 and version==3
        h=json.loads(f.read(n));r=h['region']
    if 2836<=r['x']<=2839 and 424<=r['y']<=427:
        shutil.copy2(path,nav/path.name);count+=1
shutil.copy2(root/'catalog.json',fixture/'catalog.json')
source=sqlite3.connect((root/'features.sqlite').as_uri()+'?mode=ro',uri=True)
identity=source.execute('SELECT identity,content_hash FROM native_content').fetchone();source.close()
target=sqlite3.connect(fixture/'features.sqlite')
target.execute('CREATE TABLE native_content(identity TEXT NOT NULL,content_hash TEXT NOT NULL)')
target.execute('INSERT INTO native_content VALUES (?,?)',identity);target.commit();target.close()
src=repro/'source';src.mkdir(exist_ok=True)
for path in Path('runtime/marine-local/src/main/java/com/yokuli/runtime/marine/planning').glob('*.kt'):shutil.copy2(path,src/path.name)
for path in Path('tools/maritime-compiler/src/main/kotlin/com/yokuli/compiler').glob('*.kt'):shutil.copy2(path,src/path.name)
(repro/'README.txt').write_text('Host reproduction only. Exact production source and verified published nav pieces for a bounded Auckland window. The SQLite file contains source identity ONLY; raw source queries are deliberately unavailable. No Android Core/UI/device. No tide or safety certification.\n')
print('Exported nav pieces',count)
shutil.make_archive(str(root/'route-host-runtime'),'zip',repro)
