package dev.osujava.difficulty;

import dev.osujava.beatmap.*;
import java.util.*;
import java.util.concurrent.CancellationException;

/** NM difficulty-only float geometry. Gameplay keeps its own path and event contract. */
final class LinearSliderPreprocessing {
    static final int MAX_WORK = 100_000;
    // Preserve float products: MAX_SLIDER_RADIUS is slightly greater than decimal 120.
    static final float MAX_SLIDER_RADIUS = 50*2.4f;
    static final float ASSUMED_SLIDER_RADIUS = 50*1.8f;
    static final class Unsupported extends RuntimeException { Unsupported(String reason) { super(reason); } }
    static final class Budget {
        int used;
        void spend() {
            if ((++used & 255)==0 && Thread.currentThread().isInterrupted()) throw new CancellationException();
            if(used>MAX_WORK) throw new Unsupported("Slider preprocessing work limit exceeded");
        }
    }
    record Vec(float x,float y) {
        boolean same(Vec b) { return b!=null && x==b.x && y==b.y; }
        Vec plus(Vec b) { return new Vec(x+b.x,y+b.y); }
        Vec minus(Vec b) { return new Vec(x-b.x,y-b.y); }
        Vec times(float n) { return new Vec(x*n,y*n); }
        float length() { return (float)Math.sqrt(x*x+y*y); }
    }
    record Nested(double time,Vec relative,boolean repeat) { }
    static final class Path {
        final List<Vec> points = new ArrayList<>();
        final List<Double> cumulative = new ArrayList<>();
        final double distance;
        Path(HitObject object,Budget budget) {
            SliderData data=object.sliderData();
            if(data==null || data.segments().size()!=1 || data.curveType()!=SliderData.CurveType.LINEAR)
                throw new Unsupported("Only single-segment Linear slider paths are verified");
            Vec head=new Vec((float)object.x(),(float)object.y());
            Vec previousControl=null,lastControl=null;
            for(var p:data.segments().getFirst().controlPoints()) {
                budget.spend();
                if(!Double.isFinite(p.x()) || !Double.isFinite(p.y()) || Math.abs(p.x())>100_000 || Math.abs(p.y())>100_000)
                    throw new Unsupported("Slider control point outside verified range");
                Vec v=new Vec((float)p.x(),(float)p.y()).minus(head);
                previousControl=lastControl;lastControl=v;
                if(points.isEmpty() || !points.getLast().same(v)) points.add(v);
            }
            double calculated=0;cumulative.add(0.0);
            for(int i=1;i<points.size();i++) { calculated+=points.get(i).minus(points.get(i-1)).length();cumulative.add(calculated); }
            double expected=data.pixelLength();
            if(expected>100_000) throw new Unsupported("Slider length exceeds verified range");
            boolean duplicateEnd=lastControl.same(previousControl) && expected>calculated;
            if(duplicateEnd) cumulative.add(calculated);
            else if(calculated!=expected) {
                cumulative.removeLast();int end=points.size()-1;
                if(calculated>expected) while(!cumulative.isEmpty() && cumulative.getLast()>=expected) {
                    cumulative.removeLast();points.remove(end--);
                }
                if(end<=0) cumulative.add(0.0);
                else {
                    Vec direction=points.get(end).minus(points.get(end-1));
                    if(direction.length()==0) throw new Unsupported("Slider segment below verified float precision");
                    direction=direction.times(1/direction.length());
                    points.set(end,points.get(end-1).plus(direction.times((float)(expected-cumulative.getLast()))));
                    cumulative.add(expected);
                }
            }
            if(!points.getFirst().same(new Vec(0,0))) throw new Unsupported("Slider path does not start at its head");
            for(Vec point:points) if(!Float.isFinite(point.x) || !Float.isFinite(point.y))
                throw new Unsupported("Non-finite slider path");
            distance=cumulative.getLast();
        }
        Vec at(double progress) {
            double d=Math.clamp(progress,0,1)*distance;
            int i=Collections.binarySearch(cumulative,d);if(i<0) i=~i;
            if(i<=0) return points.getFirst();if(i>=points.size()) return points.getLast();
            double from=cumulative.get(i-1),to=cumulative.get(i);
            if(Math.abs(to-from)<=1e-7) return points.get(i-1);
            return points.get(i-1).plus(points.get(i).minus(points.get(i-1)).times((float)((d-from)/(to-from))));
        }
    }
    static final class Slider {
        final Path path;
        final int repeats;
        final double start,spanDuration,end;
        final Vec head;
        final List<Nested> nested=new ArrayList<>();
        Vec endRelative,lazy;
        float lazyDistance;
        double lazyTime;
        Slider(HitObject object,double beatLength,double sv,BeatmapDifficulty chart,Budget budget) {
            head=new Vec((float)object.x(),(float)object.y());
            path=new Path(object,budget);repeats=object.sliderData().repeatCount();
            if(repeats>=MAX_WORK || path.distance<=0) throw new Unsupported("Degenerate or excessive-repeat slider is not verified");
            double multiplier=chart.settings().sliderMultiplier(),tickRate=chart.settings().sliderTickRate();
            if(!Double.isFinite(multiplier) || multiplier<.4 || multiplier>3.6 || !Double.isFinite(tickRate) || tickRate<.5 || tickRate>8)
                throw new Unsupported("Slider settings outside verified range");
            start=object.timeMs();double scoringDistance=100*multiplier*sv,velocity=scoringDistance/beatLength;
            spanDuration=path.distance/velocity;end=start+(repeats+1)*spanDuration;
            if(!Double.isFinite(end) || end-start>6*60*60*1000L) throw new Unsupported("Slider duration exceeds verified range");
            double tickDistance=Math.min(scoringDistance/tickRate,path.distance);
            endRelative=path.at((repeats+1)%2);
            budget.spend();nested.add(new Nested(start,path.at(0),false));
            for(int span=0;span<=repeats;span++) {
                budget.spend();boolean reversed=span%2==1;double spanStart=start+span*spanDuration;
                var ticks=new ArrayList<Nested>();
                for(double d=tickDistance;d<path.distance-velocity*10;d+=tickDistance) {
                    budget.spend();double progress=d/path.distance;
                    ticks.add(new Nested(spanStart+(reversed?1-progress:progress)*spanDuration,path.at(progress),false));
                }
                if(reversed) Collections.reverse(ticks);nested.addAll(ticks);
                if(span<repeats) {budget.spend();nested.add(new Nested(spanStart+spanDuration,path.at((span+1)%2),true));}
            }
            budget.spend();nested.add(new Nested(Math.max(start+(end-start)/2,end-36),endRelative,false));
            // HitObject.ApplyDefaults sorts nested objects by StartTime: late ticks can follow the legacy tail.
            nested.sort(Comparator.comparingDouble(Nested::time));
            for(int i=1;i<nested.size();i++) if(nested.get(i).time==nested.get(i-1).time)
                throw new Unsupported("Coincident slider nested events are not verified");
        }
        Vec nestedPosition(Vec relative,float offset) { return head.plus(relative).plus(new Vec(offset,offset)); }
        void cursor(float offset,float radius) {
            if(lazy!=null) return;
            Vec stackedHead=head.plus(new Vec(offset,offset));
            lazyTime=nested.getLast().time-start;
            double progress=lazyTime/spanDuration;
            progress=progress%2>=1?1-progress%1:progress%1;
            Vec temporary=stackedHead.plus(path.at(progress)),cursor=stackedHead;
            double scale=50/(double)radius;
            for(int i=1;i<nested.size();i++) {
                if((i&255)==0 && Thread.currentThread().isInterrupted()) throw new CancellationException();
                Nested n=nested.get(i);Vec movement=nestedPosition(n.relative,offset).minus(cursor);
                if(i==nested.size()-1) {
                    Vec alternative=temporary.minus(cursor);
                    if(alternative.length()<movement.length()) movement=alternative;
                }
                double distance=scale*movement.length(),threshold=i<nested.size()-1 && n.repeat?50:ASSUMED_SLIDER_RADIUS;
                if(distance>threshold) {
                    double portion=(distance-threshold)/distance;
                    cursor=cursor.plus(movement.times((float)portion));
                    lazyDistance+=(float)(distance*portion);
                }
            }
            lazy=cursor;
        }
    }
    static Slider[] prepare(BeatmapDifficulty chart) {
        Slider[] result=new Slider[chart.hitObjects().size()];
        if(chart.hitObjects().stream().noneMatch(o->o.type()==HitObject.Type.SLIDER)) return result;
        if(chart.settings().formatVersion()<8) throw new Unsupported("Pre-v8 slider tick distance is not verified");
        var timing=chart.timingPoints();
        if(timing.size()>MAX_WORK) throw new Unsupported("Timing point work limit exceeded");
        double previous=Double.NEGATIVE_INFINITY;
        for(var p:timing) {
            if(!Double.isFinite(p.timeMs()) || !Double.isFinite(p.beatLength()) || p.timeMs()<=previous)
                throw new Unsupported("Non-finite or coincident slider timing points are not verified");
            previous=p.timeMs();
            if(p.uninherited() && (p.beatLength()<6 || p.beatLength()>60000)) throw new Unsupported("Beat length outside verified range");
        }
        Budget budget=new Budget();int point=0;
        // TimingPointAt falls back to the first red line, even before its timestamp.
        double beat=timing.stream().filter(TimingPoint::uninherited).mapToDouble(TimingPoint::beatLength).findFirst().orElse(1000),sv=1;
        for(int i=0;i<result.length;i++) {
            var object=chart.hitObjects().get(i);
            while(point<timing.size() && timing.get(point).timeMs()<=object.timeMs()) {
                budget.spend();var p=timing.get(point++);
                if(p.uninherited()) beat=p.beatLength();
                double raw=p.beatLength()<0?100/-p.beatLength():1;
                // Until rounding-boundary oracle cases exist, accept only already representable SVs.
                sv=Math.clamp(raw,.1,10);
                if(Math.abs(sv-Math.rint(sv/.01)*.01)>1e-10) throw new Unsupported("Slider velocity precision boundary is not verified");
                sv=Math.rint(sv/.01)*.01;
            }
            if(object.type()==HitObject.Type.SLIDER) result[i]=new Slider(object,beat,sv,chart,budget);
        }
        return result;
    }
}
