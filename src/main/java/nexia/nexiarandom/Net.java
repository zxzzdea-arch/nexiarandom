package nexia.nexiarandom;

import nexia.nexiarandom.client.ClientHooks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/** Packets for the pool screen and the roulette. The server decides everything: it checks the permission on every action. */
public final class Net {
    private Net() {}

    private static final String VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(NexiarandomMod.MODID, "main"), () -> VERSION,
            NetworkRegistry.acceptMissingOr(VERSION), NetworkRegistry.acceptMissingOr(VERSION));
    private static boolean done;

    public static void init() {
        if (done) return;
        done = true;
        CHANNEL.registerMessage(0, PoolSyncPacket.class, PoolSyncPacket::encode, PoolSyncPacket::decode, PoolSyncPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(1, PoolActionPacket.class, PoolActionPacket::encode, PoolActionPacket::decode, PoolActionPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(2, SpinPacket.class, SpinPacket::encode, SpinPacket::decode, SpinPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(3, ClaimPacket.class, ClaimPacket::encode, ClaimPacket::decode, ClaimPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }

    /** Sends the pools to one admin. open = true opens the screen, false only refreshes it. */
    public static void syncPools(ServerPlayer player, boolean open, String status, boolean error) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new PoolSyncPacket(Randoms.snapshot(server), open, status, error));
    }

    /** Starts the roulette animation on one player's screen. */
    public static void sendSpin(ServerPlayer player, int id, String title, List<String> entries, int winner, long seed) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new SpinPacket(id, title, entries, winner, seed));
    }

    /** Server to client: the random pools. */
    public static final class PoolSyncPacket {
        final List<Randoms.Info> infos;
        final boolean open;
        final String status;
        final boolean error;

        PoolSyncPacket(List<Randoms.Info> infos, boolean open, String status, boolean error) {
            this.infos = infos;
            this.open = open;
            this.status = status;
            this.error = error;
        }

        static void encode(PoolSyncPacket m, FriendlyByteBuf b) {
            b.writeVarInt(m.infos.size());
            for (Randoms.Info i : m.infos) {
                b.writeUtf(i.name, 64);
                b.writeBoolean(i.builtin);
                b.writeVarInt(i.items.size());
                for (String s : i.items) b.writeUtf(s, 160);
            }
            b.writeBoolean(m.open);
            b.writeUtf(m.status, 300);
            b.writeBoolean(m.error);
        }

        static PoolSyncPacket decode(FriendlyByteBuf b) {
            int n = b.readVarInt();
            List<Randoms.Info> list = new ArrayList<>();
            for (int k = 0; k < n; k++) {
                String name = b.readUtf(64);
                boolean builtin = b.readBoolean();
                int c = b.readVarInt();
                List<String> items = new ArrayList<>();
                for (int j = 0; j < c; j++) items.add(b.readUtf(160));
                list.add(new Randoms.Info(name, builtin, items));
            }
            boolean open = b.readBoolean();
            String status = b.readUtf(300);
            boolean error = b.readBoolean();
            return new PoolSyncPacket(list, open, status, error);
        }

        static void handle(PoolSyncPacket m, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context c = ctx.get();
            c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> ClientHooks.poolSync(m.infos, m.open, m.status, m.error)));
            c.setPacketHandled(true);
        }
    }

    /** Client to server: one pool action. op = add, rmitem, remove or spin. */
    public static final class PoolActionPacket {
        final String op;
        final String name;
        final String text;
        final int number;
        final int weight;

        public PoolActionPacket(String op, String name, String text, int number, int weight) {
            this.op = op;
            this.name = name;
            this.text = text;
            this.number = number;
            this.weight = weight;
        }

        static void encode(PoolActionPacket m, FriendlyByteBuf b) {
            b.writeUtf(m.op, 16);
            b.writeUtf(m.name, 64);
            b.writeUtf(m.text, 128);
            b.writeVarInt(m.number);
            b.writeVarInt(m.weight);
        }

        static PoolActionPacket decode(FriendlyByteBuf b) {
            return new PoolActionPacket(b.readUtf(16), b.readUtf(64), b.readUtf(128), b.readVarInt(), b.readVarInt());
        }

        static void handle(PoolActionPacket m, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context c = ctx.get();
            c.enqueueWork(() -> {
                ServerPlayer p = c.getSender();
                if (p == null || p.getServer() == null || !p.hasPermissions(Randoms.ADMIN)) return;
                MinecraftServer s = p.getServer();
                Randoms.Result r = switch (m.op) {
                    case "add" -> Randoms.add(s, m.name, m.text, m.number, m.weight);
                    case "rmitem" -> Randoms.removeItem(s, m.name, m.number);
                    case "remove" -> Randoms.removePool(s, m.name);
                    case "spin" -> {
                        Randoms.Result sr = Randoms.start(s, p, m.name);
                        yield sr.ok() ? new Randoms.Result(true, "Spinning " + m.name + "... the prize arrives when the wheel stops.") : sr;
                    }
                    default -> new Randoms.Result(false, "Unknown action.");
                };
                syncPools(p, false, r.text(), !r.ok());
            });
            c.setPacketHandled(true);
        }
    }

    /** Server to client: start the roulette. The winner is already decided; the client only shows it. */
    public static final class SpinPacket {
        final int id;
        final String title;
        final List<String> entries;
        final int winner;
        final long seed;

        SpinPacket(int id, String title, List<String> entries, int winner, long seed) {
            this.id = id;
            this.title = title;
            this.entries = entries;
            this.winner = winner;
            this.seed = seed;
        }

        static void encode(SpinPacket m, FriendlyByteBuf b) {
            b.writeVarInt(m.id);
            b.writeUtf(m.title, 64);
            b.writeVarInt(m.entries.size());
            for (String s : m.entries) b.writeUtf(s, 160);
            b.writeVarInt(m.winner);
            b.writeLong(m.seed);
        }

        static SpinPacket decode(FriendlyByteBuf b) {
            int id = b.readVarInt();
            String title = b.readUtf(64);
            int n = b.readVarInt();
            List<String> list = new ArrayList<>();
            for (int k = 0; k < n; k++) list.add(b.readUtf(160));
            int winner = b.readVarInt();
            long seed = b.readLong();
            return new SpinPacket(id, title, list, winner, seed);
        }

        static void handle(SpinPacket m, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context c = ctx.get();
            c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> ClientHooks.spin(m.id, m.title, m.entries, m.winner, m.seed)));
            c.setPacketHandled(true);
        }
    }

    /** Client to server: the animation ended, hand over the prize. The server only gives what it stored for this player. */
    public static final class ClaimPacket {
        final int id;

        public ClaimPacket(int id) {
            this.id = id;
        }

        static void encode(ClaimPacket m, FriendlyByteBuf b) {
            b.writeVarInt(m.id);
        }

        static ClaimPacket decode(FriendlyByteBuf b) {
            return new ClaimPacket(b.readVarInt());
        }

        static void handle(ClaimPacket m, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context c = ctx.get();
            c.enqueueWork(() -> {
                ServerPlayer p = c.getSender();
                if (p != null) Randoms.claim(p, m.id);
            });
            c.setPacketHandled(true);
        }
    }
}
