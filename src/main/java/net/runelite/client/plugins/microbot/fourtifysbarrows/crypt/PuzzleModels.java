package net.runelite.client.plugins.microbot.fourtifysbarrows.crypt;

public final class PuzzleModels
{
    public static final int FIRST_MODEL_WIDGET = (25 << 16) | 3;

    private PuzzleModels()
    {
    }

    public static int answerIndex(int first, int a, int b, int c)
    {
        if (first < 3)
        {
            return -1;
        }
        int answer = first - 3;
        int matches = (a == answer ? 1 : 0) + (b == answer ? 1 : 0) + (c == answer ? 1 : 0);
        if (matches != 1)
        {
            return -1;
        }
        return a == answer ? 0 : b == answer ? 1 : 2;
    }

    public static int buttonId(int index)
    {
        checkIndex(index);
        return (25 << 16) | (12 + index * 2);
    }

    public static int modelId(int index)
    {
        checkIndex(index);
        return (25 << 16) | (13 + index * 2);
    }

    private static void checkIndex(int index)
    {
        if (index < 0 || index > 2)
        {
            throw new IllegalArgumentException("Unknown puzzle answer: " + index);
        }
    }
}
