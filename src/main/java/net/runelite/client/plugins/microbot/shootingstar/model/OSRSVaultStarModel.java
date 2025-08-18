package net.runelite.client.plugins.microbot.shootingstar.model;

import com.google.gson.annotations.SerializedName;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import net.runelite.client.plugins.microbot.shootingstar.enums.ShootingStarLocation;
import net.runelite.client.plugins.microbot.shootingstar.model.Star;

public class OSRSVaultStarModel implements Star
{
	@SerializedName("called_at")
	private String calledAt;
	@SerializedName("ends_at")
	private String endsAt;
	@Getter
	private int world;
	@Getter
	private Object locationKey;
	@Getter
	@SerializedName("called_location")
	private String rawLocation;
	@Getter
	@Setter
	private ShootingStarLocation shootingStarLocation;
	@Getter
	@Setter
	private int tier;
	@Getter
	@Setter
	private transient boolean selected;
	@Getter
	@Setter
	private transient boolean hidden;
	@Getter
	@Setter
	private boolean memberWorld;
	@Getter
	@Setter
	private boolean gameModeWorld;
	@Getter
	@Setter
	private boolean seasonalWorld;

	public long getCalledAt()
	{
		return Instant.parse(calledAt).toEpochMilli();
	}

	public long getEndsAt()
	{
		return Instant.parse(endsAt).toEpochMilli();
	}

	public void setEndsAt(long endsAt)
	{
		this.endsAt = Instant.ofEpochMilli(endsAt).toString();
	}

	@Override
	public boolean equals(Object obj)
	{
		if (this == obj)
		{
			return true;
		}
		if (obj == null || getClass() != obj.getClass())
		{
			return false;
		}
		Star other = (Star) obj;
		return getWorld() == other.getWorld() &&
			(getShootingStarLocation() != null && getShootingStarLocation().equals(other.getShootingStarLocation()));
	}

	@Override
	public int hashCode()
	{
		int result = Integer.hashCode(getWorld());
		result = 31 * result + (getShootingStarLocation() != null ? getShootingStarLocation().hashCode() : 0);
		return result;
	}
}
