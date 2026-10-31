package dev.actest;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Die Testfunktionen. Zweck: dem Anticheat des eigenen Servers verdächtiges Verhalten liefern.
 * Alle Funktionsschalter sind nur im Speicher und beim Start immer aus.
 */
public final class Modules {

    private Modules() {
    }

    public static boolean autoHit = false;
    public static boolean bhop = false;
    public static boolean autoTotem = false;
    public static boolean autoSprint = false;

    /** Ziele für Auto-Hit. */
    public static boolean targetPlayers = true;
    public static boolean targetMobs = true;

    /** Normale Sprintgeschwindigkeit in Blöcken pro Tick (ca. 5,6 Blöcke/s). */
    private static final double BASE_SPEED = 0.28;

    private static int hitTicks = 0;
    private static int totemTicks = 0;
    private static boolean jumpForced = false;
    private static boolean warned = false;

    public static boolean anyOn() {
        return autoHit || bhop || autoTotem || autoSprint;
    }

    public static void disableAll() {
        autoHit = false;
        bhop = false;
        autoTotem = false;
        autoSprint = false;
        hitTicks = 0;
    }

    public static void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || mc.gameMode == null) {
            disableAll();
            return;
        }

        if (!Guard.allowed(mc)) {
            if (anyOn()) {
                disableAll();
                if (!warned) {
                    warned = true;
                    p.sendSystemMessage(Component.literal(
                            "[ACTest] Gesperrt: Dieser Server steht nicht in config/actest.json (allowedServers)."));
                }
            }
            releaseJump(mc);
            return;
        }
        warned = false;

        // Bei offenem Menü/Chat nichts tun
        if (mc.screen != null) {
            releaseJump(mc);
            return;
        }

        boolean fwd = mc.options.keyUp.isDown();
        boolean back = mc.options.keyDown.isDown();
        boolean left = mc.options.keyLeft.isDown();
        boolean right = mc.options.keyRight.isDown();
        boolean moving = fwd || back || left || right;

        if (autoSprint && fwd) {
            mc.options.keySprint.setDown(true);
        }

        if (bhop && moving) {
            // Dauerhaft springen, sobald man am Boden ist
            if (p.onGround()) {
                mc.options.keyJump.setDown(true);
                jumpForced = true;
            } else {
                releaseJump(mc);
            }
            boost(p, fwd, back, left, right);
        } else {
            releaseJump(mc);
        }

        if (autoTotem && ++totemTicks >= 4) {
            totemTicks = 0;
            tryTotem(mc, p);
        }

        if (autoHit) {
            hitTicks++;
            autoHit(mc, p);
        } else {
            hitTicks = 0;
        }
    }

    private static void releaseJump(Minecraft mc) {
        if (jumpForced) {
            mc.options.keyJump.setDown(false);
            jumpForced = false;
        }
    }

    /** Setzt die horizontale Geschwindigkeit auf (BASE_SPEED * Faktor) in Laufrichtung. */
    private static void boost(LocalPlayer p, boolean fwd, boolean back, boolean left, boolean right) {
        if (p.isInWater()) return;

        double forward = (fwd ? 1 : 0) - (back ? 1 : 0);
        double strafe = (left ? 1 : 0) - (right ? 1 : 0); // links = positiv
        double len = Math.sqrt(forward * forward + strafe * strafe);
        if (len == 0) return;
        forward /= len;
        strafe /= len;

        double yaw = Math.toRadians(p.getYRot());
        double dx = -Math.sin(yaw) * forward + Math.cos(yaw) * strafe;
        double dz = Math.cos(yaw) * forward + Math.sin(yaw) * strafe;

        double speed = BASE_SPEED * (ActestConfig.get().bhopSpeedTenths / 10.0);
        Vec3 v = p.getDeltaMovement();
        p.setDeltaMovement(dx * speed, v.y, dz * speed);
    }

    private static boolean isTarget(Entity e, LocalPlayer p, double rangeSq) {
        if (e == p || !(e instanceof LivingEntity le) || !le.isAlive()) return false;
        if (e instanceof Player) {
            if (!targetPlayers || e.isSpectator()) return false;
        } else if (e instanceof Mob) {
            if (!targetMobs) return false;
        } else {
            return false; // z. B. Rüstungsständer
        }
        return p.distanceToSqr(e) <= rangeSq;
    }

    /**
     * Schlägt bis zu maxTargets Ziele gleichzeitig: ohne Blickrichtung, ohne Sichtlinie (auch durch Wände).
     * Ziele sind Spieler und/oder Mobs im eingestellten Abstand, die nächsten zuerst.
     */
    private static void autoHit(Minecraft mc, LocalPlayer p) {
        ActestConfig cfg = ActestConfig.get();
        if (hitTicks < cfg.hitInterval) return;

        double range = cfg.hitRangeTenths / 10.0;
        double rangeSq = range * range;
        List<Entity> found = new ArrayList<>(
                mc.level.getEntities(p, p.getBoundingBox().inflate(range), e -> isTarget(e, p, rangeSq)));
        if (found.isEmpty()) return;

        found.sort((a, b) -> Double.compare(p.distanceToSqr(a), p.distanceToSqr(b)));

        hitTicks = 0;
        int count = Math.min(cfg.maxTargets, found.size());
        for (int i = 0; i < count; i++) {
            mc.gameMode.attack(p, found.get(i));
        }
        p.swing(InteractionHand.MAIN_HAND);
    }

    /** Legt einen Totem aus dem Inventar in die zweite Hand, falls dort keiner liegt. */
    private static void tryTotem(Minecraft mc, LocalPlayer p) {
        if (p.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) return;
        Inventory inv = p.getInventory();
        MultiPlayerGameMode gm = mc.gameMode;
        for (int i = 0; i < 36; i++) {
            if (inv.getItem(i).is(Items.TOTEM_OF_UNDYING)) {
                // Inventar-Index -> Slot im Spielermenü: Hotbar 0-8 = Slot 36-44, Rest unverändert
                int slot = i < 9 ? 36 + i : i;
                // Button 40 = Tausch mit der zweiten Hand
                gm.handleContainerInput(p.containerMenu.containerId, slot, 40, ContainerInput.SWAP, p);
                return;
            }
        }
    }
}
