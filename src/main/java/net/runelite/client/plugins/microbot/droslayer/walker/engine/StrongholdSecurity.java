package net.runelite.client.plugins.microbot.droslayer.walker.engine;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.IntUnaryOperator;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.util.Text;

/** Door questions and floor-skipping portals in the Stronghold of Security. */
final class StrongholdSecurity {
    private static final byte[] LEGACY_XOR_KEY = {42, 70, 37, 126, 115, 38, 40, 61, 92, 84, 52, 23, 88, 56, 98, 92, 96};
    private static final Set<Integer> DOOR_OBJECT_IDS = Set.of(17009, 17100, 19206, 19207, 23653, 23654, 23727, 23728);
    private static final Set<String> NORMALIZED_SECURITY_ANSWERS = loadSecurityAnswers();

    static boolean isStrongholdDoor(int objectId) {
        return DOOR_OBJECT_IDS.contains(objectId);
    }

    static List<TransportCatalog.TransportEdge> createPortalTransports() {
        return List.of(
            TransportCatalog.createObjectTransport(new WorldPoint(1863, 5239, 0), new WorldPoint(1914, 5222, 0), "Use", "Portal", 20786),
            TransportCatalog.createObjectTransport(new WorldPoint(2040, 5240, 0), new WorldPoint(2021, 5223, 0), "Use", "Portal", 19005),
            TransportCatalog.createObjectTransport(new WorldPoint(2120, 5257, 0), new WorldPoint(2146, 5287, 0), "Use", "Portal", 23707),
            TransportCatalog.createObjectTransport(new WorldPoint(2364, 5212, 0), new WorldPoint(2341, 5219, 0), "Use", "Portal", 23922)
        );
    }

    static boolean isFloorPortal(int objectId) {
        return objectId == 20786 || objectId == 19005 || objectId == 23707 || objectId == 23922;
    }

    static boolean canUsePortal(int objectId, int combatLevel, IntUnaryOperator varbitReader) {
        switch (objectId) {
            case 19005: return combatLevel >= 51 || varbitReader.applyAsInt(2310) == 1;
            case 20786: return combatLevel >= 26 || varbitReader.applyAsInt(2309) == 1;
            case 23707: return combatLevel >= 76 || varbitReader.applyAsInt(2311) == 1;
            case 23922: return varbitReader.applyAsInt(2312) == 1;
            default: return true;
        }
    }

    /** Return the original option object only when exactly one option is a known answer. */
    static String selectDialogueAnswer(List<String> options) {
        String answer = null;
        for (String option : options) {
            if (NORMALIZED_SECURITY_ANSWERS.contains(normalizeAnswer(option))) {
                if (answer != null) return null;
                answer = option;
            }
        }
        return answer;
    }

    private static Set<String> normalizedAnswerSet(String... answers) {
        Set<String> normalized = new HashSet<>();
        Arrays.stream(answers).map(StrongholdSecurity::normalizeAnswer).forEach(normalized::add);
        return Collections.unmodifiableSet(normalized);
    }

    private static String normalizeAnswer(String answer) {
        return Text.sanitizeMultilineText(answer).replace('\u2018', (char) 39).replace('\u2019', (char) 39).trim().toLowerCase(Locale.ROOT);
    }

    private static Set<String> loadSecurityAnswers() {
        // The original decoder used the platform charset for this decimal key. ASCII-compatible
        // defaults all produce the reviewed literals below. Keep the exact historical fallback
        // for unusual defaults, including its decoding exceptions and initialization order.
        if (!Arrays.equals("2091593700".getBytes(), "2091593700".getBytes(StandardCharsets.US_ASCII))) {
            return legacySecurityAnswers();
        }
        return normalizedAnswerSet(
            "No.",
            "Me.",
            "Nobody.",
            "Talk to any banker.",
            "Nothing, it's a fake.",
            "Delete it - it's a fake!",
            "Don't give them my password.",
            "Report the player for phishing.",
            "Use the Account Recovery system.",
            "No way! I'm reporting you to Jagex!",
            "No, you should never buy an account.",
            "Secure my device and reset my password.",
            "Decline the offer and report that player.",
            "The birthday of a famous person or event.",
            "Only on the Old School RuneScape website.",
            "Read the text and follow the advice given.",
            "Virus scan my device then change my password.",
            "Report the incident and do not click any links.",
            "Don't share your information and report the player.",
            "Set up two-factor authentication with my email provider.",
            "No, you should never allow anyone to level your account.",
            "No, you should never allow anyone to use your account.",
            "Authenticator and two-step login on my registered email.",
            "Through account settings on oldschool.runescape.com.",
            "No way! You'll just take my gold for your own! Reported!",
            "Don't type in my password backwards and report the player.",
            "Don't give them the information and send an 'Abuse report'.",
            "Don't tell them anything and click the 'Report Abuse' button.",
            "Politely tell them no and then use the 'Report Abuse' button.",
            "Politely tell them no, then use the 'Report Abuse' button.",
            "Don't give out your password to anyone. Not even close friends.",
            "Do not visit the website and report the player who messaged you.",
            "Report the stream as a scam. Real Jagex streams have a 'verified' mark.",
            "Two-factor authentication on your account and your registered email.",
            "Nope, you're tricking me into going somewhere dangerous.",
            "It's never used on other websites or accounts.",
            "It's never reused on other websites or accounts."
        );
    }

    private static Set<String> legacySecurityAnswers() {
        return normalizedAnswerSet(
            decodeLegacyAnswer("5okcKUZbGzJsDAYeYVlXFlMp", -1662623441),
            decodeLegacyAnswer("5okcPUZ0G3JsKgZXYTlXLVMp", 420326212),
            decodeLegacyAnswer("5okcfkZoG1tsUQZ1YWBXJFN7dk9MLB9MCCtndScECjBlPB8TFmZDUxow", -1758333447),
            decodeLegacyAnswer("5okcfkZuG15sDAZlYUZXElN2dlJMAh8gCC1nVCcRCiZlMB8MFiRDUBpGZRUBaGtNUgdSLn9XSwAVcQ02ZmAuHQ0UbxQaQxcrSmIdaG8nBERqQVsGVVF1Y04SFEAEXWFsJDcIJ243ExwQeg==", 220544622),
            decodeLegacyAnswer("5okcI0ZvG21sHQZlYSZXF1NMdn5MDx9hCAJncydZCh5lEx9NFnxDTxpmZTYBSWtlUgtSXH9YSwkVbw0rZmwuMw0TbyYaPBdlSn0dbG8nBHBqYlsnVUV1YE4lFC4EKWFhJBkIJG4oEwUQPkBFGExuKA1A", -1958131581),
            decodeLegacyAnswer("5okcJ0Z6G3tsVAZkYTtXL1NqdndMDh9VCDVnUScECjdlEh8tFntDRhprZQwBeGtIUgFSW38jSz4VKw05ZjMuIg0Cbw4aGBceSkcdM28oBHFqR1scVWd1JE47FFoEHGFnJB8IfW4CEy4QK0BeGHRuCA1DbTJRLFB8dFJHLhNeDh1kZw==", 24941880),
            decodeLegacyAnswer("5okcFUZNG3hsCgZiYW5XDlNodkZMBR9PCBBnQSc+CjdlYR9OFhhDJhpmZQIBWGs3Uj5SYn94SwcVSw0LZlwuPg1jb2AaMxcNSlQdS283BHJqWFsfVSF1eE4sFF4EDGFJJCwIeW4SEx8QdkB/GDxuCA12bVRRE1BxdG1HGxMjDgZkMyUeASBpKBk7FT1BLhFHaV0HT2gxUC1ZJg==", -1084170323),
            decodeLegacyAnswer("5okcB0Z0G2tsUwZDYVtXDFN+dmZMGR8tCAlnNicFCh5lAx9IFitDJRo5ZRQBTWt+Ug1SYH9uSysVXQ02ZkkuJg1tb2MaPxceSlodL28WBF9qO1sKVVJ1QU4oFC8EQmFhJDoIOW4iEyMQDEBeGFpuMg1SbVtRPFBIdE1HAxNjDgxkXCUyATdpNBkYFRdBQhE/aQIHS2hdUARZInN2TSIWMw8PbXUiFgtlbG0=", 898951618),
            decodeLegacyAnswer("5okcPUZrG2hsPgZ9YThXFFNQdmFMFx94CCRnYCdRCiVlGh88FgVDJBpaZT8BRmtbUjpSXH9wS3gVVw1bZlUuWg0eb28aQhckSiIdNm8HBHRqUlsKVUV1X05zFG4ENWFFJDgIN25hEwMQc0BHGERuVA0TbUpRLVB/dFxHexNGDixkSyUwATZpOhkpFQdBQRFBaS4HaGhfUCRZVnMqTQIWKw8UbXYiNwtibDIYWRwORiIbNw==", -1279774089),
            decodeLegacyAnswer("5okcHUYvGztsKwYUYURXK1M2dkRMGh9DCFxnZycgCn1lPB8tFh5DIBpbZTQBdGtcUhZSeH9ZS3IVUA0OZk8uOQ0pbzsaFxckSlQdZm8XBBZqT1sKVX11VU4ZFEEEJ2FMJCwIK257EzMQfkBiGDVuXQ1JbVtREVAjdCVHKBMgDllkMCUpARJpEBlEFStBRRFiaQcHGWhAUANZbXNvTQMWcQ8cbWQiLwsRbGEYFRw1Ri0bIWwMBl1helcVUy12VEx+HyQ=", 699170028),
            decodeLegacyAnswer("5okcLkYqG0NsCQZlYWZXKlN0dk9MDh9TCBRnNScvCiFlJx8bFg1DURpPZQ0BVGt7UlhSLn9aS3gVLQ0bZmMuHw1pbxEaLBc5SlYdbm85BF1qMVsVVSZ1a04yFC8EDmF7JDUIJG4UE0MQEkBlGFduCw1obVBRHFAqdHFHNRNEDhlkSSUtAQBpPxkcFQRBWhF/aVQHUGhhUEdZbXNwTTcWew8hbWoiTgsZbGUYABwlRmobYWwoBm5hZVc2U3t2d0wHH1Y=", -1479309055),
            decodeLegacyAnswer("5okcHEZqG2tsFQZ1YW9XUVNxdn5MBh9TCDVnYCcRCjtlCx8XFj9DfhpnZQcBZ2t/UihSUH9ASzMVWA0CZkguMQ1jbzEaOxcWSm0dcW9XBG1qfVteVUt1YU4hFCgEBGFAJAwIM24dEzgQJUBSGFtuBQ11bXlRU1AqdGNHGhN5DltkQSUMAWNpIBk+FSNBfhF2aRsHEWhEUAtZdXMlTR4WTA8dbUsiCAstbBEYXRw3RmYbY2wuBk9hOFcUU3h2c0woH14IFGc7Jw4KIGVmHykWfkN1", 1573376950),
            decodeLegacyAnswer("5okcfkZWGyVsUgZGYU5XUFN0dltMCh8tCCZnZiclCjplBx88FnxDXBpoZSYBHmtZUgJSbH99SwEVWw0oZm0uHA03bxQaBBcDSiUdVG9bBBJqYlsuVXB1YE4mFHIEW2FGJD0IHG4IEzQQDkBYGEhuNg0TbWlRPFBqdCJHEBNFDilkdCVeAWRpchlIFXpBPRFvaTQHc2hQUAlZc3N0TQwWWQ8fbXUiOQsxbGEYORx8RiwbOGwgBkphalcRU252UEwRH10IKGdVJzkKamUCHw4WcUNQGkRlKAF9a0lSDVJ9fylLdw==", -605348821),
            decodeLegacyAnswer("5okcLEYmG1xsUwYeYThXNVNXdnNMBh9RCEpnVSdaChdlOB8oFiVDfBp6ZQQBTGttUl5SVH9FSwUVVw0OZnwuDw10bxUaLxceSlgdPW9QBFVqUFs6VVx1dU4BFEkELmFhJCQIGG4zExgQcUAgGE9uPg1MbXFRPlBudE1HdRMmDhFkSSUdAQNpNxk1FTxBThFkaUIHdmhOUDhZLnNGTSMWXw9DbTIiDwsxbBMYIBwNRi8bOWwIBhJhc1dQU392b0wvH1oIFGd6Jw8Ka2VgHxgWA0N7GmtlEQFDa05SNFJLfylLdw==", 1344218688),
            decodeLegacyAnswer("5okcCEZ8G19sXAZWYT1XVFMpdnFMNx8hCCNnOiceCiFlCB8vFi5DVRpVZTcBGWtEUiZSQH9CSywVbA0OZlMuXw0ob2YaOBclSmUdam80BEFqOVtVVXB1Rk4FFC4EI2F/JCoIfW4dEy4QCkBbGEhuJw1SbTVRMlB2dHhHehNbDi5kbiVaARVpbhkLFSNBUBFjaQcHa2g8UEdZcHNpTQ4WQA9abUsiLgsBbBoYJRwrRi0bZWwmBldhQlcKU0d2fkwpHyAINmd0JwoKPWUfHxEWEENZGm9lUgFia15SOFJ9fylLdw==", -838635275),
            decodeLegacyAnswer("5okcDkZzG3hsEwYXYUpXLFNydi1Mdh9vCChnKCcrCghlFx86FnFDZxp6ZR0BaWs+UgdSan9mSwkVZw0mZlAuCA0MbxQaPRc+SmMdZ28nBEhqf1sCVWV1eE4IFHoEXGFIJFwIGm44EyUQbEBXGE5uEw1IbS5RPVA1dFJHaRNfDipkKyUBASNpahk3FQNBVBF8aSkHbWhxUApZK3NrTQ0Waw8lbV8iIwsXbAcYPRwWRiwbc2wvBl9hS1dOU0V2JkwEHygITmdMJwsKNGUBHzkWZkNwGmVlDQFha1VSJVJcf1BLBQ==", 1144438122),
            decodeLegacyAnswer("5okcN0ZKGzpsCwZWYWFXCFMtdnhMeh8tCFBncScSCiBlAx88FgZDcBpvZR0BHGs/UipSaX9OS38VKw00ZjUuHA1pb2EaMhcGSk8dZW86BENqTlskVXF1fU4CFHsEH2EmJBwIIW40E0EQbEBcGFxuNA1QbUJRB1BvdHFHGhNwDhxkMSVfAR1pbhk4FSpBIhFCaTQHTGheUFRZcHNvTTwWQQ9ZbS4iKgsabDMYIhwlRnAbOGwuBm9hUFcnU2h2fUwiH14ID2c2JwEKY2U3Hx4WE0N4GltlKwFsa2RSJVIrf3tLCRV6DVRmVi4NDRdvYBoOF30=", -1034238049),
            decodeLegacyAnswer("5okcDUYoG3BsUAZFYW5XUlNedmNMAh9uCC1nWycsCihlHR8zFi9DUBpXZRwBSmtrUg1SYn9TSyEVRw0WZnUuJQ1sbwIaJBd8SmIdR28pBE1qOVsdVVx1R04HFFIEW2FoJAcIZW4VExEQCkBhGDpuCA1obTFRUlBNdCVHMxNnDipkUCUaASVpLBkgFWVBehE+aVwHQ2hAUAVZXXNsTQsWQg88bXYiIwtkbD4YPhwYRmobZGwXBhBhcFcCU292R0xzH1YIIWdQJzAKJWU4Hw8WGUNfGkZlEQFZazZSVFJQf0xLHxVwDQhmUS4jDRpvHxoFF3dKOB13b1EEQWpLWzxVLnUv", 2018381876),
            decodeLegacyAnswer("5okcBEZvG2FsMgZpYVtXEVModkVMBh91CFxnMScjCiNlHB9OFhxDYhpjZTwBamtNUgFSb39eSx4VXg0iZk4uIQ0YbzoaBBcnSk8dRW8kBEpqb1sIVUB1J04NFCEEP2FiJB0IPG4SEwUQFUBBGDhuJQ1cbVJROFBrdFxHExNUDjJkbiUqATBpLRlJFQRBTxFEaSMHUmhLUA9ZaHNYTTIWLQ8NbUkiLAscbDcYGhwuRncbc2wKBn1hblcIU0t2RkwIHyEIDmdqJ0cKHWUJHxAWD0NQGjllUwF/azxSXlJgf2ZLOBVuDVxmNS4wDQtvBhoVFyxKLh1DbxIEa2owWyBVYHVWTi4UdQRGYWwkVwg0bhwTBw==", -160277847),
            decodeLegacyAnswer("5okcFkYmG0VsCAYUYV5XUlNMdi1MDh9pCB1nTSc4CgZlHB8nFjFDZxpJZRcBQmtpUi1SUH9zS3sVcw0fZmguWg1ub2caAhc8SnAdNm9IBBRqSlsnVVF1Y04nFFQEF2FbJAgIKG5mE0EQFEB1GDRuLQ1EbUlRU1BidCZHNBNiDgNkTSUbASlpOBlAFTZBeRF1aQQHUWhfUFRZQnNkTRwWbw89bSoiJgsFbBcYQhw3RlUbM2wIBldhXlcrU3J2WUw5H0AIAGdFJ0cKEGUIHxIWHUNXGk5lSgFCa3ZSNlJPfyBLDBVbDQRmYi4sDQNvExoEF31Keh1nb1MEbmpgWydVIHUkTi0UewQcYWQkWAgcbmETPxAWQCIYPm5cDUFtTFE+UGx0Wkd3E10OW2RXJTkBbmlk", 1818649822),
            decodeLegacyAnswer("5okceEZsG09sEwZWYUtXPVNOdkZMMx9ICDVnNyc6CgBlPB9OFjtDQho9ZQwBYWtaUltSV39MSwYVKg0nZkMuOQ0dbwYaEBcvSl0dUW8ABBFqRVsBVVd1WU4SFCgEKmE9JEAIeW42E0cQMkB8GDRuAg1AbWBRMVBxdFZHChNiDjxkSyU4ARFpbxkjFSJBfBFYaVsHdGhfUAZZcHNOTQsWaA81bXAiCws+bCMYMRwKRnwbfmxLBnNhXFcIU1J2T0wMH0gINGc6JzAKCmUVHwoWLUNOGjVlFAEFa0VSXlI1f1pLHxVKDT9mSy5aDRlvExo0FwlKJh1lbzoEF2pvWyFVZXVmTikUVwQJYWckJQgobj8TNhAGQEMYaG4GDUVteFEcUFh0d0cVE14OAmR3JQ8Bbmlk", -359812749),
            decodeLegacyAnswer("5okcPkZLG09sIwZCYVFXFVNNdkRMMx8yCABnYSdfCj9lCx8pFiVDJRo4ZVUBWmtVUhlSKX9OSzAVKg0UZmcuGA0QbzgaGRcdSmYdXG87BEFqTls0VUV1fk45FFQEN2FrJCQIE24yEw4QNUBoGEBuBw1pbUdRRFB/dGFHNBNDDiRkYCUjAXxpbRlAFXlBIRFIaSoHYWheUF5ZcnMoTQAWaA82bUEiEAsnbDQYHhw3Rmobe2wKBlBhYlchUy92fUwGH1MIUWdSJ0cKIWVgHw4WM0NYGmJlXAEea21SGlJcf1ZLfRVsDQtmRi5CDQxvMxokFwNKVh1+bxEEEmpYWyVVI3VnTgUUegQ5YVokPghnbmATXBA+QFkYZm43DW5tb1EuUE8=", 1622277096),
            decodeLegacyAnswer("5okcd0ZOG2ZsNgZ9YXFXDlNkdiFMdB9JCFdnVCcsCmJlYx81FhNDQBpAZRwBSWtqUhZSfn8iSxIVfQ0KZmEuPA1obwUaThd7SkEdTm8XBBBqZFsfVVR1fE4pFFQEKGFpJA0IHW4DEzoQJEBTGEhuBw11bVFRDlBQdD5HIBNhDgRkayUGASVpYRlEFSVBfRF9aV0HD2hSUC9ZUHNtTSsWVw8nbUoiIwsbbBcYABwZRlYbYWwsBn5hZFc3UyR2Z0wqH18IUmd0Jy4KMGUGH04WOkMuGjhlFwFtazZSQ1JXf1ZLLhV1DSlmVi4RDWJvHBpCFzxKfR01bzQEY2pxWyVVY3VaThIULgQ+YWckVggQbhgTDxACQEEYbG5PDUBtblE6UFZ0ZUc2E1QODGRUJR8Bbmlk", 514130973),
            decodeLegacyAnswer("5okcPUZlG0tsKgZlYV5XBFM2dlZMcx9jCDxnRCcmChZlOx8cFg1DPRpnZSkBRWtNUh1SMX8lS38VeA0jZn4uCw0Wb2EaARcgSmUdS28XBH1qTVs0VWR1WU4wFFwECGE5JDgIKG57E0QQckBiGDxuNg1LbTZRBVBZdHZHGxNVDiRkciU8AWJpFBkwFSZBYBFdaUIHcGhiUC1ZYXNaTTQWSw8JbVIiFAsnbCYYExw5RnIbaWwuBgxhWFcIU1F2d0xyH3gIXGdnJwEKeWUXHwoWDUNcGl9lUAF+a05SD1JQf0dLZRUuDQ9mVS4hDXRvHBohF2FKYx1SbxUEH2pEWwtVfnVXThUUTgQjYX8kLQgIbiUTIhAQQGIYRG5Z", -1798746446),
            decodeLegacyAnswer("5okcYEYpGzpsLgZqYT9XNFN1dlBMAh9sCC1nRydQChxlFB8dFihDZBpIZQQBaWtGUiBScn9fS3kVUQ1ZZl0uCg0ybxUaQBcgSicdN29aBGNqWVsqVSR1Jk4aFG0EHGF0JD4IP24DExkQPUAgGHRuAg1VbTFROlAqdH9HMhNZDhlkUyUJATppCBkQFRlBehF0aSAHWmheUAhZaXNbTRIWUw8abXYiBgsebBUYLhw3RlYbc2wIBm9hIlcOU1t2eEx2H0MIDGd2JysKa2UHH1AWDkNRGldlEwF+a3dSClIxfyVLOhV9DTxmTy4FDS9vNBohF39KVR1UbwYERmpiWxdVWHV1TioUSgRYYXokKAgYbmMTMRAQQEMYam4qDUJtYlEsUEN0UEdwE0MOLGRjJSkBbmlk", 318544679),
            decodeLegacyAnswer("5okcfkYwG0ZsNAZ1YVxXUFNMdkZMex8uCFRnUSdaChVlNB9LFhxDeBprZQYBWWs2Ui9SK393Sz8VeQ0eZnYuDw05b3gaQhcLSnAdUG87BHNqfFsKVWJ1d04XFGgEFWFmJAcIOG4nExoQdEAlGGRuTw1QbTNRLVAidGVHExN7DglkNSUZAR1pIxklFQVBeRFOaQMHa2g4UB5ZcHNJTTMWIA8pbVEiMAs8bDgYBhwlRlIbJWxPBlJhXFdTU252TEwhH3oIAmdsJwkKamVjHwcWLUNuGk9lIwF+a0ZSA1JIf25LMBUuDSJmRi4cDS1vARowFwNKRR1ObzQEd2pdWwdVenVVTnMUXAQoYU8kGQgBbhwTHBAoQFIYT24HDXFtMVEGUG10UEcDE0AOLGR0JV8BGGkeGTsVHUFXETE=", -1998543524),
            decodeLegacyAnswer("5okcHUZQG1BsKQZCYXpXK1MqdkBMLh98CClnUSdHCghlMB8QFg5DTho0ZVIBYGtNUidSUX8tSwcVLQ0oZmcuGQ1ub2caJBcoSmYdSG8OBEhqWlsvVVB1fE5sFDMECGFeJBcIC24aE04QdEAgGE5uNg1zbU1RBlBxdEVHBhNlDhNkNyU+ATtpFxkgFTRBLhE1aRoHU2hMUBhZbnMvTQwWcA8fbVQiOAtmbAkYOhwXRlUbP2wOBmlhYlcAUy92TUwIHyEIFWdbJyYKImUBHw0WJUNnGnllBgFjazdSL1JTf2FLJBVJDRRmUC4nDWlvIxpAFz9KIh1tb1EEUmpFWwpVYXUqTiEUVwQcYTwkAgg6bhITFhB2QHIYQW4lDQ1tMlFTUHR0WUcBE2kOKmR9JT4BKWkjGRwVLEFcEWJpKQdhaDVQUQ==", 85258225),
            decodeLegacyAnswer("5okcNUZpG3NsBwZFYV1XLlN1dixMMB9uCE5nTSddCgVlCx81Fh9DTho5ZRwBGmt7UllSYn9dSy0VZg0LZkAuQg0Wbw8aThcPSmQdcG8RBH5qUlsgVV91YE4AFDMEOmFfJAgIHm4DEy0QLEB+GF9uLA1kbTdRRFBydHdHARNkDlxkTyURASppAxleFRZBZhFiaS4HamhKUB5ZdHMrTRIWSA8UbXYiLAs6bB0YBhwBRlQbe2wABndhf1cMU0l2IkxzH08ILmdrJw0KZGUIHy4WJUNFGlxlBwF+az5SPFJbf0dLBRUoDRVmTC4LDTJvfBoYFydKYh1obw8EZmpCWyFVcnVCTncUXQQIYXgkRAggbmATDhAQQF0YZG4dDUptd1ENUF10eUcsEyQOHWRtJR8BCmlqGScVYUF5EUtpAwcYaGBQXllic1JNFRYl", -2093467546),
            decodeLegacyAnswer("5okcHUZoG35sFwZqYURXPFNqdiNMNB8sCB1ncScqCnllIh9IFnlDLxpbZU4BSGtDUgBSeX98SxsVRw0FZm8uHw0Bb2YaThd8SiUdVG8rBEhqb1sqVSp1ak4QFEoED2E0JAIIPW4aEwcQK0BUGFduNA1ybUBRDVAodFlHIRMmDi9kKyUdARxpbhlFFSlBYxFfaVkHeWhbUANZTXNXTSwWXw9HbXIiIgszbCkYPhwHRmYbZGwWBkJhO1cuU1Z2X0wgHysIMGdBJ0cKJmU3HxYWM0NYGnxlBwFKa3hSOVJMf31LPhV0DRhmVy4gDTpvZhoPFzZKeR0zb1AEHmpGW1VVenV0TgoULwQlYT8kRAgnbjYTLhByQF0Yb24ODU1tT1EPUGt0T0cbE2QOQGRTJQ8BFWk7GToVAEF9EXZpKwdraGBQP1lDcy5NFRYl", 959201947),
            decodeLegacyAnswer("5okcf0Z1G3hsVwZmYV5XVlN/dkdMFR9+CDRnVCcSCmtlKx8lFjpDdBpKZSsBGmtNUl1SfH9DSzMVbQ1UZmguLg0qbwUaAxc2SnIdcW81BFRqJ1sEVTx1WE4FFFAEC2FpJFsIK24hEz4QBEBSGDhuDw1MbVVRKVAqdENHKxNwDl9kMiU5ATdpHRkkFT1BXxF1aSYHYmhaUFtZfHNqTQwWIA8gbWMiCwsHbAkYFRx4RlcbRmwhBh5hRFcMUyt2Qkx1HygILWdBJyUKFmUwHx4WcUNBGmZlLwFDa0dSKVI1fydLGRUuDTpmbC5eDQFvERo/FwNKJR0xbwgEV2pNWyJVd3VATgYUawQ+YT4kDggXbgQTTxAoQCAYWm4mDVRtRVFfUHh0eUdzE0MOGGRHJRoBP2kSGR8VN0FPETE=", -1219260624),
            decodeLegacyAnswer("5okcLkZuGztsEAZ+YW1XP1NediVMKh9qCCtnUCcNChhlKx8QFnlDdBpLZSQBb2tLUiFSK39XSykVZg0nZnEuDA0Rb2EaNRc9SnodaW80BGhqOFsHVVd1a04zFCEEL2FaJBsIFW4hExYQDkBbGHluEA0RbWhRWFAudFZHIxMnDhtkYSVdARFpdhlDFTpBUBFCaRcHE2hLUDpZX3NyTQ0WaQ8WbUsiAAsXbCgYAhwuRmobOWxLBkxhP1cpU3V2UkwqH3MIUmc2Jy0Ka2UjHxUWJkN+GmJlUgFEa3hSHFJ7f25LHxVNDSJmfC4wDW9veBpAFxlKJR1LbzEEcmpgWzhVQnV0ThkUeQQlYUokWggabjUTQhAQQGIYYm4wDVBta1ERUGl0WUcjE1QOKmReJTIBMmlvGQAVPUF5EW1pHAdxaHFQWFkpc1hNDxZBD1ttVSItCxs=", 759683493),
            decodeLegacyAnswer("5okcA0Z9G2NsCAZDYURXV1NXdnBMJx99CFNnTycOCjZlPx8SFg1DWhp6ZS0Bf2tMUhZSKX9sS3kVKg0lZlUuEA01by8aXRcnSkYdb29UBGZqRVsuVTh1ZE4lFGsEWWFZJBcIIG4+EwUQD0B2GH9uKA0fbWxRI1BWdExHNRN+DjJkbyU6AT1pIxlEFSRBIRF0aUIHcGhkUCpZYXN2TTwWQA8kbXwiCgsCbGMYMxwZRngbIWxdBlVhZlc1UyV2ckxwHyEIDWd7JyQKBmV6HxAWCENwGm5lAAF5azhSC1J4f01LIRVODTVmdy5bDTpvPxo+FzRKcx11bxoEdGp/W1lVX3VVTgEUIAQFYTskOAgWbj8TJxAXQHAYb243DWxtdFEZUG90e0cHEyEOKmRGJQoBZmkcGSkVHEFwEWhpJAdZaGdQJ1lqc3FNfBZ5Dz9tdyIxCzRsMRg9HBpGIg==", -1418992678),
            decodeLegacyAnswer("5okcPEZTG0hsVwYIYV1XUlNZdlFMOx9jCClnVicgCj9lBx88Fn9DXhphZVUBTWs4Uh5STn9BSyEVdw0gZk4uPg1ibyUaFBcbSngdbm8vBHNqR1sJVVV1V04TFFkEX2F9JB4IFm5nEz4QN0B+GHtuLA1WbVlRPVBOdHtHCxMgDgpkUiUfATFpExkZFShBdRFbaToHWWheUBxZLnNoTW8WaQ8rbVciNwsxbCMYIxx6RnIbIWwvBnVhQVddU2p2eUwLH00IIGd3Jy4KAmUjHxIWLUNXGmtlTgFoa3hSCFJXf2FLABVTDTtmRi4PDTpvGRoBFwdKIR1Cb1EEYmplW0JVXnV8TjkULQQiYWUkIQgjbgETABAXQHwYPG5PDWRtY1EnUH50dkcHE3UOWmRvJS4BMWkeGTYVe0E5EWdpKwcLaH5QL1lXc3JNCxZ0DyVtcCIlCwNsKhgfHD1GdxtHbCcGfmFlVzNTK3YhTC4fQAgKZ2UnBgo5ZQAfEBYEQ0YaXGVYARM=", 564079695),
            decodeLegacyAnswer("5okcJEY0G3tsNgZfYURXU1NLdlJMLx94CE5nZycvCjNlJx8lFgdDQBo6ZRABFmtCUj9SL398Sy4VXQ0jZm8uIg1vbzIaHBccSkUdYW8pBGlqf1s0VSF1Jk43FFsEGWE0JF0IP24cEw0QJkB5GGduXQ0QbTBREVBedFFHOhNoDilkTSUwASRpDhkFFTlBIRFjaTUHdmhbUDZZLHNXTWsWKw8EbSoiDAsybGgYLhwsRn0bJWwHBmthalcfU3Z2fUwzH3EIDGdnJxIKIWUrHxMWP0N/GkxlKAFHa19SI1JQf05LYRV8DThmNC4eDTJvZhoMFwNKcR08bxMEYmpcWyRVfHV6Ti8UdARZYVokXAgbbhYTOhAXQFYYYm4eDWFtSlE6UEx0Wkc0E0QOGWRBJVoBO2keGRUVd0FEEX1pDgdTaERQFllBc1JNCxZQDyhtbyIICxpsGBgaHHZGSxtObFIGdGF6VyFTTXZNTAgffggSZz4nVQ==", -545032476),
            decodeLegacyAnswer("5okcB0ZcGz9sAwZjYWJXXVMudmNMOx9XCFJndycCCjplZx8XFj5DPRp0ZRcBR2tZUgBSSn98SzkVcw00ZkEuKg1ibwUaPxcbSkMdQm8GBGJqbFsbVXh1R04nFH0EO2FFJCgIPG4zEwYQDkByGHRuFQ1DbWhRD1BfdHhHcBN0Dj1kaiUgARxpchkmFXtBWhE0aRUHR2hgUBhZTnMkTQMWQg9ebVciAAtgbAYYRBxkRn0bM2wLBh5hUFcsU1N2YEwIH1oINmdKJzwKMWUyHw8WekNfGkFlNQF3a39SKlJ9f2JLJxVJDSRmfi4/DWlvLxoCFxlKZB1IbwgEampdWzlVUnUhTgYUYQQfYUAkKghnbhwTGBAVQCMYd24PDVJtWVEmUEx0b0cJE0AODmRwJSkBbmlk", 1438024473),
            decodeLegacyAnswer("5okcH0ZoG1BsXQZfYU9XEFNRdkVMbB9JCBxnTCcJCgtlIR8zFjtDfBpMZQMBdms9Ui1SV38mSx4VUQ0fZlAuKw0/bz0aLxcKSi8dRW8vBGtqTFsGVWp1dk4tFG8EVWFfJCAIBm4zEyQQCkB2GFxuLg1FbS5RQFB4dCNHJhN7Dh9kYyULAWtpchkyFQNBPRFBaScHVmh7UCRZLHN/TRwWLA9UbXIiCgsYbAAYJxw2RlEbZWwOBkJhO1crUy52cEwrHy4IH2drJy8KZ2UBH0sWPUNFGjhlNwFra2VSIFItf1VLORVHDQlmdy4CDRdvHBoVF3lKZx1nb1sEGg==", -740635250),
            decodeLegacyAnswer("5okcHkZyG2VsKQZNYVtXJlMydlxMBh9tCEpnUCcFCjplZh85FgFDORomZTABGGtpUjtSWH9BSw8Vaw0lZkAuCA0vbyMaRRc7SkMdPG9XBHVqWVtcVXB1Ik4yFHIEX2F6JCUIAW4UEy0QJUBFGD5uAQ1LbWhRM1BCdEdHcRNrDl1kRiVRATFpKhk6FXZBXhE/aQAHd2huUDlZeXNyTSMWLQ8ObUoiAgsebDEYMxw3RkwbYWxXBkJhRVcWU2d2dEx0H3cIJmd6JysKY2UJHwUWI0N8GmhlHQF5azdSX1Isf2JLAhVODUdmUy44DR9vPhovFzxKch1gbyUEHmoxWz1VdXVx", 1238242851)
        );
    }

    private static String decodeLegacyAnswer(String encodedBytes, int seed) {
        return decodeLegacyInner(decodeLegacyOuter(Base64.getDecoder().decode(encodedBytes), 2091593700), seed);
    }

    /** Historical in-place XOR layer; default-charset and two-write behavior are deliberate. */
    public static String decodeLegacyOuter(byte[] payload, int seed) {
        byte[] numericKey = Integer.toString(seed).getBytes();
        for (int index = 0; index < payload.length; index++) {
            payload[index] = (byte) (payload[index] ^ numericKey[index % numericKey.length]);
            payload[index] = (byte) (payload[index] ^ LEGACY_XOR_KEY[index % LEGACY_XOR_KEY.length]);
        }
        return new String(payload, StandardCharsets.UTF_16);
    }

    /** Historical Base64/LCG layer, including Java int overflow and interned result identity. */
    private static String decodeLegacyInner(String encoded, int seed) {
        byte[] payload = Base64.getDecoder().decode(encoded);
        char[] decoded = new char[payload.length >>> 1];
        for (int index = 0; index < decoded.length; index++) {
            seed = seed * 1664525 + 1013904223;
            decoded[index] = (char) ((((payload[index << 1] & 255) << 8)
                | (payload[(index << 1) + 1] & 255)) ^ (seed >>> 16));
        }
        return new String(decoded).intern();
    }
}
