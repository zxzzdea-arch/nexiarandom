package nexia.nexiarandom;

import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Random pools: /nexia random <pool> [targets]. The server picks the winner (weighted), the client only plays the animation.
 * A won item is handed over when the animation ends (Net.ClaimPacket) or after a timeout, so nobody can lose it.
 */
@Mod.EventBusSubscriber(modid = NexiarandomMod.MODID)
public final class Randoms {
    private Randoms() {}

    /** Permission level needed to start a spin with the command. */
    public static final int PERM = 2;

    /** Permission level needed for the pool screen. */
    public static final int ADMIN = 2;

    public record Result(boolean ok, String text) {}

    public static String normalizeItem(String raw) {
        String t = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (t.isEmpty()) return null;
        if (!t.contains(":")) t = "minecraft:" + t;
        ResourceLocation id = ResourceLocation.tryParse(t);
        if (id == null || !ForgeRegistries.ITEMS.containsKey(id) || ForgeRegistries.ITEMS.getValue(id) == Items.AIR) return null;
        return id.toString();
    }

    /** Pools the mod ships with: { name, "item_id*amount*weight", ... }. Copied into the world once; after that they are edited in game like any other pool. */
    private static final String[][] POOLS = {
        {"lucky", "minecraft:diamond*1*5", "minecraft:emerald*3*15", "minecraft:golden_apple*2*25", "minecraft:iron_ingot*8*55"}
    };

    /** The saved pools of this world (the shipped pools are copied in the first time). */
    private static PoolData data(MinecraftServer server) {
        PoolData d = PoolData.get(server);
        d.seed(POOLS);
        return d;
    }

    public record Entry(String item, int count, int weight) {}

    public static final class Info {
        public final String name;
        public final boolean builtin;
        public final List<String> items;

        public Info(String name, boolean builtin, List<String> items) {
            this.name = name;
            this.builtin = builtin;
            this.items = items;
        }
    }

    private record Pending(UUID player, List<ItemStack> stacks, String shown, int deadline) {}

    private static final Map<Integer, Pending> PENDING = new HashMap<>();
    private static int nextId = 1;
    private static final int TIMEOUT_TICKS = 20 * 20;

    public static Entry parse(String s) {
        try {
            String[] p = s.split("\\*");
            return new Entry(p[0], Integer.parseInt(p[1]), p.length > 2 ? Integer.parseInt(p[2]) : 1);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** @return the stack, or ItemStack.EMPTY when the item does not exist. */
    public static ItemStack stackOf(String id, int count) {
        ResourceLocation rl = ResourceLocation.tryParse(id == null ? "" : id);
        if (rl == null || !ForgeRegistries.ITEMS.containsKey(rl)) return ItemStack.EMPTY;
        Item item = ForgeRegistries.ITEMS.getValue(rl);
        if (item == null || item == Items.AIR) return ItemStack.EMPTY;
        return new ItemStack(item, Math.max(1, count));
    }

    private static String clean(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    }

    public static List<String> find(String name, PoolData data) {
        PoolData.Entry e = data.getPool(name);
        return e == null ? null : new ArrayList<>(e.items);
    }

    public static Set<String> allNames(MinecraftServer server) {
        return new LinkedHashSet<>(data(server).allPools().keySet());
    }

    public static List<Info> snapshot(MinecraftServer server) {
        List<Info> out = new ArrayList<>();
        for (Map.Entry<String, PoolData.Entry> m : data(server).allPools().entrySet()) {
            out.add(new Info(m.getKey(), false, new ArrayList<>(m.getValue().items)));
        }
        return out;
    }

    private static Result ok(String t) {
        return new Result(true, t);
    }

    private static Result fail(String t) {
        return new Result(false, t);
    }

    public static Result add(MinecraftServer s, String rawName, String itemRaw, int count, int weight) {
        String name = clean(rawName);
        if (!name.matches("[a-z0-9_.+-]{1,32}")) return fail("Invalid pool name. Use 1-32 characters: a-z, 0-9, _ . + -");
        if (name.equals("list") || name.equals("gui")) return fail("The names list and gui are reserved. Pick another pool name.");
        if (count < 1 || count > 2304) return fail("Amount must be from 1 to 2304.");
        if (weight < 1 || weight > 100000) return fail("Weight must be from 1 to 100000 (bigger = more likely).");
        String id = normalizeItem(itemRaw);
        if (id == null) return fail("Unknown item: " + (itemRaw == null ? "" : itemRaw.trim()));
        int total = data(s).addPoolItem(name, id + "*" + count + "*" + weight);
        return ok("Pool " + name + ": added " + count + "x " + id + " (weight " + weight + ", " + total + " item" + (total == 1 ? "" : "s") + ")");
    }

    public static Result edit(MinecraftServer s, String rawName, int index, String itemRaw, int count, int weight) {
        String name = clean(rawName);
        if (count < 1 || count > 2304) return fail("Amount must be from 1 to 2304.");
        if (weight < 1 || weight > 100000) return fail("Weight must be from 1 to 100000 (bigger = more likely).");
        String id = normalizeItem(itemRaw);
        if (id == null) return fail("Unknown item: " + (itemRaw == null ? "" : itemRaw.trim()));
        if (!data(s).setPoolItem(name, index, id + "*" + count + "*" + weight)) return fail("That item is not in pool " + name + ".");
        return ok("Pool " + name + ": item " + (index + 1) + " is now " + count + "x " + id + " (weight " + weight + ")");
    }

    public static Result removeItem(MinecraftServer s, String rawName, int index) {
        String name = clean(rawName);
        int left = data(s).removePoolItem(name, index);
        if (left < 0) return fail("That item is not in pool " + name + ".");
        return ok(left == 0 ? "Removed the last item, so pool " + name + " was deleted." : "Removed an item from pool " + name + " (" + left + " left)");
    }

    public static Result removePool(MinecraftServer s, String rawName) {
        String name = clean(rawName);
        if (!data(s).removePool(name)) return fail("No pool named " + name + ".");
        return ok("Removed pool " + name);
    }

    /** Picks a winner for one player and starts the animation on their screen. */
    public static Result start(MinecraftServer server, ServerPlayer target, String rawName) {
        String name = clean(rawName);
        List<String> raw = find(name, data(server));
        if (raw == null) return fail("No random pool named " + name + ".");
        List<String> valid = new ArrayList<>();
        List<Entry> entries = new ArrayList<>();
        int total = 0;
        for (String s : raw) {
            Entry e = parse(s);
            if (e == null || e.weight() < 1 || stackOf(e.item(), e.count()).isEmpty()) continue;
            valid.add(s);
            entries.add(e);
            total += e.weight();
        }
        if (entries.isEmpty()) return fail("Pool " + name + " has no valid items.");

        Random rnd = new Random();
        int pick = rnd.nextInt(total);
        int win = 0;
        for (int i = 0; i < entries.size(); i++) {
            pick -= entries.get(i).weight();
            if (pick < 0) {
                win = i;
                break;
            }
        }
        Entry w = entries.get(win);
        ItemStack proto = stackOf(w.item(), 1);
        List<ItemStack> stacks = new ArrayList<>();
        int left = w.count();
        while (left > 0) {
            int n = Math.min(left, proto.getMaxStackSize());
            stacks.add(new ItemStack(proto.getItem(), n));
            left -= n;
        }
        String shown = w.count() + "x " + proto.getHoverName().getString();

        Connection conn = target.connection.connection;
        boolean hasMod = !NetworkHooks.isVanillaConnection(conn) && (conn.isMemoryConnection() || Net.CHANNEL.isRemotePresent(conn));
        if (!hasMod) {
            give(target, stacks, shown); // player has no mod: no animation possible, give the item right away
            return ok("ok");
        }
        int id = nextId++;
        PENDING.put(id, new Pending(target.getUUID(), stacks, shown, server.getTickCount() + TIMEOUT_TICKS));
        Net.sendSpin(target, id, name, valid, win, rnd.nextLong());
        return ok("ok");
    }

    private static void give(ServerPlayer p, List<ItemStack> stacks, String shown) {
        for (ItemStack stack : stacks) {
            if (!p.getInventory().add(stack)) p.drop(stack, false);
        }
        p.sendSystemMessage(Component.literal("You won " + shown + "!"));
    }

    /** Called when the client finished the animation (or closed the screen). */
    public static void claim(ServerPlayer p, int id) {
        Pending pd = PENDING.get(id);
        if (pd == null || !pd.player().equals(p.getUUID())) return;
        PENDING.remove(id);
        give(p, pd.stacks(), pd.shown());
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || PENDING.isEmpty()) return;
        MinecraftServer server = e.getServer();
        int now = server.getTickCount();
        Iterator<Map.Entry<Integer, Pending>> it = PENDING.entrySet().iterator();
        while (it.hasNext()) {
            Pending pd = it.next().getValue();
            if (pd.deadline() > now) continue;
            it.remove();
            ServerPlayer p = server.getPlayerList().getPlayer(pd.player());
            if (p != null) give(p, pd.stacks(), pd.shown());
        }
    }

    /** A player who leaves during the animation still gets the prize. */
    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        Iterator<Map.Entry<Integer, Pending>> it = PENDING.entrySet().iterator();
        while (it.hasNext()) {
            Pending pd = it.next().getValue();
            if (!pd.player().equals(p.getUUID())) continue;
            it.remove();
            give(p, pd.stacks(), pd.shown());
        }
    }
}
