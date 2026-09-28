#!/usr/bin/env python3
"""Read-only legacy PNG audit. Requires Pillow; never extracts skin archives."""
import argparse, hashlib, io, itertools, json, re, zipfile
from pathlib import Path
from PIL import Image, ImageChops, ImageDraw
PATTERN = re.compile(r'^(selection-(?:mode|mods|random|options)(?:-over)?|selection-tab|menu-back(?:-\d+)?|menu-button-background|songselect-(?:top|bottom)|star2?|rank-forum|ranking-.+|score-.+)\.png$', re.I)
EXPECTED = {'selection-mode': (92,90), 'selection-mods': (77,90), 'selection-random': (77,90), 'selection-options': (77,90), 'menu-back':(200,214), 'menu-button-background':(690,85), 'star':(50,50)}
REQUIRED = ['selection-'+x+s for x in ['mode','mods','random','options'] for s in ['', '-over']] + ['songselect-top','songselect-bottom','menu-back','menu-button-background','star']
def source(path):
    if path.is_dir():
        return {p.relative_to(path).as_posix(): p.read_bytes() for p in sorted(path.rglob('*')) if p.is_file() and (p.suffix.lower()=='.png' or p.name.lower()=='skin.ini')}
    with zipfile.ZipFile(path) as z:
        # Reading only, no paths from archives are written to disk.
        return {i.filename:z.read(i) for i in z.infolist() if not i.is_dir() and (i.filename.lower().endswith('.png') or i.filename.lower().endswith('skin.ini'))}
def scan(label,path):
    files=source(path); ini=files.get('skin.ini',b'').decode('utf-8-sig',errors='replace')
    def setting(key,default):
        m=re.search(r'^\s*'+key+r'\s*:\s*([^\r\n/]+)',ini,re.M|re.I); return m.group(1).strip() if m else default
    version=setting('Version','1.0' if ini else 'latest'); effective=2.7 if version.lower()=='latest' else float(version)
    records=[]
    for filename,data in files.items():
        base=Path(filename).name; density=2 if '@2x' in base.lower() else 1
        plain=base.replace('@2x',''); match=PATTERN.match(plain)
        if not match: continue
        name=match.group(1); r={'filename':filename,'asset':name,'density':density,'sha256':hashlib.sha256(data).hexdigest(),'provider':'current' if '/' not in filename else 'inactive-subdirectory','flags':[]}
        try:
            im=Image.open(io.BytesIO(data)).convert('RGBA'); w,h=im.size; alpha=im.getchannel('A'); box=alpha.getbbox(); hist=alpha.histogram()
            r.update(raw=[w,h],logical=[w/density,h/density],alpha_bounds=box,alpha_coverage=round((w*h-hist[0])/(w*h),6),alpha_mass=round(sum(i*n for i,n in enumerate(hist))/(255*w*h),6),transparent_margins=[box[0],box[1],w-box[2],h-box[3]] if box else None)
            flags=r['flags']; lw,lh=r['logical']; expected=EXPECTED.get(name.removesuffix('-over'))
            if max(lw,lh)>1024 or w*h>4_000_000: flags.append('giant')
            if w==h==1: flags.append('1x1')
            if not box: flags.append('fully-transparent')
            if max(w/h,h/w)>12: flags.append('extreme-aspect')
            if box and 1-(box[2]-box[0])*(box[3]-box[1])/(w*h)>.35: flags.append('large-transparent-margin')
            if expected and (max(lw/expected[0],lh/expected[1])>3 or min(lw/expected[0],lh/expected[1])<.25): flags.append('outside-reference-size')
            if name.startswith('selection-') and name!='selection-tab':
                ew,eh=expected
                if effective<2: eh=87
                r['classification']='transparent-replacement' if not box else 'composite-canvas' if lw>ew*3 and lh>eh*2 else 'decorated-button' if lw>ew*1.3 or lh>eh*1.3 or 'large-transparent-margin' in flags else 'button-like'
                # Same thresholds/canonical region as the runtime, recorded independently from full alpha.
                left,top=0,0 if effective<2 else max(0,h-round(eh*density))
                crop=alpha.crop((left,top,min(w,round(ew*density)),min(h,top+round(eh*density))))
                body=crop.point(lambda a:255 if a>=160 else 0).getbbox() or crop.point(lambda a:255 if a>=16 else 0).getbbox()
                r['interaction_content']=[body[0]/density,(h-top-body[3])/density,(body[2]-body[0])/density,(body[3]-body[1])/density] if body else [0,0,0,0]

        except Exception as e: r.update(error=str(e));r['flags'].append('decode-failed')
        counterpart=name[:-5] if name.endswith('-over') else name+'-over'
        r['normal_hover_pair']=[f for f in files if Path(f).parent==Path(filename).parent and Path(f).name.replace('@2x','')==counterpart+'.png']
        frame=re.match(r'^(menu-back)-(\d+)$',name); r['animation_frame']=int(frame[2]) if frame else None
        records.append(r)
    by={(r['filename'].replace('@2x',''),r['density']):r for r in records}
    for r in records:
        other=by.get((r['filename'].replace('@2x',''),3-r['density']))
        if other and r.get('logical')!=other.get('logical'): r['flags'].append('density-size-mismatch')
    for r in records:
        if r['animation_frame'] is not None:
            frames={v['animation_frame'] for v in records if v['density']==r['density'] and Path(v['filename']).parent==Path(r['filename']).parent and v['animation_frame'] is not None}
            r['animation_frames']=len(frames)
            contiguous=0
            while contiguous in frames: contiguous+=1
            r['contiguous_animation_frames']=contiguous
    return {'id':label,'source':str(path),'name':setting('Name',label),'declared_version':version,'effective_version':effective,'assets':records,'missing':[n for n in REQUIRED if not any(r['asset']==n and r['provider']=='current' for r in records) and not (n=='menu-back' and any(r['asset']=='menu-back-0' for r in records))]},files

def difference(a,b):
    d=ImageChops.difference(a,b); mask=ImageChops.lighter(ImageChops.lighter(d.getchannel('R'),d.getchannel('G')),ImageChops.lighter(d.getchannel('B'),d.getchannel('A'))).point(lambda x:255 if x else 0)
    hist=mask.histogram(); return mask, {'equal_ratio':hist[0]/(a.width*a.height),'changed_pixels':hist[255],'changed_bounds':mask.getbbox()}
def diffs(files,out):
    out.mkdir(parents=True,exist_ok=True); entries=[]; images={}
    for n in REQUIRED[:8]:
        f=next((n+s+'.png' for s in ['@2x',''] if n+s+'.png' in files),None)
        if f:
            density=2 if '@2x' in f else 1; im=Image.open(io.BytesIO(files[f])).convert('RGBA')
            images[n]=(f,im.resize((round(im.width*2/density),round(im.height*2/density)),Image.Resampling.NEAREST))
    for a,b in itertools.combinations(images,2):
        fa,ia=images[a];fb,ib=images[b];size=(max(ia.width,ib.width),max(ia.height,ib.height))
        ca=Image.new('RGBA',size); cb=Image.new('RGBA',size);ca.paste(ia,(0,size[1]-ia.height));cb.paste(ib,(0,size[1]-ib.height))
        raw_a=Image.open(io.BytesIO(files[fa])).size;raw_b=Image.open(io.BytesIO(files[fb])).size
        mask,stats=difference(ca,cb); stats.update(a=fa,b=fb,a_raw=raw_a,b_raw=raw_b,same_raw_dimensions=raw_a==raw_b,comparison='2x logical raster, common local bottom-left origin; padding included',changed_logical_bounds=[v/2 for v in stats['changed_bounds']] if stats['changed_bounds'] else None)
        entries.append(stats); heat=Image.new('RGBA',size,(230,30,140,0));heat.putalpha(mask);heat.thumbnail((1100,900));heat.save(out/(a+'--'+b+'.png'))
    sheet=Image.new('RGB',(1000,8*185),(35,35,42));draw=ImageDraw.Draw(sheet)
    for i,(n,(f,im)) in enumerate(images.items()):
        im.thumbnail((950,160));sheet.paste(im,(20,i*185+22),im);draw.text((10,i*185+3),f,fill='white')
    sheet.save(out/'eight-assets.png');return entries

def write_inventory(path,corpus):
    # Compact one asset per line; no copyrighted skin images are included in this manifest.
    lines=['{"schema":1,"classifications_are_diagnostics_only":true,"skins":[']
    default=next((c for c in corpus if c['id']=='Greylooks'),None)
    for ci,c in enumerate(corpus):
        resolved_before=[];resolved_after=[]
        for name in REQUIRED:
            chosen=None
            for provider in [c,default] if default and default!=c else [c]:
                for variant in ([name+'-0',name] if name=='menu-back' else [name]):
                    candidates=[r for r in provider['assets'] if r['provider']=='current' and r['asset']==variant and 'error' not in r]
                    if candidates:
                        chosen=dict(max(candidates,key=lambda r:r['density']));chosen['provider']='current' if provider==c else 'bundled';chosen['source_skin']=provider['id'];break
                if chosen:break
            summary={k:chosen[k] for k in ['filename','raw','logical','density','alpha_bounds','transparent_margins','provider','source_skin']} if chosen else {'provider':'procedural'}
            resolved_before.append(dict(asset=name,**summary))
            current_surface=any(r['provider']=='current' and 'error' not in r and (r['asset'].startswith('selection-') or r['asset'].startswith('menu-back') or r['asset'] in ['menu-button-background','songselect-top','songselect-bottom']) for r in c['assets'])
            if name in ['songselect-top','songselect-bottom'] and current_surface and summary['provider']!='current':
                layout_provider=summary['provider']
                summary={'provider':'procedural','reason':'authored-current-surface'}
                if name=='songselect-top': summary['layout_provider']=layout_provider
            if name.endswith('-over') and summary['provider']!='current' and any(r['asset']==name.removesuffix('-over') and r['provider']=='current' and 'error' not in r for r in c['assets']):
                summary={'provider':'procedural','reason':'authored-current-normal'}
            resolved_after.append(dict(asset=name,**summary))
        header={k:v for k,v in c.items() if k not in ['assets','selection_diffs']}
        header.update(regression=c['id'] in ['Greylooks','WhiteCat','Seoul','Default'],resolved_before=resolved_before,resolved_after=resolved_after)
        lines.append(json.dumps(header,ensure_ascii=False)[:-1]+',"assets":[')
        for ri,r in enumerate(c['assets']):lines.append(json.dumps(r,ensure_ascii=False)+(',' if ri+1<len(c['assets']) else ''))
        lines.append(']'+(',"selection_diffs":'+json.dumps(c['selection_diffs']) if 'selection_diffs' in c else '')+'}'+(',' if ci+1<len(corpus) else ''))
    lines.append(']}');path.write_text('\n'.join(lines)+'\n')

def main():
    p = argparse.ArgumentParser()
    p.add_argument('--skin', action='append', help='ID=directory or .osk')
    p.add_argument('--output', type=Path)
    p.add_argument('--fixtures', type=Path, help='write portable GL-free measured geometry corpus')
    p.add_argument('--diff', help='corpus ID')
    p.add_argument('--captures', type=Path)
    p.add_argument('--verify', type=Path, help='verify pinned corpus from its original sources (no image writes)')
    a = p.parse_args()
    corpus = []
    if a.verify:
        baseline=json.loads(a.verify.read_text())
        for expected in baseline['skins']:
            actual,_=scan(expected['id'],Path(expected['source']))
            actual=json.loads(json.dumps(actual))
            for key in ['name','declared_version','effective_version','assets','missing']:
                if actual[key]!=expected[key]: raise SystemExit('Corpus changed: '+expected['id']+' '+key)
            print('CORPUS PASS',expected['id'],len(actual['assets']))
        return
    if not a.skin or not a.output:
        p.error('--skin and --output, or --verify, required')
    for item in a.skin:
        label,path=item.split('=',1);record,files=scan(label,Path(path));corpus.append(record)
        if label==a.diff:
            if not a.captures:p.error('--diff requires --captures')
            record['selection_diffs']=diffs(files,a.captures)
    a.output.parent.mkdir(parents=True,exist_ok=True);write_inventory(a.output,corpus)
    if a.fixtures:
        fixture=[]
        for c in corpus:
            if c['id'] not in ['Greylooks','WhiteCat','Seoul','Default']:continue
            fixture.append({k:c[k] for k in ['id','effective_version']} | {'assets':[r for r in c['assets'] if r['provider']=='current' and (r['asset'] in REQUIRED[:8]+['menu-back-0','menu-back','songselect-top','songselect-bottom','menu-button-background','star'] or (r['asset'].startswith('ranking-') and r['asset'].endswith('-small')))]})
        a.fixtures.parent.mkdir(parents=True,exist_ok=True);a.fixtures.write_text(json.dumps(fixture,indent=2)+'\n')
    for c in corpus:
        from collections import Counter
        print(c['id'],c['declared_version'],len(c['assets']),dict(Counter(f for r in c['assets'] for f in r['flags'])))
if __name__=='__main__':main()
