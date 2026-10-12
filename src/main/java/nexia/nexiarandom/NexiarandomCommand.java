package nexia.nexiarandom;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Collection;
import java.util.List;

/**
 * /nexia random                           open the pool screen (permission 2, needs this mod on your client)
 * /nexia random <pool>                    spin the pool for yourself (permission 2)
 * /nexia random <pool> <players|@a>       show the roulette to those players and give each one a prize (permission 2)
 * /nexia random list                      show all pools (permission 2)
 */
@Mod.EventBusSubscriber(modid = NexiarandomMod.MODID)
public class NexiarandomCommand {

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("nexia")
                .then(Commands.literal("random")
                        .requires(src -> src.hasPermission(Math.min(Randoms.PERM, Randoms.ADMIN)))
                        .executes(ctx -> gui(ctx.getSource()))
                        .then(Commands.literal("list")
                                .requires(src -> src.hasPermission(Randoms.ADMIN))
                                .executes(ctx -> list(ctx.getSource())))
                        .then(Commands.argument("pool", StringArgumentType.word())
                                .requires(src -> src.hasPermission(Randoms.PERM))
                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(Randoms.allNames(ctx.getSource().getServer()), b))
                                .executes(ctx -> spin(ctx.getSource(), StringArgumentType.getString(ctx, "pool"),
                                        List.of(ctx.getSource().getPlayerOrException())))
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(ctx -> spin(ctx.getSource(), StringArgumentType.getString(ctx, "pool"),
                                                EntityArgument.getPlayers(ctx, "targets")))))));
    }

    private static int gui(CommandSourceStack src) throws CommandSyntaxException {
        if (!src.hasPermission(Randoms.ADMIN)) {
            src.sendFailure(Component.literal("You need permission level " + Randoms.ADMIN + " to open the pool screen. Use /nexia random <pool> to spin."));
            return 0;
        }
        ServerPlayer player = src.getPlayerOrException();
        Net.syncPools(player, true, "", false);
        src.sendSuccess(() -> Component.literal("Opening Nexia Random (it needs this mod on your client)."), false);
        return 1;
    }

    private static int spin(CommandSourceStack src, String pool, Collection<ServerPlayer> targets) {
        int n = 0;
        for (ServerPlayer p : targets) {
            Randoms.Result r = Randoms.start(src.getServer(), p, pool);
            if (!r.ok()) {
                src.sendFailure(Component.literal(r.text()));
                return 0;
            }
            n++;
        }
        int count = n;
        src.sendSuccess(() -> Component.literal("Random " + pool + ": started for " + count + " player(s)"), true);
        return n;
    }

    private static int list(CommandSourceStack src) {
        List<Randoms.Info> all = Randoms.snapshot(src.getServer());
        StringBuilder sb = new StringBuilder(all.isEmpty() ? "No pools." : all.size() + " pool(s):");
        for (Randoms.Info pl : all) {
            sb.append("\n").append(pl.name).append(pl.builtin ? " [mod]: " : " [game]: ")
                    .append(String.join(", ", pl.items).replace("*", "x "));
        }
        String text = sb.toString();
        src.sendSuccess(() -> Component.literal(text), false);
        return all.size();
    }
}
