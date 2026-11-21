package net.runelite.client.plugins.microbot.drozulrah;

import java.util.regex.Pattern;

final class ZulrahGearName
{
    private static final Pattern CHARGE_SUFFIX = Pattern.compile("\\s*\\((?:full|\\d+)\\)$", Pattern.CASE_INSENSITIVE);

    private ZulrahGearName() {}

    static String base(String name)
    {
        if (name == null) return "";
        return CHARGE_SUFFIX.matcher(name.trim()).replaceFirst("");
    }
}
