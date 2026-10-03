package dev.osujava.difficulty;

import dev.osujava.beatmap.SliderData.CurveType;
import java.util.*;
import static dev.osujava.difficulty.SliderPreprocessing.*;

/** Independent bounded float curve maths, verified against the pinned public framework oracle. */
final class SliderPathApproximator {
    static final int MAX_CURVE_CONTROLS = 64;
    private static final int MAX_SUBDIVISION_DEPTH = 32;
    private record Curve(Vec[] controls,int depth) { }

    static void append(CurveType type,List<Vec> controls,boolean borrowed,int version,List<Vec> output,Budget budget) {
        if(type==CurveType.BSPLINE) throw new Unsupported("Degree-specific B-spline is outside the pinned legacy decoder contract");
        if(type==CurveType.PERFECT) {
            if(controls.size()!=3) type=CurveType.BEZIER;
            else if(collinear(controls.toArray(Vec[]::new))) type=CurveType.LINEAR;
        }
        int start=0,readable=controls.size()-(borrowed?1:0);
        for(int i=1;i<readable;i++) {
            if(!controls.get(i).same(controls.get(i-1)) || i==readable-1 || type==CurveType.CATMULL && i>1 && version<128) continue;
            approximate(type,controls.subList(start,i).toArray(Vec[]::new),output,budget);
            start=i; // Retain the shared endpoint as the first point of the next mathematical curve.
        }
        approximate(type,controls.subList(start,controls.size()).toArray(Vec[]::new),output,budget);
    }
    private static void emit(List<Vec> output,Vec point,Budget budget) {
        budget.spend();
        if(!Float.isFinite(point.x()) || !Float.isFinite(point.y())) throw new Unsupported("Non-finite curve approximation");
        if(output.isEmpty() || !output.getLast().same(point)) output.add(point);
    }
    private static void approximate(CurveType type,Vec[] p,List<Vec> output,Budget budget) {
        if(p.length==0) return;
        if(type==CurveType.LINEAR) {for(Vec v:p) emit(output,v,budget);return;}
        if(p.length>MAX_CURVE_CONTROLS) throw new Unsupported("Curve control point limit exceeded");
        if(type==CurveType.CATMULL) {
            for(int i=0;i<p.length-1;i++) {
                Vec a=p[Math.max(0,i-1)],b=p[i],c=p[i+1],d=i+2<p.length?p[i+2]:c.plus(c).minus(b);
                for(int step=0;step<50;step++) {
                    emit(output,catmull(a,b,c,d,(float)step/50),budget);
                    emit(output,catmull(a,b,c,d,(float)(step+1)/50),budget);
                }
            }
        } else if(type==CurveType.PERFECT && p.length==3 && !collinear(p)) arc(p,output,budget);
        else bezier(p,output,budget);
    }
    private static Vec catmull(Vec a,Vec b,Vec c,Vec d,float t) {
        return new Vec(polynomial(a.x(),b.x(),c.x(),d.x(),t),polynomial(a.y(),b.y(),c.y(),d.y(),t));
    }
    private static float polynomial(float a,float b,float c,float d,float t) {
        float t2=t*t,t3=t*t2;
        return .5f*(2*b+(-a+c)*t+(2*a-5*b+4*c-d)*t2+(-a+3*b-3*c+d)*t3);
    }
    private static boolean collinear(Vec[] p) {
        float cross=(p[1].y()-p[0].y())*(p[2].x()-p[0].x())-(p[1].x()-p[0].x())*(p[2].y()-p[0].y());
        return Math.abs(cross)<=1e-3f;
    }
    private static void arc(Vec[] p,List<Vec> output,Budget budget) {
        Vec a=p[0],b=p[1],c=p[2];
        float divisor=2*(a.x()*(b.y()-c.y())+b.x()*(c.y()-a.y())+c.x()*(a.y()-b.y()));
        Vec centre=new Vec(a.squaredLength()*(b.y()-c.y())+b.squaredLength()*(c.y()-a.y())+c.squaredLength()*(a.y()-b.y()),
                a.squaredLength()*(c.x()-b.x())+b.squaredLength()*(a.x()-c.x())+c.squaredLength()*(b.x()-a.x())).divided(divisor);
        Vec first=a.minus(centre),last=c.minus(centre);float radius=first.length();
        double start=Math.atan2(first.y(),first.x()),end=Math.atan2(last.y(),last.x());
        while(end<start) end+=2*Math.PI;
        double range=end-start,direction=1;
        Vec chord=c.minus(a),side=b.minus(a);
        if(chord.y()*side.x()-chord.x()*side.y()<0) {direction=-1;range=2*Math.PI-range;}
        double samples=2*radius<=.1f?2:Math.max(2,Math.ceil(range/(2*Math.acos(1-.1f/radius))));
        if(!Double.isFinite(samples) || samples>MAX_WORK) throw new Unsupported("Circular arc precision or sample limit exceeded");
        for(int i=0;i<(int)samples;i++) {
            double angle=start+direction*((double)i/((int)samples-1))*range;
            emit(output,centre.plus(new Vec((float)Math.cos(angle),(float)Math.sin(angle)).times(radius)),budget);
        }
    }
    /** De Casteljau at t=1/2, retaining the two control polygons. */
    private static Vec[][] halves(Vec[] p,Budget budget) {
        Vec[] work=p.clone(),left=new Vec[p.length],right=new Vec[p.length];
        for(int row=0;row<p.length;row++) {
            left[row]=work[0];right[p.length-1-row]=work[p.length-1-row];
            for(int i=0;i<p.length-1-row;i++) {budget.spend();work[i]=work[i].plus(work[i+1]).divided(2);}
        }
        return new Vec[][]{left,right};
    }
    private static void bezier(Vec[] controls,List<Vec> output,Budget budget) {
        var pending=new ArrayDeque<Curve>();pending.push(new Curve(controls,0));
        while(!pending.isEmpty()) {
            budget.spend();Curve curve=pending.pop();Vec[] p=curve.controls;
            boolean flat=true;
            for(int i=1;i<p.length-1;i++) {
                budget.spend();
                if(p[i-1].minus(p[i].times(2)).plus(p[i+1]).squaredLength()>.25f*.25f*4) {flat=false;break;}
            }
            Vec[][] split=halves(p,budget);
            if(!flat) {
                if(curve.depth>=MAX_SUBDIVISION_DEPTH) throw new Unsupported("Bezier subdivision depth exceeded");
                pending.push(new Curve(split[1],curve.depth+1));pending.push(new Curve(split[0],curve.depth+1));
            } else {
                Vec[] chain=new Vec[p.length*2-1];System.arraycopy(split[0],0,chain,0,p.length);
                System.arraycopy(split[1],1,chain,p.length,p.length-1);
                emit(output,p[0],budget);
                for(int i=1;i<p.length-1;i++) {
                    int j=2*i;emit(output,chain[j-1].plus(chain[j].times(2)).plus(chain[j+1]).times(.25f),budget);
                }
            }
        }
        emit(output,controls[controls.length-1],budget);
    }
}
