package net.runelite.client.plugins.microbot.driftnet;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.function.Function;
import net.runelite.client.input.MouseAdapter;
import net.runelite.client.input.MouseManager;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.mouse.BotEventGuard;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.components.*;

final class DriftNetOverlayDropdown {
    static final Color BACKGROUND = new Color(42,42,42,156);
    static final Color ACCENT = new Color(205,211,218,225);
    static final Color TEXT = new Color(218,222,228);
    static final Color MUTED = new Color(195,202,211);
    private final Overlay owner;
    private volatile boolean collapsed = true;
    private volatile boolean shown;
    private boolean captured;
    private MouseManager manager;
    private net.runelite.client.ui.overlay.OverlayManager overlayManager;
    private Canvas canvas;
    private final java.awt.event.AWTEventListener canvasInput = event -> {
        if (!(event instanceof MouseEvent) || event.getSource()!=canvas) return;
        MouseEvent mouse=(MouseEvent)event;
        switch(mouse.getID()) {
            case MouseEvent.MOUSE_PRESSED: this.input.mousePressed(mouse);break;
            case MouseEvent.MOUSE_RELEASED: this.input.mouseReleased(mouse);break;
            case MouseEvent.MOUSE_CLICKED: this.input.mouseClicked(mouse);break;
            default:break;
        }
    };
    final MouseAdapter input = new MouseAdapter() {
        @Override public MouseEvent mousePressed(MouseEvent event) {
            if(event.isConsumed()||BotEventGuard.isSynthetic())return event;
            if(overlayManager!=null) {
                synchronized(overlayManager) {
                    if(!overlayManager.getOverlays().contains(owner))return event;
                }
            }
            Rectangle bounds=owner.getBounds();
            Rectangle button=new Rectangle(bounds.x+bounds.width-25,bounds.y+4,22,22);
            if(shown&&bounds.width>0&&event.getButton()==MouseEvent.BUTTON1&&!event.isAltDown()&&button.contains(event.getPoint())) {
                collapsed=!collapsed;captured=true;event.consume();
            }
            return event;
        }
        @Override public MouseEvent mouseReleased(MouseEvent event) {
            if(!BotEventGuard.isSynthetic()&&captured&&event.getButton()==MouseEvent.BUTTON1)event.consume();
            return event;
        }
        @Override public MouseEvent mouseClicked(MouseEvent event) {
            if(!BotEventGuard.isSynthetic()&&captured&&event.getButton()==MouseEvent.BUTTON1) {captured=false;event.consume();}
            return event;
        }
    };
    DriftNetOverlayDropdown(Overlay owner){this.owner=owner;}
    void enable() {
        if(manager!=null||Microbot.getInjector()==null)return;
        collapsed=true;captured=false;shown=false;
        manager=Microbot.getInjector().getInstance(MouseManager.class);
        overlayManager=Microbot.getInjector().getInstance(net.runelite.client.ui.overlay.OverlayManager.class);
        canvas=Microbot.getClientThread().runOnClientThreadOptional(()->Microbot.getClient().getCanvas()).orElse(null);
        manager.registerMouseListener(0,input);
        if(canvas!=null&&!GraphicsEnvironment.isHeadless())Toolkit.getDefaultToolkit().addAWTEventListener(canvasInput,AWTEvent.MOUSE_EVENT_MASK);
    }
    void disable() {
        if(manager!=null)manager.unregisterMouseListener(input);
        Toolkit.getDefaultToolkit().removeAWTEventListener(canvasInput);
        manager=null;overlayManager=null;canvas=null;shown=false;captured=false;
    }
    Dimension render(Graphics2D graphics,Function<Graphics2D,Dimension> content) {
        Graphics2D body=(Graphics2D)graphics.create();
        Dimension size;
        try {
            body.setFont(FontManager.getRunescapeFont());
            if(collapsed)body.clip(new Rectangle(0,0,16384,42));
            size=content.apply(body);
        } finally {body.dispose();}
        shown=size!=null&&size.width>0;
        if(!shown)return size;
        int width=size.width,height=collapsed?50:Math.max(50,size.height);
        Graphics2D g=(Graphics2D)graphics.create();
        try {
            if(collapsed){g.setColor(BACKGROUND);g.fillRect(0,42,width,8);}
            border(g,width,height);
            if(collapsed){g.setColor(new Color(205,211,218,75));g.drawLine(10,45,width-10,45);}
            g.setComposite(AlphaComposite.SrcOver);
            g.setColor(BACKGROUND);g.fillRect(width-25,4,22,22);
            g.setColor(new Color(205,211,218,40));g.fillRoundRect(width-25,4,22,22,5,5);
            g.setFont(FontManager.getRunescapeFont());g.setColor(new Color(0,0,0,190));g.drawString(collapsed?"+":"-",width-18,20);
            g.setColor(ACCENT);g.drawString(collapsed?"+":"-",width-19,19);
        } finally {g.dispose();}
        return new Dimension(width,height);
    }
    static void border(Graphics2D graphics,int width,int height) {
        Graphics2D g=(Graphics2D)graphics.create();
        try {g.setStroke(new BasicStroke(1f));g.setColor(new Color(0,0,0,225));g.drawRect(0,0,width-1,height-1);}
        finally {g.dispose();}
    }
    static void panel(Graphics2D graphics,PanelComponent panel) {
        panel.setBackgroundColor(BACKGROUND);
        panel.setPreferredSize(new Dimension(280,0));
        graphics.setFont(FontManager.getRunescapeFont());
        for(LayoutableRenderableEntity child:panel.getChildren()) {
            if(child instanceof LineComponent) {
                LineComponent line=(LineComponent)child;line.setLeftColor(MUTED);line.setRightColor(TEXT);
            } else if(child instanceof TitleComponent)((TitleComponent)child).setColor(ACCENT);
        }
    }
}
