package nexia.nexiarandom.client;

import nexia.nexiarandom.Net;
import nexia.nexiarandom.Randoms;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Admin screen of the random pools. Pick a pool on the left; on the right its items can be removed (x) or changed (e).
 * Items are added without typing an id: click an item in your inventory, search any item by name, or add the item in your hand.
 * Every click on an item adds it at once, using the amount and weight in the boxes at the bottom.
 */
public class RandomScreen extends Screen {
    private static final int ROWS = 5;
    private static final int ITEM_ROWS = 4;
    private static final int SEARCH_ROWS = 5;
    private static final int GOLD = 0xFFFFFFFF;
    private static final int LIST = 0;
    private static final int INVENTORY = 1;
    private static final int SEARCH = 2;

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
    private int mode = LIST;
    private int editIdx = -1;
    private boolean confirmDelete;
    private boolean focusItem;
    private List<ItemStack> results = new ArrayList<>();

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
        Randoms.Info named = find(poolName());
        if (named != null && !named.name.equals(selected)) {
            selected = named.name;
            itemPage = 0;
        }
        if (!error || find(selected) == null) editIdx = -1;
        confirmDelete = false;
        if (mode == SEARCH) focusItem = true;
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
        send(op, name, text, number, weight, 0);
    }

    private void send(String op, String name, String text, int number, int weight, int index) {
        Net.CHANNEL.sendToServer(new Net.PoolActionPacket(op, name, text, number, weight, index));
    }

    private String poolName() {
        return nameText.trim().toLowerCase(Locale.ROOT);
    }

    private int rightWidth() {
        return width0 - width0 * 3 / 8 - 8;
    }

    private int gridX() {
        return left + width0 * 3 / 8 + 8;
    }

    private int gridY() {
        return top + 32;
    }

    private int searchCols() {
        return Math.max(1, Math.min(13, rightWidth() / ItemSearch.CELL));
    }

    private void setMode(int m) {
        mode = m;
        editIdx = -1;
        confirmDelete = false;
        error = false;
        if (m == SEARCH) {
            results = ItemSearch.search(itemText, searchCols() * SEARCH_ROWS);
            focusItem = true;
            status = "Type part of an item name in the item box (for example: dia), then click the item.";
        } else if (m == INVENTORY) {
            status = "Click an item in your inventory to add it to the pool.";
        } else {
            status = "";
        }
        this.rebuildWidgets();
    }

    /** Adds one item to the pool named in the pool box, using the amount and weight boxes. */
    private void quickAdd(String id) {
        String pool = poolName();
        if (pool.isEmpty()) {
            status = "Type a pool name first (the box at the bottom left).";
            error = true;
            return;
        }
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
        send("add", pool, id, n, w);
    }

    private void addHeld() {
        Minecraft mc = Minecraft.getInstance();
        ItemStack s = mc.player == null ? ItemStack.EMPTY : mc.player.getMainHandItem();
        if (s.isEmpty()) {
            status = "Hold an item in your main hand first.";
            error = true;
            return;
        }
        quickAdd(InvGrid.id(s));
    }

    private void startEdit(Randoms.Info sel, int idx) {
        if (editIdx == idx) {
            editIdx = -1;
            status = "Edit cancelled.";
            error = false;
            this.rebuildWidgets();
            return;
        }
        Randoms.Entry e = Randoms.parse(sel.items.get(idx));
        if (e == null) return;
        editIdx = idx;
        nameText = sel.name;
        itemText = e.item();
        amountText = String.valueOf(e.count());
        weightText = String.valueOf(e.weight());
        status = "Editing item " + (idx + 1) + ": change the boxes below, then press Save item.";
        error = false;
        this.rebuildWidgets();
    }

    @Override
    protected void init() {
        width0 = Math.min(this.width - 10, 400);
        left = (this.width - width0) / 2;
        top = Math.max(4, (this.height - 205) / 2);
        int lw = width0 * 3 / 8;
        int rx = left + lw + 8;
        int rw = width0 - lw - 8;

        this.addRenderableWidget(Button.builder(Component.literal("X"), b -> this.onClose())
                .bounds(left + width0 - 20, top, 20, 14).build());

        int pages = Math.max(1, (infos.size() + ROWS - 1) / ROWS);
        listPage = Math.min(listPage, pages - 1);
        for (int i = 0; i < ROWS; i++) {
            int idx = listPage * ROWS + i;
            if (idx >= infos.size()) break;
            Randoms.Info in = infos.get(idx);
            String label = (in.name.equals(selected) ? "> " : "") + in.name;
            this.addRenderableWidget(Button.builder(Component.literal(label), b -> {
                selected = in.name;
                nameText = in.name;
                itemPage = 0;
                editIdx = -1;
                confirmDelete = false;
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
        if (sel != null && mode == LIST) {
            int pagesI = Math.max(1, (sel.items.size() + ITEM_ROWS - 1) / ITEM_ROWS);
            itemPage = Math.min(itemPage, pagesI - 1);
            for (int i = 0; i < ITEM_ROWS; i++) {
                int idx = itemPage * ITEM_ROWS + i;
                if (idx >= sel.items.size()) break;
                this.addRenderableWidget(Button.builder(Component.literal("x"), b -> send("rmitem", sel.name, "", idx, 1))
                        .bounds(rx + rw - 20, top + 46 + i * 16, 20, 14).build());
                this.addRenderableWidget(Button.builder(Component.literal("e"), b -> startEdit(sel, idx))
                        .bounds(rx + rw - 42, top + 46 + i * 16, 20, 14).build());
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
        itemBox.setHint(Component.literal(mode == SEARCH ? "search item name" : "item (name or id)"));
        itemBox.setValue(itemText);
        itemBox.setResponder(s -> {
            itemText = s;
            if (mode == SEARCH) results = ItemSearch.search(s, searchCols() * SEARCH_ROWS);
        });
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
        this.addRenderableWidget(Button.builder(Component.literal(editIdx >= 0 ? "Save item" : "Add item"), b -> {
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
            if (editIdx >= 0 && !selected.isEmpty()) {
                send("edit", selected, itemText, n, w, editIdx);
            } else {
                send("add", poolName(), itemText, n, w);
            }
        }).bounds(left + cw + iw + aw + ww + 16, y1, bw, 18).build());

        // bottom row 2: ways to add items without an id, spin, delete
        int y2 = top + 164;
        int q = (width0 - 16) / 5;
        this.addRenderableWidget(Button.builder(Component.literal(mode == INVENTORY ? "Back to list" : "Inventory"),
                b -> setMode(mode == INVENTORY ? LIST : INVENTORY)).bounds(left, y2, q, 18).build());
        this.addRenderableWidget(Button.builder(Component.literal(mode == SEARCH ? "Back to list" : "Search name"),
                b -> setMode(mode == SEARCH ? LIST : SEARCH)).bounds(left + (q + 4), y2, q, 18).build());
        this.addRenderableWidget(Button.builder(Component.literal("Add held item"), b -> addHeld())
                .bounds(left + 2 * (q + 4), y2, q, 18).build());
        this.addRenderableWidget(Button.builder(Component.literal("Spin (me)"), b -> send("spin", poolName(), "", 0, 1))
                .bounds(left + 3 * (q + 4), y2, q, 18).build());
        this.addRenderableWidget(Button.builder(Component.literal(confirmDelete ? "Sure? Click" : "Delete pool"), b -> {
            if (!confirmDelete) {
                confirmDelete = true;
                status = "Press Delete pool again to delete the pool " + poolName() + ".";
                error = false;
                this.rebuildWidgets();
            } else {
                confirmDelete = false;
                send("remove", poolName(), "", 0, 1);
            }
        }).bounds(left + 4 * (q + 4), y2, q, 18).build());

        if (mode == SEARCH && focusItem) {
            this.setInitialFocus(itemBox);
            focusItem = false;
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && mode == INVENTORY) {
            ItemStack s = InvGrid.at(gridX(), gridY(), (int) mx, (int) my);
            if (!s.isEmpty()) {
                quickAdd(InvGrid.id(s));
                return true;
            }
        } else if (button == 0 && mode == SEARCH) {
            ItemStack s = ItemSearch.at(gridX(), gridY(), searchCols(), SEARCH_ROWS, results, (int) mx, (int) my);
            if (!s.isEmpty()) {
                quickAdd(InvGrid.id(s));
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
        if (mode == INVENTORY || mode == SEARCH) {
            String pool = poolName();
            String head = "Click an item to add it to: " + (pool.isEmpty() ? "(type a pool name below)" : pool);
            g.drawString(this.font, this.font.plainSubstrByWidth(head, rw), rx, top + 14, GOLD);
            g.drawString(this.font, this.font.plainSubstrByWidth("Amount and weight come from the boxes below", rw), rx, top + 23, 0xAAAAAA);
            if (mode == INVENTORY) {
                InvGrid.render(g, this.font, gridX(), gridY(), mouseX, mouseY);
            } else {
                ItemSearch.render(g, this.font, gridX(), gridY(), searchCols(), SEARCH_ROWS, results, mouseX, mouseY);
                if (results.isEmpty()) {
                    String hint = itemText.trim().isEmpty() ? "Type a name in the item box below" : "No item found";
                    g.drawString(this.font, this.font.plainSubstrByWidth(hint, rw), gridX() + 4, gridY() + 4, 0xAAAAAA);
                }
            }
        } else {
            String head = sel == null ? "Pick a pool, or type a new name below" : sel.name + "  (" + sel.items.size() + " item" + (sel.items.size() == 1 ? "" : "s") + ")";
            g.drawString(this.font, this.font.plainSubstrByWidth(head, rw - 24), rx, top + 16, 0xFFFFFF);
            if (sel != null) {
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
                    int color = idx == editIdx ? 0xFFFF55 : 0xFFFFFF;
                    g.drawString(this.font, this.font.plainSubstrByWidth(text, rw - 74 - cwid), rx + 20, top + 49 + i * 16, color);
                    g.drawString(this.font, chance, rx + rw - 46 - cwid, top + 49 + i * 16, GOLD);
                }
                if (sel.items.size() > ITEM_ROWS) {
                    g.drawString(this.font, "page " + (itemPage + 1), rx + 52, top + 49 + ITEM_ROWS * 16, 0xAAAAAA);
                }
            }
        }
        g.drawString(this.font, this.font.plainSubstrByWidth(status, width0), left, top + 190, error ? 0xFF5555 : 0x55FF55);
        super.render(g, mouseX, mouseY, partial);
        if (mode == INVENTORY) InvGrid.tooltip(g, this.font, gridX(), gridY(), mouseX, mouseY);
        if (mode == SEARCH) ItemSearch.tooltip(g, this.font, gridX(), gridY(), searchCols(), SEARCH_ROWS, results, mouseX, mouseY);
    }
}
