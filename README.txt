OP Menu 1.2.0 (Fabric, Minecraft 1.21.11, client-side)

WHAT IT DOES
  Press G (rebindable under Controls > OP Menu) to open the menu. It has three tabs:
   - Players:    op/deop/kick/ban, gamemodes, heal/feed, teleport, whitelist (applies to the target player)
   - Give Items: search any item, set an amount, add enchantments (any level 1-255), optional Unbreakable
   - Server:     time, weather, difficulty, keepInventory/mobGriefing, whitelist on/off, save-all, seed,
                 plus a box to run any command
  The target dropdown shows player heads. Look at a player before pressing G to preselect them.
  Ban, Kill and Clear inv need a second click to confirm.

BUILD (easiest: GitHub Actions, see the .github/workflows/build.yml file)
  Or locally: JDK 21 + Gradle 9.4.1, then run `gradle build`.
  The jar is build/libs/opgui-1.2.0+1.21.11.jar (NOT the -sources jar).

INSTALL
  Put the jar and Fabric API (for 1.21.11) in your .minecraft/mods folder (Fabric Loader 0.18+).
  Nothing is needed on the server. You just have to be OP there (or have cheats on in singleplayer).
