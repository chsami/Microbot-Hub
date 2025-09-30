package net.runelite.client.plugins.microbot.zeroprayer;

/*
 * Copyright (c) 2021, Matsyir <https://github.com/matsyir>
 * Copyright (c) 2020, Mazhar <https://twitter.com/maz_rs>
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
import lombok.Getter;
import net.runelite.api.HeadIcon;
import net.runelite.api.SpriteID;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

import java.security.InvalidParameterException;
import java.util.*;
import java.util.stream.Collectors;

public enum AnimationData
{
    // MELEE
    MELEE_VIGGORAS_CHAINMACE(245, AttackStyle.CRUSH),
    MELEE_DAGGER_SLASH(376, AttackStyle.SLASH), // tested w/ dds
    MELEE_SPEAR_STAB(381, AttackStyle.STAB), // tested w/ zammy hasta
    MELEE_SWORD_STAB(386, AttackStyle.STAB), // tested w/ dragon sword, obby sword, d long
    MELEE_SCIM_SLASH(390, AttackStyle.SLASH), // tested w/ rune & dragon scim, d sword, VLS, obby sword

    MELEE_LANCE_STAB(8288, AttackStyle.STAB),
    MELEE_LANCE_CRUSH(8290, AttackStyle.CRUSH),
    MELEE_LANCE_SLASH(8289, AttackStyle.SLASH),

    MELEE_FANG_STAB(9471, AttackStyle.STAB), // tested w/ fang
    MELEE_FANG_SPEC(6118, AttackStyle.STAB, true), // tested w/ fang spec

    MELEE_GENERIC_SLASH(393, AttackStyle.SLASH), // tested w/ zuriel's staff, d long slash, dclaws regular slash
    MELEE_STAFF_CRUSH(0, AttackStyle.SLASH), // 393 previously, save name to support old fights but no longer track

    MELEE_BATTLEAXE_SLASH(395, AttackStyle.SLASH), // tested w/ rune baxe
    MELEE_MACE_STAB(400, AttackStyle.STAB), // tested w/ d mace
    MELEE_BATTLEAXE_CRUSH(401, AttackStyle.CRUSH), // tested w/ rune baxe, dwh & statius warhammer animation, d mace
    MELEE_2H_CRUSH(406, AttackStyle.CRUSH), // tested w/ rune & dragon 2h
    MELEE_2H_SLASH(407, AttackStyle.SLASH), // tested w/ rune & dragon 2h
    MELEE_STAFF_CRUSH_2(414, AttackStyle.CRUSH), // tested w/ ancient staff, 3rd age wand
    MELEE_STAFF_CRUSH_3(419, AttackStyle.CRUSH), // Common staff crush. Air/fire/etc staves, smoke battlestaff, SOTD/SOL crush, zammy hasta crush
    MELEE_PUNCH(422, AttackStyle.CRUSH),
    MELEE_KICK(423, AttackStyle.CRUSH),
    MELEE_STAFF_STAB(428, AttackStyle.STAB), // tested w/ SOTD/SOL jab, vesta's spear stab, c hally
    MELEE_SPEAR_CRUSH(429, AttackStyle.CRUSH), // tested w/ vesta's spear
    MELEE_STAFF_SLASH(440, AttackStyle.SLASH), // tested w/ SOTD/SOL slash, zammy hasta slash, vesta's spear slash, c hally
    MELEE_DLONG_SPEC(1058, AttackStyle.SLASH, true), // tested w/ d long spec, also thammaron's sceptre crush (????)...
    MELEE_DRAGON_MACE_SPEC(1060, AttackStyle.CRUSH, true),
    MELEE_DRAGON_DAGGER_SPEC(1062, AttackStyle.STAB, true),
    MELEE_DRAGON_WARHAMMER_SPEC(1378, AttackStyle.CRUSH, true), // tested w/ dwh, statius warhammer spec
    MELEE_ABYSSAL_WHIP(1658, AttackStyle.SLASH), // tested w/ whip, tent whip
    MELEE_GRANITE_MAUL(1665, AttackStyle.CRUSH), // tested w/ normal gmaul, ornate maul
    MELEE_GRANITE_MAUL_SPEC(1667, AttackStyle.CRUSH, true), // tested w/ normal gmaul, ornate maul
    MELEE_DHAROKS_GREATAXE_CRUSH(2066, AttackStyle.CRUSH),
    MELEE_DHAROKS_GREATAXE_SLASH(2067, AttackStyle.SLASH),
    MELEE_AHRIMS_STAFF_CRUSH(2078, AttackStyle.CRUSH),
    MELEE_OBBY_MAUL_CRUSH(2661, AttackStyle.CRUSH),
    MELEE_ABYSSAL_DAGGER_STAB(3297, AttackStyle.STAB), // spec un-tested
    MELEE_ABYSSAL_BLUDGEON_CRUSH(3298, AttackStyle.CRUSH),
    MELEE_ABYSSAL_BLUDGEON_SPEC(3299, AttackStyle.CRUSH, true),
    MELEE_LEAF_BLADED_BATTLEAXE_CRUSH(3852, AttackStyle.CRUSH),
    MELEE_INQUISITORS_MACE(4503, AttackStyle.CRUSH),
    MELEE_BARRELCHEST_ANCHOR_CRUSH(5865, AttackStyle.CRUSH),
    MELEE_BARRELCHEST_ANCHOR_CRUSH_SPEC(5870, AttackStyle.CRUSH, true),
    MELEE_LEAF_BLADED_BATTLEAXE_SLASH(7004, AttackStyle.SLASH),
    MELEE_GODSWORD_SLASH(7045, AttackStyle.SLASH), // tested w/ AGS, BGS, ZGS, SGS, AGS(or) sara sword
    MELEE_GODSWORD_CRUSH(7054, AttackStyle.CRUSH), // tested w/ AGS, BGS, ZGS, SGS, sara sword
    MELEE_GODSWORD_DEFENSIVE(7055, AttackStyle.SLASH), // tested w/ BGS
    MELEE_RUNE_CLAWS_SPEC(923, AttackStyle.SLASH, true),
    MELEE_DRAGON_CLAWS_SPEC(7514, AttackStyle.SLASH, true),
    MELEE_VLS_SPEC(7515, AttackStyle.SLASH, true), // both VLS and dragon sword spec
    MELEE_ELDER_MAUL(7516, AttackStyle.CRUSH),
    MELEE_ZAMORAK_GODSWORD_SPEC(7638, AttackStyle.SLASH, true), // tested zgs spec
    MELEE_ELDER_MAUL_SPEC(11124, AttackStyle.CRUSH),
    MELEE_ZAMORAK_GODSWORD_OR_SPEC(7639, AttackStyle.SLASH, true), // verified 22/06/2024, assumed due to ags(or)
    MELEE_SARADOMIN_GODSWORD_SPEC(7640, AttackStyle.SLASH, true), // tested sgs spec
    MELEE_SARADOMIN_GODSWORD_OR_SPEC(7641, AttackStyle.SLASH, true), // verified 22/06/2024, assumed due to ags(or)
    MELEE_BANDOS_GODSWORD_SPEC(7642, AttackStyle.SLASH, true), // tested bgs spec
    MELEE_BANDOS_GODSWORD_OR_SPEC(7643, AttackStyle.SLASH, true), // verified 22/06/2024, assumed due to ags(or)
    MELEE_ARMADYL_GODSWORD_SPEC(7644, AttackStyle.SLASH, true), // tested ags spec
    MELEE_ARMADYL_GODSWORD_OR_SPEC(7645, AttackStyle.SLASH, true), // tested ags(or) spec
    MELEE_SCYTHE(8056, AttackStyle.SLASH), // tested w/ all scythe styles (so could be crush, but unlikely)
    MELEE_GHAZI_RAPIER_STAB(8145, AttackStyle.STAB), // rapier slash is 390, basic slash animation. Also VLS stab.
    MELEE_ANCIENT_GODSWORD_SPEC(9171, AttackStyle.SLASH, true),
    MELEE_CRYSTAL_HALBERD_SPEC(1203, AttackStyle.SLASH, true),
    MELEE_SOULREAPER_AXE(10172, AttackStyle.SLASH, true),
    MELEE_SOULREAPER_AXE_SPEC(10173, AttackStyle.SLASH, true),
    MELEE_GUTHANS_LUNGE(2080, AttackStyle.STAB),
    MELEE_GUTHANS_SWIPE(2081, AttackStyle.STAB),
    MELEE_GUTHANS_POUNDMA(2082, AttackStyle.CRUSH),
    MELEE_TORAG_HAMMERS(2068, AttackStyle.CRUSH),
    MELEE_VERACS_FLAIL(2062, AttackStyle.STAB),
    MELEE_BLISTERWOOD_FLAIL_CRUSH(8010, AttackStyle.CRUSH), // blisterwood flail
    MELEE_BONE_DAGGER_SPEC(4198, AttackStyle.STAB, true), // tested with all poison variants (p, p+, p++, none)
    MELEE_DUAL_MACUAHUITL(10989, AttackStyle.CRUSH), // https://oldschool.runescape.wiki/w/Dual_macuahuitl set effect needs custom code
    MELEE_BLUE_MOON_SPEAR_SPEC(1710, AttackStyle.STAB, true), // https://oldschool.runescape.wiki/w/Blue_moon_spear
    MELEE_BLUE_MOON_SPEAR(1711, AttackStyle.STAB),
    MELEE_DHINS(7511, AttackStyle.CRUSH), // https://oldschool.runescape.wiki/w/Dinh%27s_bulwark
    MELEE_URSINE_CHAINMACE_SPEC(9963, AttackStyle.CRUSH, true), // https://oldschool.runescape.wiki/w/Ursine_chainmace#Charged
    MELEE_ANCIENT_MACE_SPEC(6147, AttackStyle.CRUSH, true), // https://oldschool.runescape.wiki/w/Ancient_mace
    MELEE_DSCIM_SPEC(1872, AttackStyle.SLASH, true), // https://oldschool.runescape.wiki/w/Dragon_scimitar
    MELEE_D2H_SPEC(3157, AttackStyle.SLASH, true), // https://oldschool.runescape.wiki/w/Dragon_2h_sword
    MELEE_ARCLIGHT_SPEC(2890, AttackStyle.SLASH, true), // https://oldschool.runescape.wiki/w/Arclight
    MELEE_SARA_SWORD_SPEC(1132, AttackStyle.SLASH, true), // https://oldschool.runescape.wiki/w/Saradomin_sword assumed to be the same for the blessed version
    MELEE_RED_KERIS_SPEC(9544, AttackStyle.SLASH, true), // https://oldschool.runescape.wiki/w/Keris_partisan_of_corruption
    MELEE_SALAMANDER(5247, AttackStyle.SLASH), // https://oldschool.runescape.wiki/w/Salamander

    // RANGED
    RANGED_CHINCHOMPA(7618, AttackStyle.RANGED),
    RANGED_SHORTBOW(426, AttackStyle.RANGED), // Confirmed same w/ 3 types of arrows, w/ maple, magic, & hunter's shortbow, craw's bow, dbow, dbow spec
    RANGED_RUNE_KNIFE_PVP(929, AttackStyle.RANGED), // 1 tick animation, has 1 tick delay between attacks. likely same for all knives. Same for morrigan's javelins, both spec & normal attack.
    RANGED_MAGIC_SHORTBOW_SPEC(1074, AttackStyle.RANGED, true),
    RANGED_CROSSBOW_PVP(4230, AttackStyle.RANGED), // Tested RCB & ACB w/ dragonstone bolts (e) & diamond bolts (e)
    RANGED_BLOWPIPE(5061, AttackStyle.RANGED), // tested in PvP with all styles. Has 1 tick delay between animations in pvp.
    RANGED_DARTS(7554, AttackStyle.RANGED), // tested w/ addy darts. Seems to be constant animation but sometimes stalls and doesn't animate
    RANGED_BALLISTA(7218, AttackStyle.RANGED), // Tested w/ dragon javelins.
    RANGED_BALLISTA_SPEC(7556, AttackStyle.RANGED, true),
    RANGED_RUNE_THROWNAXE_SPEC(1068, AttackStyle.RANGED, true), // https://oldschool.runescape.wiki/w/Rune_thrownaxe
    RANGED_DRAGON_THROWNAXE_SPEC(7521, AttackStyle.RANGED, true),
    RANGED_RUNE_CROSSBOW(7552, AttackStyle.RANGED),
    RANGED_RUNE_CROSSBOW_OR(9206, AttackStyle.RANGED),
    RANGED_BALLISTA_2(7555, AttackStyle.RANGED), // tested w/ light & heavy ballista, dragon & iron javelins.
    RANGED_RUNE_KNIFE(7617, AttackStyle.RANGED), // 1 tick animation, has 1 tick delay between attacks. Also d thrownaxe
    RANGED_DRAGON_KNIFE(8194, AttackStyle.RANGED),
    RANGED_DRAGON_KNIFE_SPEC(8291, AttackStyle.RANGED, true),
    RANGED_DRAGON_KNIFE_POISONED(8195, AttackStyle.RANGED), // tested w/ d knife p++
    RANGED_DRAGON_KNIFE_POISONED_SPEC(8292, AttackStyle.RANGED, true),
    RANGED_ZARYTE_CROSSBOW(9168, AttackStyle.RANGED),
    RANGED_ZARYTE_CROSSBOW_PVP(9166, AttackStyle.RANGED),
    RANGED_BLAZING_BLOWPIPE(10656, AttackStyle.RANGED),
    RANGED_VENATOR_BOW(9858, AttackStyle.RANGED),
    RANGED_KARIL_CROSSBOW(2075, AttackStyle.RANGED),
    RANGED_ATLATL(11057, AttackStyle.RANGED), // https://oldschool.runescape.wiki/w/Eclipse_atlatl
    RANGED_ATLATL_SPEC(11060, AttackStyle.RANGED, true),
    RANGED_TONALZTICS(10923, AttackStyle.RANGED), // https://oldschool.runescape.wiki/w/Tonalztics_of_ralos#Charged
    RANGED_TONALZTICS_SPEC(10914, AttackStyle.RANGED, true),
    RANGED_WEBWEAVER_SPEC(9964, AttackStyle.RANGED, true), // https://oldschool.runescape.wiki/w/Webweaver_bow#Charged
    RANGED_BONE_CROSSBOW_SPEC(7557, AttackStyle.RANGED, true), // https://oldschool.runescape.wiki/w/Dorgeshuun_crossbow

    // MAGIC - Keep in spellbook order (staves last) then alphabetical order and oneline
    MAGIC_GOD_SPELL(811, AttackStyle.MAGIC, Spellbook.STANDARD), // https://oldschool.runescape.wiki/w/God_spells
    MAGIC_IBAN_BLAST(708, AttackStyle.MAGIC, Spellbook.STANDARD),
    MAGIC_SLAYER_DART(1576, AttackStyle.MAGIC, Spellbook.STANDARD), // https://oldschool.runescape.wiki/w/Magic_Dart
    MAGIC_STANDARD_BIND(710, AttackStyle.MAGIC, Spellbook.STANDARD), // tested w/ bind, snare, entangle
    MAGIC_STANDARD_BIND_STAFF(1161, AttackStyle.MAGIC, Spellbook.STANDARD), // tested w/ bind, snare, entangle, various staves
    MAGIC_STANDARD_CONFUSE(1163, AttackStyle.MAGIC, Spellbook.STANDARD),
    MAGIC_STANDARD_CRUMBLE_UNDEAD(724, AttackStyle.MAGIC, Spellbook.STANDARD),
    MAGIC_STANDARD_CRUMBLE_UNDEAD_HOLDING_STAFF(1166, AttackStyle.MAGIC, Spellbook.STANDARD),
    MAGIC_STANDARD_ENFEEBLE(1168, AttackStyle.MAGIC, Spellbook.STANDARD),
    MAGIC_STANDARD_STRIKE_BOLT_BLAST(711, AttackStyle.MAGIC, Spellbook.STANDARD), // tested w/ bolt
    MAGIC_STANDARD_STRIKE_BOLT_BLAST_STAFF(1162, AttackStyle.MAGIC, Spellbook.STANDARD), // strike, bolt and blast (tested all spells, different weapons)
    MAGIC_STANDARD_STUN(1169, AttackStyle.MAGIC, Spellbook.STANDARD),
    MAGIC_STANDARD_SURGE_STAFF(7855, AttackStyle.MAGIC, Spellbook.STANDARD), // tested many staves
    MAGIC_STANDARD_VULNERABILITY_CURSE(1165, AttackStyle.MAGIC, Spellbook.STANDARD),
    MAGIC_STANDARD_WAVE(727, AttackStyle.MAGIC, Spellbook.STANDARD), // tested w/ wave spells
    MAGIC_STANDARD_WAVE_STAFF(1167, AttackStyle.MAGIC, Spellbook.STANDARD), // tested many staves
    MAGIC_STANDARD_WEAKEN(1164, AttackStyle.MAGIC, Spellbook.STANDARD),

    MAGIC_ANCIENT_MULTI_TARGET(1979, AttackStyle.MAGIC, Spellbook.ANCIENT), // Burst & Barrage animations (tested all 8, different weapons)
    MAGIC_ANCIENT_SINGLE_TARGET(1978, AttackStyle.MAGIC, Spellbook.ANCIENT), // Rush & Blitz animations (tested all 8, different weapons)

    MAGIC_ARCEUUS_DEMONBANE(8977, AttackStyle.MAGIC, Spellbook.ARCEUUS), // Also greater corruption, so that may accidentally trigger a manual-cast, but that's probably fine only affects Muspah
    MAGIC_ARCEUUS_GRASP(8972, AttackStyle.MAGIC, Spellbook.ARCEUUS),

    MAGIC_ACCURSED_SCEPTRE_SPEC(9961, AttackStyle.MAGIC, true),
    MAGIC_TUMEKENS_SHADOW(9493, AttackStyle.MAGIC, false),
    MAGIC_WARPED_SCEPTRE(10501, AttackStyle.MAGIC, false), // https://oldschool.runescape.wiki/w/Warped_sceptre
    MAGIC_VOLATILE_NIGHTMARE_STAFF_SPEC(8532, AttackStyle.MAGIC, true), // assume 99 mage's base damage (does not rise when boosted).

    // Misc
    MARK_OF_DARKNESS(8970, AttackStyle.NON_ATTACK),
    PICK_POCKETING(881, AttackStyle.NON_ATTACK),
    EAT_FOOD_OR_POTION(829, AttackStyle.NON_ATTACK),
    SUMMON_THRALL(8973, AttackStyle.NON_ATTACK),
    TAKING_HIT_1HANDED_UNARMED(397, AttackStyle.NON_ATTACK),
    TAKING_HIT_2H_SWORD(410, AttackStyle.NON_ATTACK),
    TAKING_HIT_ANCHOR(5866, AttackStyle.NON_ATTACK),
    TAKING_HIT_BLISTERWOOD_FLAIL(8017, AttackStyle.NON_ATTACK),
    TAKING_HIT_BLOWPIPE(430, AttackStyle.NON_ATTACK),
    TAKING_HIT_BULWARK(7512, AttackStyle.NON_ATTACK),
    TAKING_HIT_CHAINMACE(7200, AttackStyle.NON_ATTACK),
    TAKING_HIT_CHIN_CHOMPA(3176, AttackStyle.NON_ATTACK),
    TAKING_HIT_DAGGER(378, AttackStyle.NON_ATTACK),
    TAKING_HIT_DEFENDER(4177, AttackStyle.NON_ATTACK),
    TAKING_HIT_FANG(388, AttackStyle.NON_ATTACK),
    TAKING_HIT_GODSWORD(7056, AttackStyle.NON_ATTACK),
    TAKING_HIT_KERIS(383, AttackStyle.NON_ATTACK),
    TAKING_HIT_LARGE_STAFF(420, AttackStyle.NON_ATTACK),
    TAKING_HIT_MACE(403, AttackStyle.NON_ATTACK),
    TAKING_HIT_OBBY_MAUL(1666, AttackStyle.NON_ATTACK),
    TAKING_HIT_SCYTHE(435, AttackStyle.NON_ATTACK),
    TAKING_HIT_SHIELD(1156, AttackStyle.NON_ATTACK),
    TAKING_HIT_SPEAR(1709, AttackStyle.NON_ATTACK),
    TAKING_HIT_STAFF(415, AttackStyle.NON_ATTACK),
    TAKING_HIT_UNARMED(424, AttackStyle.NON_ATTACK),
    TAKING_HIT_VERACS_FLAIL(2063, AttackStyle.NON_ATTACK),
    TAKING_HIT_WHIP(1659, AttackStyle.NON_ATTACK),
    LOW_ALCH(712, AttackStyle.NON_ATTACK),
    HIGH_ALCH(713, AttackStyle.NON_ATTACK);

    private static final Map<Integer, AnimationData> DATA;
    private static final Map<Spellbook, Set<AnimationData>> spellBookAnimations;
    private static final Map<Integer, AnimationData> notAttacks;

    public final int animationId;
    public final boolean isSpecial;
    public final AttackStyle attackStyle;
    private final Spellbook spellbook;

    // Simple animation data constructor for all melee and range attacks
    AnimationData(int animationId, AttackStyle attackStyle)
    {
        if (attackStyle == null)
        {
            throw new InvalidParameterException("Attack Style must be valid for AnimationData");
        }
        this.animationId = animationId;
        this.attackStyle = attackStyle;
        this.isSpecial = false;
        this.spellbook = null;
    }

    // Simple animation data constructor for all melee and range attacks w/ special
    AnimationData(int animationId, AttackStyle attackStyle, boolean isSpecial)
    {
        if (attackStyle == null)
        {
            throw new InvalidParameterException("Attack Style must be valid for AnimationData");
        }
        this.animationId = animationId;
        this.attackStyle = attackStyle;
        this.isSpecial = isSpecial;
        this.spellbook = null;
    }

    // Simple animation data constructor for all magic attacks
    AnimationData(int animationId, AttackStyle attackStyle, Spellbook book)
    {
        if (attackStyle == null)
        {
            throw new InvalidParameterException("Attack Style must be valid for AnimationData");
        }
        this.animationId = animationId;
        this.attackStyle = attackStyle;
        this.isSpecial = false;
        this.spellbook = book;
    }

    static
    {
        ImmutableMap.Builder<Integer, AnimationData> builder = new ImmutableMap.Builder<>();
        ImmutableMap.Builder<Integer, AnimationData> notAttacksBuilder = new ImmutableMap.Builder<>();
        Map<Spellbook, Set<AnimationData>> spellBookBuilder = new HashMap<>();

        for (Spellbook s : Spellbook.values())
        {
            spellBookBuilder.put(s, new HashSet<AnimationData>());
        }

        for (AnimationData data : values())
        {
            builder.put(data.animationId, data);

            if (data.spellbook != null)
            {
                if (data.attackStyle != AttackStyle.MAGIC)
                {
                    throw new InvalidParameterException("Spell book should only be magic animations");
                }
                spellBookBuilder.get(data.spellbook).add(data);
            }
            if (data.attackStyle == AttackStyle.NON_ATTACK)
            {
                notAttacksBuilder.put(data.animationId, data);
            }
        }

        DATA = builder.build();
        notAttacks = notAttacksBuilder.build();
        spellBookAnimations = spellBookBuilder;
    }

    public static AnimationData fromId(int animationId)
    {
        return DATA.get(animationId);
    }

    public static Set<AnimationData> getAnimationsForSpellbook(Spellbook s)
    {
        return spellBookAnimations.get(s);
    }

    public static boolean isManualCasting(AnimationData animationData)
    {
        // This check ensures we don't treat staff animations which are magic attacks as a "manual cast".
        if (animationData.spellbook != null && animationData != null)
        {
            // We tell a manual cast by the animation data:
            return animationData.attackStyle == AttackStyle.MAGIC &&
                spellBookAnimations.get(animationData.spellbook).contains(animationData);
        }
        return false;
    }

    public static boolean isBlockListAnimation(int animationId)
    {
        return notAttacks.containsKey(animationId);
    }

    @Override
    public String toString()
    {
        String[] words = super.toString().toLowerCase().split("_");
        Arrays.stream(words)
                .map(StringUtils::capitalize).collect(Collectors.toList()).toArray(words);

        return String.join(" ", words);
    }

    public boolean matchesSpellbook(Spellbook s)
    {
        if (this.spellbook != null)
        {
            return this.spellbook == s;
        }
        return false;
    }


    // An enum of combat styles (including stab, slash, crush).
    public enum AttackStyle
    {
        STAB(HeadIcon.MELEE, SpriteID.COMBAT_STYLE_SWORD_STAB),
        SLASH(HeadIcon.MELEE, SpriteID.COMBAT_STYLE_SWORD_SLASH),
        CRUSH(HeadIcon.MELEE, SpriteID.COMBAT_STYLE_HAMMER_POUND),
        RANGED(HeadIcon.RANGED, SpriteID.SKILL_RANGED),
        MAGIC(HeadIcon.MAGIC, SpriteID.SKILL_MAGIC),
        NON_ATTACK(HeadIcon.SMITE, SpriteID.SKILL_THIEVING);

        static AttackStyle[] MELEE_STYLES = {STAB, SLASH, CRUSH};

        @Getter
        private final HeadIcon protection;

        @Getter
        private final int styleSpriteId;

        AttackStyle(HeadIcon protection, int styleSpriteId)
        {
            this.protection = protection;
            this.styleSpriteId = styleSpriteId;
        }

        public boolean isMelee()
        {
            return ArrayUtils.contains(AttackStyle.MELEE_STYLES, this);
        }

        public boolean isUsingSuccessfulOffensivePray(int pray)
        {
            return (pray > 0 &&
                    ((isMelee() &&
                            (pray == SpriteID.PRAYER_PIETY ||
                                    pray == SpriteID.PRAYER_ULTIMATE_STRENGTH)) ||
                            (this == RANGED &&
                                    (pray == SpriteID.PRAYER_RIGOUR ||
                                            pray == SpriteID.PRAYER_EAGLE_EYE)) ||
                            (this == MAGIC &&
                                    (pray == SpriteID.PRAYER_AUGURY ||
                                            pray == SpriteID.PRAYER_MYSTIC_MIGHT)))
            );
        }


        @Override
        public String toString()
        {
            return StringUtils.capitalize(super.toString().toLowerCase());
        }
    }
}
