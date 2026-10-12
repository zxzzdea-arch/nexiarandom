package nexia.nexiarandom.client;

import nexia.nexiarandom.Randoms;
import net.minecraft.client.Minecraft;

import java.util.List;

/** Client-only entry point, called from Net when pools or a spin arrive. */
public final class ClientHooks {
    private ClientHooks() {}

    public static void poolSync(List<Randoms.Info> infos, boolean open, String status, boolean error) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof RandomScreen screen) {
            screen.update(infos, status, error);
        } else if (open) {
            mc.setScreen(new RandomScreen(infos, status, error));
        }
    }

    public static void spin(int id, String title, List<String> entries, int winner, long seed) {
        Minecraft.getInstance().setScreen(new SpinScreen(id, title, entries, winner, seed));
    }
}
