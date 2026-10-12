package net.runelite.client.plugins.microbot.drokbd;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;
import javax.imageio.ImageIO;

/** OSRS Wiki King Black Dragon icon, embedded for single-folder portability.
 * Source: https://oldschool.runescape.wiki/w/File:King_Black_Dragon_icon.png */
final class DroKbdImages
{
    private DroKbdImages() {}
    static BufferedImage load()
    {
        try
        {
            return ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAABkAAAAZCAMAAADzN3VRAAAAIVBMVEUAAAAFBAQVFBIdGxUmJiUzMiuampuDfHpoZGJSS0nNJBQYQGMHAAAAAXRSTlMAQObYZgAAALtJREFUeNplzgEOhSAMA1DabuD3/gf+KQwjcXE69gbSngDq9Ql0OI8WnFtquYdnZww/a1lSHYpiTWBCVOcmeddEwOAGX3vokXBBrg66OtYEyfoh14W0Pqw7wLVcBMIfeQ5tBkLenZHzMHsFIFnSIsGyKVJgZhLKMDxiUkTIUFLEyMyZNBwSmYavKGKCPmIKwykQKRlc4NwCQBIA8hTglcdhsFbKVDJWsy0aeKCPC7+L5uuHa3Rs2fGqWvsD98wD3yRMa6AAAAAASUVORK5CYII=")));
        }
        catch (IOException | IllegalArgumentException e)
        {
            return null;
        }
    }
}
