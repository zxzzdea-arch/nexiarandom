package nexia.nexiarandom.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

/** Draws the player's own inventory so an admin can click an item instead of typing its id. Client only. */
public final class InvGrid {
    private InvGrid() {}

    public static final int CELL = 18;
    public static final int W = 9 * CELL;
    public static final int H = 4 * CELL + 4;

    private static int slotIndex(int k) {
        return k < 27 ? k + 9 : k - 27; // three inventory rows first, hotbar last
    }

    private static int cellX(int x, int k) {
        return x + (k % 9) * CELL;
    }

    private static int cellY(int y, int k) {
        int row = k / 9;
        return y + row * CELL + (row == 3 ? 4 : 0);
    }

    public static void render(GuiGraphics g, Font font, int x, int y, int mx, int my) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        Inventory inv = mc.player.getInventory();
        g.fill(x - 3, y - 3, x + W + 2, y + H + 1, 0xCC000000);
        for (int k = 0; k < 36; k++) {
            int sx = cellX(x, k);
            int sy = cellY(y, k);
            ItemStack s = inv.getItem(slotIndex(k));
            boolean hover = mx >= sx && mx < sx + CELL - 1 && my >= sy && my < sy + CELL - 1;
            g.fill(sx, sy, sx + CELL - 1, sy + CELL - 1, hover && !s.isEmpty() ? 0xFF6A5A1F : 0xFF373737);
            if (!s.isEmpty()) {
                g.renderItem(s, sx + 1, sy + 1);
                g.renderItemDecorations(font, s, sx + 1, sy + 1);
            }
        }
    }

    /** @return the stack under the mouse, or ItemStack.EMPTY. */
    public static ItemStack at(int x, int y, int mx, int my) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return ItemStack.EMPTY;
        Inventory inv = mc.player.getInventory();
        for (int k = 0; k < 36; k++) {
            int sx = cellX(x, k);
            int sy = cellY(y, k);
            if (mx >= sx && mx < sx + CELL - 1 && my >= sy && my < sy + CELL - 1) {
                return inv.getItem(slotIndex(k));
            }
        }
        return ItemStack.EMPTY;
    }

    public static void tooltip(GuiGraphics g, Font font, int x, int y, int mx, int my) {
        ItemStack s = at(x, y, mx, my);
        if (!s.isEmpty()) g.renderTooltip(font, s, mx, my);
    }

    /** @return the stack for an item id, or ItemStack.EMPTY when the item does not exist. */
    public static ItemStack stackOf(String id, int count) {
        ResourceLocation rl = ResourceLocation.tryParse(id == null ? "" : id);
        if (rl == null || !ForgeRegistries.ITEMS.containsKey(rl)) return ItemStack.EMPTY;
        Item item = ForgeRegistries.ITEMS.getValue(rl);
        if (item == null || item == Items.AIR) return ItemStack.EMPTY;
        return new ItemStack(item, Math.max(1, count));
    }

    public static String id(ItemStack s) {
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(s.getItem());
        return key == null ? "minecraft:air" : key.toString();
    }
}
