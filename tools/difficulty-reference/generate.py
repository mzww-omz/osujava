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
FILES += ['osu.Game/Beatmaps/ControlPoints/'+name+'.cs' for name in
    ['IControlPoint','ControlPoint','ControlPointGroup','ControlPointInfo','DifficultyControlPoint',
     'TimingControlPoint','EffectControlPoint','SampleControlPoint']]
FILES += ['osu.Game/Beatmaps/Legacy/'+name+'.cs' for name in
    ['LegacyControlPointInfo','LegacySampleBank','LegacyEffectFlags']]
FILES += ['osu.Game/Beatmaps/Timing/TimeSignature.cs']
FRAMEWORK_COMMIT = '3365c86f769cb0ed84a10c5c96313a73e552dc2d'
p = argparse.ArgumentParser()
p.add_argument('--dotnet', default='dotnet')
p.add_argument('--fixtures', type=pathlib.Path, help='Fixture directory to update; defaults to checked-in references')
a = p.parse_args()
root=pathlib.Path(__file__).resolve().parents[2]
def complete_member(source, signature):
    start = source.index(signature)
    end = source.index('{', start) + 1
    depth = 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[start:end]

with tempfile.TemporaryDirectory(prefix='osujava-difficulty-oracle-') as temp:
    work=pathlib.Path(temp)
    for name in ['Reference.csproj','Program.cs','Stubs.cs','TimingAdapters.cs']:
        shutil.copy(pathlib.Path(__file__).parent/name,work/name)
    for file in FILES:
        (work/pathlib.Path(file).name).write_bytes(urllib.request.urlopen('https://raw.githubusercontent.com/ppy/osu/'+COMMIT+'/'+file).read())
    for file in ['PathApproximator','CircularArcProperties']:
        (work/(file+'.cs')).write_bytes(urllib.request.urlopen('https://raw.githubusercontent.com/ppy/osu-framework/'+FRAMEWORK_COMMIT+'/osu.Framework/Utils/'+file+'.cs').read())
    # These complete public classes execute the pinned SV range/precision implementation.
    # Only basic unbound Bindable storage and unrelated model types remain adapters.
    for name in ['BindableDouble', 'BindableInt', 'BindableNumber', 'RangeConstrainedBindable', 'IBindableNumber']:
        (work/(name+'.cs')).write_bytes(urllib.request.urlopen(
            'https://raw.githubusercontent.com/ppy/osu-framework/'+FRAMEWORK_COMMIT+
            '/osu.Framework/Bindables/'+name+'.cs').read())
    (work/'SortedList.cs').write_bytes(urllib.request.urlopen(
        'https://raw.githubusercontent.com/ppy/osu-framework/'+FRAMEWORK_COMMIT+'/osu.Framework/Lists/SortedList.cs').read())
    # The legacy control point keeps raw/clamped SV (precision double.Epsilon); the osu! slider
    # applies precision 0.01. Execute both real declarations, not a hand-written rounding formula.
    legacy_decoder = urllib.request.urlopen('https://raw.githubusercontent.com/ppy/osu/'+COMMIT+
        '/osu.Game/Beatmaps/Formats/LegacyDecoder.cs').read().decode('utf-8-sig')
    slider = urllib.request.urlopen('https://raw.githubusercontent.com/ppy/osu/'+COMMIT+
        '/osu.Game.Rulesets.Osu/Objects/Slider.cs').read().decode('utf-8-sig')
    (work/'LegacyDecoder.reference.txt').write_text(legacy_decoder)
    (work/'Slider.reference.txt').write_text(slider)
    legacy_class = complete_member(legacy_decoder, 'public class LegacyDifficultyControlPoint')
    legacy_sample = complete_member(legacy_decoder, 'internal class LegacySampleControlPoint')
    # This property is followed by an object initializer, which also belongs to the declaration.
    field_start = slider.index('public BindableNumber<double> SliderVelocityBindable')
    velocity_field = slider[field_start:slider.index('};', field_start)+2]
    velocity_property = complete_member(slider, 'public double SliderVelocity\n')
    (work/'FixtureLegacyTiming.cs').write_text(
        '// Public MIT declarations; original headers/sources: LegacyDecoder.reference.txt, Slider.reference.txt\n'
        '#nullable disable\nusing System; using osu.Framework.Bindables; using osu.Game.Beatmaps.ControlPoints; using osu.Game.Audio; using osu.Game.Rulesets.Objects.Legacy; using osu.Game.Beatmaps;\n'
        'namespace osu.Game.Beatmaps.ControlPoints { '+legacy_class+' }\n'
        'namespace osu.Game.Beatmaps.ControlPoints { '+legacy_sample+' }\n'
        'namespace osu.Game.Rulesets.Osu.Objects { public partial class Slider { '+velocity_field+
        '\n'+velocity_property+'\n'+complete_member(slider, 'protected override void ApplyDefaultsToSelf')+' } }\n')
    timing_decoder = urllib.request.urlopen('https://raw.githubusercontent.com/ppy/osu/'+COMMIT+
        '/osu.Game/Beatmaps/Formats/LegacyBeatmapDecoder.cs').read().decode('utf-8-sig')
    (work/'LegacyBeatmapDecoder.reference.txt').write_text(timing_decoder)
    timing_methods = [complete_member(timing_decoder, signature) for signature in
        ['private void handleTimingPoint', 'private void addControlPoint', 'private void flushPendingPoints']]
    (work/'FixtureTimingDecoder.cs').write_text(
        '// Public MIT timing methods; original source/header: LegacyBeatmapDecoder.reference.txt\n'
        '#nullable disable\nusing System; using System.IO; using System.Collections.Generic; '
        'using osu.Framework.Extensions.EnumExtensions; using osu.Game.Audio; using osu.Game.IO; '
        'using osu.Game.Beatmaps.ControlPoints; using osu.Game.Beatmaps.Legacy; using osu.Game.Beatmaps.Timing;\n'
        'partial class FixtureTimingDecoder { '+ '\n'.join(timing_methods)+' }\n')
    # Execute the original private path-conversion methods, without needing full decoder/runtime dependencies.
    parser = urllib.request.urlopen('https://raw.githubusercontent.com/ppy/osu/'+COMMIT+'/osu.Game/Rulesets/Objects/Legacy/ConvertHitObjectParser.cs').read().decode('utf-8-sig')
    (work/'LegacyPathDecoder.reference.txt').write_text(parser)
    methods=[]
    for signature in ['private PathType convertPathType','private PathControlPoint[] convertPathString','private IEnumerable<Memory<PathControlPoint>> convertPoints','private PathControlPoint[] mergePointsLists']:
        methods.append(complete_member(parser, signature))
    (work/'FixturePathDecoder.cs').write_text('// Public MIT path-decoder methods; original source/header: LegacyPathDecoder.reference.txt\n#nullable disable\nusing System; using System.Collections.Generic; using osuTK; using osu.Framework.Utils; using osu.Game.Rulesets.Objects; using osu.Game.Rulesets.Objects.Types; using osu.Game.IO; using osu.Game.Beatmaps.Formats;\nclass FixturePathDecoder { public int FormatVersion=14; public PathControlPoint[] Decode(string s,Vector2 p)=>convertPathString(s,p);\n'+ '\n'.join(methods)+'\n}')
    subprocess.run([a.dotnet,'run','--project',str(work/'Reference.csproj'),'--',str(a.fixtures.resolve() if a.fixtures else root/'core/src/test/resources/difficulty/reference-20220902')],check=True)
