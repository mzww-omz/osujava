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
    // Verified slider fixtures are v6+ (no early-version offset); parsing retains source order.
    private double getOffsetTime(double time)=>time;
    private TimingControlPoint CreateTimingControlPoint()=>new();
    public LegacyControlPointInfo Decode(IEnumerable<string> sourceRows) {
        foreach(var line in sourceRows)handleTimingPoint(line);
        flushPendingPoints();
        return beatmap.ControlPointInfo;
    }
}
namespace osu.Game.Beatmaps {
    public record FixtureDifficulty(double SliderMultiplier,double SliderTickRate,float CircleSize=5,float OverallDifficulty=5,float ApproachRate=5,float DrainRate=5):IBeatmapDifficultyInfo;
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

namespace osu.Game.Rulesets.Objects.Types {
    public interface IHasPosition { osuTK.Vector2 Position { get; } }
    public interface IHasCombo { bool NewCombo { get; } int ComboOffset { get; } }
    public interface IHasSliderVelocity { double SliderVelocity { get; } }
    public interface IHasGenerateTicks { bool GenerateTicks { get; } }
    public interface IHasDuration { double EndTime { get; } }
    public interface IHasLegacyLastTickOffset { double? LegacyLastTickOffset { get; } }
    public interface IHasPathWithRepeats { osu.Game.Rulesets.Objects.SliderPath Path { get; } IList<IList<osu.Game.Audio.HitSampleInfo>> NodeSamples { get; } int RepeatCount { get; } }
    public interface IHasComboInformation { osu.Framework.Bindables.Bindable<int> ComboIndexBindable { get; } osu.Framework.Bindables.Bindable<int> ComboIndexWithOffsetsBindable { get; } osu.Framework.Bindables.Bindable<int> IndexInCurrentComboBindable { get; } }
    public static class FixtureSpanExtensions { public static int SpanCount(this osu.Game.Rulesets.Osu.Objects.Slider slider)=>slider.RepeatCount+1; }
}
namespace osu.Framework.Extensions.IEnumerableExtensions { public static class FixtureEnumerableExtensions { public static IEnumerable<T> Yield<T>(this T value) { yield return value; } } }
namespace osu.Game.Rulesets.Osu.UI { public static class OsuPlayfield { public static readonly osuTK.Vector2 BASE_SIZE=new(512,384); } }
abstract class FixtureConverterBase {
    protected abstract IEnumerable<osu.Game.Rulesets.Osu.Objects.OsuHitObject> ConvertHitObject(osu.Game.Rulesets.Objects.HitObject original,osu.Game.Beatmaps.IBeatmap map,CancellationToken token);
}
partial class FixtureOsuConverter:FixtureConverterBase {
    public osu.Game.Rulesets.Osu.Objects.Slider Convert(FixtureLegacySlider original,osu.Game.Beatmaps.IBeatmap map)=>(osu.Game.Rulesets.Osu.Objects.Slider)ConvertHitObject(original,map,default).Single();
}
class FixtureLegacySlider:osu.Game.Rulesets.Objects.HitObject,osu.Game.Rulesets.Objects.Types.IHasPosition,osu.Game.Rulesets.Objects.Types.IHasPathWithRepeats,osu.Game.Rulesets.Objects.Types.IHasSliderVelocity,osu.Game.Rulesets.Objects.Types.IHasGenerateTicks,osu.Game.Rulesets.Objects.Types.IHasLegacyLastTickOffset {
    public osuTK.Vector2 Position { get; init; }
    public osu.Game.Rulesets.Objects.SliderPath Path { get; init; }=new();
    public IList<IList<osu.Game.Audio.HitSampleInfo>> NodeSamples { get; init; }=new List<IList<osu.Game.Audio.HitSampleInfo>>();
    public int RepeatCount { get; init; }
    public double SliderVelocity { get; init; }
    public bool GenerateTicks { get; init; }
    public double? LegacyLastTickOffset { get; init; }=36;
}
