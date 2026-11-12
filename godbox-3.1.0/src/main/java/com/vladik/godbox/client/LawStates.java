package com.vladik.godbox.client;

import com.vladik.godbox.Category;
import com.vladik.godbox.Power;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * v3.1: состояние законов на клиенте. Сервер присылает его маской в HUD-пакете (sync), а ответ
 * «Закон «…»: включён/выключен» обновляет его сразу после переключения; до первого пакета состояние неизвестно.
 */
final class LawStates {
    private static final Map<Power, Boolean> STATE = new EnumMap<>(Power.class);
    private static boolean registered;

    private LawStates() {}

    static void init() {
        if (registered) return;
        registered = true;
        ClientReceiveMessageEvents.GAME.register(LawStates::onMessage);
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> STATE.clear());
    }

    /** true — включён, false — выключен, null — неизвестно. */
    static Boolean get(Power p) {
        return p == null ? null : STATE.get(p);
    }

    /** Маска из HUD-пакета сервера: бит = порядковый номер закона среди сил категории LAWS. */
    static void sync(long mask) {
        int i = 0;
        for (Power p : Power.values()) {
            if (p.category != Category.LAWS) continue;
            if (i < 64) STATE.put(p, (mask >> i & 1L) != 0);
            i++;
        }
    }

    private static void onMessage(Text message, boolean overlay) {
        try {
            String s = message.getString();
            parse(s);
            // HUD в режиме бога скрыт, поэтому ответы сервера над хотбаром показываем на экране бога
            if (overlay && MinecraftClient.getInstance().currentScreen instanceof GodScreen gs) {
                TextColor c = message.getStyle().getColor();
                gs.note(s, c == null ? Ui.WHITE : 0xFF000000 | c.getRgb());
            }
        } catch (Exception ignored) {
        }
    }

    private static void parse(String s) {
        String head = "Закон «";
        if (!s.startsWith(head)) return;
        int e = s.indexOf("»: ");
        if (e < 0) return;
        String title = s.substring(head.length(), e);
        String rest = s.substring(e + 3).toLowerCase(Locale.ROOT);
        Boolean on = rest.startsWith("вкл") ? Boolean.TRUE : rest.startsWith("выкл") ? Boolean.FALSE : null;
        if (on == null) return;
        for (Power p : Power.values())
            if (p.category == Category.LAWS && p.title.equals(title)) STATE.put(p, on);
    }
}
