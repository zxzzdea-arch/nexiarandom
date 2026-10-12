package nexia.nexiarandom.client;

import nexia.nexiarandom.Net;
import nexia.nexiarandom.Randoms;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The Nexia roulette, plain black and white with no background. A strip of items slides past a marker and slows down on the prize the
 * server already picked. It has no buttons and cannot be closed by the player: it runs to the end, shows the prize
 * for a moment and closes by itself.
 */
public class SpinScreen extends Screen {
    private static final int CELL = 48;
    private static final int STRIP = 64;
    private static final int WIN_POS = 52;
    private static final double SPIN_SECONDS = 6.0;
    private static final double SHOW_SECONDS = 3.0;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GRAY = 0xFF8A8A8A;

    private final int id;
    private final List<Randoms.Entry> entries = new ArrayList<>();
    private final List<ItemStack> stacks = new ArrayList<>();
    private final int[] strip = new int[STRIP];
    private final int winner;
    private final double jitter;
    private long startNanos = -1L;
    private long doneNanos = -1L;
    private boolean claimed;
    private boolean finished;
    private int lastCell = -1;

    public SpinScreen(int id, String pool, List<String> raw, int winner, long seed) {
        super(Component.literal("Nexia Random"));
        this.id = id;
        int sum = 0;
        for (String s : raw) {
            Randoms.Entry e = Randoms.parse(s);
            if (e == null) continue;
            ItemStack st = Randoms.stackOf(e.item(), e.count());
            if (st.isEmpty()) continue;
            entries.add(e);
            stacks.add(st);
            sum += Math.max(1, e.weight());
        }
        int total = Math.max(1, sum);
        this.winner = Math.max(0, Math.min(winner, Math.max(0, entries.size() - 1)));
        Random rnd = new Random(seed);
        for (int i = 0; i < STRIP; i++) {
            if (i == WIN_POS || entries.isEmpty()) {
                strip[i] = this.winner;
                continue;
            }
            int pick = rnd.nextInt(total);
            int chosen = 0;
            for (int k = 0; k < entries.size(); k++) {
                pick -= Math.max(1, entries.get(k).weight());
                if (pick < 0) {
                    chosen = k;
                    break;
                }
            }
            strip[i] = chosen;
        }
        this.jitter = 0.0; // the prize stops exactly under the marker
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** The player cannot cancel the spin. */
    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        return true;
    }

    private void claim() {
        if (claimed) return;
        claimed = true;
        Net.CHANNEL.sendToServer(new Net.ClaimPacket(id));
    }

    @Override
    public void removed() {
        claim();
        super.removed();
    }

    private void click(float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, pitch));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        // No panel, no frame, no dimmed background: only the items and the result are drawn over the game.
        if (entries.isEmpty()) {
            claim();
            this.onClose();
            return;
        }
        long now = System.nanoTime();
        if (startNanos < 0L) startNanos = now;
        double t = (now - startNanos) / 1.0e9;
        double p = Math.min(1.0, t / SPIN_SECONDS);
        double eased = 1.0 - Math.pow(1.0 - p, 4.0);
        double off = eased * (WIN_POS * CELL + jitter);

        int vw = Math.min(this.width - 16, 380);
        int vh = 52;
        int vx = (this.width - vw) / 2;
        int vy = this.height / 2 - 34;
        int markerX = vx + vw / 2;

        g.enableScissor(vx, vy, vx + vw, vy + vh);
        for (int i = 0; i < STRIP; i++) {
            if (finished && i != WIN_POS) continue;
            double ccx = markerX + i * CELL - off;
            int left = (int) Math.round(ccx - CELL / 2.0);
            if (left + CELL < vx || left > vx + vw) continue;
            int idx = strip[i];
            ItemStack st = stacks.get(idx);
            g.pose().pushPose();
            g.pose().translate(left + (CELL - 32) / 2.0F, vy, 0.0F);
            g.pose().scale(2.0F, 2.0F, 1.0F);
            g.renderItem(st, 0, 0);
            g.pose().popPose();
            String label = "x" + entries.get(idx).count();
            g.drawString(this.font, label, left + (CELL - this.font.width(label)) / 2, vy + 40, WHITE, true);
        }
        g.disableScissor();

        // marker: two short white ticks above and below the strip
        g.fill(markerX - 1, vy - 12, markerX + 1, vy - 3, WHITE);
        g.fill(markerX - 1, vy + vh + 3, markerX + 1, vy + vh + 12, WHITE);

        int cell = (int) Math.floor(off / CELL + 0.5);
        if (cell != lastCell) {
            lastCell = cell;
            if (!finished && p < 1.0) click(0.8F + 0.5F * (float) p);
        }

        int ry = vy + vh + 20;
        if (p >= 1.0 && !finished) {
            finished = true;
            doneNanos = now;
            claim();
            click(1.6F);
        }
        if (finished) {
            Component name = stacks.get(winner).getHoverName().copy().withStyle(ChatFormatting.BOLD);
            int nameW = this.font.width(name);
            g.pose().pushPose();
            g.pose().translate(this.width / 2.0F, ry, 0.0F);
            g.pose().scale(1.6F, 1.6F, 1.0F);
            g.drawString(this.font, name, -nameW / 2, 0, WHITE, true);
            g.pose().popPose();
            String cnt = "x" + entries.get(winner).count();
            g.drawString(this.font, cnt, (this.width - this.font.width(cnt)) / 2, ry + 17, GRAY, true);
            if ((now - doneNanos) / 1.0e9 > SHOW_SECONDS) this.onClose();
        }
        super.render(g, mouseX, mouseY, partial);
    }
}
