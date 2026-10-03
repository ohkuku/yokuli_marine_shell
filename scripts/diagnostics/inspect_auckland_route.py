"""Read-only published-package inspection, not execution of the Android planner."""
from __future__ import annotations
import hashlib, io, json, math, pathlib, sqlite3, struct, sys, time, urllib.request, zipfile, zlib
from geographiclib.geodesic import Geodesic
from shapely import from_wkb
from shapely.geometry import Point, Polygon
REF='05229686ca880b434157dfb56dd71d4415e561d1'
URL=f'https://media.githubusercontent.com/media/ohkuku/yokuli_marine_shell/{REF}/chart-library/packages/nz-linz-native-2026-10-03.yklpkg'
SHA='0259dc0b71842e5615456aa35947b6745f40d9170c19fa944b407e8128c8d576'
SIZE=1211654336
POINTS={'A':(-36.786067,174.672049),'B':(-36.744436,174.807857)}
ROOT=pathlib.Path(sys.argv[1] if len(sys.argv)>1 else '/tmp/yokuli-route-probe').resolve()
ROOT.mkdir(parents=True,exist_ok=True)
def emit(event,**values):
    print(json.dumps({'event':event,**values},ensure_ascii=False,allow_nan=False),flush=True)
def digest_file(path):
    d=hashlib.sha256()
    with open(path,'rb') as f:
        while b:=f.read(4*1024*1024):d.update(b)
    return d.hexdigest()
def download():
    path=ROOT/'nz.yklpkg'
    if not path.exists():
        req=urllib.request.Request(URL,headers={'User-Agent':'Yokuli-readonly-route-diagnostic'})
        digest=hashlib.sha256();total=0;last=0
        with urllib.request.urlopen(req,timeout=90) as src,open(path,'wb') as dst:
            while b:=src.read(4*1024*1024):
                dst.write(b);digest.update(b);total+=len(b)
                assert total<=SIZE,'PACKAGE_SIZE_OVERFLOW'
                if total-last>=128*1024*1024:emit('download',bytes=total);last=total
        assert total==SIZE and digest.hexdigest()==SHA,'PACKAGE_HASH_OR_SIZE_MISMATCH'
    else:assert path.stat().st_size==SIZE and digest_file(path)==SHA,'PACKAGE_HASH_OR_SIZE_MISMATCH'
    emit('package_verified',sha256=SHA,bytes=SIZE,ref=REF)
    return path
def extract_member(z,item,path):
    assert z.getinfo(item['path']).file_size==item['bytes']
    h=hashlib.sha256();n=0
    with z.open(item['path']) as src,open(path,'wb') as out:
        while b:=src.read(4*1024*1024):
            n+=len(b);assert n<=item['bytes'];h.update(b);out.write(b)
    assert n==item['bytes'] and h.hexdigest()==item['sha256'],'MEMBER_CHECKSUM'
    emit('member_verified',path=item['path'],bytes=n,sha256=h.hexdigest())
def unpack(path):
    with zipfile.ZipFile(path) as z:
        outer=json.loads(z.read('manifest.json'));child=next(x for x in outer['files'] if x['format']=='geodata')
        inner=ROOT/'data.yklgeodata';extract_member(z,child,inner)
    with zipfile.ZipFile(inner) as z:
        manifest=json.loads(z.read('manifest.json'))
        for item in manifest['files']:
            if item['format'] in ('native-catalog','native-facts','native-navigation'):
                extract_member(z,item,ROOT/pathlib.PurePosixPath(item['path']).name)
    return json.loads((ROOT/'catalog.json').read_text())
def normalize(x):return (x+180)%360-180
def metadata(blob):
    magic,version,n,raw_n,parts,crc,dcrc=struct.unpack_from('>5i2q',blob)
    assert magic==0x594b4637 and version==1 and parts==0 and n==len(blob)-36
    raw=zlib.decompress(blob[36:],-15)
    assert len(raw)==raw_n and zlib.crc32(raw)==crc and dcrc==0
    return json.loads(raw)
def span_points(blob,n,raw_n,crc):
    raw=zlib.decompress(blob,-15);assert len(raw)==raw_n and zlib.crc32(raw)==crc
    out=[];off=0
    for _ in range(n):
        lat,lon,has=struct.unpack_from('>dd?',raw,off);off+=17
        depth=struct.unpack_from('>d',raw,off)[0] if has else None
        if has:off+=8
        out.append((lat,lon,depth))
    assert off==len(raw)
    return out
def contains(db,row,lat,lon):
    winding=0;used=0
    for part,hole,n,west,east,south,north in db.execute('SELECT part_no,hole,point_count,min_x,max_x,min_y,max_y FROM geometry_part INDEXED BY geometry_part_latitude WHERE feature_row=? AND min_y<=? AND max_y>=? ORDER BY part_no',(str(row),str(lat+1e-10),str(lat-1e-10))):
        if n<3:continue
        x=lon+360*round(((west+east)*.5-lon)/360)
        if x<west-1e-10 or x>east+1e-10:continue
        inside=boundary=False
        for count,anchor,raw_n,crc,blob in db.execute('SELECT point_count,anchor_x,raw_size,crc,payload FROM geometry_span INDEXED BY geometry_span_latitude WHERE feature_row=? AND part_no=? AND min_y<=? AND max_y>=? AND max_x>=? ORDER BY span_no',(str(row),str(part),str(lat+1e-10),str(lat-1e-10),str(x-1e-10))):
            used+=1;ps=span_points(blob,count,raw_n,crc);ax=anchor
            for a,b in zip(ps,ps[1:]):
                bx=ax+normalize(b[1]-a[1]);cross=(x-ax)*(b[0]-a[0])-(lat-a[0])*(bx-ax)
                if abs(cross)<=1e-10*max(abs(bx-ax)+abs(b[0]-a[0]),1e-10) and min(ax,bx)-1e-10<=x<=max(ax,bx)+1e-10 and min(a[0],b[0])-1e-10<=lat<=max(a[0],b[0])+1e-10:boundary=True
                if (a[0]>lat)!=(b[0]>lat) and x<(bx-ax)*(lat-a[0])/(b[0]-a[0])+ax:inside=not inside
                ax=bx
        if boundary or inside:winding+=-1 if hole else 1
    return winding>0,used
def full_contains(db,row,lat,lon):
    winding=0
    for part,hole,n in db.execute('SELECT part_no,hole,point_count FROM geometry_part WHERE feature_row=? ORDER BY part_no',(row,)):
        points=[]
        for start,count,raw_n,crc,blob in db.execute('SELECT point_start,point_count,raw_size,crc,payload FROM geometry_span WHERE feature_row=? AND part_no=? ORDER BY span_no',(row,part)):
            ps=span_points(blob,count,raw_n,crc);points.extend(ps[0 if start==0 else 1:])
        if len(points)<3:continue
        xs=[]
        for p in points:xs.append((p[1] if not xs else xs[-1][0]+normalize(p[1]-points[len(xs)-1][1]),p[0]))
        x=lon+360*round(((min(p[0] for p in xs)+max(p[0] for p in xs))/2-lon)/360)
        if Polygon(xs).covers(Point(x,lat)):winding+=-1 if hole else 1
    return winding>0
def in_bounds(box,lat,lon):
    return box['south']<=lat<=box['north'] and (box['west']<=lon<=box['east'] if box['west']<=box['east'] else lon>=box['west'] or lon<=box['east'])
def inspect_facts(catalog):
    db=sqlite3.connect((ROOT/'features.sqlite').as_uri()+'?mode=ro',uri=True)
    ds=catalog['dataset'];cells=ds['cells']
    emit('catalog',id=ds['id'],format=ds.get('format'),cells=len(cells),features=sum(c['featureCount'] for c in cells),rasters=len(ds.get('rasters') or []),eligibility=ds.get('eligibility'),sqliteVersion=db.execute('PRAGMA user_version').fetchone()[0])
    report={}
    query="SELECT DISTINCT f.rowid,f.feature_id,f.cell,f.kind,f.payload FROM spatial s JOIN spatial_feature sf ON sf.id=s.id JOIN features f ON f.rowid=sf.feature_row WHERE s.max_x>=? AND s.min_x<=? AND s.max_y>=? AND s.min_y<=? AND f.kind IN ('DEPTH_AREA','DREDGED_AREA','LAND','DRYING_AREA','OTHER') ORDER BY f.feature_id"
    for name,(lat,lon) in POINTS.items():
        t=time.perf_counter();args=(lon,lon,lat,lat)
        rows=list(db.execute(query,args));string_rows=list(db.execute(query,tuple(map(str,args))))
        hits=[];misses=[];blocks=0
        for row,fid,cell,kind,blob in rows:
            md=metadata(blob);hit,used=contains(db,row,lat,lon);blocks+=used
            geos=full_contains(db,row,lat,lon) if md['geometry']['kind']=='POLYGON' else False
            item={'id':fid,'row':row,'cell':cell,'kind':kind,'geometryKind':md['geometry']['kind'],'depth':md.get('depth'),'issues':md.get('issues',[]),'sourceLayer':md.get('attributes',{}).get('LINZ_LDS_LAYER'),'tier':md.get('attributes',{}).get('YOKULI_DETAIL_TIER'),'scale':md.get('attributes',{}).get('YOKULI_DETAIL_SCALE'),'spanContains':hit,'geosContains':geos,'blocks':used}
            (hits if hit else misses).append(item)
        coverage=[]
        for cell in cells:
            if cell.get('cancelled'):continue
            bound_hit=not cell.get('bounds') or any(in_bounds(b,lat,lon) for b in cell['bounds'])
            positives=[];negatives=[];unresolved=[]
            for c in cell.get('coverage',[]):
                row=db.execute('SELECT rowid FROM features WHERE feature_id=?',(c['featureId'],)).fetchone()
                if not row:unresolved.append(c['featureId']);continue
                hit,_=contains(db,row[0],lat,lon)
                if hit:(positives if c['covered'] else negatives).append(c['featureId'])
            coverage.append({'cell':cell['cellId'],'priority':cell.get('priority'),'manual':cell.get('priorityExplicit',False),'featureCount':cell['featureCount'],'boundsHit':bound_hit,'positiveHits':positives[:8],'negativeHits':negatives[:8],'unresolvedRefs':unresolved[:8]})
        report[name]={'coordinate':[lat,lon],'bboxCandidates':len(rows),'stringBoundCandidates':len(string_rows),'hits':hits,'misses':misses[:20],'coverage':coverage,'decodedSpanCount':blocks,'elapsedMs':round((time.perf_counter()-t)*1000,3)}
        emit('endpoint_facts',name=name,**report[name])
    db.close();return report
class Reader:
    def __init__(self,b):self.f=io.BytesIO(b)
    def get(self,fmt):
        n=struct.calcsize(fmt);raw=self.f.read(n);assert len(raw)==n
        return struct.unpack(fmt,raw)[0]
    def i(self):return self.get('>i')
    def q(self):return self.get('>q')
    def read(self,n):
        assert 0<=n<=64*1024*1024;b=self.f.read(n);assert len(b)==n;return b
def read_nav(data):
    assert zlib.crc32(data[:-8])==struct.unpack('>q',data[-8:])[0],'NAV_CRC'
    r=Reader(data);assert r.i()==0x594b4e31 and r.i()==3
    b=r.read(r.i());assert zlib.crc32(b)==r.q();h=json.loads(b);shapes=[]
    def shape():
        n=r.i()
        if n<0:return shapes[-n-1]
        size=r.i();raw=zlib.decompress(r.read(n));assert len(raw)==size
        v=from_wkb(raw);assert v.is_valid;shapes.append(v);return v
    water=shape();components=[shape() for _ in range(h['componentCount'])]
    evidence=[shape() for _ in h['evidence']]
    constraints=[shape() for _ in h.get('constraints',[])]
    return h,water,components,evidence,constraints
def projected(point,region):
    origin=(region['y']*.125-90+.0625,region['x']*.125-180+.0625)
    v=Geodesic.WGS84.Inverse(*origin,*point);a=math.radians(v['azi1'])
    return Point(v['s12']*math.sin(a),v['s12']*math.cos(a))
def inspect_nav():
    targets={name:(math.floor((lon+180)/.125),math.floor((lat+90)/.125)) for name,(lat,lon) in POINTS.items()}
    xmin=min(p[0] for p in targets.values())-1;xmax=max(p[0] for p in targets.values())+1
    ymin=min(p[1] for p in targets.values())-1;ymax=max(p[1] for p in targets.values())+1
    chosen={};summary=[]
    with open(ROOT/'navigation.bin','rb') as f:
        assert struct.unpack('>iii',f.read(12))[:2]==(0x594e4133,1)
        f.seek(8);count=struct.unpack('>i',f.read(4))[0]
        for _ in range(count):
            n=struct.unpack('>H',f.read(2))[0];filename=f.read(n).decode('ascii');size=struct.unpack('>q',f.read(8))[0];begin=f.tell()
            magic,version,n=struct.unpack('>iii',f.read(12));hb=f.read(n);hc=struct.unpack('>q',f.read(8))[0]
            assert magic==0x594b4e31 and version==3 and zlib.crc32(hb)==hc
            h=json.loads(hb);region=h['region'];x,y=region['x'],region['y']
            if xmin<=x<=xmax and ymin<=y<=ymax:
                f.seek(begin);data=f.read(size);crc=struct.unpack('>q',f.read(8))[0];assert zlib.crc32(data)==crc
                summary.append({'file':filename,'region':[x,y],'bytes':size,'components':h['componentCount'],'portals':len(h['portals']),'constraints':len(h.get('constraints',[])),'source':h['source'],'rules':h['rules']})
                chosen[(x,y)]=read_nav(data)
            else:f.seek(begin+size+8)
        assert not f.read(1)
    emit('navigation_regions',archiveCount=count,nearby=summary)
    out={}
    for name,reg in targets.items():
        if reg not in chosen:out[name]={'present':False};continue
        h,water,components,evidence,constraints=chosen[reg];p=projected(POINTS[name],h['region'])
        at=[c for c,s in zip(h.get('constraints',[]),constraints) if s.covers(p)]
        ev=[e for e,s in zip(h['evidence'],evidence) if s.covers(p)]
        out[name]={'present':True,'region':reg,'waterContains':bool(water.covers(p)),'waterDistanceMeters':water.distance(p) if not water.is_empty else None,'componentHits':[i for i,s in enumerate(components) if s.covers(p)],'constraintsAtPoint':at,'incompleteEvidenceAtPoint':ev,'margin':h['margin']}
        emit('endpoint_navigation',name=name,**out[name])
    return out
def main():
    path=download();catalog=unpack(path);facts=inspect_facts(catalog);nav=inspect_nav()
    d=Geodesic.WGS84.Inverse(*POINTS['A'],*POINTS['B'])['s12']
    report={'sourceSha256':SHA,'sourceRef':REF,'points':POINTS,'straightDistanceMeters':d,'facts':facts,'navigation':nav,'boundary':'Read-only package inspection. Not an Android planner execution, a performance benchmark, or a safe sailing route.'}
    (ROOT/'report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2,allow_nan=False))
    emit('completed',straightDistanceMeters=round(d,3),report=str(ROOT/'report.json'))
if __name__=='__main__':main()
