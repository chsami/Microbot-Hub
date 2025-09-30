package net.runelite.client.plugins.microbot.zeroprayer;

/*
 * Copyright (c) 2024, Lexer747 <https://github.com/Lexer747>
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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

// https://oldschool.runescape.wiki/w/Powered_staff
//
// They are typically charged with runes or coins to automatically fire magic projectiles. Unlike other Magic
// weapons, they cannot be used to auto-cast spells. In addition, they have an attack speed of 4, faster than
// other magic weapons, which have an attack speed of 5. The Tumeken's shadow, however, has an attack speed of
// 5. This Enum is only to contain the staves which allow magic at 4 ticks.
@Getter
enum PoweredStaves
{
    WEAPON_ACCURSED( Set.of(AnimationData.MAGIC_STANDARD_WAVE_STAFF, AnimationData.MAGIC_ACCURSED_SCEPTRE_SPEC), Projectiles(2337, 2339), 27665, 27666), // https://oldschool.runescape.wiki/w/Accursed_sceptre
    WEAPON_BLUE_C_STAFF_A(AnimationData.MAGIC_STANDARD_WAVE_STAFF, Projectiles(1720), 23899), // https://oldschool.runescape.wiki/w/Crystal_staff_(attuned)
    WEAPON_BLUE_C_STAFF_B(AnimationData.MAGIC_STANDARD_WAVE_STAFF, Projectiles(1720), 23898), // https://oldschool.runescape.wiki/w/Crystal_staff_(basic)
    WEAPON_BLUE_C_STAFF_P(AnimationData.MAGIC_STANDARD_WAVE_STAFF, Projectiles(1720),23900), // https://oldschool.runescape.wiki/w/Crystal_staff_(perfected)
    WEAPON_BONE_STAFF(AnimationData.MELEE_GENERIC_SLASH, Projectiles(2647),  28796, 28797), //https://oldschool.runescape.wiki/w/Bone_staff
    WEAPON_DAWNBRINGER(AnimationData.MAGIC_STANDARD_WAVE_STAFF,  Projectiles(1544, 1547),22516), // https://oldschool.runescape.wiki/w/Dawnbringer
    WEAPON_HARM(Set.of(AnimationData.MAGIC_STANDARD_WAVE_STAFF, AnimationData.MAGIC_STANDARD_STRIKE_BOLT_BLAST_STAFF, AnimationData.MAGIC_STANDARD_SURGE_STAFF),
        Projectiles(/*in level order then air -> fire*/
            91, 94, 97, 100, /* strikes */
            118, 121, 124, 127, /* bolts */
            133, 136, 139, 130, /* blasts */
            159, 162, 165, 156, /* waves */
            1456, 1459, 1462, 1465 /* surges */),
        24508), // https://oldschool.runescape.wiki/w/Harmonised_nightmare_staff
    WEAPON_RED_C_STAFF_A(AnimationData.MAGIC_STANDARD_WAVE_STAFF, Projectiles(1723), 23853), // https://oldschool.runescape.wiki/w/Corrupted_staff_(attuned)
    WEAPON_RED_C_STAFF_B(AnimationData.MAGIC_STANDARD_WAVE_STAFF, Projectiles(1723), 23852), // https://oldschool.runescape.wiki/w/Corrupted_staff_(basic)
    WEAPON_RED_C_STAFF_P(AnimationData.MAGIC_STANDARD_WAVE_STAFF, Projectiles(1723), 23854), // https://oldschool.runescape.wiki/w/Corrupted_staff_(perfected)
    WEAPON_SANG(AnimationData.MAGIC_STANDARD_WAVE_STAFF, Projectiles(1539), 22323), // https://oldschool.runescape.wiki/w/Sanguinesti_staff#Charged
    WEAPON_SANG_KIT(AnimationData.MAGIC_STANDARD_WAVE_STAFF, 25731), // https://oldschool.runescape.wiki/w/Holy_sanguinesti_staff#Charged
    WEAPON_STARTER_STAFF(22335, 22336, 28557, 28558), // https://oldschool.runescape.wiki/w/Starter_staff TODO get the animation when DMM goes live
    WEAPON_SWAMP(AnimationData.MAGIC_STANDARD_WAVE_STAFF, Projectiles(1040),12899), // https://oldschool.runescape.wiki/w/Trident_of_the_swamp#Charged
    WEAPON_SWAMP_E(AnimationData.MAGIC_STANDARD_WAVE_STAFF, Projectiles(1252),22292), // https://oldschool.runescape.wiki/w/Trident_of_the_swamp_(e)#Charged
    WEAPON_THAMMARON(AnimationData.MAGIC_STANDARD_WAVE_STAFF, Projectiles(2340),22555, 22556), //https://oldschool.runescape.wiki/w/Thammaron%27s_sceptre
    WEAPON_TRIDENT(AnimationData.MAGIC_STANDARD_WAVE_STAFF, Projectiles(1252), 11907), // https://oldschool.runescape.wiki/w/Trident_of_the_seas#Partially_charged
    WEAPON_TRIDENT_E(AnimationData.MAGIC_STANDARD_WAVE_STAFF, Projectiles(1252),22288), // https://oldschool.runescape.wiki/w/Trident_of_the_seas_(e)#Charged
    WEAPON_WARPED_SCEPTRE(AnimationData.MAGIC_WARPED_SCEPTRE, 28585, 28586); // https://oldschool.runescape.wiki/w/Warped_sceptre

    private final Set<Integer> ids;
    private final Set<Integer> projectiles;
    private final Set<AnimationData> animations;

    private static Set<Integer> Projectiles(int... id)
    {
        ImmutableSet.Builder<Integer> builder = new ImmutableSet.Builder<>();
        if (id.length == 0)
        {
            return builder.build();
        }
        for (int i : id)
        {
            builder.add(i);
        }
        return builder.build();
    }

    // Unknown projectile and animation
    PoweredStaves(int... id)
    {
        this.ids = Projectiles(id);
        this.projectiles = null;
        this.animations = null;
    }

    // Unknown projectile
    PoweredStaves(AnimationData spell, int... id)
    {
        this.ids = Projectiles(id);
        this.projectiles = new HashSet<Integer>();
        this.animations = Set.of(spell);
    }

    // Single animation 4t powered staff
    PoweredStaves(AnimationData spell, Set<Integer> projectiles, int... id)
    {
        this.ids = Projectiles(id);
        this.projectiles = projectiles;
        this.animations = Set.of(spell);
    }
    // Multiple animations 4t powered staff
    PoweredStaves(Set<AnimationData> spell, Set<Integer> projectiles, int... id)
    {
        this.ids = Projectiles(id);
        this.projectiles = projectiles;
        this.animations = spell;
    }

    protected static final boolean LOCAL_DEBUGGING = false;
    protected static final int UNKNOWN_SPELL = 0xDEADBEEF;
    protected static final ImmutableMap<Integer, ImmutableMap<Integer, PoweredStaves>> poweredStaves;

    static
    {
        ImmutableMap.Builder<Integer, ImmutableMap<Integer, PoweredStaves>> builder = new ImmutableMap.Builder<>();

        for (PoweredStaves p : values())
        {
            for (int id : p.ids)
            {
                ImmutableMap.Builder<Integer, PoweredStaves> spellMap = new ImmutableMap.Builder<>();
                if (p.animations == null)
                {
                    spellMap.put(UNKNOWN_SPELL, p);
                }
                else
                {
                    for (AnimationData spell : p.animations)
                    {
                        spellMap.put(spell.animationId, p);
                    }
                }
                builder.put(id, spellMap.build());
            }
        }
        if (LOCAL_DEBUGGING)
        {
            // Fake the kodai to be a harm for testing, because I don't own a harm.
            ImmutableMap.Builder<Integer, PoweredStaves> spellMap = new ImmutableMap.Builder<>();
            for (AnimationData harmAnim : WEAPON_HARM.animations) {
                spellMap.put(harmAnim.animationId, WEAPON_HARM);
            }
            builder.put(21006, spellMap.build());
        }

        poweredStaves = builder.build();
    }

    public static PoweredStaves getPoweredStaves(int weaponId, AnimationData animation)
    {
        ImmutableMap<Integer, PoweredStaves> weaponMap = poweredStaves.get(weaponId);
        if (weaponMap == null || animation == null)
        {
            return null;
        }
        // If the data in the enum doesn't have a spell then we can simply return the stave based on the
        // weapon ID only.
        if (weaponMap.containsKey(UNKNOWN_SPELL))
        {
            return weaponMap.get(UNKNOWN_SPELL);
        }
        return weaponMap.get(animation.animationId);
    }

    @Override
    public String toString()
    {
        String[] words = super.toString().toLowerCase().split("_");
        Arrays.stream(words)
                .map(StringUtils::capitalize).collect(Collectors.toList()).toArray(words);

        return String.join(" ", words);
    }

    public boolean MatchesProjectile(int projectile)
    {
        if (this.projectiles == null)
        {
            return false;
        }
        return this.projectiles.contains(projectile);
    }
}
