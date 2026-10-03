using System.Globalization;
using osu.Game.Beatmaps;
using osu.Game.Beatmaps.ControlPoints;
using osu.Game.Rulesets.Osu.Objects;
using osu.Game.Rulesets.Osu.Beatmaps;
using osu.Game.Rulesets.Difficulty.Preprocessing;
using osu.Game.Rulesets.Osu.Difficulty.Preprocessing;
using osu.Game.Rulesets.Osu.Difficulty.Skills;
using osu.Game.Rulesets.Osu.Difficulty.Evaluators;
using osu.Game.Rulesets.Mods;
using osu.Game.Rulesets.Objects;
using osu.Game.Rulesets.Objects.Types;
using osuTK;
CultureInfo.CurrentCulture=CultureInfo.InvariantCulture;
string fmt(double n) => n.ToString("R",CultureInfo.InvariantCulture);
foreach (var file in Directory.GetFiles(args[0],"*.osu").Order()) {
    var lines=File.ReadAllLines(file);
    double setting(string name,double fallback) { var l=lines.FirstOrDefault(l=>l.StartsWith(name+":")); return l==null?fallback:double.Parse(l.Split(':')[1]); }
    float cs=(float)setting("CircleSize",5),ar=(float)setting("ApproachRate",5),od=(float)setting("OverallDifficulty",5);
    float scale=(1f-.7f*(cs-5)/5)/2;
    var beatmap=new Beatmap<OsuHitObject>();
    beatmap.BeatmapInfo.BeatmapVersion=int.Parse(lines[0].Split('v')[1]);
    beatmap.BeatmapInfo.StackLeniency=(float)setting("StackLeniency",.7);
    var timingRows=new List<string>();
    var timing=new List<(double time,double beat,bool red)>(); bool timingSection=false;
    foreach(var line in lines) {
        if(line.StartsWith("[")) { timingSection=line=="[TimingPoints]";continue; }
        if(timingSection && line.Length>0) {timingRows.Add(line);var t=line.Split(',');timing.Add((double.Parse(t[0]),double.Parse(t[1]),t.Length<7 || t[6]=="1"));}
    }
    var controlPoints=new FixtureTimingDecoder().Decode(timingRows);
    var sliderTiming=new Dictionary<Slider,double[]>();
    bool parsing=false;
    foreach(var line in lines) {
        if(line=="[HitObjects]") { parsing=true; continue; }
        if(!parsing || line.Length==0) continue;
        var p=line.Split(','); int type=int.Parse(p[3]);
        OsuHitObject o=(type&8)!=0?new Spinner():(type&1)!=0?new HitCircle():(type&2)!=0?new Slider():throw new NotSupportedException();
        o.Position=new Vector2(float.Parse(p[0]),float.Parse(p[1]));o.StartTime=double.Parse(p[2]);o.EndTime=(type&8)!=0?double.Parse(p[5]):o.StartTime;
        o.Scale=scale;o.TimePreempt=(float)(ar>5?1200-150*(ar-5):1800-120*ar);o.HitWindows.Window=(type&8)!=0?0:80-6*od;
        if(o is Slider slider) {
            // Stream/object conversion and nested creation still use a fixture wrapper.
            // NaN/pre-v8 timing acceptance awaits its own regression unit.
            if(beatmap.BeatmapInfo.BeatmapVersion<8 || timing.Any(t=>!double.IsFinite(t.time)||!double.IsFinite(t.beat)))
                throw new NotSupportedException("Oracle slider timing requires v8+ and finite source values: "+file);
            var controls=new FixturePathDecoder{FormatVersion=beatmap.BeatmapInfo.BeatmapVersion}.Decode(p[5],o.Position);
            slider.Path=new osu.Game.Rulesets.Objects.SliderPath(controls,double.Parse(p[7]));
            slider.RepeatCount=int.Parse(p[6])-1;
            var timingPoint=controlPoints.TimingPointAt(o.StartTime);
            var difficultyPoint=controlPoints.DifficultyPointAt(o.StartTime);
            double beat=timingPoint.BeatLength,sv=difficultyPoint.SliderVelocity;
            slider.SliderVelocity=sv;
            slider.GenerateTicks=difficultyPoint is not LegacyDifficultyControlPoint legacy || legacy.GenerateTicks;
            slider.ApplyFixtureDefaults(controlPoints,new FixtureDifficulty(
                Math.Clamp(setting("SliderMultiplier",1.4),.4,3.6),Math.Clamp(setting("SliderTickRate",1),.5,8)));
            double velocity=slider.Velocity,tickDistance=slider.TickDistance;
            slider.SpanDuration=slider.Path.Distance/velocity;o.EndTime=o.StartTime+(slider.RepeatCount+1)*slider.SpanDuration;
            sliderTiming[slider]=new[]{beat,sv,slider.SliderVelocity,tickDistance};
            foreach(var e in SliderEventGenerator.Generate(o.StartTime,slider.SpanDuration,velocity,tickDistance,slider.Path.Distance,slider.RepeatCount+1,36)) {
                if(e.Type==SliderEventType.Tail)continue;
                OsuHitObject nested=e.Type==SliderEventType.Repeat?new SliderRepeat():new HitCircle();
                nested.StartTime=e.Time;nested.EndTime=e.Time;nested.Scale=scale;nested.HitWindows.Window=80-6*od;
                nested.Position=e.Type==SliderEventType.Head?o.Position:e.Type==SliderEventType.LegacyLastTick?slider.EndPosition:o.Position+slider.Path.PositionAt(e.PathProgress);
                slider.NestedHitObjects.Add(nested);
                if(e.Type==SliderEventType.Head)slider.HeadCircle=(HitCircle)nested;
                if(e.Type==SliderEventType.LegacyLastTick)slider.TailCircle=(HitCircle)nested;
            }
            slider.NestedHitObjects.Sort((a,b)=>a.StartTime.CompareTo(b.StartTime));
        }
        beatmap.HitObjects.Add(o);
    }
    new OsuBeatmapProcessor(beatmap).PostProcess();
    var objects=new List<DifficultyHitObject>();
    for(int i=1;i<beatmap.HitObjects.Count;i++) objects.Add(new OsuDifficultyHitObject(beatmap.HitObjects[i],beatmap.HitObjects[i-1],i>1?beatmap.HitObjects[i-2]:null,1,objects,i-1));
    var aim=new Aim(Array.Empty<Mod>(),true);var speed=new Speed(Array.Empty<Mod>());
    foreach(var o in objects) { aim.Process(o); speed.Process(o); }
    double a=Math.Sqrt(aim.DifficultyValue())*.0675,s=Math.Sqrt(speed.DifficultyValue())*.0675;
    double perf=Math.Pow(Math.Pow(Math.Pow(5*Math.Max(1,a/.0675)-4,3)/100000,1.1)+Math.Pow(Math.Pow(5*Math.Max(1,s/.0675)-4,3)/100000,1.1),1/1.1);
    double stars=beatmap.HitObjects.Count==0?0:perf>.00001?Math.Cbrt(1.14)*.027*(Math.Cbrt(100000/Math.Pow(2,1/1.1)*perf)+4):0;
    var output=new List<string>{"# Public ppy/osu 4e96853c7543f80a1b822ccd381943c7377543d7; calculator 20220902; NM fixture oracle", "stars="+fmt(stars),"aim="+fmt(a),"speed="+fmt(s)};
    output.Add("heights="+string.Join(",",beatmap.HitObjects.Select(o=>o.StackHeight)));
    for(int i=0;i<objects.Count;i++) {
        var o=(OsuDifficultyHitObject)objects[i];
        output.Add("object."+i+"="+string.Join(",",new[]{o.StrainTime,o.LazyJumpDistance,o.Angle??-1,AimEvaluator.EvaluateDifficultyOf(o,true),SpeedEvaluator.EvaluateDifficultyOf(o),RhythmEvaluator.EvaluateDifficultyOf(o),o.TravelDistance,o.TravelTime,o.MinimumJumpDistance,o.MinimumJumpTime}.Select(fmt)));
    }
    for(int i=0;i<beatmap.HitObjects.Count;i++) if(beatmap.HitObjects[i] is Slider slider) {
        // Trigger lazy calculation for a lone / first slider as well, without feeding skills.
        if(slider.LazyEndPosition==null) _=new OsuDifficultyHitObject(slider,new HitCircle{Scale=scale,StartTime=slider.StartTime-100},null,1,new(),0);
        if(Path.GetFileName(file).StartsWith("timing-"))output.Add("sliderTiming."+i+"="+string.Join(",",sliderTiming[slider].Select(fmt)));
        output.Add("slider."+i+"="+string.Join(",",new[]{slider.Path.Distance,slider.SpanDuration,slider.EndTime,slider.EndPosition.X,slider.EndPosition.Y,slider.LazyTravelTime,slider.LazyTravelDistance,slider.LazyEndPosition!.Value.X,slider.LazyEndPosition!.Value.Y}.Select(fmt)));
        for(int j=0;j<slider.NestedHitObjects.Count;j++) {var n=(OsuHitObject)slider.NestedHitObjects[j];output.Add($"nested.{i}.{j}="+string.Join(",",new[]{n.StartTime,n.StackedPosition.X,n.StackedPosition.Y,n is SliderRepeat?1d:0d}.Select(fmt)));}
        output.Add("path."+i+"="+string.Join(",",new[]{0d,.25,.5,.75,1}.SelectMany(t=>{var v=slider.Path.PositionAt(t);return new[]{(double)v.X,v.Y};}).Select(fmt)));
    }
    output.Add("aimPeaks="+string.Join(",",aim.GetCurrentStrainPeaks().Select(fmt)));
    output.Add("speedPeaks="+string.Join(",",speed.GetCurrentStrainPeaks().Select(fmt)));
    File.WriteAllLines(Path.ChangeExtension(file,"properties"),output);
    Console.WriteLine(Path.GetFileName(file)+" "+fmt(stars));
}
