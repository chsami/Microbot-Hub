package net.runelite.client.plugins.microbot.zeroprayer;

/*
 * Copyright (c) 2017, honeyhoney <https://github.com/honeyhoney>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.EnumID;
import net.runelite.api.ParamID;
import net.runelite.api.StructComposition;

import static net.runelite.client.plugins.microbot.zeroprayer.AttackStyle.*;

final class WeaponType
{
    private static final int BLUE_MOON_SPEAR = 22;
    private static final int DEFENSIVE_CASTING_SLOT = 5;

    private WeaponType()
    {
    }

    static AttackStyle[] getAttackStyles(Client client, int weaponType)
    {
        EnumComposition weaponStyles = client.getEnum(EnumID.WEAPON_STYLES);
        int weaponStyleEnum = weaponStyles == null ? -1 : weaponStyles.getIntValue(weaponType);
        if (weaponStyleEnum == -1)
        {
            if (weaponType == BLUE_MOON_SPEAR)
            {
                return new AttackStyle[]{ACCURATE, AGGRESSIVE, null, DEFENSIVE, CASTING, DEFENSIVE_CASTING};
            }
            return new AttackStyle[0];
        }

        EnumComposition weaponStyleComposition = client.getEnum(weaponStyleEnum);
        int[] weaponStyleStructs = weaponStyleComposition == null ? null : weaponStyleComposition.getIntVals();
        if (weaponStyleStructs == null)
        {
            return new AttackStyle[0];
        }

        AttackStyle[] styles = new AttackStyle[weaponStyleStructs.length];
        for (int i = 0; i < weaponStyleStructs.length; i++)
        {
            StructComposition attackStyleStruct = client.getStructComposition(weaponStyleStructs[i]);
            String attackStyleName = attackStyleStruct == null ? null : attackStyleStruct.getStringValue(ParamID.ATTACK_STYLE_NAME);
            AttackStyle attackStyle = fromName(attackStyleName);
            if (attackStyle == OTHER)
            {
                continue;
            }
            if (attackStyle == DEFENSIVE && (i == DEFENSIVE_CASTING_SLOT || styles[0] == CASTING))
            {
                attackStyle = DEFENSIVE_CASTING;
            }
            styles[i] = attackStyle;
        }
        return styles;
    }

    private static AttackStyle fromName(String attackStyleName)
    {
        if (attackStyleName == null)
        {
            return OTHER;
        }
        for (AttackStyle attackStyle : AttackStyle.values())
        {
            if (attackStyle.name().equalsIgnoreCase(attackStyleName))
            {
                return attackStyle;
            }
        }
        return OTHER;
    }
}
