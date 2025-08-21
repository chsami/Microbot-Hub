package net.runelite.client.plugins.microbot;

/**
 * Shared constants for Microbot plugins.
 * This class will be included in each plugin JAR during the shadow build process.
 */
public final class PluginConstants
{

    private PluginConstants()
	{
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

	public static final String DEFAULT_PREFIX = "<html>[<font color=#b8f704>MB</font>] ";
	public static final String MOCROSOFT = "<html>[<font color=#b8f704M>M</font>] ";
	public static final String BOLADO = "<html>[<font color=#FF0000><b>\ud83d\ude21</b></font>] ";
	public static final String GMASON = "<html>[<font color=#0077B6>G</font>] ";
    public static final String SHOOTING_STAR_PREFIX = "<html>[<font color=#0077B6>✨</font>] ";
	public static final String KSP = "<html>[<font color=#b8f704>KSP</font>] ";
	public static final String STICKTOTHESCRIPT = "<html>[<font color=#FF4F00>STTS</font>] ";
	public static final String MKE = "<html>[<font color=#48ecf7>MKE</font>] ";

    public static final boolean DEFAULT_ENABLED = false;
    public static final boolean IS_EXTERNAL = true;
}
