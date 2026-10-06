package net.runelite.client.plugins.microbot.drozulrah.helper;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ZulrahImagesTest
{
    @Test public void originalImagesLoadWithoutAnyClasspathResources() throws Exception
    {
        URL location = ZulrahImages.class.getProtectionDomain().getCodeSource().getLocation();
        try (URLClassLoader loader = new URLClassLoader(new URL[]{location}, null)
        {
            @Override public InputStream getResourceAsStream(String name) { return null; }
        })
        {
            Class<?> images = Class.forName(ZulrahImages.class.getName(), true, loader);
            Method load = images.getDeclaredMethod("load", String.class);
            load.setAccessible(true);
            String[][] originals = {
                {"zulrah_range.png", "e0f84ca5b96bd37ec7a9b268a21a8bb79700af4f85ab409b3f9f5f18e38242b6"},
                {"zulrah_melee.png", "70aeabb2cf1a0d57e679eeedb60ea9a9e6d5c30155a9d41b63325e8b7a8484d8"},
                {"zulrah_magic.png", "575f732545c1baa062446b11d0a9e807b4171891653d913f491cf33b3bb462ab"}
            };
            for (String[] original : originals)
            {
                String name = original[0];
                BufferedImage actual = (BufferedImage) load.invoke(null, name);
                assertEquals(50, actual.getWidth(), name);
                assertEquals(50, actual.getHeight(), name);
                int[] pixels = actual.getRGB(0, 0, 50, 50, null, 0, 50);
                ByteBuffer bytes = ByteBuffer.allocate(pixels.length * Integer.BYTES);
                for (int pixel : pixels) bytes.putInt(pixel);
                byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes.array());
                StringBuilder hash = new StringBuilder();
                for (byte value : digest) hash.append(String.format("%02x", value & 255));
                // Recorded from the original PNG pixels before removing redundant resources.
                assertEquals(original[1], hash.toString(), name);
            }
        }
    }
}
