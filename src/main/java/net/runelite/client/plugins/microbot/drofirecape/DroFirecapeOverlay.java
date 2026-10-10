/*
 * Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Free and open source. Retain this notice and the LICENSE.txt terms.
 * Developed with OpenAI Codex; see CREDITS.txt. Third-party notices follow.
 */
package net.runelite.client.plugins.microbot.drofirecape;

import javax.inject.Inject;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Locale;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.*;

/** DroCooker's compact card, with a fire cape and lava/gold colours. Diagnostics live in logs. */
public final class DroFirecapeOverlay extends Overlay {
    private static final int WIDTH=224,HEIGHT=132,PADDING=10,ICON_SIZE=30,ROW_HEIGHT=17;
    private static final Color BACKGROUND=new Color(27,11,8,242),BORDER=new Color(234,126,40,200),
        GOLD=new Color(255,177,66),TEXT=new Color(255,246,227),MUTED=new Color(204,171,143),
        LIVE=new Color(255,121,48),SHADOW=new Color(0,0,0,190);
    private static final Font TITLE_FONT=FontManager.getRunescapeBoldFont(),
        LABEL_FONT=FontManager.getRunescapeSmallFont(),VALUE_FONT=FontManager.getRunescapeBoldFont();
    private final FcControllers script;
    private final DroFirecapeConfig config;
    private final ItemManager itemManager;
    private BufferedImage capeIcon;

    @Inject public DroFirecapeOverlay(DroFirecapePlugin plugin,FcControllers script,
        DroFirecapeConfig config,ItemManager itemManager) {
        super(plugin);this.script=script;this.config=config;this.itemManager=itemManager;
        setPosition(OverlayPosition.TOP_LEFT);setLayer(OverlayLayer.ABOVE_WIDGETS);setPriority(OverlayPriority.HIGH);
    }
    @Override public Dimension render(Graphics2D graphics) {
        if(config.hideOverlay())return null;
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setColor(BACKGROUND);graphics.fillRoundRect(0,0,WIDTH,HEIGHT,10,10);
        graphics.setStroke(new BasicStroke(1.2f));graphics.setColor(BORDER);
        graphics.drawRoundRect(0,0,WIDTH-1,HEIGHT-1,10,10);
        if(capeIcon==null)capeIcon=itemManager.getImage(6570);
        if(capeIcon!=null)graphics.drawImage(capeIcon,PADDING,7,ICON_SIZE,ICON_SIZE,null);
        int textX=PADDING+ICON_SIZE+8;
        drawText(graphics,TITLE_FONT,GOLD,"DRO's FIRECAPE",textX,19);
        graphics.setColor("STOPPED".equals(script.state())?MUTED:LIVE);
        graphics.fillOval(textX,28,7,7);
        drawText(graphics,LABEL_FONT,MUTED,fitText(graphics,stage(),textX+12),textX+12,35);
        graphics.setColor(new Color(234,126,40,75));graphics.drawLine(PADDING,45,WIDTH-PADDING,45);
        long seconds=script.runtime()/1000;
        row(graphics,"Runtime",String.format(Locale.US,"%02d:%02d:%02d",seconds/3600,seconds/60%60,seconds%60),52);
        row(graphics,"Wave",script.wave()+" / 63",52+ROW_HEIGHT);
        row(graphics,"Monsters left",script.monsterCount(),52+2*ROW_HEIGHT);
        row(graphics,"Capes earned","COMPLETE".equals(script.state())?"1":"0",52+3*ROW_HEIGHT);
        return new Dimension(WIDTH,HEIGHT);
    }
    private String stage() {
        if("ROTATION_WAIT".equals(script.state())) {
            String estimate=script.rotationWaitEstimate();
            if(!estimate.isEmpty())return "Rotation wait "+estimate;
        }
        String stage=script.state().toLowerCase(Locale.US).replace('_',' ');
        return Character.toUpperCase(stage.charAt(0))+stage.substring(1);
    }
    private static void row(Graphics2D graphics,String label,String value,int y) {
        drawText(graphics,LABEL_FONT,MUTED,label,PADDING,y+12);
        drawText(graphics,VALUE_FONT,TEXT,value,WIDTH-PADDING-graphics.getFontMetrics(VALUE_FONT).stringWidth(value),y+12);
    }
    private static void drawText(Graphics2D graphics,Font font,Color colour,String text,int x,int y) {
        graphics.setFont(font);graphics.setColor(SHADOW);graphics.drawString(text,x+1,y+1);
        graphics.setColor(colour);graphics.drawString(text,x,y);
    }
    private static String fitText(Graphics2D graphics,String text,int x) {
        FontMetrics metrics=graphics.getFontMetrics(LABEL_FONT);
        while(text.length()>3&&metrics.stringWidth(text)>WIDTH-PADDING-x)
            text=text.substring(0,text.length()-4)+"...";
        return text;
    }
}
