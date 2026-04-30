![image](https://github.com/user-attachments/assets/7c08e053-c84f-41f8-bc97-f55130100419)

<a href="https://www.paypal.com/paypalme/MicrobotBE?country.x=BE" target="_blank">
  <img src="https://img.shields.io/badge/Donate-%E2%9D%A4-ff69b4?style=for-the-badge">
</a>
<a href="https://www.youtube.com/@themicrobot" target="_blank">
  <img src="https://img.shields.io/badge/YouTube-Subscribe-FF0000?style=for-the-badge&logo=youtube&logoColor=white">
</a>
<a href="https://themicrobot.com" target="_blank">
  <img src="https://img.shields.io/badge/Microbot-Website-0A66C2?style=for-the-badge&logo=google-chrome&logoColor=white">
</a>

# Microbot
Microbot is a fun Old School RuneScape side project built on RuneLite. It focuses on learning and sharing automation scripts, not enterprise software.

- Core plugin: `runelite-client/src/main/java/net/runelite/client/plugins/microbot`
- Queryable API docs: `runelite-client/src/main/java/net/runelite/client/plugins/microbot/api/QUERYABLE_API.md` (quick read: `api/README.md`)
- Helpers/utilities: `microbot/util` inside the plugin tree

## Installing & Running
- Download shaded releases from the GitHub releases page (see `docs/installation.md` for step‑by‑step and launcher notes).
- Linux/macOS/Windows: run the shaded JAR with Java 17+ (`java -jar client-<version>-SNAPSHOT-shaded.jar`), or swap it into RuneLite/Bolt as described in `docs/installation.md`.
- Stuck? Join the Discord below.

## Building from Source
- Quick compile: `./gradlew :runelite-client:compileJava`
- Full build: `./gradlew build`
- Main sources are included builds defined in `settings.gradle.kts` (cache, runelite-api, runelite-client, runelite-jshell).
- Development setup guide: `docs/development.md`

## Developing Scripts
- New scripts belong in the microbot plugin folder: `runelite-client/src/main/java/net/runelite/client/plugins/microbot`.
- Share reusable helpers under `microbot/util`.
- Use the Queryable API caches via `Microbot.getRs2XxxCache().query()`; do not instantiate caches/queryables directly. See `api/QUERYABLE_API.md` and examples under `api/*/*ApiExample.java`.
- Example scripts live in `runelite-client/src/main/java/net/runelite/client/plugins/microbot/example/`.

## Discord
[![Discord Banner 1](https://discord.com/api/guilds/1087718903985221642/widget.png?style=banner1)](https://discord.gg/zaGrfqFEWE)

If you have any questions, please join our [Discord](https://discord.gg/zaGrfqFEWE) server. 
