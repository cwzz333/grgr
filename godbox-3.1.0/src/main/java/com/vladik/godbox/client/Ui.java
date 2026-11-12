package com.vladik.godbox.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.tooltip.TooltipPositioner;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.MathHelper;
import org.joml.Vector2i;

import java.util.Locale;

/** v3.1: общая палитра, отступы и мелкие помощники интерфейса бога. */
final class Ui {
    // палитра
    static final int BG = 0xC0101018, BG_SOLID = 0xF0121218, LINE = 0x60FFFFFF;
    static final int CELL = 0xC0121218, CELL_HOVER = 0xE0484858, CELL_SEL = 0xE0403010, CELL_SOLID = 0xC0262630;
    static final int GOLD = 0xFFFFD700, GOLD_DARK = 0xFFB8860B, GOLD_LIGHT = 0xFFFFE680;
    static final int TEXT = 0xFFDDDDDD, MUTED = 0xFFBBBBBB, DIM = 0xFF808090, WHITE = 0xFFFFFFFF;
    static final int BLUE = 0xFF9AD0FF, AQUA = 0xFF40A0E0, RED = 0xFFE04040, GREEN = 0xFF55E070;
    static final int LAW_ON = 0xC0143A1C, LAW_OFF = 0xC03A1616;
    // отступы
    static final int PAD = 4, GAP = 2;

    private Ui() {}

    static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /** Обрезает строку до ширины, добавляя «…». */
    static String ellipsis(TextRenderer tr, String s, int w) {
        if (s == null || w <= 0) return "";
        if (tr.getWidth(s) <= w) return s;
        int dots = tr.getWidth("…");
        if (w <= dots) return "";
        return tr.trimToWidth(s, w - dots) + "…";
    }

    /** Для поиска: нижний регистр, «ё» = «е». */
    static String norm(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replace('ё', 'е');
    }

    /** Обычный щелчок кнопки, как в меню игры. */
    static void click() {
        MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    /** Подсказка у курсора, которая не выходит за края экрана. */
    static final TooltipPositioner TIP = (sw, sh, x, y, w, h) -> {
        int tx = x + 12;
        if (tx + w + 4 > sw) tx = x - 12 - w;
        tx = MathHelper.clamp(tx, 4, Math.max(4, sw - w - 4));
        int ty = MathHelper.clamp(y - 12, 4, Math.max(4, sh - h - 4));
        return new Vector2i(tx, ty);
    };
}
