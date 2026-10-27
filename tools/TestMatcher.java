package ru.family.serenehome;
public class TestMatcher {
    static void check(boolean statement) { if (!statement) throw new AssertionError("Matcher test failed"); }
    public static void main(String[] args) {
        check(AppMatcher.score(0,"com.mediatek.wwtv.tvcenter","Live TV") == 100);
        check(AppMatcher.score(0,"com.random.streaming","Live Channel") < 0);
        check(AppMatcher.score(1,"com.vk.tv","VK Видео") == 100);
        check(AppMatcher.score(2,"ru.kinopoisk.tv","Кинопоиск") == 100);
        check(AppMatcher.score(3,"org.videolan.vlc","VLC") == 100);
        check(AppMatcher.score(1,"org.videolan.vlc","VLC") < 0);
        check(AppMatcher.score(3,"x","vlc for Android") > 0);
        check(AppMatcher.score(7,"any", "any") == -1);
        System.out.println("All 8 matcher checks passed");
    }
}
