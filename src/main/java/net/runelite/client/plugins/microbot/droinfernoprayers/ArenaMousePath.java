package net.runelite.client.plugins.microbot.droinfernoprayers;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.runelite.api.Point;

/** Spatial variation only. The arena's existing clock, duration and sample cadence own timing. */
final class ArenaMousePath
{
    private static final List<Template> TEMPLATES=load();
    private static final double[] PIXEL_CAP={1.5,4,10,18},FRACTION_CAP={.01,.025,.05,.08};
    private final Point from,to;
    private final double dx,dy,distance,normalX,normalY,lateralGain,deviationGain,noiseGain,missGain,overshoot;
    /** BaseProfileDro/NaturalMouse overshoot points beside the target (largest first), pixels; empty for none. */
    private final double[][] shoots;
    private final Template template;
    private final Human human;
    final int family;
    final boolean corrective;

    /** Eagerly load the small spatial bank when the mouse is created, outside gesture execution. */
    static void preload(){ }
    /** NaturalMouse's fast-gamer overshoot count (FactoryTemplates.createFastGamerMotionFactory: setOvershoots(3)). */
    static final int OVERSHOOTS=3;
    /** NaturalMouse fast-gamer deviation divider (SinusoidalDeviationProvider.DEFAULT_SLOPE_DIVIDER). */
    static final double SLOPE_DIVIDER=10;
    /** Share of that deviation the arena keeps, so cursor speed stays within the 5% budget. */
    static double DEVIATION_SCALE=Double.parseDouble(System.getProperty("arena.mouse.deviation","1.0"));
    /** Share of eligible moves that overshoot (user: NaturalMouse's every-move overshoots were "so many"). */
    static final double SHOOT_CHANCE=.15;
    /** Overshoots from this distance (NaturalMouse rounds small offsets to zero, so short moves rarely show them). */
    static final double MIN_SHOOT_DISTANCE=24;
    /** Speed/length budget: the largest overshoot offset stays within these (scaled, never extra time). */
    static final double SHOOT_CAP_PIXELS=6,SHOOT_CAP_FRACTION=.02;
    /** The main stroke reaches the target by this share of the spatial path (+4% peak speed, inside the 5% budget);
     *  the overshoot and correction legs share the rest, in the slow tail. */
    static final double SHOOT_FROM=.96;
    private static final double[] ZERO={0,0};
    static int templateCount(){return TEMPLATES.size();}

    static ArenaMousePath choose(Point from,Point to,Random random){
        double distance=Math.hypot(to.getX()-from.getX(),to.getY()-from.getY());
        int draw=random.nextInt(100),family=draw<55?0:draw<90?1:draw<98?2:3;
        if(distance<24)family=0;else if(distance<80)family=Math.min(1,family);
        // Select uniformly within the family. The resource is balanced across contributors.
        int count=0;for(Template t:TEMPLATES)if(t.family==family)count++;
        Template selected=Template.STRAIGHT;
        if(count>0){int wanted=random.nextInt(count);for(Template t:TEMPLATES)if(t.family==family&&wanted--==0){selected=t;break;}}
        double sign=random.nextBoolean()?1:-1;
        // 1.0.74 (user: "fully integrate baseprofiledro mouse into the arena mouse", "keep cursor speed", no misclicks):
        // BaseProfileDro moves through NaturalMouse, whose overshoot manager aims up to OVERSHOOTS points beside the
        // target before the last correction: offset (int)(U*m-m/2)*remaining per axis, m = distance/20 (Default-
        // OvershootManager). Here they fit the arena's existing travel time and are scaled to SHOOT_CAP so the cursor's
        // speed and path length stay within 5% (seeded p95, 1,000 paths per distance). The press is always on the target.
        // User, 2026-11-06 ("that is so many" at 82% of long moves): overshoots are occasional, SHOOT_CHANCE of the moves
        // long enough for them, and never on a move that already makes the short-then-correct stroke.
        Human human=new Human(distance,false,random);
        double[][] shoots=new double[0][];
        if(distance>=MIN_SHOOT_DISTANCE&&human.split==0&&random.nextDouble()<SHOOT_CHANCE)
        {
            double m=distance/20d,big=0;double[][] raw=new double[OVERSHOOTS][2];int used=0;
            for(int remaining=OVERSHOOTS;remaining>=1;remaining--)
            {
                double x=(int)(random.nextDouble()*m-m/2d)*remaining,y=(int)(random.nextDouble()*m-m/2d)*remaining;
                if(x==0&&y==0)continue;raw[used][0]=x;raw[used][1]=y;used++;big=Math.max(big,Math.hypot(x,y));
            }
            double cap=Math.min(SHOOT_CAP_PIXELS,distance*SHOOT_CAP_FRACTION),scale=big>cap?cap/big:1;
            shoots=new double[used][];for(int i=0;i<used;i++)shoots[i]=new double[]{raw[i][0]*scale,raw[i][1]*scale};
        }
        double overshoot=0;
        // Human variation is drawn after the template so template selection is unchanged per seed.
        return new ArenaMousePath(from,to,selected,family,sign,overshoot,shoots,human);
    }

    private ArenaMousePath(Point from,Point to,Template template,int family,double sign,double overshoot,double[][] shoots,Human human){
        this.from=from;this.to=to;this.template=template;this.family=family;this.overshoot=overshoot;this.shoots=shoots;this.human=human;
        dx=to.getX()-from.getX();dy=to.getY()-from.getY();distance=Math.hypot(dx,dy);
        normalX=distance==0?0:-dy/distance;normalY=distance==0?0:dx/distance;
        double max=0;for(double y:template.y)max=Math.max(max,Math.abs(y));
        // Dampen recorded geometry to the user's arena preference. Never amplify a near-straight path.
        // Deviation, noise and the corrective miss share the same cap, so the total bound is unchanged.
        double cap=Math.min(PIXEL_CAP[family],distance*FRACTION_CAP[family]);
        // 1.0.75 (user: "way too many straight lines, previously reduced way too much"): BaseProfileDro/NaturalMouse's own
        // sinusoidal deviation, distance / SLOPE_DIVIDER per axis (SinusoidalDeviationProvider), not the old pixel cap.
        deviationGain=human.deviationShare==0?0:distance/SLOPE_DIVIDER*DEVIATION_SCALE;
        noiseGain=cap*human.noiseShare;missGain=sign*cap*human.missShare;
        double curve=cap*(1-human.deviationShare-human.noiseShare-human.missShare)*human.curveScale;
        lateralGain=sign*(max==0?0:Math.min(distance,curve/max));
        corrective=human.split>0;
    }

    Point at(double fraction){
        if(fraction<=0)return from;if(fraction>=1)return to;
        // Same 0..1 time fraction from the caller's clock; only where the cursor is at that time changes.
        double progress,miss;
        if(human.split>0&&fraction<human.split){
            double v=skewed(fraction/human.split,human.skew);progress=human.reach*v;miss=v;
        } else if(human.split>0){
            double v=minimumJerk((fraction-human.split)/(1-human.split));progress=human.reach+(1-human.reach)*v;miss=1-v;
        } else {progress=skewed(fraction,human.skew);miss=0;}
        // With overshoots the main stroke arrives by SHOOT_FROM of the spatial path; the overshoot and correction legs
        // then play around the target itself in the slow tail (a real NaturalMouse overshoot lands past or beside it).
        boolean shooting=shoots.length>0;double total=progress;
        if(shooting)progress=Math.min(1,progress/SHOOT_FROM);
        double index=progress*(template.y.length-1);int lo=(int)index;
        double lateral=(template.y[lo]+(template.y[Math.min(lo+1,template.y.length-1)]-template.y[lo])*(index-lo))*lateralGain
            +miss*missGain;
        double along=progress;
        if(overshoot>0&&progress>.84){
            if(progress<.93){double v=(progress-.84)/.09;v=v*v*(3-2*v);along=.84+(1+overshoot/distance-.84)*v;}
            else {double v=(progress-.93)/.07;v=v*v*(3-2*v);along=1+overshoot/distance*(1-v);}
        }
        // BaseProfileDro's NaturalMouse character in screen space: sinusoidal deviation with per-axis
        // multipliers plus accumulated noise, both faded out linearly before the target.
        double fade=overshoot>0?fadeBefore(progress,.84):fade(progress),bend=(1-Math.cos(progress*Math.PI*2))/2*deviationGain*fade;
        double offsetX=human.deviationX*bend+human.noiseX(progress)*noiseGain*fade;
        double offsetY=human.deviationY*bend+human.noiseY(progress)*noiseGain*fade;
        if(shooting&&total>SHOOT_FROM)
        {
            // Overshoot points one after another, then the final correction onto the target (at fraction 1, exactly).
            double span=(1-SHOOT_FROM)/(shoots.length+1),local=(total-SHOOT_FROM)/span;int leg=Math.min((int)local,shoots.length);
            double v=local-leg;v=v*v*(3-2*v);
            double[] a=leg==0?ZERO:shoots[leg-1],b=leg>=shoots.length?ZERO:shoots[leg];
            offsetX+=a[0]+(b[0]-a[0])*v;offsetY+=a[1]+(b[1]-a[1])*v;
        }
        return new Point((int)Math.round(from.getX()+dx*along+normalX*lateral+offsetX),
            (int)Math.round(from.getY()+dy*along+normalY*lateral+offsetY));
    }

    /** Minimum-jerk stroke whose velocity peak comes early: quicker launch, longer gentle settle. */
    static double skewed(double t,double skew){return minimumJerk(1-Math.pow(1-t,skew));}
    static double minimumJerk(double t){return t*t*t*(10-15*t+6*t*t);}
    /** NaturalMouse effect fade: full effect, then linear decay to zero over the final stretch. */
    static double fade(double progress){return fadeBefore(progress,1);}
    /** Paths with the rare tiny overshoot finish fading before it starts, keeping its 3 px bound. */
    static double fadeBefore(double progress,double end){double start=end-.25;return progress<=start?1:Math.max(0,(end-progress)/.25);}

    /** Per-gesture human variation. Spatial only; never changes duration, sampling or the endpoint. */
    private static final class Human {
        private static final int KNOTS=9;
        final double skew,deviationShare,noiseShare,missShare,curveScale,split,reach,deviationX,deviationY;
        final double[] walkX=new double[KNOTS],walkY=new double[KNOTS];
        Human(double distance,boolean overshoot,Random random){
            skew=1.12+random.nextDouble()*.33;
            boolean direct=distance<24;
            curveScale=direct?1:.8+random.nextDouble()*.2;
            deviationShare=direct?0:.10+random.nextDouble()*.15;
            noiseShare=direct?0:.04+random.nextDouble()*.08;
            // SinusoidalDeviationProvider multipliers, kept inside a unit length so the cap holds.
            double mx=(random.nextDouble()-.5)*2,my=(random.nextDouble()-.5)*2,length=Math.max(1,Math.hypot(mx,my));
            // NaturalMouse's MouseMotion multipliers: independent per axis on [-1,1].
            deviationX=mx;deviationY=my;
            // DefaultNoiseProvider: occasional kicks, likelier in the slow launch/settle, accumulated.
            double max=0;
            for(int i=1;i<KNOTS;i++){
                double p=i/(double)(KNOTS-1),slowness=1-4*p*(1-p);
                walkX[i]=walkX[i-1];walkY[i]=walkY[i-1];
                if(random.nextDouble()<.2+.6*slowness){walkX[i]+=random.nextDouble()-.5;walkY[i]+=random.nextDouble()-.5;}
                max=Math.max(max,Math.hypot(walkX[i],walkY[i]));
            }
            if(max>0)for(int i=0;i<KNOTS;i++){walkX[i]/=max;walkY[i]/=max;}
            // Occasional primary stroke that stops a little short, then a small corrective sub-movement.
            if(!direct&&!overshoot&&distance>=120&&random.nextDouble()<.22){
                split=.70+random.nextDouble()*.14;
                double gap=Math.max(2,Math.min(14,distance*(.015+random.nextDouble()*.025)));
                reach=1-gap/distance;missShare=random.nextDouble()*.13;
            } else {split=0;reach=1;missShare=0;}
        }
        double noiseX(double progress){return walk(walkX,progress);}
        double noiseY(double progress){return walk(walkY,progress);}
        private static double walk(double[] knots,double progress){
            double index=Math.max(0,Math.min(1,progress))*(KNOTS-1);int lo=Math.min((int)index,KNOTS-2);
            double v=index-lo;v=v*v*(3-2*v);
            return knots[lo]+(knots[lo+1]-knots[lo])*v;
        }
    }

    private static List<Template> load(){
        List<Template> templates=new ArrayList<>();
        try(InputStream in=ArenaMousePath.class.getResourceAsStream("arena-paths.csv")){
            if(in==null)return List.of(Template.STRAIGHT);
            try(BufferedReader reader=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){
                String line;
                while((line=reader.readLine())!=null){
                    if(line.isBlank()||line.startsWith("#"))continue;
                    String[] fields=line.split(",");if(fields.length!=19)continue;
                    int family=Integer.parseInt(fields[0]);double originalOvershoot=Double.parseDouble(fields[1]);
                    double[] y=new double[17];boolean valid=family>=0&&family<4&&Double.isFinite(originalOvershoot)&&originalOvershoot>=0;
                    for(int i=0;i<y.length;i++){y[i]=Double.parseDouble(fields[i+2]);valid&=Double.isFinite(y[i])&&Math.abs(y[i])<=.25;}
                    if(valid&&y[0]==0&&y[16]==0)templates.add(new Template(family,y,originalOvershoot));
                }
            }
        } catch(java.io.IOException|IllegalArgumentException unavailable){return List.of(Template.STRAIGHT);}
        return templates.isEmpty()?List.of(Template.STRAIGHT):List.copyOf(templates);
    }

    private static final class Template {
        static final Template STRAIGHT=new Template(0,new double[17],0);
        final int family;final double[] y;final double originalOvershoot;
        Template(int family,double[] y,double originalOvershoot){this.family=family;this.y=y;this.originalOvershoot=originalOvershoot;}
    }
}
