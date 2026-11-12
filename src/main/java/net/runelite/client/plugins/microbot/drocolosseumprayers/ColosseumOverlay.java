package net.runelite.client.plugins.microbot.drocolosseumprayers;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.List;
import java.util.function.LongSupplier;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.input.MouseAdapter;
import net.runelite.client.input.MouseManager;
import net.runelite.client.input.MouseWheelListener;
import net.runelite.client.plugins.microbot.util.mouse.BotEventGuard;
import net.runelite.client.ui.overlay.*;

public final class ColosseumOverlay extends Overlay {
    private static final Color BG=new Color(51,47,38),ACCENT=new Color(226,171,75),TEXT=new Color(227,210,173),MUTED=new Color(180,163,135),CELL=new Color(81,72,60),ON=new Color(116,106,85);
    private static final Font ACTIVITY_FONT=new Font(Font.SANS_SERIF,Font.BOLD,10),STATUS_FONT=new Font(Font.SANS_SERIF,Font.PLAIN,10);
    private final DroColosseumPrayersPlugin plugin;
    private final Runnable planner;
    private final LongSupplier nanoClock=System::nanoTime;
    @Inject private MouseManager mouseManager;
    @Inject private net.runelite.client.game.SpriteManager spriteManager;
    @Inject private net.runelite.client.eventbus.EventBus eventBus;
    private volatile Frame frame=new Frame();
    private volatile long generation;
    private boolean enabled;
    private static final long PARTIAL_GRACE_NANOS=2_400_000_000L;
    private volatile long partialSince;
    private volatile boolean partialActive;
    private volatile List<Hit> hits=Collections.emptyList();
    private volatile Rectangle panel=new Rectangle();
    private volatile Point hover=new Point(-1,-1);
    private volatile Display display;
    private volatile Shield painted;
    private boolean collapsed;
    private Hit pressed;
    private final Set<Integer> captured=new HashSet<>(),forwarded=new HashSet<>(),blockedClicks=new HashSet<>();
    static final class Frame {
        GameState gameState=GameState.UNKNOWN;
        boolean loggedIn,chatBusy,chatboxUnavailable=true;
        int width,height;long overlaySession;
        Rectangle chatbox=new Rectangle();
    }
    private static final class Hit {
        final Rectangle r;final Runnable action;final String help;
        Hit(Rectangle r,Runnable action,String help){this.r=r;this.action=action;this.help=help;}
    }
    private static final class Display {
        final WeakReference<Frame> source;final long run,session;final int width,height;final Rectangle chatbox;
        Display(Frame frame,long run){source=new WeakReference<>(frame);this.run=run;session=frame.overlaySession;width=frame.width;height=frame.height;chatbox=new Rectangle(frame.chatbox);}
        boolean matches(Frame frame,long generation){return run==generation&&session==frame.overlaySession&&width==frame.width&&height==frame.height&&chatbox.equals(frame.chatbox);}
    }
    private static final class Shield {
        final Display layout;final Rectangle bounds,expand;final boolean compact;
        Shield(Display layout,Rectangle bounds,boolean compact,Rectangle expand){this.layout=layout;this.bounds=new Rectangle(bounds);this.compact=compact;this.expand=new Rectangle(expand);}
        boolean contains(Point point){return compact?expand.contains(point):bounds.contains(point);}
    }
    @Inject public ColosseumOverlay(DroColosseumPrayersPlugin plugin){
        super(plugin);this.plugin=plugin;
        planner=()->{if(eventBus!=null)eventBus.post(new net.runelite.client.events.OverlayMenuClicked(new OverlayMenuEntry(net.runelite.api.MenuAction.RUNELITE_OVERLAY_CONFIG,"Configure","Colosseum"),this));};
        setPosition(OverlayPosition.DYNAMIC);setLayer(OverlayLayer.ABOVE_WIDGETS);setPriority(OverlayPriority.HIGH);setMovable(false);setResizable(false);
    }
    private final MouseWheelListener wheel=e->{if(!BotEventGuard.isSynthetic()&&blocksPointer(e.getPoint()))e.consume();return e;};
    void start(){enabled=true;generation++;if(mouseManager!=null){mouseManager.registerMouseListener(mouse);mouseManager.registerMouseWheelListener(wheel);}}
    private final ColosseumOverlayInputShield inputShield=new ColosseumOverlayInputShield(this::contains,this::blocksPointer);
    void stop(){inputShield.close();enabled=false;generation++;clear();if(mouseManager!=null){mouseManager.unregisterMouseListener(mouse);mouseManager.unregisterMouseWheelListener(wheel);}}
    void capture(Client client){
        if(enabled)inputShield.attach(client.getCanvas());
        Frame previous=frame,f=new Frame();f.gameState=client.getGameState();f.width=client.getCanvasWidth();f.height=client.getCanvasHeight();
        f.overlaySession=previous.overlaySession+(f.gameState==GameState.LOADING||f.gameState==GameState.LOGGED_IN?0:1);
        f.loggedIn=f.gameState==GameState.LOGGED_IN&&client.getLocalPlayer()!=null;
        var chat=client.getWidget(162,0);Rectangle bounds=chat!=null&&!chat.isHidden()?chat.getBounds():null;
        f.chatboxUnavailable=!overlayGeometry(bounds,f.width,f.height);
        f.chatbox=!f.chatboxUnavailable?new Rectangle(bounds):previous.width==f.width&&previous.height==f.height?new Rectangle(previous.chatbox):new Rectangle();
        f.chatBusy=f.gameState==GameState.LOGGED_IN?net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue.isInDialogue()||net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange.isOpen():f.gameState==GameState.LOADING&&previous.chatBusy;
        frame=f;
    }
    static boolean overlayGeometry(Rectangle bounds,int width,int height){return width>=100&&height>=26&&bounds!=null&&bounds.width>=100&&bounds.height>=26&&new Rectangle(0,0,width,height).contains(bounds);}
    private boolean visibleRun(){return enabled&&plugin.arenaHud()!=null;}
    private static String timeText(long ms){long s=ms/1000;return String.format(Locale.ROOT,"%d:%02d:%02d",s/3600,s/60%60,s%60);}
    final MouseAdapter mouse=new MouseAdapter(){
        @Override public MouseEvent mouseMoved(MouseEvent e){
            if(BotEventGuard.isSynthetic())return e;

            if(e.getID()==MouseEvent.MOUSE_MOVED){captured.clear();pressed=null;}
            hover=e.getPoint();if(blocksPointer(e.getPoint()))e.consume();return ownedMotion(e);
        }
        @Override public MouseEvent mouseEntered(MouseEvent e){return mouseMoved(e);}
        @Override public MouseEvent mouseExited(MouseEvent e){if(!BotEventGuard.isSynthetic())hover=new Point(-1,-1);return e;}
        @Override public MouseEvent mousePressed(MouseEvent e){
            if(BotEventGuard.isSynthetic())return e;
            int button=e.getButton();blockedClicks.remove(button);
            if(button==MouseEvent.BUTTON1)pressed=null;
            if(contains(e.getPoint())){
                captured.add(button);forwarded.remove(button);
                if(button==MouseEvent.BUTTON1&&actionsReady())for(Hit h:hits)if(h.r.contains(e.getPoint())){pressed=h;break;}
                e.consume();
            }else{captured.remove(button);forwarded.add(button);}
            return e;
        }
        @Override public MouseEvent mouseReleased(MouseEvent e){
            if(BotEventGuard.isSynthetic())return e;
            int button=e.getButton();boolean owned=captured.remove(button),gamePress=forwarded.remove(button);
            Hit hit=button==MouseEvent.BUTTON1?pressed:null;if(button==MouseEvent.BUTTON1)pressed=null;
            if(owned){
                e.consume();blockedClicks.add(button);
                if(hit!=null&&contains(e.getPoint())&&actionsReady()&&hit.r.contains(e.getPoint()))hit.action.run();
            }else if(!gamePress&&contains(e.getPoint()))e.consume();
            return e;
        }
        @Override public MouseEvent mouseClicked(MouseEvent e){if(!BotEventGuard.isSynthetic()&&(blockedClicks.remove(e.getButton())||contains(e.getPoint())))e.consume();return e;}
        @Override public MouseEvent mouseDragged(MouseEvent e){if(!BotEventGuard.isSynthetic()){hover=e.getPoint();if(blocksPointer(e.getPoint())||!compact()&&!captured.isEmpty())e.consume();}return ownedMotion(e);}
    };

    private static MouseEvent ownedMotion(MouseEvent event){
        if(!event.isConsumed())return event;
        MouseEvent hidden=new MouseEvent(event.getComponent(),event.getID(),event.getWhen(),event.getModifiersEx(),-1,-1,0,false);
        hidden.consume();return hidden;
    }
    private boolean compact(){Shield shield=painted;return shield==null?collapsed:shield.compact;}
    private static boolean transientFrame(Frame frame){return frame.gameState==GameState.LOADING||frame.gameState==GameState.LOGGED_IN;}
    boolean contains(Point point){
        if(!visibleRun()){clear();return false;}
        Frame frame=this.frame;Shield shield=painted;
        if(frame.chatBusy||(!frame.loggedIn&&!transientFrame(frame))){clear();return false;}
        if(shield==null)return false;
        if(!shield.layout.matches(frame,generation)){clear();return false;}
        if(!frame.loggedIn||frame.chatboxUnavailable){if(partialExpired()){clear();return false;}clearActions();}else resetPartialGrace();
        return shield.contains(point);
    }
    boolean blocksPointer(Point point){return contains(point)&&!compact();}
    private boolean actionsReady(){
        if(!visibleRun())return false;
        Frame frame=this.frame;Shield shield=painted;
        return frame.loggedIn&&!frame.chatBusy&&!frame.chatboxUnavailable&&shield!=null&&shield.layout.matches(frame,generation);
    }

    private void clearActions(){hits=Collections.emptyList();pressed=null;}
    private void clearTargets(){painted=null;panel=new Rectangle();clearActions();}
    void clear(){clearTargets();display=null;resetPartialGrace();}
    private void resetPartialGrace(){partialActive=false;partialSince=0;}
    private boolean partialExpired(){return partialActive&&nanoClock.getAsLong()-partialSince>=PARTIAL_GRACE_NANOS;}
    private Display display(Frame frame,long run){
        Display previous=display;
        if(frame.chatBusy||(!frame.loggedIn&&!transientFrame(frame))){clear();return null;}
        if(previous!=null&&!previous.matches(frame,run)){clear();previous=null;}
        if(frame.loggedIn&&!frame.chatboxUnavailable&&overlayGeometry(frame.chatbox,frame.width,frame.height)){
            resetPartialGrace();
            if(previous==null||previous.source.get()!=frame)display=new Display(frame,run);
            return display;
        }
        if(previous==null){clearTargets();return null;}

        if(!partialActive){partialSince=nanoClock.getAsLong();partialActive=true;}
        if(partialExpired()){clear();return null;}
        clearActions();return previous;
    }
    private void publishHits(List<Hit> next,Frame frame,Display layout,Rectangle bounds,boolean compact){
        if(!frame.loggedIn||frame.chatboxUnavailable)clearActions();else hits=Collections.unmodifiableList(next);
        panel=bounds;painted=new Shield(layout,bounds,compact,compact?next.get(0).r:new Rectangle());
    }
    @Override public Dimension render(Graphics2D original){
        if(!visibleRun()){clear();return null;}
        Frame frame=this.frame;Display f=display(frame,generation);if(f==null)return null;
        ArenaHud arena=plugin.arenaHud();
        boolean compact=collapsed||f.chatbox.height<144;
        int width=Math.min(Math.max(100,f.chatbox.width),Math.max(100,f.width));if(compact)width=Math.min(width,220);int height=compact?26:Math.min(157,f.chatbox.height-8);
        int x=Math.max(0,Math.min(f.width-width,f.chatbox.x));
        int anchor=f.chatbox.y+f.chatbox.height;
        int y=Math.max(0,Math.min(f.height-height,anchor-height));
        Rectangle bounds=new Rectangle(x,y,width,height);List<Hit> next=new ArrayList<>();
        Graphics2D g=(Graphics2D)original.create();
        try{
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING,RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
            g.setColor(new Color(BG.getRed(),BG.getGreen(),BG.getBlue(),225));g.fillRoundRect(x,y,width,height,10,10);
            g.setColor(new Color(130,118,94));g.drawRoundRect(x,y,width-1,height-1,10,10);g.setColor(ACCENT);g.fillRoundRect(x+5,y+3,width-10,2,2,2);
            if(compact){
                String activity=arena.title();
                String status=arena.status;
                int available=width-41,titleWidth=Math.min(available/3,g.getFontMetrics(ACTIVITY_FONT).stringWidth(activity));
                g.setFont(ACTIVITY_FONT);g.setColor(ACCENT);
                drawFit(g,activity,x+9,y+17,titleWidth);
                int statusX=x+9+titleWidth+(titleWidth>0?6:0);
                g.setFont(STATUS_FONT);g.setColor(MUTED);
                drawFit(g,status,statusX,y+17,x+width-32-statusX);
                button(g,next,new Rectangle(x+width-27,y+4,22,18),"+",false,()->collapsed=false,"Expand controls");
                publishHits(next,frame,f,bounds,true);return null;
            }
            if(arena!=null){renderArena(g,next,arena,x,y,width,height);publishHits(next,frame,f,bounds,false);return null;}
        }finally{g.dispose();}
        return null;
    }
    private void renderArena(Graphics2D g,List<Hit> next,ArenaHud arena,int x,int y,int width,int height){
        int cx=x+width/2;
        button(g,next,new Rectangle(x+10,y+9,70,22),"Settings",false,planner,"Open plugin settings");
        button(g,next,new Rectangle(x+width-32,y+9,22,22),"\u2212",false,()->collapsed=true,"Collapse overlay");
        Font titleFont=new Font(Font.SANS_SERIF,Font.BOLD,17);g.setFont(titleFont);
        String title=arena.title();var icon=ColosseumOverlayArt.small();
        int iconWidth=icon==null?0:icon.getIconWidth()+6,titleWidth=Math.min(width-200,g.getFontMetrics().stringWidth(title));
        int titleX=cx-(iconWidth+titleWidth)/2;
        if(icon!=null)icon.paintIcon(null,g,titleX,y+7);
        g.setColor(ACCENT);drawFit(g,title,titleX+iconWidth,y+26,titleWidth);
        boolean showModifiers=height>=155&&!arena.modifiers.isEmpty();
        if(showModifiers){
            int size=18,gap=3,count=arena.modifiers.size(),rowWidth=Math.min(width-40,count*(size+gap)-gap);
            drawModifiers(g,arena.modifiers,new Rectangle(cx-rowWidth/2,y+34,rowWidth,size));
        }
        int top=showModifiers?y+14:y+7;
        int[] columns={x+width/6,cx,x+width*5/6};
        String[] labels={"Current wave","Run time","Wave time"};
        long now=System.currentTimeMillis();
        String[] values={arena.wave>0?"Wave "+arena.wave:"Wave \u2014",timeText(Math.max(0,now-arena.startedAt)),arena.waveAt>0?timeText(Math.max(0,now-arena.waveAt)):"\u2014"};
        g.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,10));g.setColor(MUTED);
        for(int i=0;i<3;i++)drawCentered(g,labels[i],columns[i],top+52,width/3-10);
        for(int i=0;i<3;i++){
            g.setFont(i==0?new Font(Font.SANS_SERIF,Font.BOLD,20):new Font(Font.SANS_SERIF,Font.PLAIN,16));g.setColor(i==0?ACCENT:TEXT);
            drawCentered(g,values[i],columns[i],top+74,width/3-10);
        }
        g.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,10));g.setColor(TEXT);
        drawCentered(g,"HP "+arena.hp+"  |  Prayer "+arena.prayer+"  |  Spec "+arena.spec+"%  |  Brew doses "+arena.brewDoses+"  |  Restore doses "+arena.restoreDoses,cx,top+94,width-24);
        int divider=y+height-43,statusY=y+height-23;
        g.setColor(CELL);g.drawLine(x+10,divider,x+width-10,divider);
        if(arena.state!=null){
            g.setFont(new Font(Font.SANS_SERIF,Font.BOLD,13));g.setColor(ACCENT);drawCentered(g,"State: "+arena.state,cx,statusY,width-40);
        }else{
            g.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,11));g.setColor(TEXT);drawCentered(g,arena.status,cx,statusY,width-40);
        }
        next.add(new Hit(new Rectangle(x+10,divider+3,width-20,24),()->showStatus(arena.title()+"\n"+arena.status),"Read full status"));
        g.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,9));g.setColor(MUTED);drawCentered(g,"v"+VERSION,cx,y+height-7,width-40);
    }
    private static void drawCentered(Graphics2D g,String text,int centreX,int baseline,int max){
        int w=Math.min(max,g.getFontMetrics().stringWidth(text));drawFit(g,text,centreX-w/2,baseline,max);
    }
    static final String VERSION=version();
    private static String version(){
        var d=DroColosseumPrayersPlugin.class.getAnnotation(net.runelite.client.plugins.PluginDescriptor.class);return d==null?"?":d.version();
    }

    private final Map<Long,java.awt.image.BufferedImage> icons=new HashMap<>();
    private java.awt.image.BufferedImage icon(int sprite,int size){
        if(spriteManager==null)return null;
        long key=(long)sprite<<16|size;var cached=icons.get(key);if(cached!=null)return cached;
        var raw=spriteManager.getSprite(sprite,0);if(raw==null)return null;
        var scaled=net.runelite.client.util.ImageUtil.resizeImage(raw,size,size);icons.put(key,scaled);return scaled;
    }

    private void drawModifiers(Graphics2D g,List<String> tokens,Rectangle box){
        if(tokens.isEmpty()||box.width<20)return;
        int gap=3,size=Math.max(14,Math.min(box.height,(box.width+gap)/tokens.size()-gap));
        int cx=box.x;
        for(String token:tokens){
            int colon=token.indexOf(':');var info=ColosseumModifierIcons.ALL.get(colon<0?token:token.substring(0,colon));if(info==null)continue;
            int level=colon<0?1:Integer.parseInt(token.substring(colon+1));
            if(cx+size>box.x+box.width)break;
            var image=icon(info.sprite(level),size);
            if(image!=null)g.drawImage(image,cx,box.y+(box.height-size)/2,null);
            else{
                g.setColor(CELL);g.fillRoundRect(cx,box.y+(box.height-size)/2,size,size,6,6);
                g.setFont(new Font(Font.SANS_SERIF,Font.BOLD,9));g.setColor(TEXT);
                String tag=info.title.substring(0,2)+(level>1?level:"");drawFit(g,tag,cx+2,box.y+box.height/2+3,size-3);
            }
            cx+=size+gap;
        }
    }
    private static void showStatus(String status) {
        javax.swing.SwingUtilities.invokeLater(()->{
            javax.swing.JTextArea text=new javax.swing.JTextArea(status,6,52);
            text.setEditable(false);text.setLineWrap(true);text.setWrapStyleWord(true);text.setCaretPosition(0);
            javax.swing.JOptionPane.showMessageDialog(null,new javax.swing.JScrollPane(text),"Colosseum Prayers status",javax.swing.JOptionPane.INFORMATION_MESSAGE);
        });
    }
    private static void button(Graphics2D g,List<Hit> hits,Rectangle r,String label,boolean selected,Runnable action,String help){
        g.setColor(selected?ON:CELL);g.fillRoundRect(r.x,r.y,r.width,r.height,5,5);g.setColor(selected?TEXT:MUTED);g.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,10));drawFit(g,label,r.x+6,r.y+r.height/2+4,r.width-12);hits.add(new Hit(r,action,help));
    }
    private static void drawFit(Graphics2D g,String text,int x,int y,int max){
        if(text==null)return;String s=text;FontMetrics fm=g.getFontMetrics();while(s.length()>1&&fm.stringWidth(s)>max)s=s.substring(0,s.length()-1);if(s.length()<text.length()&&s.length()>1)s=s.substring(0,s.length()-1)+"...";g.drawString(s,x,y);
    }
}
