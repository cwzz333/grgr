package com.vladik.godbox;

import net.minecraft.item.Item;
import net.minecraft.item.Items;

/** Вкладки панели сил. v3.0: у каждой вкладки есть иконка для боковой панели. */
public enum Category {
    BOMBS("Бомбы", Items.TNT),
    DISASTERS("Стихии", Items.LAVA_BUCKET),
    DIVINE("Божественное", Items.TOTEM_OF_UNDYING),
    LIFE("Существа", Items.EGG),
    BOSSES("Боссы", Items.DRAGON_HEAD),
    KINGDOMS("Королевства", Items.WHITE_BANNER),
    CIVIL("Цивилизация", Items.BELL),
    TERRAIN("Земля", Items.GRASS_BLOCK),
    BIOMES("Биомы", Items.OAK_SAPLING),
    BUILD("Постройки", Items.BRICKS),
    GENERATE("Генерация", Items.FILLED_MAP),
    LAWS("Законы", Items.WRITABLE_BOOK),
    WORLD("Мир", Items.COMPASS);

    public final String title;
    public final Item icon;

    Category(String title, Item icon) {
        this.title = title;
        this.icon = icon;
    }
}
