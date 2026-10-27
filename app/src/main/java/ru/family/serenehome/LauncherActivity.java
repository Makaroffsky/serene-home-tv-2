package ru.family.serenehome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Serene Home, an offline, native Android TV launcher for the Xiaomi A2.
 * No background services, polling networks, accessibility, or tuner mutation.
 * Built around 4 large D-pad accessible shortcuts. Hardware verification pending.
 */
public final class LauncherActivity extends Activity {
    private static final Locale RU = new Locale("ru", "RU");
    private static final String PREF_FILE = "serene_preferences";
    private static final String[] TITLES = {"Live TV", "VK Видео", "Кинопоиск", "VLC"};
    private static final String[] SUBTITLES = {"Каналы с антенны", "Видео и эфиры", "Фильмы и сериалы", "Ваши медиафайлы"};
    private static final String[] SYMBOLS = {"TV", "VK", "К", "VLC"};
    private static final String INPUTS_ACTION = "com.android.tv.action.VIEW_INPUTS";
    private static final String TUNER_PACKAGE = "com.mediatek.wwtv.tvcenter";
    // Observed on some MediaTek/Xiaomi firmwares; NOT guaranteed on the user's firmware.
    private static final String TUNER_ACTIVITY = "com.mediatek.wwtv.tvcenter.nav.TurnkeyUiMainActivity";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private SharedPreferences preferences;
    private TextView clockView;
    private TextView dateView;
    private LinearLayout dock;
    private final FrameLayout[] cards = new FrameLayout[4];
    private View appsButton;
    private View inputsButton;
    private View settingsButton;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            Date now = new Date();
            if (clockView != null) clockView.setText(new SimpleDateFormat("HH:mm", RU).format(now));
            if (dateView != null) dateView.setText(new SimpleDateFormat("EEE, d MMMM", RU).format(now));
            handler.postDelayed(this, 30_000);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        preferences = getSharedPreferences(PREF_FILE, MODE_PRIVATE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        render();
    }

    @Override protected void onResume() {
        super.onResume();
        handler.removeCallbacks(tick);
        handler.post(tick);
    }

    @Override protected void onPause() {
        handler.removeCallbacks(tick);
        super.onPause();
    }

    private void render() {
        float density = getResources().getDisplayMetrics().density;
        int w = (int) (getResources().getDisplayMetrics().widthPixels / density);
        int h = (int) (getResources().getDisplayMetrics().heightPixels / density);
        final boolean compact = w < 1100 || h < 470;
        final int margin = compact ? 29 : 47;

        FrameLayout root = new FrameLayout(this);
        root.setClipChildren(false);
        root.setClipToPadding(false);

        ImageView wallpaper = new ImageView(this);
        wallpaper.setImageResource(R.drawable.sunset);
        wallpaper.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(wallpaper, new FrameLayout.LayoutParams(-1, -1));
        root.addView(new ShadeView(), new FrameLayout.LayoutParams(-1, -1));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.TOP | Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);
        FrameLayout.LayoutParams hp = new FrameLayout.LayoutParams(-1, dp(82), Gravity.TOP);
        hp.leftMargin = hp.rightMargin = dp(margin);
        hp.topMargin = dp(compact ? 23 : 37);
        root.addView(header, hp);

        LinearLayout time = new LinearLayout(this);
        time.setOrientation(LinearLayout.VERTICAL);
        clockView = label("--:--", compact ? 40 : 49, Color.WHITE, false);
        clockView.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        time.addView(clockView);
        dateView = label("", compact ? 13 : 15, 0xfff1e8ec, false);
        LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(-2, -2);
        dp.topMargin = -dp(4);
        time.addView(dateView, dp);
        header.addView(time, new LinearLayout.LayoutParams(0, -2, 1f));

        LinearLayout commands = new LinearLayout(this);
        commands.setOrientation(LinearLayout.HORIZONTAL);
        commands.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(commands, new LinearLayout.LayoutParams(-2, dp(52)));
        appsButton = topButton(commands, "Все приложения", 0, () -> showAllApps());
        inputsButton = topButton(commands, "Источники сигнала", 1, () -> openInputs());
        settingsButton = topButton(commands, "Настройки", 2, () -> showSettings());

        LinearLayout dockArea = new LinearLayout(this);
        dockArea.setOrientation(LinearLayout.VERTICAL);
        dockArea.setClipChildren(false);
        dockArea.setClipToPadding(false);
        FrameLayout.LayoutParams dockPosition = new FrameLayout.LayoutParams(-1, dp(compact ? 174 : 207), Gravity.BOTTOM);
        dockPosition.leftMargin = dockPosition.rightMargin = dp(margin);
        dockPosition.bottomMargin = dp(compact ? 25 : 42);
        root.addView(dockArea, dockPosition);

        TextView eyebrow = label("ВАШ ДОМАШНИЙ ЭКРАН", 11, 0xfff2e6ea, true);
        eyebrow.setLetterSpacing(.18f);
        LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(-1, dp(26));
        dockArea.addView(eyebrow, ep);

        dock = new LinearLayout(this);
        dock.setGravity(Gravity.CENTER_VERTICAL);
        dock.setOrientation(LinearLayout.HORIZONTAL);
        dock.setClipChildren(false);
        dock.setClipToPadding(false);
        dockArea.addView(dock, new LinearLayout.LayoutParams(-1, dp(compact ? 119 : 145)));
        populateCards(compact);

        TextView hint = label("OK — открыть     •     Стрелки — выбрать     •     ⚙ — настройки", compact ? 11 : 12, 0xffd6c6cd, false);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(-1, -2);
        hintParams.topMargin = dp(11);
        dockArea.addView(hint, hintParams);

        setContentView(root);
        // Avoid focus jumping to top buttons on launch.
        cards[0].requestFocus();
    }

    private void populateCards(boolean compact) {
        dock.removeAllViews();
        for (int i = 0; i < 4; i++) {
            final int index = i;
            FrameLayout card = new FrameLayout(this);
            card.setId(View.generateViewId());
            card.setFocusable(true);
            card.setClickable(true);
            card.setClipChildren(false);
            card.setClipToPadding(false);
            card.setElevation(dp(6));
            card.setBackground(cardBackground(false));
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, -1, 1f);
            if (i != 0) cp.leftMargin = dp(compact ? 8 : 12);
            dock.addView(card, cp);
            cards[i] = card;

            LinearLayout content = new LinearLayout(this);
            content.setOrientation(LinearLayout.VERTICAL);
            content.setGravity(Gravity.CENTER_VERTICAL);
            content.setPadding(dp(compact ? 15 : 20), dp(12), dp(8), dp(9));
            card.addView(content, new FrameLayout.LayoutParams(-1, -1));

            Drawable icon = getShortcutIcon(index);
            if (icon != null) {
                ImageView appIcon = new ImageView(this);
                appIcon.setImageDrawable(icon);
                appIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
                content.addView(appIcon, new LinearLayout.LayoutParams(dp(compact ? 36 : 46), dp(compact ? 36 : 46)));
            } else {
                LogoView fallback = new LogoView(index);
                content.addView(fallback, new LinearLayout.LayoutParams(dp(compact ? 42 : 52), dp(compact ? 42 : 52)));
            }
            content.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1f));
            TextView name = label(TITLES[index], compact ? 17 : 19, Color.WHITE, true);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            content.addView(name);
            TextView subtitle = label(SUBTITLES[index], compact ? 10 : 11, 0xffede1e7, false);
            subtitle.setSingleLine(true);
            subtitle.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
            sp.topMargin = dp(3);
            content.addView(subtitle, sp);

            card.setContentDescription(TITLES[index] + ". " + SUBTITLES[index]);
            card.setOnClickListener(v -> openShortcut(index));
            card.setOnFocusChangeListener((v, focused) -> {
                card.animate().cancel();
                card.animate().scaleX(focused ? 1.055f : 1.0f)
                        .scaleY(focused ? 1.055f : 1.0f)
                        .setDuration(160L).start();
                card.setBackground(cardBackground(focused));
                card.setElevation(dp(focused ? 19 : 6));
            });
        }
        for (int i = 0; i < cards.length; i++) {
            cards[i].setNextFocusLeftId(cards[Math.max(0, i-1)].getId());
            cards[i].setNextFocusRightId(cards[Math.min(3, i+1)].getId());
            cards[i].setNextFocusUpId(i < 2 ? appsButton.getId() : settingsButton.getId());
        }
        appsButton.setNextFocusDownId(cards[0].getId());
        inputsButton.setNextFocusDownId(cards[2].getId());
        settingsButton.setNextFocusDownId(cards[3].getId());
    }

    private GradientDrawable cardBackground(boolean focused) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] { focused ? 0xbd4a485e : 0xb73b3948, focused ? 0xd62b2b39 : 0xc51b2030 });
        g.setCornerRadius(dp(18));
        g.setStroke(dp(focused ? 2 : 1), focused ? 0xfffce9eb : 0x60ffffff);
        return g;
    }

    private View topButton(LinearLayout target, String description, int glyph, Runnable action) {
        FrameLayout b = new FrameLayout(this);
        b.setId(View.generateViewId());
        b.setContentDescription(description);
        b.setBackground(buttonBackground(false));
        b.setFocusable(true);
        b.setClickable(true);
        GlyphView drawing = new GlyphView(glyph);
        b.addView(drawing, new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(47), dp(47));
        p.leftMargin = dp(11);
        target.addView(b, p);
        b.setOnClickListener(v -> action.run());
        b.setOnFocusChangeListener((v, focused) -> {
            b.setBackground(buttonBackground(focused));
            b.animate().scaleX(focused ? 1.09f : 1f).scaleY(focused ? 1.09f : 1f).setDuration(130).start();
        });
        return b;
    }

    private GradientDrawable buttonBackground(boolean selected) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(selected ? 0xa3a7a4b2 : 0x6946485e);
        drawable.setCornerRadius(dp(50));
        drawable.setStroke(dp(1), selected ? 0xeeffffff : 0x40ffffff);
        return drawable;
    }

    private TextView label(String text, int sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(sp);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setIncludeFontPadding(false);
        return view;
    }

    private int dp(float value) { return (int) (value * getResources().getDisplayMetrics().density + .5f); }

    private Intent launchByPackage(String pkg) {
        PackageManager pm = getPackageManager();
        try {
            Intent intent = pm.getLeanbackLaunchIntentForPackage(pkg);
            if (intent == null) intent = pm.getLaunchIntentForPackage(pkg);
            if (intent != null) return intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        } catch (RuntimeException ignored) { }
        return null;
    }

    private Intent findShortcut(int index) {
        String saved = preferences.getString("shortcut_" + index, null);
        if (saved != null) {
            ComponentName cn = ComponentName.unflattenFromString(saved);
            if (cn != null) {
                Intent it = new Intent(Intent.ACTION_MAIN);
                it.setComponent(cn);
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                return it;
            }
        }
        for (String pkg : AppMatcher.PACKAGES[index]) {
            Intent i = launchByPackage(pkg);
            if (i != null) return i;
        }
        List<AppItem> options = listApps();
        int bestScore = -1;
        Intent best = null;
        for (AppItem option : options) {
            int score = AppMatcher.score(index, option.pkg, option.name);
            // Only real matches are eligible. A negative score means unrelated app.
            if (score > 0 && score > bestScore) { bestScore = score; best = option.intent; }
        }
        if (best != null) return best;
        if (index == 0) {
            try {
                getPackageManager().getPackageInfo(TUNER_PACKAGE, 0);
                // This is a guarded attempt: activity class is vendor-firmware-specific.
                return new Intent(Intent.ACTION_MAIN).setClassName(TUNER_PACKAGE, TUNER_ACTIVITY)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            } catch (PackageManager.NameNotFoundException ignored) { }
        }
        return null;
    }

    private Drawable getShortcutIcon(int index) {
        try {
            Intent intent = findShortcut(index);
            if (intent == null) return null;
            if (intent.getComponent() != null) {
                return getPackageManager().getActivityIcon(intent.getComponent());
            }
            String pkg = intent.getPackage();
            if (pkg != null) return getPackageManager().getApplicationIcon(pkg);
        } catch (RuntimeException | PackageManager.NameNotFoundException ignored) { }
        return null;
    }

    private void openShortcut(int index) {
        Intent intent = findShortcut(index);
        if (intent != null) {
            try { startActivity(intent); return; }
            catch (RuntimeException exception) {
                if (index == 0) {
                    showTunerFallback();
                    return;
                }
            }
        }
        if (index == 0) { showTunerFallback(); return; }
        new AlertDialog.Builder(this).setTitle(TITLES[index] + " не найдено")
                .setMessage("Назначьте установленное приложение вручную.")
                .setPositiveButton("Выбрать", (d, w) -> chooseApp(index))
                .setNegativeButton("Отмена", null).show();
    }

    private void showTunerFallback() {
        new AlertDialog.Builder(this).setTitle("Live TV · антенные каналы")
                .setMessage("Не удалось открыть системное Live TV. На этой прошивке Xiaomi может потребоваться другая точка входа. Можно выбрать приложение вручную или попробовать открыть меню источников.")
                .setPositiveButton("Выбрать приложение", (d, w) -> chooseApp(0))
                .setNeutralButton("Источники", (d, w) -> openInputs())
                .setNegativeButton("Отмена", null).show();
    }

    private void openInputs() {
        try {
            startActivity(new Intent(INPUTS_ACTION).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (RuntimeException ex) {
            new AlertDialog.Builder(this).setTitle("Источники сигнала")
                    .setMessage("Системное меню выбора входов не предоставлено этой прошивкой. Используйте кнопку Source/Input на штатном пульте либо штатные настройки телевизора.")
                    .setPositiveButton("Настройки ТВ", (d, w) -> openSystemSettings())
                    .setNegativeButton("Закрыть", null).show();
        }
    }

    private void openSystemSettings() {
        try { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
        catch (RuntimeException ex) { Toast.makeText(this, "Настройки ТВ не открылись", Toast.LENGTH_LONG).show(); }
    }

    private void openHomeSettings() {
        try { startActivity(new Intent(Settings.ACTION_HOME_SETTINGS)); }
        catch (RuntimeException ex) { openSystemSettings(); }
    }

    private void showSettings() {
        final String[] options = { "Назначить Live TV", "Назначить VK Видео", "Назначить Кинопоиск",
                "Назначить VLC", "Все приложения", "Выбор домашнего экрана",
                "Настройки телевизора", "Информация и восстановление" };
        new AlertDialog.Builder(this).setTitle("Serene Home · настройки")
                .setItems(options, (dialog, item) -> {
                    if (item <= 3) chooseApp(item);
                    else if (item == 4) showAllApps();
                    else if (item == 5) openHomeSettings();
                    else if (item == 6) openSystemSettings();
                    else showAbout();
                })
                .setNegativeButton("Закрыть", null).show();
    }

    private void showAbout() {
        new AlertDialog.Builder(this).setTitle("Serene Home · 1.0 beta")
                .setMessage("Лёгкий офлайн-лаунчер для Xiaomi TV A2 на Android TV 10.\n\n" +
                        "Ничего не удаляет и не отключает. Настройки привязки приложений хранятся только на ТВ.\n\n" +
                        "Чтобы вернуться к прежнему HOME, откройте «Выбор домашнего экрана» либо удалите Serene Home из приложений. " +
                        "НЕ отключайте com.mediatek.wwtv.tvcenter: он нужен для антенны и входов HDMI.")
                .setPositiveButton("Понятно", null).show();
    }

    private void showAllApps() {
        showPicker(-1);
    }

    private void chooseApp(int index) {
        showPicker(index);
    }

    private void showPicker(int index) {
        List<AppItem> installed = listApps();
        if (installed.isEmpty()) {
            new AlertDialog.Builder(this).setTitle("Приложения не найдены")
                    .setMessage("Нет доступных для запуска приложений. Убедитесь, что они установлены на ТВ.")
                    .setPositiveButton("ОК", null).show();
            return;
        }
        String[] choices = new String[installed.size()];
        for (int i = 0; i < installed.size(); i++) {
            choices[i] = installed.get(i).name + "   ·   " + installed.get(i).pkg;
        }
        AlertDialog.Builder dialog = new AlertDialog.Builder(this)
                .setTitle(index < 0 ? "Все приложения" : "Назначить: " + TITLES[index])
                .setItems(choices, (d, which) -> {
                    AppItem selected = installed.get(which);
                    if (index < 0) {
                        try { startActivity(selected.intent); }
                        catch (RuntimeException ex) { Toast.makeText(this, "Не удалось открыть приложение", Toast.LENGTH_SHORT).show(); }
                    } else {
                        ComponentName cn = selected.intent.getComponent();
                        if (cn != null) {
                            preferences.edit().putString("shortcut_" + index, cn.flattenToString()).apply();
                            updateIcons();
                            Toast.makeText(this, "Сохранено: " + selected.name, Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("Отмена", null);
        if (index >= 0) {
            dialog.setNeutralButton("Сбросить привязку", (d, which) -> {
                preferences.edit().remove("shortcut_" + index).apply();
                updateIcons();
            });
        }
        dialog.show();
    }

    private void updateIcons() {
        // Redrawing four cards only is cheap, avoids re-decoding the 1080p wallpaper.
        boolean compact = getResources().getDisplayMetrics().widthPixels /
                getResources().getDisplayMetrics().density < 1100 ||
                getResources().getDisplayMetrics().heightPixels /
                getResources().getDisplayMetrics().density < 470;
        populateCards(compact);
        cards[0].requestFocus();
    }

    private static final class AppItem {
        final String name;
        final String pkg;
        final Intent intent;
        AppItem(String n, String p, Intent i) { name = n; pkg = p; intent = i; }
    }

    private List<AppItem> listApps() {
        PackageManager pm = getPackageManager();
        List<AppItem> all = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String category : new String[] { Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER }) {
            Intent query = new Intent(Intent.ACTION_MAIN).addCategory(category);
            for (ResolveInfo resolve : pm.queryIntentActivities(query, 0)) {
                ActivityInfo info = resolve.activityInfo;
                if (info == null || info.packageName.equals(getPackageName()) || !seen.add(info.packageName)) continue;
                Intent open = new Intent(Intent.ACTION_MAIN).addCategory(category);
                open.setComponent(new ComponentName(info.packageName, info.name));
                open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                CharSequence label = resolve.loadLabel(pm);
                all.add(new AppItem(label == null ? info.packageName : label.toString(), info.packageName, open));
            }
        }
        Collections.sort(all, Comparator.comparing(a -> a.name.toLowerCase(RU)));
        return all;
    }

    private final class ShadeView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Shader topGradient;
        private Shader lowerGradient;
        ShadeView() { super(LauncherActivity.this); }
        @Override protected void onSizeChanged(int w, int h, int oldW, int oldH) {
            super.onSizeChanged(w, h, oldW, oldH);
            topGradient = new LinearGradient(0, 0, 0, h * .27f,
                    0xa10b1021, 0x000b1021, Shader.TileMode.CLAMP);
            lowerGradient = new LinearGradient(0, h * .48f, 0, h,
                    0x00070b17, 0xb8080b18, Shader.TileMode.CLAMP);
        }
        @Override protected void onDraw(Canvas canvas) {
            paint.setShader(topGradient);
            canvas.drawRect(0, 0, getWidth(), getHeight() * .27f, paint);
            paint.setShader(lowerGradient);
            canvas.drawRect(0, getHeight() * .48f, getWidth(), getHeight(), paint);
            paint.setShader(null);
        }
    }

    private final class LogoView extends View {
        private final int which;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        LogoView(int item) { super(LauncherActivity.this); which = item; }
        @Override protected void onDraw(Canvas c) {
            float s = Math.min(getWidth(), getHeight());
            float x = (getWidth()-s)/2, y=(getHeight()-s)/2;
            p.setColor(which == 1 ? 0xff1d7de9 : which == 2 ? 0xff0b0b12 :
                    which == 3 ? 0xffee7f24 : 0x5bffffff);
            c.drawRoundRect(new RectF(x+1,y+1,x+s-1,y+s-1),s*.25f,s*.25f,p);
            p.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
            p.setTextSize(s * (which == 3 ? .26f : .40f));
            p.setTextAlign(Paint.Align.CENTER);
            p.setColor(which == 2 ? 0xffff8a08 : Color.WHITE);
            Paint.FontMetrics fm = p.getFontMetrics();
            c.drawText(SYMBOLS[which],x+s/2,y+s/2-(fm.ascent+fm.descent)/2,p);
        }
    }

    private final class GlyphView extends View {
        private final int mode;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        GlyphView(int type) { super(LauncherActivity.this); mode=type; }
        @Override protected void onDraw(Canvas c) {
            float a = getWidth()/24f, b=getHeight()/24f;
            c.save(); c.scale(a,b);
            p.setColor(Color.WHITE); p.setStrokeWidth(1.8f); p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeJoin(Paint.Join.ROUND);
            if (mode == 0) {
                c.drawRoundRect(4,4,10,10,1,1,p); c.drawRoundRect(14,4,20,10,1,1,p);
                c.drawRoundRect(4,14,10,20,1,1,p); c.drawRoundRect(14,14,20,20,1,1,p);
            } else if (mode == 1) {
                c.drawRoundRect(3,5,21,17,2,2,p); c.drawLine(9,20,15,20,p);
                c.drawLine(12,17,12,20,p); c.drawLine(12,9,16,13,p); c.drawLine(16,13,20,9,p);
            } else {
                c.drawCircle(12,12,6,p);
                for (int i=0;i<8;i++) {
                    double theta = Math.PI * i / 4;
                    c.drawLine(12+8*(float)Math.cos(theta), 12+8*(float)Math.sin(theta),
                            12+10*(float)Math.cos(theta), 12+10*(float)Math.sin(theta),p);
                }
                c.drawCircle(12,12,2,p);
            }
            p.setStyle(Paint.Style.FILL);
            c.restore();
        }
    }
}
