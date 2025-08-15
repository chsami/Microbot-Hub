package net.runelite.client.plugins.microbot.shootingstar.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ShootingStarProvider
{
	ZERO_SEVEN("07"),
	OSRS_VAULT("OSRS Vault"),
	;

	private final String providerName;

	@Override
	public String toString()
	{
		return providerName;
	}
}
