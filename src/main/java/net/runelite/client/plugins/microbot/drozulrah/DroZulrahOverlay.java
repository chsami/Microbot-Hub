package net.runelite.client.plugins.microbot.drozulrah;

import javax.inject.Inject;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Locale;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.*;

/** Compact Firecape-style session card. Detailed states stay out of the card. */
public final class DroZulrahOverlay extends Overlay {
    private static final int WIDTH=280,HEIGHT=132,PADDING=10,ICON_SIZE=30,ROW_HEIGHT=17;
    private static final Color BACKGROUND=new Color(0,0,0,210),BORDER=new Color(55,205,200,200),
        GOLD=new Color(55,205,200),TEXT=new Color(235,240,245),MUTED=new Color(160,170,180),
        LIVE=new Color(55,205,200),SHADOW=new Color(0,0,0,190);
    private static final Font TITLE_FONT=FontManager.getRunescapeBoldFont(),
        LABEL_FONT=FontManager.getRunescapeSmallFont(),VALUE_FONT=FontManager.getRunescapeBoldFont();
    private final DroZulrahScript script;
    private final DroZulrahConfig config;
    private BufferedImage bossIcon;

    @Inject public DroZulrahOverlay(DroZulrahPlugin plugin,DroZulrahScript script,
        DroZulrahConfig config) {
        super(plugin);this.script=script;this.config=config;
        setPosition(OverlayPosition.TOP_LEFT);setLayer(OverlayLayer.ABOVE_WIDGETS);setPriority(OverlayPriority.HIGH);
    }
    @Override public Dimension render(Graphics2D graphics) {
        if(config.hideOverlay())return null;
        graphics=(Graphics2D)graphics.create();
        try {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setColor(BACKGROUND);graphics.fillRoundRect(0,0,WIDTH,HEIGHT,10,10);
        graphics.setStroke(new BasicStroke(1.2f));graphics.setColor(BORDER);
        graphics.drawRoundRect(0,0,WIDTH-1,HEIGHT-1,10,10);
        if(bossIcon==null)bossIcon=net.runelite.client.plugins.microbot.drozulrah.helper.constants.ZulrahType.MAGIC.getImage();
        if(bossIcon!=null)graphics.drawImage(bossIcon,PADDING,7,ICON_SIZE,ICON_SIZE,null);
        int textX=PADDING+ICON_SIZE+8;
        drawText(graphics,TITLE_FONT,GOLD,"DRO's Zulrah",textX,19);
        graphics.setColor("STOPPED".equals(String.valueOf(script.getState()))?MUTED:LIVE);
        graphics.fillOval(textX,28,7,7);
        drawText(graphics,LABEL_FONT,MUTED,fitText(graphics,script.getStatus(),textX+12),textX+12,35);
        graphics.setColor(new Color(55,205,200,75));graphics.drawLine(PADDING,45,WIDTH-PADDING,45);
        long seconds=script.getSessionElapsedMs()/1000;
        row(graphics,"Runtime",String.format(Locale.US,"%02d:%02d:%02d",seconds/3600,seconds/60%60,seconds%60),52);
        row(graphics,"Kills / Profit",number(script.getKills())+" / "+number(script.getEstimatedProfit()),52+ROW_HEIGHT);
        row(graphics,"Est. profit/hr",number(script.getEstimatedProfitPerHour())+" gp",52+2*ROW_HEIGHT);
        row(graphics,"Trips / Deaths",number(script.getTrips())+" / "+number(script.getDeaths()),52+3*ROW_HEIGHT);
        return new Dimension(WIDTH,HEIGHT);
        } finally { graphics.dispose(); }
    }
    private static String number(long value) { return String.format(Locale.US,"%,d",value); }
    private static void row(Graphics2D graphics,String label,String value,int y) {
        drawText(graphics,LABEL_FONT,MUTED,label,PADDING,y+12);
        drawText(graphics,VALUE_FONT,TEXT,value,WIDTH-PADDING-graphics.getFontMetrics(VALUE_FONT).stringWidth(value),y+12);
    }
    private static void drawText(Graphics2D graphics,Font font,Color colour,String text,int x,int y) {
        graphics.setFont(font);graphics.setColor(SHADOW);graphics.drawString(text,x+1,y+1);
        graphics.setColor(colour);graphics.drawString(text,x,y);
    }
    private static String fitText(Graphics2D graphics,String text,int x) {
        if(text==null||text.isEmpty())return "Ready";
        FontMetrics metrics=graphics.getFontMetrics(LABEL_FONT);
        while(text.length()>3&&metrics.stringWidth(text)>WIDTH-PADDING-x)
            text=text.substring(0,text.length()-4)+"...";
        return text;
    }
}
