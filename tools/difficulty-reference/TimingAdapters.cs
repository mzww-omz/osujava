// Fixture-only dependencies around the unchanged public timing decoder/control-point methods.
// No audio, serialization, editor or framework binding graph runs in this development oracle.
using osu.Game.Beatmaps.ControlPoints;
using osu.Game.Beatmaps.Legacy;
using osu.Game.Beatmaps.Timing;
using osu.Game.Audio;

partial class FixtureTimingDecoder {
    private sealed class RulesetInfo { public int OnlineID=0; }
    private sealed class MapInfo { public RulesetInfo Ruleset=new(); }
    private sealed class Map { public MapInfo BeatmapInfo=new(); public LegacyControlPointInfo ControlPointInfo=new(); }
    private readonly Map beatmap=new();
    private readonly List<ControlPoint> pendingControlPoints=new();
    private readonly HashSet<Type> pendingControlPointTypes=new();
    private double pendingControlPointsTime;
    private LegacySampleBank defaultSampleBank;
    private int defaultSampleVolume=100;
    // Verified slider fixtures are v8+ (no early-version offset); parsing retains source order.
    private double getOffsetTime(double time)=>time;
    private TimingControlPoint CreateTimingControlPoint()=>new();
    public LegacyControlPointInfo Decode(IEnumerable<string> sourceRows) {
        foreach(var line in sourceRows)handleTimingPoint(line);
        flushPendingPoints();
        return beatmap.ControlPointInfo;
    }
}
namespace osu.Game.Beatmaps {
    public interface IBeatmapDifficultyInfo { double SliderMultiplier { get; } double SliderTickRate { get; } }
    public record FixtureDifficulty(double SliderMultiplier,double SliderTickRate):IBeatmapDifficultyInfo;
}
namespace osu.Game.Utils { public interface IDeepCloneable<T> { T DeepClone(); } }
namespace osu.Game.Screens.Edit { public static class BindableBeatDivisor { public static readonly int[] PREDEFINED_DIVISORS=Array.Empty<int>(); } }
namespace JetBrains.Annotations { public class NotNullAttribute:Attribute { } }
namespace Newtonsoft.Json {
    public class JsonWriter { } public class JsonReader { }
    public class JsonSerializer {
        public void Serialize(JsonWriter writer,object value)=>throw new NotSupportedException();
        public void Populate(JsonReader reader,object value)=>throw new NotSupportedException();
    }
}
namespace osu.Framework.IO.Serialization { public interface ISerializableSortedList { void SerializeTo(Newtonsoft.Json.JsonWriter writer,Newtonsoft.Json.JsonSerializer serializer); void DeserializeFrom(Newtonsoft.Json.JsonReader reader,Newtonsoft.Json.JsonSerializer serializer); } }
namespace osu.Framework.Extensions.TypeExtensions { public static class FixtureTypeExtensions { public static string ReadableName(this Type type)=>type.Name; } }
namespace osu.Framework.Extensions.EnumExtensions { public static class FixtureEnumExtensions { public static bool HasFlagFast<T>(this T value,T flag) where T:Enum=>value.HasFlag(flag); } }
namespace osu.Game.Audio {
    public class HitSampleInfo {
        public const string BANK_NORMAL="normal",HIT_NORMAL="hitnormal";
        public string Bank; public int Volume;
        public HitSampleInfo(string name=HIT_NORMAL,string bank=BANK_NORMAL,int volume=100) { Bank=bank;Volume=volume; }
        public virtual HitSampleInfo With(string? newBank=null,int? newVolume=null,int? newCustomSampleBank=null)=>new(bank:newBank??Bank,volume:newVolume??Volume);
    }
}
namespace osu.Game.Rulesets.Objects.Legacy {
    public class ConvertHitObjectParser {
        public class LegacyHitSampleInfo:osu.Game.Audio.HitSampleInfo { public int CustomSampleBank; public bool BankSpecified; }
    }
}
