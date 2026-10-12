package nexia.nexiarandom.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Finds any item in the game (vanilla and other mods) by its name, so an admin never has to type an item id. Client only. */
public final class ItemSearch {
    private ItemSearch() {}

    public static final int CELL = 18;

    private record Row(ItemStack stack, String name, String id) {}

    private static List<Row> all;

    private static List<Row> all() {
        if (all == null) {
            List<Row> list = new ArrayList<>();
            for (Item item : ForgeRegistries.ITEMS.getValues()) {
                if (item == Items.AIR) continue;
                ItemStack s = new ItemStack(item);
                if (s.isEmpty()) continue;
                list.add(new Row(s, s.getHoverName().getString().toLowerCase(Locale.ROOT), InvGrid.id(s)));
            }
            all = list;
        }
        return all;
    }

    /** Items whose name or id contains every word of the query. Names that start with the query come first. */
    public static List<ItemStack> search(String query, int max) {
        List<ItemStack> first = new ArrayList<>();
        List<ItemStack> later = new ArrayList<>();
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return first;
        String[] words = q.split("\\s+");
        for (Row r : all()) {
            boolean match = true;
            for (String w : words) {
                if (!r.name().contains(w) && !r.id().contains(w)) {
                    match = false;
                    break;
                }
            }
            if (!match) continue;
            if (r.name().startsWith(q) || r.id().startsWith(q) || r.id().contains(":" + q)) {
                if (first.size() < max) first.add(r.stack());
            } else if (later.size() < max) {
                later.add(r.stack());
            }
        }
        for (ItemStack s : later) {
            if (first.size() >= max) break;
            first.add(s);
        }
        return first;
    }

    public static void render(GuiGraphics g, Font font, int x, int y, int cols, int rows, List<ItemStack> results, int mx, int my) {
        g.fill(x - 3, y - 3, x + cols * CELL + 2, y + rows * CELL + 1, 0xCC000000);
        for (int k = 0; k < cols * rows; k++) {
            int sx = x + (k % cols) * CELL;
            int sy = y + (k / cols) * CELL;
            boolean has = k < results.size();
            boolean hover = has && mx >= sx && mx < sx + CELL - 1 && my >= sy && my < sy + CELL - 1;
            g.fill(sx, sy, sx + CELL - 1, sy + CELL - 1, hover ? 0xFF6A5A1F : 0xFF373737);
            if (has) g.renderItem(results.get(k), sx + 1, sy + 1);
        }
    }

    /** @return the result under the mouse, or ItemStack.EMPTY. */
    public static ItemStack at(int x, int y, int cols, int rows, List<ItemStack> results, int mx, int my) {
        if (mx < x || my < y) return ItemStack.EMPTY;
        int dx = mx - x;
        int dy = my - y;
        int c = dx / CELL;
        int r = dy / CELL;
        if (c >= cols || r >= rows) return ItemStack.EMPTY;
        if (dx % CELL >= CELL - 1 || dy % CELL >= CELL - 1) return ItemStack.EMPTY;
        int k = r * cols + c;
        return k < results.size() ? results.get(k) : ItemStack.EMPTY;
    }

    public static void tooltip(GuiGraphics g, Font font, int x, int y, int cols, int rows, List<ItemStack> results, int mx, int my) {
        ItemStack s = at(x, y, cols, rows, results, mx, my);
        if (!s.isEmpty()) g.renderTooltip(font, s, mx, my);
    }
}
