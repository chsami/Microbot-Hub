package net.runelite.client.plugins.microbot.drozulrah.helper;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;
import javax.imageio.ImageIO;

/** Original overlay PNGs embedded so copying the Java package includes its runtime images. */
final class ZulrahImages
{
    private ZulrahImages()
    {
    }

    static BufferedImage load(String name)
    {
        final String encoded;
        switch (name)
        {
            case "zulrah_range.png":
                encoded =
                    "iVBORw0KGgoAAAANSUhEUgAAADIAAAAyCAYAAAAeP4ixAAAPI0lEQVRoQ82ae3BUVZ7Hv7ffjzw6SXcSEgIhsIAkxJCQDSCsREQx" +
                    "M+v6AnXGKced0R3HcWusfbjD4BQ7iuPI4mwpFA6KW6LjIzFRZGAAwaACIYEAIZCEkKQ7/X7dfnffvs+zdZvFVTeQgGHLrup/+p5z" +
                    "ft/POb/f7/zOuU3hO/pp+lEhm4xO16h1NjT8FQPv+SKekqiK19uGnGNJpr5rHLc9UNAQCeYeo6gsSKIErYpF7RwvXOdNUFAARVFo" +
                    "3mv/P7q/cyCLV9WQkDcKQeBRX0lDqUQGIBgEuGgueEFCXo4KOz91KwCQSwvxnQJZ9+ssunWvJV+rZFFiDiOdSn/pMHX1P8TGjW9n" +
                    "9N5QkfuCJV/z9OcnAl/qvyzI889nWdauTQT+P13vHx43E4lLwukC0gyDJCO7kgGzZi6DQqmpeOutXdav6mm6ecqePZ95muTfvgRp" +
                    "vMv8DieIDzAJikrGVCgvAkqLFcjLE2AysUSS4A4EpTs2b072Xi+45U2zyNQ8F4aGGITCQEHBfIiiiK6uPiUA6Up2x3Mt6tcv5ko8" +
                    "JyIeIYj6eJRN0SInm4LttKnqtVb7ucmCam6G8oUt84TKshFEYxRcLgYlJTct3rXryLGJ2BgPJDPGs6/kEZ4DBEECx0pgEkCeGjDo" +
                    "CeynLOtebbVuWPq9aXmHd9vDEzE6VptHHi2/vfeCYW8GJE6BSRJMm74Kr73+0YQ0TqjR+vVQZZXl8TIMzwMCDyQTQiab6DhArQJ2" +
                    "HVbh+IHwhMYbC6S+sZIQQjCvzIpoFAjRDAoLG2OtH7bnTmRyJmz4jy0WIgPwvJABkYE4jiCdEqFUqaBjeHgGi7gtbw9rZcPNzc3K" +
                    "NWvWiBMRIbeZXV2eSaU1c3yIRYFwhIFKeWPnkY6eRRMZY8Igb+81E4GnMhCCcDFLpNNixt0kEZAkCoFBPfREgWio6PHpM+9S6o3a" +
                    "MyePbvvcSXNQ67TgE9yhvV+MNo4lbG5tBZEHqprlRzwqAdRc7NvfM2F9E2744eFiIgvOpLr/6ZVKCpCkixBEAggBXCPA/Io/IDc3" +
                    "Hz0nOnHqaCsSKRZZ0xYgMHwCjpQKFsSfUUzlExX5wW3btiElj/nQjy3EZGTgdovwuBkUWOpgMOST5uZP5I1v3M8VQZavtmSplWQn" +
                    "QDVWl2gzbdVqASoVC0CAXk8hK0cJrZGCqVgJjZHCi88W4ZmfPwOiIdh9cBfAtOKRv/8LLlzox1stLyAWUOKeu2g4nCpkZ7N4bkMi" +
                    "M+727YaS4SGFy+0UEIqI8Pl4LFhwD8LhON5v/mTcCb9Mg9XKefPOKUUxboZWoRVEobxydjRvygyxVRauN6qg0VBQKAmYFCBwgMQT" +
                    "kLgKx85aUF8ZBqebhrS/HwWlldDnFuPoX45hwbR8iKYYOo65oTZVwu9LYnXTfVAojfr169enG1ZUk5uqrfD7BSRTgM+bhilvMVwu" +
                    "/096eobe+Db7yNf6PvkytIVUXlqvB1QqQKlSgudFEIlkgibuN+CLg0YUlVmw/GA/an+/GQ9vfAYzZpRhcHAUs/NZuILydq2CoDfi" +
                    "3gdj0OkJhODPodfrNzTvaf5rURRXLpzrBB3kEQwwyDUtQfGUmd6CgsK2DRs2PXE5mHGXbKyOm97IIzq9CgYDoNYQpBkKCoUMJn8J" +
                    "+CcohH/xJJKqTWh7rxiCyCJLU4CHf/ADaDQMjp08mRTdp43Vd0Wg0ytBidPAB9fgiV88TS1cPo8wKR7zpjkQj1EIhhiUl6+A0aiH" +
                    "TqtBKBwztbQciH5T1zWByINsfd9CDEbAYFBClAjUatnVgDNPpeBYOh0L6j1Qqihs2qBEhGZROCUfeoMGHCciOz+A2hla6HRqmEsJ" +
                    "KpfwCPb/G1hW+vf/fG3rMAVpx9IaN5xOCqkUgzQLLKxfA3mf8XppOBzezq6uvq+l5WsG2bHL/Kg+S7VNpwMEEdBq5dUBUkkCR58a" +
                    "gVEJNlsK591lYJgIOI5GeUktfrj6IcTjVlhtbticrZg1SwGLSY35t+pA9z+H++//SUbT2l/lkEhEQjQqguMBlgXM5pUIBMIZmOPH" +
                    "+76m/ZpBZGO7u4qJKAIaDYH9nAJ+WwIer4RgUEI4zGFapQFn+833R4Pc+zpdVmZGlUoFFAoFzOowbrozjZy8ixJSDgE3LqvHul8e" +
                    "1HR3g29YUf2BIAj3LquxIxgUEYkCyTiDAvMtiMaS+OSTzskDefm1UmLvj8HrE5BISJizUIepM1SZTVLeV2TIytI/6V7dsS5NRwKZ" +
                    "39NpCSVKCjOrAFMRQUW1gJxcJVhOhPcMB4J78M//8l8ZkQuXVxL57KRRsSjMciMZFxCO8igtrUd2tgUGg+H2bds+2J/Z28bdab7R" +
                    "YOnd5u/HPNm7mKTIqhE62dhELc63KJFXQMGUp0KOSYkckzx7gNctomZaC072fIqXXtmOV178DZzOUZzuGUSY3Y2yfDPmLGZQNFUF" +
                    "rZbC0HEWhzt1+NNr7oyu6tuqjcoEe8fShmSLThmFy8Vn4sXvTaO65j4oFBQ2b27JtL1qkLHA16+HwjDVxFgsas1FKAXogISAl8eN" +
                    "Za043duF2TMrMTR0FAIvYOfO13HLvRIitIhsokP995VQaygoFQQ/e8iIs11DX9N1xx2ztAFGm15cJbuZkIGJhBiYC1cgEIicP3So" +
                    "e+6kgFyCe2uPmeSblSgoVOL4XhW6j4Ywo6wJq+5cg6NHjoCSy2UCmOfugEJJZVYt4OBQXJiNaVUimBTB8SPMmt/+U7TlmxMmp2W1" +
                    "SsSsYjsSCSASZmDMrkLAT9DZde5S1XS1DnbZ9tTvfl8mnegMoK8/LSYSqGl+/71elmVhtQ5jT8ubKJnN4dYHCbKyKCSTBLGoiKEO" +
                    "I+YuSyEZJ3jk7/73HP5VKzKIHGOl5ihUJAImKcIf5GAy1eHQoe7JA2m8t+DASK92yeig23BJgH30LNFocxAMDGFgoAt9fdux8Htx" +
                    "GIyKTIXLJJEBGT1pxLk+H9ramMt6SMX8ivkrlqbPmIwx2KwiaJpBPAmUlTWira19UkCo2dWlwcEzLvNXr2c6j33wmaVw9t+wrAS/" +
                    "/xQcdgdGbS+iblUuUoyIeExEMgEwSR7+89ngSAx/2Bi9oqvXN1aTW+qt8PkEpFLIVMnFJUuh0WSXfasYuXFZ8W97vvD+ZixH27v3" +
                    "FZKTbYHbY4PfF4bT5UfAH0FRYQcMUyQYsrmHf/njyA6574r7zMR+Xi9c6HWor+TkclE5rYiGUR3KgMhBr6CmwmypnJysNZbxnTv/" +
                    "Y2UwEN0vA9B0BNFoEt3dB1FQnnPnUF/6XddINOtSv7l1M14e6Lb+43iRKseJ3KZqujUDEo3IVYUOxqyq6wfS07OfOB1WOBxyWR5G" +
                    "MBjBvv3vo3COSfji48gVZ34soIabq34kEHEHL4iomu5ELE6QSjDgBBM4tuT6gDgcNhKLhZFIxBCLncPwsANWqxsHPnkzxlBF1r5u" +
                    "X814sz/W85c2mUgoJB+6BHg8F1OwUj0dTNL4/LeKkbGMtbevV5nNy3hR5MFxafi8I/D7Q3C7abzTsv3u/l7uo2uB2Lo1q/CN5grf" +
                    "7UtGEI8DDocIOsggN28hPv74xKRkra/pam198aaCAsvhNJMEHYqADoZht/vg8Yzi0LHBP7pGfD+7FpCn/rWUHDmRj1sbrPC4RbAc" +
                    "4PdxsBQuwrvvHZl8EFlkW9tGEgpF4fPSoEMxRMJxdB3/M86ela7ZA+obqwghEmrm+CEwSfDCxSupnJwlkIix9JoHvtKsdnZ+SPr7" +
                    "e0AHIwhH4pnzw+HDH2Fg4NpicmFjpQhCMrcpKgWHuVNdmRUJ0cDsOXdmisdJBxkYOExYVot4bBA07cHAwChGrDacOLEL3d3XZm/z" +
                    "5txfdXbrl2pVTBMRWdAhCukUMqfHKaUroVQoOiYdxGa7QBKJOOSvw96ROdF5vEHs/vN2nDx19SAvv5w/b0+75am6eV7Il+nhkEDs" +
                    "durRZJJBIkUhL68W7e2TWGvJy37w4JulFkuJU+BZpFJR2B1WeDzBTOrdv795eHAQs64m0OU754PHbtjKcmqsXDQCJg3QQQEeN6lN" +
                    "JtnK3LybtA6HF729w5Mb7M3Nq5XFxX8rJBMx+P3BDMSo3QM6GMXJUwdWDQ1h39WANNwyfxsBRYkSjxX1DgSCPHieku/StC5HulKr" +
                    "X1Dy2eenpkzaweqr4j5s25gO0hGtXF/JbhVPpDAy3Auf3/a7vj5p7URB6m4ufZxS5N9AiKAXeQmL5o9mLgMvvQ0IhXW1Bw5G6i6N" +
                    "N+kxcu7spwcGBo7f4vEEKToURTyW4tsPvX1GEFSqnh5hQjv6888bH36ztcggpymWlUh2lrq8do6jQM5UcqHIpYAb5q+u2bjxrfrr" +
                    "BtJ75mA3y+kQi/bBZhtFIBgBTcfRcfjN81ZfMe0c9j55pVXZssVSHAhw1REaM+Xb+RTPIhxWZIB4VlnLshxlyJqO2bMXUy+99M5P" +
                    "rwuI0znazTBJpFJJJBIjcNiHEIunEI8lyKH2Hf0dnezTwWDKfRkQ6u4HZzZ6fVkJjvBU0yIrEmkQm02YK3IKY4qRwPM8UmlUVlSs" +
                    "1OblZePVV9sevS4gx4/vfL2gYFY5mxZyk8mEwuPuCnh8QbvN5qSs1hHy7rtfPDZejNQsLzdxgnpO0yInaFqAzyfvF7w+HscNcnxM" +
                    "mbJILuXr8nNMb7zbsrfjuoDs27dlu8VSLrFsCgHfKHyBEGw2D5zOIPx+mvSdP7F3dIRrGw8GgPKRR9QLIQA+GoiFeMQZkKlTl9TR" +
                    "dAwKhar26NHTX67Gdcla+/Zt3RaJxKgQTcProWGz+0b5dJoeuHDsfDyeSDJCMXFavV0TgMEDD6gbYrGLIFPLb4Pd7q2NRpPhc+eG" +
                    "3/tm/0nPWrKBDz7Y9JDb7c12ufxgWR4sy9kSCTrU09MOoxGkowMTApHHampSN8yYcSdcrjD56KNPL9vvuoD09OxuiEY5QtNWWK0O" +
                    "hMMRimFkf5cvDjqxfz/fOZEVkdusX/+YORCgZ27Z0nrFPtcFZHCwp4FhUojFLsDn84GmQ+AZHrEUg+7udrS29k0YZO3anxaFwylu" +
                    "69Z3rvgOf9JB3G63QaEQ58diMTAMg1isDz6fE6mUgHSaTzz22LNX9W+Jdesem/ncc9uGx1vBSQeRDRJCVD6fr45hYkil5Bczp8mq" +
                    "VY9POC7GEz3W8/8GHjXDczJaMqIAAAAASUVORK5CYII=";
                break;
            case "zulrah_melee.png":
                encoded =
                    "iVBORw0KGgoAAAANSUhEUgAAADIAAAAyCAYAAAAeP4ixAAAPkUlEQVRoQ82ZeXQVVZ7Hv1X1lryskAUSElkFRZBAUILd6FFxoEGU" +
                    "FoFBQVEQbFARGG1FsA+tCK0eFB1XtEXbpd1aBEZcoDWCkc2wLwnZXpb38val6tV6q+rOqerRAQ6SZAxzfOfUeX9U3d/9fe5vud+6" +
                    "xeBX+KMrwYZLig0xbILtz2BL01I4HEwglxf6TF+5Ujuby8yvjaNmTfFqMW4skyOAmqLwdh+BUOEo+NsCyMvNRZrLRR967DH2TL9/" +
                    "dSA7LiqkXqJByKCoGzMDLMvaV4vPDxcThzu9EP369scjj686zfdfFchDSxbT4ZvexZ4eF6AptxCaTuDkHDaIEGh5ZVvV4QXTAM59" +
                    "dZ5+wYjbsObZdT/5f1aQD198MTMQDv9m0cqVX/1/pt7rV4+iX+QUwdAJUqKEWDSCVCKKgqwcfHe8+jRfV96Y+Qc6+J6tf37yyWbL" +
                    "R2blypUOVtNu13XycjKRJLKi+ExKD5gm2Sl7970gMbkY0LMQLqcTSd1cdyKSeLSioiLV1YAb7rgjrYYx5JN1NWiLJpCQJKQUFQ6G" +
                    "QWNbqN3MafeBl+Z3p0oCEGM6EiqLRMYI5GXlwOjec+3a9esf6CqghxffT4mq4eShvdB0HdGEgIDUkN7aCrkjc7QLYhl5457uVNMB" +
                    "ogKpNgOyYMLvvATu7rm44OJhWtbu/8qT6trWPNgav68jk575zPKlSw9qRCtVVQ0NR6tAdB2yogEGc2LHseOXdMRmh0AsQ+8tLqCa" +
                    "oYMYgEYAscUEIzIISD2RKu6NfGKgft/u4vcjkr8jE5/6zH3z7qKcwwlNVdFSfQgJUYIkKYBJX6mq9y7oiL0Og2xZVnCQmCglBCCG" +
                    "Cc0wofoBPQ6wDEWVewRK+vZHdnG/vg+sWNG0/a3ivOtm+6IdcWLurJlU1w07Ek01R2DoJkRNgyqTm076/Z92xEaHQb55vDCk62YB" +
                    "MSgUq0+YABUAQ7cWDjAocIgUQOnWD4PLL1d7e170FuRy3b6tXdAzGo/jvtEfYvX2Cbh1WGj8bxdtOa0bzpgyhUqyBDERRTIWgaSq" +
                    "4EUFreFYh/3r8IP7niqU5EZ4YPzv+tA4YOoUmtcETQEMwyBiUPS/l4WgUmgpE1VkNhTVwOXFm+FKp+gXU/BMzVhz1JHvH/h7ZpG/" +
                    "orLyg6nXX/+Aw+1+urXhOGRZhWlQENNApjMtubumttsvjshzq1ffRoG3dENnjlR+DY5h4XG6wLGs7bT1z7EcHA4O2TDRW06BO3IA" +
                    "JbM5JCQTGzanIZJTglcmeHE0TPDaoQsx9opMhJvScaKpFdNmzcEd99xrL+afFy18nfe1zPVGwhBECYKkWHIECT5lHmjwcu3BtBuR" +
                    "ldOmuZqzsvJNQTAHBLe3eUDhYhk4WIBjAb0V0CRAZVhI3fKQKfL4oagPFDOCgd36oSmVAkwGVbW1mDVzNrxHf8Dh+kYsnTsfQZXg" +
                    "288+hdvteXTgyPI5mqb10/1eNEUjUDXNbsFJUUJCkAaGeb7uXDDtgpw5eP2C7jSD48A0MOAYwMFYKQVAAuJqbxwbOgCbN3+DTbkO" +
                    "mEUM5ramY8iQYWDcaRAlGX6/D4loCBMn3ojCjW/DxQJfFV8IjmHQZ9hIqKqKeFMNArE4UpKKlCJhUj9xH2OYTz21M/Xxz8F0GsQy" +
                    "9M6ivIQnjc1Jd1OkSRzQxNgw9mUCRRc4sH5HBvpFQqgePw2EEPgb6zGoRy4Mw4SYloGCwl7o98lf0Y1zIosF3uh1EYouHgrFKvRU" +
                    "CkcO7APRVUiqgew0F3I8mXA4OVQcOmYpX9pl6nfjQwU0Iw3IcHNgD/0LgppAwmNA2M1CIRpYcKgaPw2Hjh7F5WUj4HQ4IYgpWKsX" +
                    "DQWQx+jgXA7ceGgfnH1NvEouhp6TD0VR4WusgW7o8Lhc0DQdmenpoJQiKYgIJYU5LeHwhlNh/k8RsQxs+Y9e+d1yzXAaAYwTgH4C" +
                    "yDIYSIaBbUPKoHkyUVVXh99Nno5jJ6oRikQgxUMYUlQIXyyKQDyJtLxCDKvZj9xxE5ErpbBfVBAwWKS73L17u5jmpCQiJCRBNALF" +
                    "MJBICBA1BYJC0BqMdJ2M3/dUITX2Aewx4OCQMgQ92fCGQ2iNxcCEQhgk8ng+lGKe/8saWlNXh/zcPLisTpRIQCMEfbdtRC+YdkcP" +
                    "DxoMobAEjYEg/esXX7N/XHQfVYkO3dcIXyKGJJ+CqGh2zWiaiTp/oOtAHvv9lWadzDGt4Qh4ScYtchy9OBb6/6Swlci3N0SZZbdM" +
                    "pVpuT8iyDFGS7Oj0cOhwOR249tgBlDgdyLiQwZb0EeDhwNqPtjC2iCS6XfzWe8n+XTuh6gZYUBiUIsedDifYSytOnDhqy/j2+vOZ" +
                    "91ctX76YF/hnk7zQEotH370oWPFAD3COPBeL7hqH7gyHbJ2BkDLRRkwcG3OdCUrZaHp3GG1NaAgGrFUHSYq4adw1cMSiuL6lBj1H" +
                    "c1ANinfc1yGkMh/0yMv7dwsgFo/DwXFI52N2SopWV+MFuF1usAyDrw8csRk6DfJz4O8tKfi4Zzf25sIcFtmHWPh2G2hQdNReORbp" +
                    "LhdkTcOe2lp7wvpgFMuRQouigZ0wGdfu2ob0yzjkZACf9LkPix5exiyaP/9dWVFulWQZvQp7QtMIiM+L1kTM7oKxpAiiE/CSUlXn" +
                    "D1zWZSAWYOXqQtrDwyLjLRYbBg3DXn8Y/YeORFo8iIZwCDsP16CwIBdrjDicLAvRKmBCEfzdJFwtfImcy1lsq/P4Fr5QX2LZmzlz" +
                    "ZrYuSeN7lxR/aINIEvzemn/tNYJkC8uYIMAXitlbWZf9NmzYkFa/fau8q/oETrYGgpwn0nfpxDmyrBLsb2jAgOGjEK47grl1R9Gj" +
                    "iIUQNBE3gG9HX426owdw900JjFkePM2nZUuW1Os66W+BaJYi5mM4ebIG8ZQAhegguonmQLjrQNatfuKHyt17ynbs2ZMVDAZFa3U+" +
                    "/NMf6Nh/a8N77+ejORRBtc+H2ayK0mQU5t1ASjEQeNXAd6OuQt8jVSiYIF055cnwd6e9q9x1V1glJN+CsDZLjxRHUygMxQYjSEoS" +
                    "alsDvxzkmSVLPCfj8fpX3nyz16kOPHbbjEX3Tq18TjQNfPhlObxtCjwuNwoyPPjtnm9gzAJSsgFRBXY3XQ7R5PDyx1vOmiGzpk+n" +
                    "hBDau6SY0a06iUahWI0gKUBUNWSwruJflFoPL1686i/r1q04W24unDxem3flQecbO8vs1twWTyDO8xg3cjhkUcb4S/f0Hv9ErMUa" +
                    "u/rRFdRqy0+sfeas/ky74YaDAwb0L7XaMYlH4PM12aJS0XVoKrEFyy8COVdxTZs2jcuWonqUTyGa5KGoBCGeR3p2LiaNH4+nn3/+" +
                    "p7lXr1jxziOrVs36OXvLly41iE5YzQLRNIQbjkOSFcSsgldVUIOeP5DnFs6lvXIPY9Nej61k/ZEoTMrg2mvHvfDShg2dOqS4d948" +
                    "aug6DNPSAAz4QAt8bX77gELSZCRF9fyACO+XUMEJRJsNfFw1Bv5oDCf9fkiKhvGTfm+sWrvW0ZlWOWfmrdSKhq7rYJMhWwapKoGk" +
                    "KhBkAgq64byk1u5HL6KeJA+ZmPgsOhyBlGEXZn04jCGlI/HuRx91at7pkyfrgwYO9Oo6GWD4mmyA+mAAoqwgnOBR3ez/5V3rbCu7" +
                    "fMYkWsgGcTTZDYIooykahmLt4hnZ6FXUS9r0+ecZnYnInx58kFq7uBUR09+E1njM1l9xQbSPjfbW1J8fkAWTx1FZI3aR86Jk57Iv" +
                    "nkDPwmKUZGVlb66sFDoKsmThQupxu+36sLqWGWiBPxaFZlqFT6BqBlTVGNKpEHd08mfvvoNWNTbYbVdWVLSGw7YeHlU2Ov9vGzd2" +
                    "6Kzrx7nmz76dWoOt8WluN0xZhL+5zlbCQkoEx1iHH46uL/b1i+fQK/M+R0XjUDSmMuzNqykUQkpWMeiSYfho8+bOLB5z69SppgXF" +
                    "MpBj4YAnFg7C5XDAME17D1FNE+mcc29njHYoIG1rSmiozYCgmdgcu8IWdQlBhDccRn5JX3zxz392Zk5m+dKlrxs6cZuBVtkwCQRF" +
                    "xf66+rssVSwRAw7Koq6tCyTKmXTVs4uouM8EbzL4x6ARiAkpNIVDaIvFUVTSF5V7O7Z4Dy9e9JqDdVDdNDnDUE3T1wJCgUA8hoiQ" +
                    "KmuLhIcrmsEqqoK2GN/1xb5i+iQ6pP4kKgr7IJmS0BQOg5clROIJDB5+maeiokJpL7T3zJ07A8BYl9NJHU6GUS2CYCsCsRgIdEiK" +
                    "np/khd6+eHJoSyDk7tIXqx+du3PCNS+BYRYIkoRALGHncYDnkZuZFbloaOmnH2zcOP9sxzmnwt15yy2vMQ5QhmEYl9Pyk4XYXAdB" +
                    "k0EsKJ0gIohlu0/UjvxxXGfytb2FtO//573z1h9vaZkViMc8KVlBKM4rHqfzGNENpteAgfudLFf5ydatb/6csZtvuGGNSbTBrrS0" +
                    "ZpblwLCsM83tQry1zmFFxiAEuqnDxTrLPt934PyBvLxwdlV5+EtscV+FcEqwN62WcAgaMeDJ61mVnpaW+mz79qVnA3nikUfKoOs5" +
                    "1ScOlcZESY1GQoC9f5jQNMp2y3KXGqbJ6MREQbdMx2d7Dt55XiLiva3ke+o03NHvDUTTs/B5n8GQNAWSQqiQSkWaRNmfm9Pd+Lqy" +
                    "ct5ZQJiXlszp18blFujWR5igF4Zh0B3VJ0uJpjssGJlINMPlKTMBJj8rC18fPPqTnS5Nrcdun/702MaKvllRtp9kmvhk2G/kWIqv" +
                    "DidSaA4FkV/U+51tO3fuaK9Glt1/fzlp9aI1FoVMdIQi0YKYkrzA+lrm5EBhciPz0nMqv6+u/ilFuxTkj1NuGF1atWturaZALL0M" +
                    "cZazRV1zKISEKJsc63yztLTUzM7Lxoa3399zrqK7bdyYcslSvASQdQmyrFBvW3ikk3WCZZmyOn/wtKh2KYjl2OKpN04xBX5CEix4" +
                    "aw8Jhr06pTFeTCWyPZkNWT16uPLz8xObtm490l73uPma0eVWmvGyTYPGSKRMNZRUW5R/+8yxXQ5iTbDi1inX1QYCAwNxHsQg4FN8" +
                    "0M25fdY9p9OJ3cdrzxmNU52cOHpYuRUVnpexu/bnx50XkHXz7yyXjlchUdSfRnkebckkI0syCIjt43eHOw4y+aorLuUTYvo3hw+f" +
                    "E/68gLwxY3I5+e4bBMdcY3/tj/KyfSpopUmQ5/FV1bmdOjUik6+44tKkKcYr9hxuPVcqnheQLwfmlXtlgiAhcEycjBjPg+iAQojx" +
                    "6tZtP7RXG2eCbNq1q916Oi8gliNv9M0u53VAIgTyuIl4/O1/7G2v7XYG8Mxn/xts3q8hGkqzWAAAAABJRU5ErkJggg==";
                break;
            case "zulrah_magic.png":
                encoded =
                    "iVBORw0KGgoAAAANSUhEUgAAADIAAAAyCAYAAAAeP4ixAAAPX0lEQVRoQ81ZCXQUVdb+qrp6X5LOvpHEJASExAUcAVFRAQcYXBCU" +
                    "UfkHlxEHcUME56ijkREBARWIIMiAKIsTBERlGRkNu2wJm4StA1k6JJ2t01t1Vdfy/lPVJgIGaDTM8Z3TJznd7917v7u9e++j8Dtc" +
                    "BYTQ+5t/kEqaAsiO0+FI4U6AYeo8ocwMFDwUak9k6veG47bmbdPLK5omySwPEgohdKwOYmULhMYGaKOiAK2O+N59h75Q7t8bECpp" +
                    "d5EsVZyC5A6C3VcPiqZAUTRkVy10RgaCxgAmJQ2+mdPOk/13BcTyzPPE0D8VWcVHceREPSRRBM0wAEXBOKjrPPdL749DQQGdVbZX" +
                    "ctmvQWDBh23ytw+EEDqjZlOvyrTBP/wvXc++YDrp/80OfBMMgQv4Abcb8HuRNvJOOGd9dp6sMSunPCHtrC32FBaeUWRUf0zZvzRd" +
                    "puhSqabRIlU3BaUAd0p0uuaSLw8tYs2SjopPgNagB5MUvZg9cWY89uzxdihAQuj4L9ZJvu82g9taDPh9ACGqJbQWI4RjJy/rOZfd" +
                    "MNj6T4cSWXoQuDTV2p2dD3eizRbKmJO7IrBo/qMdAShueRFRBJGDLJpmvQtIIsCygM2mR1lZu1nqVwX7n2zTHZIsApBgoxmIsp9s" +
                    "ztzZyWcV9eb868HckBdj6Z88tKbLsM9+DTD7vIWENhghA3DPmBoGIklIfbDfsZppi7tFQvOyFmklcm/0LAeBDJlIkGUZBkoDs8zj" +
                    "aGx51IGEY3GJDw6CKU2fdOaR11zKmeStH15b22/csUiEsL83h6j7jEa457wfdi0ACQN7zKtfvG5cJDQiBvJq/OpvRIhdVSCSCImI" +
                    "CEAECxY6EHx6zfIsKimJsvTslWUanLugtu/Iu7v6d+Uft9xy5HKCJO3/lEAQQUIiXI9OVmNDWdF5acNaNv7w5eXOtwV7JBsnJ3y7" +
                    "n8hStERJ8EOEBICAQEBQDUxFpfusW+wlqSdjoh8ZAsrMctqUDAe7w5mHhgbc8u4j2P7sv/CHvw4Zte3WB5afyzOrvCiK40hL0xv/" +
                    "Ar+3zYgSqquZSGS7IiCzEnf/6IFoUPw4nOwo8HIABjCIpk1t/A7JZ7BpqTtb5kOQ6mpBm21gt1Uhevyd4H6oQOpfbofj0Tn19mcH" +
                    "veN6exkvb978kZo5a9aTs73/9rPchMDy5zs4/8xlxkjAXNK1Uiu+7kNAdhFBQO3D0wCKBvSGNtMrLkApHw1gpkxyGpfG0xUevn5O" +
                    "z2jorfCt/B7clh1IKnoL/NEz8C7bCn1+d7CHTwD1tehyaAlOGG5RZYhfsUpsKJytgdcLsH4gFELsmKFgzpwlriVf/aIkudKspTKJ" +
                    "KSiwAk2464OkAzpilDUUQ9FgQEODAFjQkIkAjm62+XTF1Mo0Y/+7wfAehNwB8NVOQJZhZX0g9w2Hf8v3QGMD+i6YgRa5HskjK/zE" +
                    "ZHnz8Iwus0RRhPujQkC5DAUB4DhVaTq9Pj9UXv7jpSwTcbC3EhkbPd+hhwmCVgcKNEQZaJZ80IFBY5egaXuPg8nsho2IXzQZdLwd" +
                    "dcNehLZbHhirTS0CuZoawFWLjLcex/UT7TUxIQMnk+Zsnx3Y8koMwIfg/mQR0NTYJrfxzoFO2qCfE1g4f8bFwFwxEIXQi1Gf7NHp" +
                    "jLEGmKGhjaiQvSBEVrXHEJqs3yrkNP9zCUZsvMexZsSBHMUi3KEDgMmspghzfheANqPfulxcwyedFqP0GXRzjWb5+BZIkgBDNzMa" +
                    "J7zXJjNtM8H8wK2gzAYk1NAGx9y5/JW61kWt+Vz82tMGmGQ9bQGPICg1ARCUjXTbLZ+fZS0hGy9rCbV2enUWt2M/GH0GoNdDCrCg" +
                    "uCCEpkaElFqKpjDJ8XY5Z5eyG0gVildqQRt1OHv3hHAsymEFRT93P6IDIXBVdfA7Kt70bz80+VzhfpVFFAKT4tZZtZqYAxow4MDC" +
                    "BhpWWNEoc2RWl2mZPMMy0ply2J4eB429GVzxcQSPOgCrDWhxAzwHWK0YWHnn6eKeZVlJVGrowcPDmPcb7tPYChf08n6+fDfYABAS" +
                    "AD4YBhUM/ix7dXXHlfFvJe5zBOBBFKWl5uTO61RPXFricQM+L9L9Ge4ugbymzWxh57SlhcR9+CRoWQcYjBAbG0BJEvoVZyKGT+as" +
                    "IUPNqs6r0gLRvP7u3X8s3z7FmA1RAsQQWv69EsTnB3yeMIifikl0JBDLTYN4f6BSpxR4GomW/uh/qsJCEpCnS1N5+kgAMxqH51B3" +
                    "DiSGnGyQYBBUwA/e3QLZrxTQBI+Vjyu3anMQR9GY2nlK5oC6QaEd4/VGShABIQTlxhd5Dr7FC89zc+uo/qANhhGeNz9afUUXYiuV" +
                    "xM0L5wrO5rFSo7tUbvZ83Heu/VkTbTcbNDEwMXZoiQU62oZESoc6uQpzus3NNjBamB7rg+aPvgMaXKqLGCUzgtlJsEt2fpTjZWc6" +
                    "kwhWdJPpTyzO0aenQGzSqCBos2YGrbVMdK9YBnhawhbhOFgfuQt2kxFVBQtUF/vVMXJhFhgZNWOiFrqnzUwMzJoMWKBHnXCCLMxf" +
                    "kmPQ6hCSRMhnq8PHgizuC06ElvM5vu61Lef/Tr7UnCOlu82UDXPXb88+ecPDlO31gg+8bxe8aH9vtlqHyZIEz+oi1W3VxbIwpSdA" +
                    "bzcdd6/fcW2HAVFoPxY9v9yijSc2bR4WZvwjrbHhoN50x10glZUIuuoQV10JPikZQxueRqImz+GXWKrK7NBv6/bftGeqXkGW216+" +
                    "NWl75arSZ/or9GzTZxINKBBRgiQKkL1eBL5aE85koVD4L01DiZcOBZJDdtuqBrzuCVVWKOY/CqczTzd4KNGIIoK1Z2HscwuCB0vx" +
                    "eO1zuFG8yXFKLKM4wUc+vfnznKhaRny+/oXK1+sH5rRZu6CAtgpEQaH2J8pHSRTB4u/Od4iOBJJ6em15y+y1yYHZn1rVDgyA5bkX" +
                    "iKHv7Qh8uhgxC8eh5u6J6CsP5x/gR+gCHDlTLToJH6rEih5rsvNqr3UPcP+h00zXXwLnSml9+RVCGTWi8a6ssezG8o99338HuJvC" +
                    "6ZjnwzHjdP52i8ScWmaTv/pxR8uEadedK0CKY/PLvs/3z1BKE1p0QT+oO5o/2gj7cQljjo+GWwo2QRBbnEIN1vf9NlObHqXxzz9/" +
                    "wNBKz/7OzDMSETOJLMO3cQPg8YSBeNygY20wDeiR/5tcK3btrFFNwyYsa+/6j10xw8vvrbMaB3RBguhHzdINaNl/AkhLh15rwmjH" +
                    "4/ELzz6iFlTJjrXEsvWAdOrJyb/oP6yvvhGE0WCAJKuFJF9VidCB0jDLEAdLbgqiu2V2XNZqD0z8qqmEP3Ia3iWb2n62jrjt/sSu" +
                    "16x1PD2lrTTvVvTu38semjStPRrWlybdC5B1qgvpdGqc+P6zCRCFcAajKMT16Xr1gBgGDSH63rfAs26NWra3rvg54+c1DHspoj68" +
                    "9Yz1mefCPf1PK3isDKLj5Hm4f5NrtadB5buYuQsIbbVA8nrg/mIVEmeOhmvYa6r2Upe+iqbC/x7j1q6JaDqi8igq0tgOHnlYZtnP" +
                    "1H7G3RzOYj+H+DtXBUjsqsUkdLASSmeZMSIfGq8HzVtK4Vy2GZpOmZC2F0fM1zruhSWwWB6DLKkZyrdrZ7jpUm55pahUwHRk+j3X" +
                    "Ook7VhDhhAuaGD3iRT/Kxs9RGVJxdhCdsTv27Cm7mDUv/N741FiitNMac3gu4Nv9A9R2+KespV6KNTW/Pf22G6DTxxNDZjx863aB" +
                    "2/Vj2A2UdUHFGgkY4+gniHp7A2AsFvj37QHxK+U9p35nGdZHeTvpF7GJI2Hausc4cBARZBbi2SZA4MMXlzJ/y7sOMJkQXL0qYr5p" +
                    "u4qMIb6F9a8uhdwSBBFF8IcOAsGAqqC4266DNj+r47OW8cGRxNC7D/wlpRAqTiPhtRGo/9ssFQiVlQvib9GhpESIRDGGp8YuYswm" +
                    "IgXZPJhCCK7Z3FsdEtDn6IECkof22RCxZiJhrOyJ+mAuofkQhBAPJlcHfWYc5Oo6NIz/EIhPAEpLIuJpe/LJmJCI6bTREE0xGl+g" +
                    "dL+SppQajqDe9VRbxuqoEuVCgJbXCwglyyBiCLkPdAfnrEPlvDUInKxWa6JIFWIYNfpjxYiMxSyD0lD+QyXhrsPjA0LBHuC47uoj" +
                    "AUUJqKrSRUw4UgEStywnoZIKMJlRiOU8OP7K/HCGIQSGG25cBK2e5tZ+8eSl6EUtn2rnvz48HRQDNWUZTeCPHQnXWKCVW90G1q9U" +
                    "yVZUV+eqbhupgBHvu+22++PHDVor1jfD/e6/f760NJoGfXaXdbSGnAx+++1F51OtfBJKPxvL73bY+W0nM2G2Uty+vVDrLWUJHBAI" +
                    "9kBNdc/W/R0OxDB46FLNNTGDA9/vjwfHAqLYAIoKt4ad0ksRJG/jwK7KSyomNXV00rZCE3gBMseD/XxPsn99cTIEGeBZ1brRrz16" +
                    "Y8vTU2+6akCMw4aXaFLS4D98GMYe6Yi6pzfqRr8d7uRi40twsHTMxUDoXnzxWsrt7SycrU7R6iSKP3hK9SR1hedbPdT/JQkZLz9E" +
                    "VU6Y/derAsQ2Zdq3cktzLIIhyDwHy4SBoFkWcrMX8n/2nWpcs92B8vLX2wVyxx0MoqMtppSUzoIo0kLZKeXliqCmsisIUUaUrYCu" +
                    "AyFM+uNDUDVl0VNXBUjS8U3TfEUlyVSLJwuiiPRJA5uZsop617ptcK3bDiYra6UmJqaS//LL8ku5lnbMmF7C4bJwKaIs1muEJF37" +
                    "0xlCM3TPpD/1+uRsYdHOqwIkcfOS/MB3juchExgH5CIZLJq/V4rFTYpfy2CYT7Tdu8vKE5uwevWeS8ZJt+tvBtQ3UkAIKiMkAopS" +
                    "g9ualdDDt7WkzRrKdx0e7HFrCnNh1E8UXc2wVVSg6uOvjoKmeYRCNdBqXYiKNWkzUllhw4ZLA1GkU8AILKVUu7eatNjh43qCkBI4" +
                    "nb842+FAFP7MA/febr2nRzf3W59QkCRFkw7IcviFU1lO5151zBjJys29uXdOAnWoSzoJvr9COdfuuipAtEOG9BJ0ZgJXHbQpVsp4" +
                    "fRq8Czf8LEA7Gr2YgPZlk/Ol2iaTd+LsS1rw6gAZ/udeTFoixAonBKcLiQvGqGNSkQ3CO3c1Eb7ZflHNXghIAQKns8r994U/TbH/" +
                    "hxYx/mNyLwQFNUiDJSVIXPmyCkRmhVDDDQ8fiMSjWvcoQNyj3rjsE/dVsYgihHHyO73U9ww2iMSpw1Fh7LNXmX5eCYgr2fv/7gwF" +
                    "/n2wYpUAAAAASUVORK5CYII=";
                break;
            default:
                throw new IllegalArgumentException("Unknown Zulrah image: " + name);
        }
        try (ByteArrayInputStream input = new ByteArrayInputStream(Base64.getDecoder().decode(encoded)))
        {
            synchronized (ImageIO.class)
            {
                BufferedImage image = ImageIO.read(input);
                if (image == null)
                {
                    throw new IllegalStateException("Unable to decode Zulrah image: " + name);
                }
                return image;
            }
        }
        catch (IOException ex)
        {
            throw new IllegalStateException("Unable to decode Zulrah image: " + name, ex);
        }
    }
}
