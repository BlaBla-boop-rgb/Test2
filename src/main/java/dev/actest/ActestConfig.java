package dev.actest;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * config/actest.json
 *
 * allowedServers: nur auf diesen Servern (Host ohne Port) arbeiten die Test-Funktionen.
 * Einzelspieler und LAN-Welten sind immer erlaubt. Die Liste wird bewusst nur per Datei gepflegt.
 * Ein/Aus-Schalter der Funktionen werden NICHT gespeichert: nach jedem Spielstart ist alles aus.
 */
public final class ActestConfig {

    public List<String> allowedServers = new ArrayList<>(List.of("localhost", "127.0.0.1"));

    /** Auto-Hit-Abstand in Zehntelblöcken (10 = 1,0 Blöcke, 80 = 8,0 Blöcke). */
    public int hitRangeTenths = 30;
    /** Ticks zwischen zwei Auto-Hits (20 Ticks = 1 Sekunde). */
    public int hitInterval = 10;
    /** Wie viele Ziele pro Auto-Hit-Runde gleichzeitig angegriffen werden (1 bis 10). */
    public int maxTargets = 3;
    /** B-Hop-Geschwindigkeit in Zehnteln (10 = normal, 30 = dreifach). */
    public int bhopSpeedTenths = 15;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static ActestConfig instance;

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("actest.json");
    }

    public static ActestConfig get() {
        if (instance == null) load();
        return instance;
    }

    public static void load() {
        ActestConfig loaded = null;
        try {
            if (Files.exists(path())) {
                loaded = GSON.fromJson(Files.readString(path(), StandardCharsets.UTF_8), ActestConfig.class);
            }
        } catch (Exception e) {
            System.err.println("[ACTest] Konfiguration konnte nicht gelesen werden: " + e);
        }
        if (loaded == null) loaded = new ActestConfig();
        if (loaded.allowedServers == null) loaded.allowedServers = new ArrayList<>();
        List<String> clean = new ArrayList<>();
        for (String s : loaded.allowedServers) {
            if (s != null && !s.isBlank()) clean.add(hostOnly(s));
        }
        loaded.allowedServers = clean;
        loaded.hitRangeTenths = Math.max(10, Math.min(80, loaded.hitRangeTenths));
        loaded.hitInterval = Math.max(1, Math.min(20, loaded.hitInterval));
        loaded.maxTargets = Math.max(1, Math.min(10, loaded.maxTargets));
        loaded.bhopSpeedTenths = Math.max(10, Math.min(40, loaded.bhopSpeedTenths));
        instance = loaded;
        save();
    }

    public static void save() {
        if (instance == null) return;
        try {
            Files.createDirectories(path().getParent());
            Files.writeString(path(), GSON.toJson(instance), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("[ACTest] Konfiguration konnte nicht gespeichert werden: " + e);
        }
    }

    /** "Play.Example.net:25565" -> "play.example.net" */
    public static String hostOnly(String address) {
        String h = address.trim().toLowerCase(Locale.ROOT);
        int first = h.indexOf(':');
        if (first > 0 && first == h.lastIndexOf(':')) {
            h = h.substring(0, first);
        }
        return h;
    }
}
