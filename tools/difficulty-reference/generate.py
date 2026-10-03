#!/usr/bin/env python3
"""Explicit, development-only oracle. Downloads pinned public sources, never invoked by Gradle or the app."""
import argparse, pathlib, shutil, subprocess, tempfile, urllib.request
COMMIT = '4e96853c7543f80a1b822ccd381943c7377543d7'
FILES = ['osu.Game/Rulesets/Difficulty/Skills/'+x+'.cs' for x in ['Skill','StrainSkill']]
FILES += ['osu.Game/Rulesets/Difficulty/Preprocessing/DifficultyHitObject.cs']
FILES += ['osu.Game.Rulesets.Osu/Difficulty/Skills/'+x+'.cs' for x in ['Aim','Speed','OsuStrainSkill']]
FILES += ['osu.Game.Rulesets.Osu/Difficulty/Evaluators/'+x+'Evaluator.cs' for x in ['Aim','Speed','Rhythm']]
FILES += ['osu.Game.Rulesets.Osu/Difficulty/Preprocessing/OsuDifficultyHitObject.cs','osu.Game.Rulesets.Osu/Beatmaps/OsuBeatmapProcessor.cs']
FILES += ['osu.Game/Rulesets/Objects/SliderPath.cs','osu.Game/Rulesets/Objects/SliderEventGenerator.cs']
FRAMEWORK_COMMIT = '3365c86f769cb0ed84a10c5c96313a73e552dc2d'
p = argparse.ArgumentParser(); p.add_argument('--dotnet',default='dotnet'); a=p.parse_args()
root=pathlib.Path(__file__).resolve().parents[2]
with tempfile.TemporaryDirectory(prefix='osujava-difficulty-oracle-') as temp:
    work=pathlib.Path(temp)
    for name in ['Reference.csproj','Program.cs','Stubs.cs']:
        shutil.copy(pathlib.Path(__file__).parent/name,work/name)
    for file in FILES:
        (work/pathlib.Path(file).name).write_bytes(urllib.request.urlopen('https://raw.githubusercontent.com/ppy/osu/'+COMMIT+'/'+file).read())
    for file in ['PathApproximator','CircularArcProperties']:
        (work/(file+'.cs')).write_bytes(urllib.request.urlopen('https://raw.githubusercontent.com/ppy/osu-framework/'+FRAMEWORK_COMMIT+'/osu.Framework/Utils/'+file+'.cs').read())
    # Execute the original private path-conversion methods, without needing full decoder/runtime dependencies.
    parser = urllib.request.urlopen('https://raw.githubusercontent.com/ppy/osu/'+COMMIT+'/osu.Game/Rulesets/Objects/Legacy/ConvertHitObjectParser.cs').read().decode('utf-8-sig')
    (work/'LegacyPathDecoder.reference.txt').write_text(parser)
    methods=[]
    for signature in ['private PathType convertPathType','private PathControlPoint[] convertPathString','private IEnumerable<Memory<PathControlPoint>> convertPoints','private PathControlPoint[] mergePointsLists']:
        start=parser.index(signature); end=parser.index('{',start); depth=1; end+=1
        while depth:
            depth += (parser[end]=='{') - (parser[end]=='}'); end+=1
        methods.append(parser[start:end])
    (work/'FixturePathDecoder.cs').write_text('// Public MIT path-decoder methods; original source/header: LegacyPathDecoder.reference.txt\n#nullable disable\nusing System; using System.Collections.Generic; using osuTK; using osu.Framework.Utils; using osu.Game.Rulesets.Objects; using osu.Game.Rulesets.Objects.Types; using osu.Game.IO; using osu.Game.Beatmaps.Formats;\nclass FixturePathDecoder { public int FormatVersion=14; public PathControlPoint[] Decode(string s,Vector2 p)=>convertPathString(s,p);\n'+ '\n'.join(methods)+'\n}')
    subprocess.run([a.dotnet,'run','--project',str(work/'Reference.csproj'),'--',str(root/'core/src/test/resources/difficulty/reference-20220902')],check=True)
