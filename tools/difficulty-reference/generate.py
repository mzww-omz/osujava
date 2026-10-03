#!/usr/bin/env python3
"""Explicit, development-only oracle. Downloads pinned public sources, never invoked by Gradle or the app."""
import argparse, pathlib, shutil, subprocess, tempfile, urllib.request
COMMIT = '4e96853c7543f80a1b822ccd381943c7377543d7'
FILES = ['osu.Game/Rulesets/Difficulty/Skills/'+x+'.cs' for x in ['Skill','StrainSkill']]
FILES += ['osu.Game/Rulesets/Difficulty/Preprocessing/DifficultyHitObject.cs']
FILES += ['osu.Game.Rulesets.Osu/Difficulty/Skills/'+x+'.cs' for x in ['Aim','Speed','OsuStrainSkill']]
FILES += ['osu.Game.Rulesets.Osu/Difficulty/Evaluators/'+x+'Evaluator.cs' for x in ['Aim','Speed','Rhythm']]
FILES += ['osu.Game.Rulesets.Osu/Difficulty/Preprocessing/OsuDifficultyHitObject.cs','osu.Game.Rulesets.Osu/Beatmaps/OsuBeatmapProcessor.cs']
p = argparse.ArgumentParser(); p.add_argument('--dotnet',default='dotnet'); a=p.parse_args()
root=pathlib.Path(__file__).resolve().parents[2]
with tempfile.TemporaryDirectory(prefix='osujava-difficulty-oracle-') as temp:
    work=pathlib.Path(temp)
    for name in ['Reference.csproj','Program.cs','Stubs.cs']:
        shutil.copy(pathlib.Path(__file__).parent/name,work/name)
    for file in FILES:
        (work/pathlib.Path(file).name).write_bytes(urllib.request.urlopen('https://raw.githubusercontent.com/ppy/osu/'+COMMIT+'/'+file).read())
    subprocess.run([a.dotnet,'run','--project',str(work/'Reference.csproj'),'--',str(root/'core/src/test/resources/difficulty/reference-20220902')],check=True)
