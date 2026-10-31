package dev.actest;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public class ActestMod implements ClientModInitializer {

    /** Der Chat schließt sich nach dem Befehl selbst und würde unser Menü überschreiben,
     *  deshalb öffnen wir es erst im nächsten Client-Tick. */
    private static boolean pendingOpen = false;

    @Override
    public void onInitializeClient() {
        ActestConfig.load();

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) ->
                dispatcher.register(ClientCommands.literal("pvp").executes(ctx -> {
                    pendingOpen = true;
                    return 1;
                })));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (pendingOpen) {
                pendingOpen = false;
                mc.setScreen(new ActestScreen());
            }
            Modules.tick(mc);
        });
    }
}
