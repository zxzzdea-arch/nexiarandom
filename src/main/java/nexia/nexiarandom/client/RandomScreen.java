package nexia.nexiarandom.client;

import nexia.nexiarandom.Net;
import nexia.nexiarandom.Randoms;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Locale;

/** Admin screen of the random pools: pick a pool on the left, edit its items on the right, add items from your inventory. */
public class RandomScreen extends Screen {
    private static final int ROWS = 5;
    private static final int ITEM_ROWS = 4;
    private static final int GOLD = 0xFFFFFFFF;

    private List<Randoms.Info> infos;
    private String status;
    private boolean error;
    private String selected = "";
    private String nameText = "";
    private String itemText = "";
    private String amountText = "1";
    private String weightText = "10";
    private int listPage;
    private int itemPage;
    private int left;
    private int top;
    private int width0;
    private boolean picking;

    public RandomScreen(List<Randoms.Info> infos, String status, boolean error) {
        super(Component.literal("Nexia Random"));
        this.infos = infos;
        this.status = status == null ? "" : status;
        this.error = error;
    }

    public void update(List<Randoms.Info> infos, String status, boolean error) {
        this.infos = infos;
        this.status = status == null ? "" : status;
        this.error = error;
        if (find(selected) == null) {
            selected = "";
            itemPage = 0;
        }
        this.rebuildWidgets();
    }

    private Randoms.Info find(String name) {
        for (Randoms.Info i : infos) {
            if (i.name.equals(name)) return i;
        }
        return null;
    }

    /** Must be false: a paused single-player game would not process the packets. */
    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void send(String op, String name, String text, int number, int weight) {
        Net.CHANNEL.sendToServer(new Net.PoolActionPacket(op, name, text, number, weight));
    }

    private String target() {
        return selected.isEmpty() ? nameText : selected;
    }

    private int gridX() {
        return left + width0 * 3 / 8 + 8;
    }

    private int gridY() {
        return top + 32;
    }

    @Override
    protected void init() {
        width0 = Math.min(this.width - 10, 400);
        left = (this.width - width0) / 2;
        top = Math.max(4, (this.height - 205) / 2);
        int lw = width0 * 3 / 8;
        int rx = left + lw + 8;
        int rw = width0 - lw - 8;

        int pages = Math.max(1, (infos.size() + ROWS - 1) / ROWS);
        listPage = Math.min(listPage, pages - 1);
        for (int i = 0; i < ROWS; i++) {
            int idx = listPage * ROWS + i;
            if (idx >= infos.size()) break;
            Randoms.Info in = infos.get(idx);
            String label = (in.name.equals(selected) ? "> " : "") + in.name + (in.builtin ? " (mod)" : "");
            this.addRenderableWidget(Button.builder(Component.literal(label), b -> {
                selected = in.name;
                nameText = in.name;
                itemPage = 0;
                this.rebuildWidgets();
            }).bounds(left, top + 16 + i * 18, lw, 18).build());
        }
        int py = top + 16 + ROWS * 18 + 2;
        this.addRenderableWidget(Button.builder(Component.literal("<"), b -> {
            if (listPage > 0) {
                listPage--;
                this.rebuildWidgets();
            }
        }).bounds(left, py, 22, 16).build());
        this.addRenderableWidget(Button.builder(Component.literal(">"), b -> {
            if (listPage < pages - 1) {
                listPage++;
                this.rebuildWidgets();
            }
        }).bounds(left + lw - 22, py, 22, 16).build());

        Randoms.Info sel = find(selected);
        if (sel != null && !picking) {
            int pagesI = Math.max(1, (sel.items.size() + ITEM_ROWS - 1) / ITEM_ROWS);
            itemPage = Math.min(itemPage, pagesI - 1);
            for (int i = 0; i < ITEM_ROWS; i++) {
                int idx = itemPage * ITEM_ROWS + i;
                if (idx >= sel.items.size()) break;
                if (!sel.builtin) {
                    this.addRenderableWidget(Button.builder(Component.literal("x"), b -> send("rmitem", sel.name, "", idx, 1))
                            .bounds(rx + rw - 20, top + 46 + i * 16, 20, 14).build());
                }
            }
            int iy = top + 46 + ITEM_ROWS * 16 + 2;
            this.addRenderableWidget(Button.builder(Component.literal("<"), b -> {
                if (itemPage > 0) {
                    itemPage--;
                    this.rebuildWidgets();
                }
            }).bounds(rx, iy, 22, 14).build());
            this.addRenderableWidget(Button.builder(Component.literal(">"), b -> {
                if (itemPage < pagesI - 1) {
                    itemPage++;
                    this.rebuildWidgets();
                }
            }).bounds(rx + 26, iy, 22, 14).build());
        }

        // bottom row 1: pool, item, amount, weight, add
        int y1 = top + 140;
        int cw = width0 * 24 / 100;
        int iw = width0 * 28 / 100;
        int aw = width0 * 10 / 100;
        int ww = width0 * 10 / 100;
        int bw = width0 - cw - iw - aw - ww - 16;
        EditBox nameBox = new EditBox(this.font, left, y1, cw, 18, Component.literal("pool"));
        nameBox.setMaxLength(32);
        nameBox.setHint(Component.literal("pool name"));
        nameBox.setValue(nameText);
        nameBox.setResponder(s -> nameText = s);
        this.addRenderableWidget(nameBox);
        EditBox itemBox = new EditBox(this.font, left + cw + 4, y1, iw, 18, Component.literal("item"));
        itemBox.setMaxLength(100);
        itemBox.setHint(Component.literal("item id"));
        itemBox.setValue(itemText);
        itemBox.setResponder(s -> itemText = s);
        this.addRenderableWidget(itemBox);
        EditBox amountBox = new EditBox(this.font, left + cw + iw + 8, y1, aw, 18, Component.literal("amount"));
        amountBox.setMaxLength(4);
        amountBox.setHint(Component.literal("qty"));
        amountBox.setValue(amountText);
        amountBox.setResponder(s -> amountText = s);
        this.addRenderableWidget(amountBox);
        EditBox weightBox = new EditBox(this.font, left + cw + iw + aw + 12, y1, ww, 18, Component.literal("weight"));
        weightBox.setMaxLength(6);
        weightBox.setHint(Component.literal("weight"));
        weightBox.setValue(weightText);
        weightBox.setResponder(s -> weightText = s);
        this.addRenderableWidget(weightBox);
        this.addRenderableWidget(Button.builder(Component.literal("Add item"), b -> {
            int n;
            int w;
            try {
                n = Integer.parseInt(amountText.trim());
                w = Integer.parseInt(weightText.trim());
            } catch (NumberFormatException e) {
                status = "Quantity and weight must be whole numbers.";
                error = true;
                return;
            }
            send("add", nameText, itemText, n, w);
        }).bounds(left + cw + iw + aw + ww + 16, y1, bw, 18).build());

        // bottom row 2: actions
        int y2 = top + 164;
        int q = (width0 - 12) / 4;
        this.addRenderableWidget(Button.builder(Component.literal(picking ? "Cancel pick" : "Pick from inventory"), b -> {
            picking = !picking;
            this.rebuildWidgets();
        }).bounds(left, y2, q, 18).build());
        this.addRenderableWidget(Button.builder(Component.literal("Spin now (me)"), b -> send("spin", target(), "", 0, 1))
                .bounds(left + (q + 4), y2, q, 18).build());
        this.addRenderableWidget(Button.builder(Component.literal("Delete pool"), b -> send("remove", target(), "", 0, 1))
                .bounds(left + 2 * (q + 4), y2, q, 18).build());
        this.addRenderableWidget(Button.builder(Component.literal("Close"), b -> this.onClose())
                .bounds(left + 3 * (q + 4), y2, q, 18).build());
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (picking) {
            ItemStack s = InvGrid.at(gridX(), gridY(), (int) mx, (int) my);
            if (!s.isEmpty()) {
                itemText = InvGrid.id(s);
                amountText = String.valueOf(s.getCount());
                picking = false;
                status = "Picked " + s.getHoverName().getString() + ". Type a pool name and a weight, then press Add item.";
                error = false;
                this.rebuildWidgets();
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        this.renderBackground(g);
        g.drawCenteredString(this.font, "NEXIA RANDOM", this.width / 2, top + 2, GOLD);
        int lw = width0 * 3 / 8;
        int rx = left + lw + 8;
        int rw = width0 - lw - 8;
        Randoms.Info sel = find(selected);
        String head = sel == null ? "Pick a pool, or type a new name below" : sel.name + (sel.builtin ? "  (built into the mod)" : "  (made in game)");
        g.drawString(this.font, this.font.plainSubstrByWidth(head, rw), rx, top + 16, 0xFFFFFF);
        if (picking) {
            g.drawString(this.font, "Click an item from your inventory", rx, top + 16, GOLD);
            InvGrid.render(g, this.font, gridX(), gridY(), mouseX, mouseY);
        } else if (sel != null) {
            int total = 0;
            for (String s : sel.items) {
                Randoms.Entry e = Randoms.parse(s);
                if (e != null) total += Math.max(1, e.weight());
            }
            g.drawString(this.font, "Chance = weight / " + total, rx, top + 30, 0xAAAAAA);
            for (int i = 0; i < ITEM_ROWS; i++) {
                int idx = itemPage * ITEM_ROWS + i;
                if (idx >= sel.items.size()) break;
                Randoms.Entry e = Randoms.parse(sel.items.get(idx));
                if (e == null) continue;
                ItemStack icon = Randoms.stackOf(e.item(), e.count());
                if (!icon.isEmpty()) g.renderItem(icon, rx, top + 44 + i * 16);
                String chance = String.format(Locale.ROOT, "%.1f%%", Math.max(1, e.weight()) * 100.0 / Math.max(1, total));
                String text = e.count() + "x " + (icon.isEmpty() ? e.item() : icon.getHoverName().getString());
                int cwid = this.font.width(chance);
                g.drawString(this.font, this.font.plainSubstrByWidth(text, rw - 50 - cwid), rx + 20, top + 49 + i * 16, 0xFFFFFF);
                g.drawString(this.font, chance, rx + rw - 24 - cwid, top + 49 + i * 16, GOLD);
            }
            if (sel.items.size() > ITEM_ROWS) {
                g.drawString(this.font, "page " + (itemPage + 1), rx + 52, top + 49 + ITEM_ROWS * 16, 0xAAAAAA);
            }
        }
        g.drawString(this.font, this.font.plainSubstrByWidth(status, width0), left, top + 190, error ? 0xFF5555 : 0x55FF55);
        super.render(g, mouseX, mouseY, partial);
        if (picking) InvGrid.tooltip(g, this.font, gridX(), gridY(), mouseX, mouseY);
    }
}
