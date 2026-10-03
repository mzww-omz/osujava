// Fixture-only adapters: linear path approximation is the identity; unverified curves throw.
namespace osuTK {
    public struct Vector2 {
        public float X, Y;
        public Vector2(float x,float y) { X=x; Y=y; }
        public Vector2(float v) : this(v,v) { }
        public static Vector2 Zero => new(0);
        public Vector2 Normalized() { float inverse=1/Length; return this*inverse; }
        public static bool operator ==(Vector2 a,Vector2 b) => a.X==b.X && a.Y==b.Y;
        public static bool operator !=(Vector2 a,Vector2 b) => !(a==b);
        public override bool Equals(object? o) => o is Vector2 v && this==v;
        public override int GetHashCode() => HashCode.Combine(X,Y);
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
        private int height; public virtual int StackHeight { get=>height; set=>height=value; }
        public float Scale,TimePreempt;
        public double TimeFadeIn;
        public double Radius => 64*Scale;
        public osuTK.Vector2 StackedPosition => Position+new osuTK.Vector2(StackHeight*Scale*-6.4f);
        public osu.Game.Rulesets.Scoring.HitWindows HitWindows=new();
    }
    public class HitCircle : OsuHitObject { }
    public class Spinner : OsuHitObject { }
    public class SliderRepeat : OsuHitObject { }
    public class Slider : OsuHitObject {
        public double LazyTravelTime,SpanDuration;
        public float LazyTravelDistance;
        public osuTK.Vector2? LazyEndPosition;
        public int RepeatCount;
        public HitCircle HeadCircle=new(),TailCircle=new();
        public List<osu.Game.Rulesets.Objects.HitObject> NestedHitObjects=new();
        public osu.Game.Rulesets.Objects.SliderPath Path=new();
        public override osuTK.Vector2 EndPosition => Position+Path.PositionAt((RepeatCount+1)%2);
        public override int StackHeight { get=>base.StackHeight; set { base.StackHeight=value; foreach(var n in NestedHitObjects) ((OsuHitObject)n).StackHeight=value; } }
    }
}
namespace osu.Game.Beatmaps {
    public interface IBeatmap { BeatmapInfo BeatmapInfo { get; } }
    public class BeatmapInfo { public int BeatmapVersion; public float StackLeniency; }
    public class Beatmap<T> : IBeatmap { public List<T> HitObjects=new(); public BeatmapInfo BeatmapInfo { get; }=new(); }
    public class BeatmapProcessor { protected IBeatmap Beatmap; public BeatmapProcessor(IBeatmap b) { Beatmap=b; } public virtual void PostProcess() { } }
}

namespace Newtonsoft.Json { public class JsonIgnoreAttribute:Attribute { } public class JsonConstructorAttribute:Attribute { } }
namespace osu.Framework.Caching { public class Cached { public bool IsValid; public void Invalidate()=>IsValid=false; public void Validate()=>IsValid=true; } }
namespace osu.Framework.Bindables {
    public interface IBindable<T> { }
    public class Bindable<T>:IBindable<T> { private T value=default!; public event Action<T>? ValueChanged; public T Value { get=>value; set { this.value=value; ValueChanged?.Invoke(value); } } }
    public class BindableList<T>:List<T> { public event System.Collections.Specialized.NotifyCollectionChangedEventHandler? CollectionChanged; public new void AddRange(IEnumerable<T> items) { foreach(var i in items) { Add(i);CollectionChanged?.Invoke(this,new(System.Collections.Specialized.NotifyCollectionChangedAction.Add,i)); } } }
}
namespace osu.Game.Rulesets.Objects.Types { public enum PathType { Linear,PerfectCurve,Catmull,Bezier } }
namespace osu.Game.Rulesets.Objects { public class PathControlPoint { public osuTK.Vector2 Position; public Types.PathType? Type; public event Action? Changed; public PathControlPoint(osuTK.Vector2 p,Types.PathType? t) { Position=p;Type=t; } } }
namespace osu.Framework.Utils {
    public static class Precision { public static bool AlmostEquals(double a,double b)=>Math.Abs(a-b)<=1e-7; }
    public static class PathApproximator {
        public static List<osuTK.Vector2> ApproximateLinear(ReadOnlySpan<osuTK.Vector2> p)=>p.ToArray().ToList();
        public static List<osuTK.Vector2> ApproximateCircularArc(ReadOnlySpan<osuTK.Vector2> p)=>throw new NotSupportedException();
        public static List<osuTK.Vector2> ApproximateCatmull(ReadOnlySpan<osuTK.Vector2> p)=>throw new NotSupportedException();
        public static List<osuTK.Vector2> ApproximateBezier(ReadOnlySpan<osuTK.Vector2> p)=>throw new NotSupportedException();
    }
}
