package dev.actest;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;

/** /pvp: Menü zum Ein- und Ausschalten der Testfunktionen. */
public class ActestScreen extends Screen {

    private static final int BG = 0xFF0C111D;
    private static final int BORDER = 0xFF1F2B45;
    private static final int HEADER = 0xFF111828;
    private static final int BTN = 0xFF141B2E;
    private static final int BTN_BORDER = 0xFF26334F;
    private static final int TEXT = 0xFFE6EAF2;
    private static final int MUTED = 0xFF8A94AB;
    private static final int DIM = 0xFF566079;
    private static final int ACCENT = 0xFF3CC8F0;
    private static final int OK = 0xFF55FF55;
    private static final int BAD = 0xFFFF5555;

    private static final int PW = 340;
    private static final int PH = 244;

    private static final class Toggle {
        final String label;
        final BooleanSupplier get;
        final Runnable flip;
        int x, y, w, h;
        float hover = 0f;

        Toggle(String label, BooleanSupplier get, Runnable flip) {
            this.label = label;
            this.get = get;
            this.flip = flip;
        }

        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private static final class Slider {
        final int min, max;
        final IntSupplier get;
        final IntConsumer set;
        final IntFunction<String> text;
        int x, y, w, h = 28;
        boolean dragging = false;

        Slider(int min, int max, IntSupplier get, IntConsumer set, IntFunction<String> text) {
            this.min = min;
            this.max = max;
            this.get = get;
            this.set = set;
            this.text = text;
        }

        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }

        int valueAt(double mx) {
            double frac = Math.max(0.0, Math.min(1.0, (mx - x) / (double) w));
            return (int) Math.round(min + frac * (max - min));
        }
    }

    private final List<Toggle> toggles = new ArrayList<>();
    private final List<Slider> sliders = new ArrayList<>();
    private int px, py;
    private long lastFrame = System.nanoTime();

    public ActestScreen() {
        super(Component.literal("AC Test"));
    }

    @Override
    protected void init() {
        px = (this.width - PW) / 2;
        py = Math.max(2, (this.height - PH) / 2);
        toggles.clear();
        sliders.clear();

        toggles.add(new Toggle("Auto-Hit", () -> Modules.autoHit, () -> Modules.autoHit = !Modules.autoHit));
        toggles.add(new Toggle("B-Hop", () -> Modules.bhop, () -> Modules.bhop = !Modules.bhop));
        toggles.add(new Toggle("Auto-Totem", () -> Modules.autoTotem, () -> Modules.autoTotem = !Modules.autoTotem));
        toggles.add(new Toggle("Auto-Sprint", () -> Modules.autoSprint, () -> Modules.autoSprint = !Modules.autoSprint));
        toggles.add(new Toggle("Ziel: Spieler", () -> Modules.targetPlayers, () -> Modules.targetPlayers = !Modules.targetPlayers));
        toggles.add(new Toggle("Ziel: Mobs", () -> Modules.targetMobs, () -> Modules.targetMobs = !Modules.targetMobs));

        int gap = 6;
        int gw = (PW - 40 - gap) / 2;
        for (int i = 0; i < toggles.size(); i++) {
            Toggle t = toggles.get(i);
            t.x = px + 20 + (i % 2) * (gw + gap);
            t.y = py + 62 + (i / 2) * 26;
            t.w = gw;
            t.h = 22;
        }

        ActestConfig cfg = ActestConfig.get();
        List<Slider> list = new ArrayList<>();
        list.add(new Slider(10, 80, () -> cfg.hitRangeTenths, v -> cfg.hitRangeTenths = v,
                v -> String.format(Locale.ROOT, "Abstand: %.1f Bl\u00F6cke", v / 10.0)));
        list.add(new Slider(1, 20, () -> cfg.hitInterval, v -> cfg.hitInterval = v,
                v -> String.format(Locale.ROOT, "Intervall: %d Ticks", v)));
        list.add(new Slider(1, 10, () -> cfg.maxTargets, v -> cfg.maxTargets = v,
                v -> "Max. Ziele: " + v));
        list.add(new Slider(10, 40, () -> cfg.bhopSpeedTenths, v -> cfg.bhopSpeedTenths = v,
                v -> String.format(Locale.ROOT, "B-Hop Tempo: x%.1f", v / 10.0)));
        for (int i = 0; i < list.size(); i++) {
            Slider sl = list.get(i);
            sl.x = px + 20 + (i % 2) * (gw + gap);
            sl.y = py + 148 + (i / 2) * 36;
            sl.w = gw;
            sliders.add(sl);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float delta) {
        super.extractRenderState(gfx, mouseX, mouseY, delta);

        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - lastFrame) / 1_000_000_000f);
        lastFrame = now;

        Minecraft mc = Minecraft.getInstance();
        boolean allowed = Guard.allowed(mc);

        int x1 = px, y1 = py, x2 = px + PW, y2 = py + PH;
        gfx.fill(x1 - 1, y1 - 1, x2 + 1, y2 + 1, 0x73000000);
        gfx.fill(x1, y1, x2, y2, BORDER);
        gfx.fill(x1 + 1, y1 + 1, x2 - 1, y2 - 1, BG);
        gfx.fill(x1 + 1, y1 + 1, x2 - 1, y1 + 28, HEADER);
        gfx.fill(x1 + 1, y1 + 28, x2 - 1, y1 + 29, ACCENT);

        int tx = x1 + 14, ty = y1 + 10;
        Component t1 = Component.literal("AC Test").withStyle(ChatFormatting.BOLD);
        gfx.text(this.font, t1, tx, ty, TEXT);
        tx += this.font.width(t1);
        Component t2 = Component.literal(" \u00BB ").withStyle(ChatFormatting.BOLD);
        gfx.text(this.font, t2, tx, ty, ACCENT);
        tx += this.font.width(t2);
        gfx.text(this.font, Component.literal("/pvp"), tx, ty, MUTED);

        // Status des Server-Schutzes
        String status;
        int statusColor;
        switch (Guard.state(mc)) {
            case ALLOWED -> {
                status = "Server: " + Guard.serverName(mc) + "  \u00B7  freigegeben";
                statusColor = OK;
            }
            case SINGLEPLAYER -> {
                status = "Einzelspieler  \u00B7  freigegeben";
                statusColor = OK;
            }
            default -> {
                status = "Server: " + Guard.serverName(mc) + "  \u00B7  gesperrt (nicht in actest.json)";
                statusColor = BAD;
            }
        }
        gfx.text(this.font, Component.literal(status), px + 20, py + 40, statusColor);

        // Schalter
        for (Toggle t : toggles) {
            boolean hovered = t.contains(mouseX, mouseY);
            t.hover += ((hovered ? 1f : 0f) - t.hover) * (1f - (float) Math.exp(-dt * 14f));
            boolean on = t.get.getAsBoolean();
            int accent = on ? OK : ACCENT;
            int border = lerp(BTN_BORDER, accent, t.hover);
            int bg = lerp(BTN, mix(accent, 0.22f), t.hover * 0.9f);
            gfx.fill(t.x, t.y, t.x + t.w, t.y + t.h, border);
            gfx.fill(t.x + 1, t.y + 1, t.x + t.w - 1, t.y + t.h - 1, bg);
            int midY = t.y + (t.h - this.font.lineHeight) / 2 + 1;
            gfx.text(this.font, Component.literal(t.label), t.x + 8, midY, allowed ? TEXT : DIM);
            Component val = Component.literal(on ? "An" : "Aus");
            gfx.text(this.font, val, t.x + t.w - 8 - this.font.width(val), midY, on ? OK : DIM);
        }

        // Regler
        for (Slider s : sliders) {
            int v = s.get.getAsInt();
            gfx.text(this.font, Component.literal(s.text.apply(v)), s.x, s.y, allowed ? TEXT : DIM);
            int ty2 = s.y + 17;
            float frac = (v - s.min) / (float) (s.max - s.min);
            int kx = s.x + Math.round(frac * s.w);
            gfx.fill(s.x, ty2, s.x + s.w, ty2 + 4, BTN_BORDER);
            gfx.fill(s.x, ty2, kx, ty2 + 4, ACCENT);
            gfx.fill(kx - 3, ty2 - 4, kx + 3, ty2 + 8, s.dragging || s.contains(mouseX, mouseY) ? 0xFFFFFFFF : TEXT);
        }

        Component hint = Component.literal("Alles startet ausgeschaltet. Freigabe: config/actest.json");
        gfx.text(this.font, hint, this.width / 2 - this.font.width(hint) / 2, py + PH - 14, DIM);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        if (event.button() != 0) return false;

        for (Slider s : sliders) {
            if (s.contains(event.x(), event.y())) {
                s.dragging = true;
                s.set.accept(s.valueAt(event.x()));
                return true;
            }
        }
        for (Toggle t : toggles) {
            if (t.contains(event.x(), event.y())) {
                Minecraft mc = Minecraft.getInstance();
                if (Guard.allowed(mc)) {
                    t.flip.run();
                    Minecraft.getInstance().getSoundManager()
                            .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        for (Slider s : sliders) {
            if (s.dragging) {
                s.set.accept(s.valueAt(event.x()));
                return true;
            }
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        boolean was = false;
        for (Slider s : sliders) {
            if (s.dragging) {
                s.dragging = false;
                was = true;
            }
        }
        if (was) {
            ActestConfig.save();
            return true;
        }
        return super.mouseReleased(event);
    }

    private static int lerp(int c1, int c2, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int r = Math.round(((c1 >> 16) & 0xFF) + (((c2 >> 16) & 0xFF) - ((c1 >> 16) & 0xFF)) * t);
        int g = Math.round(((c1 >> 8) & 0xFF) + (((c2 >> 8) & 0xFF) - ((c1 >> 8) & 0xFF)) * t);
        int b = Math.round((c1 & 0xFF) + ((c2 & 0xFF) - (c1 & 0xFF)) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int mix(int argb, float keep) {
        int r = Math.round(((argb >> 16) & 0xFF) * keep);
        int g = Math.round(((argb >> 8) & 0xFF) * keep);
        int b = Math.round((argb & 0xFF) * keep);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
