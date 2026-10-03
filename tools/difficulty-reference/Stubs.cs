// Fixture-only adapters. All slider paths throw: this oracle supports circles/spinners only.
namespace osuTK {
    public struct Vector2 {
        public float X, Y;
        public Vector2(float x,float y) { X=x; Y=y; }
        public Vector2(float v) : this(v,v) { }
        public float Length => (float)Math.Sqrt(X*X+Y*Y);
        public static Vector2 operator +(Vector2 a,Vector2 b) => new(a.X+b.X,a.Y+b.Y);
        public static Vector2 operator -(Vector2 a,Vector2 b) => new(a.X-b.X,a.Y-b.Y);
        public static Vector2 operator *(Vector2 a,float b) => new(a.X*b,a.Y*b);
        public static Vector2 Add(Vector2 a,Vector2 b) => a+b;
        public static Vector2 Subtract(Vector2 a,Vector2 b) => a-b;
        public static Vector2 Multiply(Vector2 a,float b) => a*b;
        public static float Dot(Vector2 a,Vector2 b) => a.X*b.X+a.Y*b.Y;
    }
}
namespace osu.Framework.Graphics { public static class Vector2Extensions { public static float Distance(osuTK.Vector2 a,osuTK.Vector2 b) => (a-b).Length; } }
namespace osu.Framework.Utils { public static class Interpolation { public static double Lerp(double a,double b,double t) => a+(b-a)*t; } }
namespace osu.Game.Rulesets.Mods { public class Mod { } }
namespace osu.Game.Rulesets.Scoring {
    public enum HitResult { Great }
    public class HitWindows { public double Window; public double WindowFor(HitResult _) => Window; }
}
namespace osu.Game.Rulesets.Objects {
    public class HitObject { public double StartTime; public double EndTime; public double GetEndTime() => EndTime; }
}
namespace osu.Game.Rulesets.Osu.Mods { public static class OsuModHidden { public const double FADE_OUT_DURATION_MULTIPLIER=.3; } }
namespace osu.Game.Rulesets.Osu.Objects {
    public class OsuHitObject : osu.Game.Rulesets.Objects.HitObject {
        public osuTK.Vector2 Position;
        public virtual osuTK.Vector2 EndPosition => Position;
        public int StackHeight;
        public float Scale,TimePreempt;
        public double TimeFadeIn;
        public double Radius => 64*Scale;
        public osuTK.Vector2 StackedPosition => Position+new osuTK.Vector2(StackHeight*Scale*-6.4f);
        public osu.Game.Rulesets.Scoring.HitWindows HitWindows=new();
    }
    public class HitCircle : OsuHitObject { }
    public class Spinner : OsuHitObject { }
    public class SliderRepeat : OsuHitObject { }
    public class SliderPath { public osuTK.Vector2 PositionAt(double _) => throw new NotSupportedException(); }
    public class Slider : OsuHitObject {
        public double LazyTravelTime,SpanDuration;
        public float LazyTravelDistance;
        public osuTK.Vector2? LazyEndPosition;
        public int RepeatCount;
        public HitCircle HeadCircle=new(),TailCircle=new();
        public List<osu.Game.Rulesets.Objects.HitObject> NestedHitObjects=new();
        public SliderPath Path=new();
    }
}
namespace osu.Game.Beatmaps {
    public interface IBeatmap { BeatmapInfo BeatmapInfo { get; } }
    public class BeatmapInfo { public int BeatmapVersion; public float StackLeniency; }
    public class Beatmap<T> : IBeatmap { public List<T> HitObjects=new(); public BeatmapInfo BeatmapInfo { get; }=new(); }
    public class BeatmapProcessor { protected IBeatmap Beatmap; public BeatmapProcessor(IBeatmap b) { Beatmap=b; } public virtual void PostProcess() { } }
}
