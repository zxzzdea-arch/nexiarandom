package nexia.nexiarandom;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** World save data (data/nexiarandom_pools.dat): every pool. Items are stored as "item_id*amount*weight". The pools the mod ships with are copied in once (seed), so they can be edited like the rest. */
public class PoolData extends SavedData {
    private static final String NAME = "nexiarandom_pools";

    public static final class Entry {
        public final List<String> items = new ArrayList<>();
    }

    private final Map<String, Entry> pools = new TreeMap<>();
    /** Names of the shipped pools that were already copied into this world (so a deleted one does not come back). */
    private final Set<String> seeded = new LinkedHashSet<>();

    public static PoolData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(PoolData::load, PoolData::new, NAME);
    }

    public static PoolData load(CompoundTag tag) {
        PoolData d = new PoolData();
        ListTag pl = tag.getList("pools", Tag.TAG_COMPOUND);
        for (int i = 0; i < pl.size(); i++) {
            CompoundTag c = pl.getCompound(i);
            Entry e = new Entry();
            ListTag items = c.getList("items", Tag.TAG_STRING);
            for (int k = 0; k < items.size(); k++) e.items.add(items.getString(k));
            d.pools.put(c.getString("name"), e);
        }
        ListTag sl = tag.getList("seeded", Tag.TAG_STRING);
        for (int i = 0; i < sl.size(); i++) d.seeded.add(sl.getString(i));
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag pl = new ListTag();
        for (Map.Entry<String, Entry> m : pools.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putString("name", m.getKey());
            ListTag items = new ListTag();
            for (String s : m.getValue().items) items.add(StringTag.valueOf(s));
            c.put("items", items);
            pl.add(c);
        }
        tag.put("pools", pl);
        ListTag sl = new ListTag();
        for (String s : seeded) sl.add(StringTag.valueOf(s));
        tag.put("seeded", sl);
        return tag;
    }

    public Entry getPool(String name) {
        return pools.get(name);
    }

    public Map<String, Entry> allPools() {
        return pools;
    }

    /** Copies the pools the mod shipped with into the world, once per pool name. */
    public void seed(String[][] defaults) {
        boolean changed = false;
        for (String[] p : defaults) {
            if (p.length == 0 || !seeded.add(p[0])) continue;
            changed = true;
            if (pools.containsKey(p[0])) continue;
            Entry e = new Entry();
            for (int i = 1; i < p.length; i++) e.items.add(p[i]);
            pools.put(p[0], e);
        }
        if (changed) setDirty();
    }

    /** Replaces one entry. @return false when the pool or the index does not exist. */
    public boolean setPoolItem(String name, int index, String entry) {
        Entry e = pools.get(name);
        if (e == null || index < 0 || index >= e.items.size()) return false;
        e.items.set(index, entry);
        setDirty();
        return true;
    }

    /** Adds one entry (creates the pool when it is new). @return entries now in the pool. */
    public int addPoolItem(String name, String entry) {
        Entry e = pools.computeIfAbsent(name, k -> new Entry());
        e.items.add(entry);
        setDirty();
        return e.items.size();
    }

    /** Removes one entry. The pool is deleted when it becomes empty. @return entries left, or -1 when pool/index does not exist. */
    public int removePoolItem(String name, int index) {
        Entry e = pools.get(name);
        if (e == null || index < 0 || index >= e.items.size()) return -1;
        e.items.remove(index);
        if (e.items.isEmpty()) pools.remove(name);
        setDirty();
        return e.items.size();
    }

    public boolean removePool(String name) {
        if (pools.remove(name) == null) return false;
        setDirty();
        return true;
    }
}
