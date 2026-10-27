package ru.family.serenehome;

import java.util.Locale;

/** Pure Java matching rules (can be unit-tested without Android). */
final class AppMatcher {
    static final String[][] PACKAGES = {
        {"com.mediatek.wwtv.tvcenter", "com.google.android.tv", "com.android.tv"},
        {"com.vk.tv"},
        {"ru.kinopoisk.tv", "ru.kinopoisk"},
        {"org.videolan.vlc"}
    };

    private AppMatcher() { }

    static int score(int tile, String packageName, String label) {
        if (tile < 0 || tile >= PACKAGES.length) return -1;
        String pkg = packageName == null ? "" : packageName.toLowerCase(Locale.ROOT);
        String name = label == null ? "" : label.toLowerCase(Locale.forLanguageTag("ru-RU")).trim();
        for (int i = 0; i < PACKAGES[tile].length; i++) {
            if (PACKAGES[tile][i].equals(pkg)) return 100 - i;
        }
        switch (tile) {
            case 0:
                return name.equals("live tv") || name.equals("live television") || name.equals("телевидение")
                        || name.equals("тв-каналы") || name.equals("каналы") ? 30 : -1;
            case 1:
                return name.contains("vk видео") || name.contains("вк видео") || name.contains("vk video") ? 30 : -1;
            case 2:
                return name.contains("кинопоиск") || name.contains("kinopoisk") ? 30 : -1;
            case 3:
                return name.equals("vlc") || name.equals("vlc for android") ? 30 : -1;
            default: return -1;
        }
    }
}
