package net.runelite.client.plugins.microbot.prometheuslogin;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import javax.imageio.ImageIO;

/** The Prometheus logo: a small window icon and a wide header artwork shown faintly behind the title. */
final class Logo
{
    private Logo() { }

    private static final String BASE = "/net/runelite/client/plugins/microbot/prometheuslogin/";
    private static BufferedImage icon;
    private static BufferedImage header;
    private static BufferedImage wordmark;
    private static BufferedImage wordmarkScaled;
    private static int wordmarkW;
    private static BufferedImage headerScaled;
    private static int scaledW, scaledH;

    private static BufferedImage load(String name)
    {
        try (InputStream in = Logo.class.getResourceAsStream(BASE + name))
        {
            return in == null ? null : ImageIO.read(in);
        }
        catch (Exception e)
        {
            return null;
        }
    }

    static synchronized BufferedImage icon()
    {
        if (icon == null) icon = load("logo_icon.png");
        return icon;
    }


    /** Paints the "Prometheus Login" wordmark (transparent background) scaled to the given width, top-left at (x, y); returns its height. */
    static synchronized int paintWordmark(Graphics2D g, int x, int y, int width)
    {
        if (wordmark == null) wordmark = load("wordmark.png");
        if (wordmark == null || width <= 0) return 0;
        int height = Math.round(wordmark.getHeight() * (width / (float) wordmark.getWidth()));
        if (wordmarkScaled == null || wordmarkW != width)
        {
            wordmarkScaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D s = wordmarkScaled.createGraphics();
            s.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            s.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            s.drawImage(wordmark, 0, 0, width, height, null);
            s.dispose();
            wordmarkW = width;
        }
        g.drawImage(wordmarkScaled, x, y, null);
        return height;
    }

    /** Paints the header artwork scaled to cover the rectangle (cropped, not stretched). focusY 0..1 picks the visible band. */
    static synchronized void paintHeader(Graphics2D g, int x, int y, int w, int h, float alpha, double focusY)
    {
        if (header == null) header = load("header.jpg");
        if (header == null) return;
        double scale = Math.max(w / (double) header.getWidth(), h / (double) header.getHeight());
        int dw = (int) Math.ceil(header.getWidth() * scale), dh = (int) Math.ceil(header.getHeight() * scale);
        if (headerScaled == null || scaledW != dw || scaledH != dh)
        {
            headerScaled = new BufferedImage(dw, dh, BufferedImage.TYPE_INT_ARGB);
            Graphics2D s = headerScaled.createGraphics();
            s.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            s.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            s.drawImage(header, 0, 0, dw, dh, null);
            s.dispose();
            scaledW = dw;
            scaledH = dh;
        }
        Graphics2D c = (Graphics2D) g.create();
        try
        {
            c.clipRect(x, y, w, h);
            c.setComposite(AlphaComposite.SrcOver.derive(alpha));
            c.drawImage(headerScaled, x + (w - dw) / 2, y - (int) Math.round((dh - h) * focusY), null);
        }
        finally
        {
            c.dispose();
        }
    }
}
