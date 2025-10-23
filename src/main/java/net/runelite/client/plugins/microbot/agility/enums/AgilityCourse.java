package net.runelite.client.plugins.microbot.agility.enums;

import lombok.Getter;
import net.runelite.client.plugins.microbot.agility.courses.*;

@Getter
public enum AgilityCourse
{
	AGILITY_PYRAMID("Agility Pyramid", new PyramidCourse()),
	AL_KHARID_ROOFTOP_COURSE("Al Kharid Rooftop Course", new AlKharidCourse()),
	APE_ATOLL_AGILITY_COURSE("Ape Atoll Agility Course", new ApeAtollCourse()),
	ARDOUGNE_ROOFTOP_COURSE("Ardougne Rooftop Course", new ArdougneCourse()),
	BRIMHAVEN_SPIKE_COURSE("Brimhaven Spike Course", new BrimhavenSpikeCourse()),
	CANIFIS_ROOFTOP_COURSE("Canifis Rooftop Course", new CanafisCourse()),
	COLOSSAL_WYRM_ADVANCED_COURSE("Colossal Wyrm Advanced Course", new ColossalWyrmAdvancedCourse()),
	COLOSSAL_WYRM_BASIC_COURSE("Colossal Wyrm Basic Course", new ColossalWyrmBasicCourse()),
	DRAYNOR_VILLAGE_ROOFTOP_COURSE("Draynor Village Rooftop Course", new DraynorCourse()),
	FALADOR_ROOFTOP_COURSE("Falador Rooftop Course", new FaladorCourse()),
	GNOME_STRONGHOLD_AGILITY_COURSE("Gnome Stronghold Agility Course", new GnomeStrongholdCourse()),
	POLLNIVNEACH_ROOFTOP_COURSE("Pollnivneach Rooftop Course", new PollnivneachCourse()),
	PRIFDDINAS_AGILITY_COURSE("Prifddinas Agility Course", new PrifddinasCourse()),
	RELLEKKA_ROOFTOP_COURSE("Rellekka Rooftop Course", new RellekkaCourse()),
	SEERS_VILLAGE_ROOFTOP_COURSE("Seers' Village Rooftop Course", new SeersCourse()),
	SHAYZIEN_ADVANCED_COURSE("Shayzien Advanced Agility Course", new ShayzienAdvancedCourse()),
	SHAYZIEN_BASIC_COURSE("Shayzien Basic Agility Course", new ShayzienBasicCourse()),
	VARROCK_ROOFTOP_COURSE("Varrock Rooftop Course", new VarrockCourse()),
	WEREWOLF_COURSE("Werewolf Agility Course", new WerewolfCourse()),
	;


	private final String tooltip;
	private final boolean rooftopCourse;
	private final AgilityCourseHandler handler;

	AgilityCourse(String tooltip, AgilityCourseHandler handler)
	{
		this.tooltip = tooltip;
		this.handler = handler;
		this.rooftopCourse = this.name().contains("ROOFTOP_COURSE");
	}
}
