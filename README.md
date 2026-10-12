# Nexiarandom

Forge 1.20.1 prize roulette mod made with Nexia. No other mod needed.

/nexia random                          open the pool screen (permission 2, mod needed on your client)
/nexia random <pool>                   spin for yourself (permission 2)
/nexia random <pool> <player|@a>       show the roulette to that player / everybody and give each a prize
/nexia random list                     show all pools (permission 2)

In the pool screen press "Pick from inventory", click an item in your own inventory, type a pool name and a weight (bigger = wins more often) and press "Add item".
The server picks the winner; the item is handed over when the wheel stops. Players without the mod get the prize at once, without the animation.

Pools built into the mod:
- lucky: 1x minecraft:diamond (weight 5), 3x minecraft:emerald (weight 15), 2x minecraft:golden_apple (weight 25), 8x minecraft:iron_ingot (weight 55)

Build: push this folder to GitHub (keep .github) or run 'gradle build' with JDK 17 and Gradle 8.8.
