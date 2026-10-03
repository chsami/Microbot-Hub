package net.runelite.client.plugins.microbot.drozulrah.helper;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Paths;
import javax.imageio.ImageIO;
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
            for (String name : new String[]{"zulrah_range.png", "zulrah_melee.png", "zulrah_magic.png"})
            {
                BufferedImage actual = (BufferedImage) load.invoke(null, name);
                BufferedImage expected = ImageIO.read(Paths.get(
                    "src/main/resources/net/runelite/client/plugins/microbot/drozulrah/helper", name).toFile());
                assertEquals(expected.getWidth(), actual.getWidth(), name);
                assertEquals(expected.getHeight(), actual.getHeight(), name);
                int width = actual.getWidth(), height = actual.getHeight();
                assertArrayEquals(expected.getRGB(0, 0, width, height, null, 0, width),
                    actual.getRGB(0, 0, width, height, null, 0, width), name);
            }
        }
    }
}
