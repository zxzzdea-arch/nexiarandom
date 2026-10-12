package nexia.nexiarandom;

import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;

/** Nexia Random. Logic: Randoms, PoolData, NexiarandomCommand. Screens: Net + client/RandomScreen + client/SpinScreen. */
@Mod(NexiarandomMod.MODID)
public class NexiarandomMod {
    public static final String MODID = "nexiarandom";

    public NexiarandomMod() {
        Net.init();
        // The mod is only required on the server. Players without it can still join; they just get the prize without the roulette animation.
        ModLoadingContext.get().registerExtensionPoint(IExtensionPoint.DisplayTest.class,
                () -> new IExtensionPoint.DisplayTest(() -> IExtensionPoint.DisplayTest.IGNORESERVERONLY, (remote, isServer) -> true));
    }
}
