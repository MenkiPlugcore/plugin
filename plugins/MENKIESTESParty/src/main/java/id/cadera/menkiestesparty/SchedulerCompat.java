package id.cadera.menkiestesparty;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.function.Consumer;

/**
 * Scheduler compatibility boundary introduced in v1.5.0.
 *
 * Paper uses BukkitScheduler. Folia is detected explicitly. Experimental Folia
 * scheduling can be enabled for testing, but v1.5.0 does not claim full Folia
 * thread-safety for every gameplay manager yet.
 */
public final class SchedulerCompat {
    private final MENKIESTESPartyPlugin plugin;
    private final boolean folia;
    private final boolean experimentalFolia;

    public SchedulerCompat(MENKIESTESPartyPlugin plugin) {
        this.plugin = plugin;
        this.folia = detectFolia();
        File settingsFile = new File(plugin.getDataFolder(), "storage.yml");
        if (!settingsFile.isFile()) plugin.saveResource("storage.yml", false);
        YamlConfiguration settings = YamlConfiguration.loadConfiguration(settingsFile);
        this.experimentalFolia = settings.getBoolean("compatibility.folia.experimental", false);
    }

    public boolean foliaDetected() {
        return folia;
    }

    public boolean runtimeAllowed() {
        return !folia || experimentalFolia;
    }

    public String mode() {
        if (!folia) return "PAPER";
        return experimentalFolia ? "FOLIA_EXPERIMENTAL" : "FOLIA_BLOCKED";
    }

    public void runGlobalTimer(Runnable runnable, long delayTicks, long periodTicks) {
        if (!folia) {
            Bukkit.getScheduler().runTaskTimer(plugin, runnable, delayTicks, periodTicks);
            return;
        }
        if (!experimentalFolia) throw new IllegalStateException("Folia runtime is blocked by compatibility.folia.experimental=false");
        invokeGlobal("runAtFixedRate", runnable, Math.max(1L, delayTicks), Math.max(1L, periodTicks));
    }

    public void runGlobal(Runnable runnable) {
        if (!folia) {
            Bukkit.getScheduler().runTask(plugin, runnable);
            return;
        }
        if (!experimentalFolia) throw new IllegalStateException("Folia runtime is blocked by compatibility.folia.experimental=false");
        invokeGlobal("run", runnable);
    }

    private void invokeGlobal(String methodName, Runnable runnable, long... numbers) {
        try {
            Object scheduler = Bukkit.getServer().getClass().getMethod("getGlobalRegionScheduler").invoke(Bukkit.getServer());
            Method method = Arrays.stream(scheduler.getClass().getMethods())
                    .filter(candidate -> candidate.getName().equals(methodName))
                    .filter(candidate -> candidate.getParameterCount() == 2 + numbers.length)
                    .findFirst()
                    .orElseThrow(() -> new NoSuchMethodException(methodName));
            Consumer<Object> task = ignored -> runnable.run();
            Object[] args = new Object[2 + numbers.length];
            args[0] = plugin;
            args[1] = task;
            for (int i = 0; i < numbers.length; i++) args[i + 2] = numbers[i];
            method.invoke(scheduler, args);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Unable to use Folia GlobalRegionScheduler: " + failure.getMessage(), failure);
        }
    }

    private static boolean detectFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer", false,
                    SchedulerCompat.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }
}
