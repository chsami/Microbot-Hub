package net.runelite.client.plugins.microbot.droinfernoprayers;
import java.awt.*;
import java.awt.image.BufferedImage;
import javax.swing.Icon;
final class InfernoOverlayArt {
private static final Icon ICON=resourceIcon(net.runelite.client.util.ImageUtil.loadImageResource(InfernoOverlayArt.class,"inferno-icon.png"),44);
static Icon icon(){return ICON;}
    static Icon resourceIcon(BufferedImage source,int size){
        final BufferedImage artwork=bounded(source,size*4);
        return new Icon(){
            public int getIconWidth(){return size;}
            public int getIconHeight(){return size;}
            public void paintIcon(Component component,Graphics graphics,int x,int y){drawOriginal(graphics,artwork,x,y,size,Double.POSITIVE_INFINITY);}
        };
    }
    private static BufferedImage bounded(BufferedImage source,int bound){
        if(source==null||Math.max(source.getWidth(),source.getHeight())<=bound)return source;
        double scale=(double)bound/Math.max(source.getWidth(),source.getHeight());
        BufferedImage small=new BufferedImage(Math.max(1,(int)Math.round(source.getWidth()*scale)),Math.max(1,(int)Math.round(source.getHeight()*scale)),BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=small.createGraphics();try{g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);g.drawImage(source,0,0,small.getWidth(),small.getHeight(),null);}finally{g.dispose();}
        return small;
    }
    private static void drawOriginal(Graphics graphics,BufferedImage source,int x,int y,int size,double maximumScale){
        if(source==null)return;
        Graphics2D g=(Graphics2D)graphics.create();
        try{
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            double scale=Math.min(maximumScale,Math.min((double)size/source.getWidth(),(double)size/source.getHeight()));
            int width=(int)Math.round(source.getWidth()*scale),height=(int)Math.round(source.getHeight()*scale);
            g.drawImage(source,x+(size-width)/2,y+(size-height)/2,width,height,null);
        }finally{g.dispose();}
    }
}
