package net.runelite.client.plugins.microbot.actionreplay;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;

@ConfigGroup(ActionReplayConfig.GROUP)
@ConfigInformation(
	"<html>Enable the plugin, then open the panel.<br>" +
	"Hit <b>Start recording</b>, do the actions in-game, then stop.<br>" +
	"Play back anytime from the panel.<br>" +
	"<br>" +
	"Developed by Red Bracket. Feel free to make improvements,<br>" +
	"just try to keep it simple :)</html>"
)
public interface ActionReplayConfig extends Config
{
	String GROUP = "actionReplay";
}
