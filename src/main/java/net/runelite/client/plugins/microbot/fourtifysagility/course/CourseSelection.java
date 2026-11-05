package net.runelite.client.plugins.microbot.fourtifysagility.course;


public final class CourseSelection
{
    private CourseSelection()
    {
    }

    public static AgilityCourse forLevel(int level, int priestInPeril)
    {
        if (level < 10)
        {
            return AgilityCourse.GNOME;
        }
        if (level < 20)
        {
            return AgilityCourse.DRAYNOR;
        }
        if (level < 30)
        {
            return AgilityCourse.AL_KHARID;
        }
        if (level < 40 || level < 50 && priestInPeril <= 2)
        {
            return AgilityCourse.VARROCK;
        }
        if (level < 50)
        {
            return AgilityCourse.CANIFIS;
        }
        if (level < 60)
        {
            return AgilityCourse.FALADOR;
        }
        if (level < 70)
        {
            return AgilityCourse.SEERS_VILLAGE;
        }
        if (level < 80)
        {
            return AgilityCourse.POLLNIVNEACH;
        }
        if (level < 90)
        {
            return AgilityCourse.RELLEKKA;
        }
        return AgilityCourse.ARDOUGNE;
    }
}
