package com.example.livescreentranslation;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/**
 * Tests the real release UI using framework/JDK APIs only. No application, SDK,
 * Kotlin, or AndroidX symbols are linked, so the test cannot change app R8 rules.
 * The host prepares synthetic fixture models and our app's overlay permission.
 */
public final class ReleasePrivacyInstrumentation extends Instrumentation {
    private String stage = "startup";
    private String scenario = "privacy";
    private String source = "en";
    private String target;
    private SharedPreferences preferences;
    private String originalSource;
    private String originalTarget;
    private boolean settingsOverridden;
    private boolean readingSettingsOverridden;
    private Integer originalCardText;
    private Integer originalCardHeight;
    private boolean practiceVerified;
    private boolean screenshotRequested;
    private boolean longPractice;
    private boolean rotationSettingsSaved;
    private int originalDisplayRotation;
    private int originalAutoRotation;
    private int originalUserRotation;
    private String originalAutoRotationSetting;
    private String originalUserRotationSetting;
    private String layoutProbe = "not-measured";
    private static final String PACKAGE = "com.example.livescreentranslation";
    private static final String SERVICE = PACKAGE + ".capture.ScreenTranslationService";

    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        if (arguments != null) {
            scenario = arguments.getString("scenario", "privacy");
            source = arguments.getString("source", "en");
            target = arguments.getString("target");
            screenshotRequested = arguments.getString("qaScreenshot", "false").equals("true");
        }
        if (target == null) target = source.equals("ko") ? "en" : "ko";
        start();
    }

    @Override public void onStart() {
        Bundle status = new Bundle();
        status.putString("id", "ScreenLingoReleasePrivacy");
        status.putString("class", getClass().getName());
        status.putString("test", scenario + "-" + source + "-" + target);
        status.putInt("numtests", 1);
        status.putInt("current", 1);
        sendStatus(1, status);
        boolean passed = false;
        try {
            require(scenario.equals("reading-settings") || scenario.equals("network-guidance") || scenario.equals("privacy") || scenario.equals("reading-limits") || scenario.equals("reading-content") || scenario.equals("reading-rotation") || scenario.equals("revoke-overlay") ||
                    scenario.equals("screen-lock") || scenario.equals("remove-task"));
            require(PACKAGE.equals(getTargetContext().getPackageName()));
            require((getTargetContext().getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) == 0);
            require(isLanguage(source) && isLanguage(target) && !source.equals(target));
            require(scenario.equals("privacy") || scenario.equals("reading-content") || scenario.equals("reading-rotation") ||
                    (source.equals("en") && target.equals("ko")));
            // Fixture setup uses the real saved-settings path, without linking app/SDK symbols.
            preferences = getTargetContext().getSharedPreferences("screenlingo_settings", Context.MODE_PRIVATE);
            originalSource = preferences.getString("source", null);
            originalTarget = preferences.getString("target", null);
            settingsOverridden = true;
            require(preferences.edit().putString("source", source)
                    .putString("target", target).commit());
            // Capture fixtures use known default geometry, then restore user choices.
            if (!scenario.equals("network-guidance")) {
                originalCardText = preferences.contains("cardTextSp") ? preferences.getInt("cardTextSp", 16) : null;
                originalCardHeight = preferences.contains("cardHeightDp") ? preferences.getInt("cardHeightDp", 300) : null;
                readingSettingsOverridden = true;
                require(preferences.edit().putInt("cardTextSp", 16).putInt("cardHeightDp", 300).commit());
            }
            AccessibilityServiceInfo info = getUiAutomation().getServiceInfo();
            info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
            getUiAutomation().setServiceInfo(info);
            Intent launch = getTargetContext().getPackageManager()
                    .getLaunchIntentForPackage(getTargetContext().getPackageName());
            require(launch != null);
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivitySync(launch);
            waitForIdleSync();

            if (scenario.equals("network-guidance")) {
                verifyNetworkGuidance();
                passed = true;
                status.putString("stream", ".");
                sendStatus(0, status);
                return;
            }
            if (scenario.equals("reading-settings")) chooseReadingSettings();

            stage = "start";
            clickLabel("화면 번역 시작");
            stage = "disclosure";
            clickLabel("화면 공유 계속");
            stage = "android-consent";
            await(20000, () -> {
                AccessibilityNodeInfo allow = findNode(node ->
                        "com.android.permissioncontroller:id/permission_allow_button".equals(node.getViewIdResourceName()));
                if (allow != null) allow.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                AccessibilityNodeInfo share = findNode(node -> {
                    String value = label(node);
                    return value.equals("Share screen") || value.equals("Start now") ||
                            value.equals("화면 공유") || value.equals("시작하기") || value.equals("지금 시작");
                });
                return share != null && share.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            });
            stage = "practice";
            clickLabel("번역 연습 화면 열기");
            stage = "practice-source";
            await(10000, () -> findNode(node -> label(node).contains(sourceFixture())) != null);
            if (source.equals("en")) {
                await(10000, () -> findNode(node -> label(node).contains("Find your") &&
                        label(node).contains("next little adventure.")) != null);
            }
            practiceVerified = true;
            if (scenario.equals("reading-settings")) movePracticeBubble();
            stage = "translation";
            // Only a native overlay TextView with the target's authored-text anchors counts.
            await(45000, () -> translationNode() != null);
            String firstTranslation = translationNode().getText().toString();

            if (scenario.equals("reading-settings")) {
                verifyReadingDimensions();
                verifyReadingRotation();
            } else if (scenario.equals("reading-limits")) {
                verifyLongReading(true, firstTranslation);
            } else if (scenario.equals("reading-content")) {
                verifyLongReading(false, firstTranslation);
            } else if (scenario.equals("reading-rotation")) {
                verifyReadingRotation();
            } else if (scenario.equals("privacy")) {
                stage = "pause";
                clickLabel("멈춤");
                await(5000, () -> findNode(node -> label(node).equals("재개")) != null &&
                        translationNode() == null);
                stage = "resume";
                clickLabel("재개");
                await(45000, () -> translationNode() != null);
            } else {
                verifyLifecycle();
            }

            stage = "log-check";
            if (screenshotRequested) savePracticeScreenshot();
            android.util.Log.w("ScreenLingoReleaseFixture", "Fixed authored release diagnostic probe.");
            String logs = processLog();
            require(logs.contains("Runtime diagnostic redacted to protect screen text."));
            require(!logs.contains(sourceFixture()));
            require(!logs.contains(firstTranslation));
            if (source.equals("en")) require(!logs.contains("경치"));
            if (scenario.equals("reading-settings") || scenario.equals("privacy") || scenario.equals("reading-limits") || scenario.equals("reading-content") || scenario.equals("reading-rotation")) {
                AccessibilityNodeInfo node = translationNode();
                require(node != null && node.getText() != null);
                require(!logs.contains(node.getText().toString()));
            }
            require(!logs.contains("Full translation:") && !logs.contains("Query (after preprocess):") &&
                    !logs.contains("Translation (after detokenizer):"));
            passed = true;
            status.putString("stream", ".");
            sendStatus(0, status);
        } catch (Throwable failure) {
            // Never print exception contents, a view tree, screenshot or text fixture.
            savePracticeScreenshot();
            String sourceFacts = longSourceFacts();
            status.putString("stream", "Release privacy regression failed at " + stage +
                    " (" + failure.getClass().getSimpleName() + ")." + failureState() + sourceFacts + networkFacts());
            sendStatus(-2, status);
        } finally {
            getTargetContext().stopService(new Intent().setClassName(getTargetContext().getPackageName(),
                    SERVICE));
            // Only restore fixture state that this selected scenario changed.
            if (scenario.equals("revoke-overlay")) shellOutput("appops set " + PACKAGE + " SYSTEM_ALERT_WINDOW allow");
            if (scenario.equals("screen-lock")) wakeFixture();
            try { restoreRotation(); } catch (Throwable ignored) {
                passed = false;
                status.putString("stream", "Fixture rotation settings could not be restored.");
                sendStatus(-2, status);
            }
            if (settingsOverridden) {
                if (!preferences.edit().putString("source", originalSource).putString("target", originalTarget).commit()) {
                    passed = false;
                    status.putString("stream", "Fixture language settings could not be restored.");
                    sendStatus(-2, status);
                }
            }
            if (readingSettingsOverridden) {
                SharedPreferences.Editor editor = preferences.edit();
                if (originalCardText == null) editor.remove("cardTextSp"); else editor.putInt("cardTextSp", originalCardText);
                if (originalCardHeight == null) editor.remove("cardHeightDp"); else editor.putInt("cardHeightDp", originalCardHeight);
                if (!editor.commit()) {
                    passed = false;
                    status.putString("stream", "Fixture reading settings could not be restored.");
                    sendStatus(-2, status);
                }
            }
            Bundle result = new Bundle();
            result.putString("stream", passed ? "OK (1 test)" : "FAILURES (1 test)");
            finish(passed ? Activity.RESULT_OK : Activity.RESULT_CANCELED, result);
        }
    }

    private String processLog() {
        return shellOutput("logcat -d --pid=" + Process.myPid() + " -v brief");
    }

    private void chooseReadingSettings() {
        stage = "reading-options";
        clickTab("설정");
        stage = "reading-font-choice";
        clickReadingOption("아주 큰 글자", "cardTextSp", 24);
        stage = "reading-height-choice";
        clickReadingOption("높은 카드", "cardHeightDp", 420);
        stage = "reading-choice-saved";
        await(5000, () -> preferences.getInt("cardTextSp", 0) == 24 && preferences.getInt("cardHeightDp", 0) == 420);
        stage = "reading-relaunch";
        Intent launch = getTargetContext().getPackageManager().getLaunchIntentForPackage(PACKAGE);
        require(launch != null);
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivitySync(launch);
        waitForIdleSync();
    }

    private void clickReadingOption(String value, String preferenceKey, int expected) {
        int[] direction = {AccessibilityNodeInfo.ACTION_SCROLL_FORWARD};
        await(20000, () -> {
            if (preferences.getInt(preferenceKey, 0) == expected) return true;
            AccessibilityNodeInfo option = findNode(node -> node.isVisibleToUser() &&
                    (label(node).equals(value) || label(node).startsWith(value + "\n") || label(node).startsWith(value + ",")));
            AccessibilityNodeInfo scroll = findNode(node -> node.isScrollable() &&
                    PACKAGE.contentEquals(node.getPackageName() == null ? "" : node.getPackageName()));
            if (option != null) {
                layoutProbe = "reading-option-visible,enabled=" + option.isEnabled() + ",saved=" + preferences.getInt(preferenceKey, 0);
                if (!option.isEnabled()) return false;
                Rect bounds = new Rect();
                option.getBoundsInScreen(bounds);
                if (bounds.isEmpty()) return false;
                Rect viewport = new Rect();
                if (scroll != null) scroll.getBoundsInScreen(viewport);
                if (scroll == null || viewport.contains(bounds.centerX(), bounds.centerY())) {
                    // Use the observed label bounds for the same touch path as a user.
                    shellOutput("input tap " + bounds.centerX() + " " + bounds.centerY());
                    return false;
                }
                direction[0] = bounds.centerY() < viewport.top
                        ? AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD : AccessibilityNodeInfo.ACTION_SCROLL_FORWARD;
            }
            layoutProbe = "reading-option-not-ready,scroll=" + (scroll != null);
            if (scroll != null && !scroll.performAction(direction[0])) {
                direction[0] = direction[0] == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                        ? AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD : AccessibilityNodeInfo.ACTION_SCROLL_FORWARD;
                scroll.performAction(direction[0]);
            }
            return false;
        });
    }

    private void movePracticeBubble() {
        stage = "reading-bubble-position";
        AccessibilityNodeInfo bubble = findNode(node -> "번역 카드 접기 또는 펼치기. 끌어서 이동".contentEquals(
                node.getContentDescription() == null ? "" : node.getContentDescription()));
        require(bubble != null);
        Rect bounds = new Rect();
        bubble.getBoundsInScreen(bounds);
        float density = getTargetContext().getResources().getDisplayMetrics().density;
        int endX = displaySize().x - Math.round(40 * density);
        int endY = Math.round(60 * density);
        shellOutput("input swipe " + bounds.centerX() + " " + bounds.centerY() + " " + endX + " " + endY + " 500");
        await(5000, () -> {
            AccessibilityNodeInfo moved = findNode(node -> "번역 카드 접기 또는 펼치기. 끌어서 이동".contentEquals(
                    node.getContentDescription() == null ? "" : node.getContentDescription()));
            AccessibilityNodeInfo sourceTitle = findNode(node -> label(node).contains("Find your") && label(node).contains("next little adventure."));
            if (moved == null || sourceTitle == null) return false;
            Rect newBounds = new Rect();
            Rect titleBounds = new Rect();
            moved.getBoundsInScreen(newBounds);
            sourceTitle.getBoundsInScreen(titleBounds);
            return newBounds.left > displaySize().x / 2 && newBounds.bottom <= titleBounds.top;
        });
    }

    private void verifyReadingDimensions() {
        stage = "reading-body-dimensions";
        require(displaySize().y > displaySize().x);
        AccessibilityNodeInfo node = nativeBodyNode();
        require(node != null && node.refreshWithExtraData(AccessibilityNodeInfo.EXTRA_DATA_RENDERING_INFO_KEY, new Bundle()));
        AccessibilityNodeInfo.ExtraRenderingInfo info = node.getExtraRenderingInfo();
        require(info != null);
        float expected = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, 24,
                getTargetContext().getResources().getDisplayMetrics());
        require(Math.abs(info.getTextSizeInPx() - expected) < 1f);
        Rect panel = new Rect();
        node.getParent().getParent().getBoundsInScreen(panel);
        float density = getTargetContext().getResources().getDisplayMetrics().density;
        require(panel.height() >= 420 * density && panel.height() <= displaySize().y * 2 / 3f);
        require(readableOverlay());
    }

    private void verifyNetworkGuidance() {
        // Only the owned emulator is admitted by the host. No model/cache deletion.
        String wifi = Settings.Global.getString(getTargetContext().getContentResolver(), "wifi_on");
        String data = Settings.Global.getString(getTargetContext().getContentResolver(), "mobile_data");
        require("1".equals(wifi) && ("0".equals(data) || "1".equals(data)));
        try {
            stage = "network-wifi";
            clickTab("설정");
            refreshNetworkHint("현재 기본 연결: Wi-Fi");
            stage = "network-offline";
            shellOutput("svc wifi disable");
            shellOutput("svc data disable");
            await(15000, () -> getTargetContext().getSystemService(android.net.ConnectivityManager.class).getActiveNetwork() == null);
            clickLabel("연결 상태 새로고침");
            await(10000, () -> findNode(node -> label(node).contains("사용 가능한 기본 연결이 없어요")) != null);
            stage = "cached-models-offline";
            clickTab("화면 번역");
            clickLabel("화면 번역 시작");
            await(10000, () -> findNode(node -> label(node).equals("화면 번역을 시작할까요?")) != null);
            clickLabel("취소");
            require(!serviceRunning() && !shellOutput("dumpsys media_projection").contains(PACKAGE));
        } finally {
            String previousStage = stage;
            stage = "network-restore";
            shellOutput("svc wifi " + (wifi.equals("1") ? "enable" : "disable"));
            shellOutput("svc data " + (data.equals("1") ? "enable" : "disable"));
            await(15000, () -> wifi.equals(Settings.Global.getString(getTargetContext().getContentResolver(), "wifi_on")) &&
                    data.equals(Settings.Global.getString(getTargetContext().getContentResolver(), "mobile_data")));
            stage = previousStage;
        }
        stage = "network-wifi-restored";
        clickTab("설정");
        refreshNetworkHint("현재 기본 연결: Wi-Fi");
    }

    private void refreshNetworkHint(String expected) {
        await(15000, () -> {
            AccessibilityNodeInfo button = findNode(node -> label(node).equals("연결 상태 새로고침"));
            if (button != null && button.isEnabled()) button.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            return findNode(node -> label(node).contains(expected)) != null;
        });
    }

    private void clickTab(String value) {
        await(20000, () -> {
            // Flutter adds the tab position to its accessible navigation label.
            AccessibilityNodeInfo node = findNode(item -> item.isEnabled() && item.isClickable() &&
                    (label(item).equals(value) || label(item).startsWith(value + "\n") ||
                            label(item).startsWith(value + ",")));
            return node != null && node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        });
    }

    private String networkFacts() {
        if (!scenario.equals("network-guidance")) return "";
        android.net.ConnectivityManager manager = getTargetContext().getSystemService(android.net.ConnectivityManager.class);
        android.net.Network network = manager.getActiveNetwork();
        android.net.NetworkCapabilities caps = network == null ? null : manager.getNetworkCapabilities(network);
        return " Network facts: default=" + (network != null) +
                ", wifi=" + (caps != null && caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)) +
                ", validated=" + (caps != null && caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED)) +
                ", settingsPage=" + (findNode(node -> label(node).equals("설정") && !node.isClickable()) != null) +
                ", refresh=" + (findNode(node -> label(node).equals("연결 상태 새로고침")) != null) +
                ", unverifiedHint=" + (findNode(node -> label(node).contains("인터넷 연결이 확인되지 않았어요")) != null) +
                ", otherHint=" + (findNode(node -> label(node).contains("기본 연결은 Wi-Fi가 아니에요")) != null) +
                ", unknownHint=" + (findNode(node -> label(node).contains("현재 연결을 확인하지 못했어요")) != null);
    }

    private String sourceFixture() {
        switch (source) {
            case "ja": return "図書館は午前9時に開きます。";
            case "zh": return "图书馆上午9点开放。";
            case "ko": return "도서관은 오전 9시에 문을 엽니다.";
            default: return "Take the scenic route";
        }
    }

    private String longFixtureTitle() {
        switch (source) {
            case "ja": return "長い文章 A17";
            case "zh": return "长段落 A17";
            case "ko": return "긴 문단 A17";
            default: return "Reading A17";
        }
    }

    private String longFixturePrefix() {
        return source.equals("en") ? "The library opens at 9 in the morning." : sourceFixture();
    }

    private AccessibilityNodeInfo translationNode() {
        return findNode(node -> PACKAGE.contentEquals(node.getPackageName() == null ? "" : node.getPackageName()) &&
                "android.widget.TextView".contentEquals(node.getClassName() == null ? "" : node.getClassName()) &&
                node.getText() != null && matchesTranslation(node.getText().toString()));
    }

    private static boolean isLanguage(String language) {
        return language.equals("en") || language.equals("ko") || language.equals("ja") || language.equals("zh");
    }

    private boolean matchesTranslation(String text) {
        String value = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFKC)
                .toLowerCase(java.util.Locale.ROOT).replaceAll("\\s+", "");
        if (source.equals("en") && !longPractice) {
            switch (target) {
                case "ko": return value.contains("경치") || (scenario.equals("reading-settings") && value.contains("모험"));
                case "ja": return value.contains("ルート") || value.contains("景色") || value.contains("風光明媚") ||
                        value.contains("冒険");
                case "zh": return value.contains("路线") || value.contains("路線") || value.contains("风景") ||
                        value.contains("風景") || value.contains("景色") || value.contains("景观") || value.contains("景觀") ||
                        value.contains("冒险") || value.contains("冒險");
                default: return false;
            }
        }
        boolean nine = value.contains("9") || value.contains("nine") || value.contains("아홉") || value.contains("九");
        if (!nine) return false;
        switch (target) {
            case "en": return value.contains("library");
            case "ko": return value.contains("도서관");
            case "ja": return value.contains("図書館") || value.contains("ライブラリ");
            case "zh": return value.contains("图书馆") || value.contains("圖書館");
            default: return false;
        }
    }

    private boolean limitNoticePresent() {
        return findNode(node -> "android.widget.TextView".contentEquals(
                node.getClassName() == null ? "" : node.getClassName()) &&
                label(node).contains("일부만 번역했어요")) != null;
    }

    private boolean changedTranslation(String previous) {
        AccessibilityNodeInfo node = translationNode();
        return node != null && node.getText() != null && !node.getText().toString().equals(previous);
    }

    private void verifyLongReading(boolean requireLimit, String initialTranslation) {
        stage = "long-practice-menu";
        clickLabel("연습 내용 선택");
        clickLabel("긴 문단·혼합 언어");
        longPractice = true;
        stage = "long-practice-source";
        await(10000, () -> findNode(node -> label(node).equals(longFixtureTitle())) != null &&
                findNode(node -> label(node).contains(longFixturePrefix())) != null);
        if (source.equals("en")) {
            await(10000, () -> findNode(node -> label(node).contains("Sample A17") && label(node).contains("图书馆")) != null);
        }
        stage = "long-practice-limit";
        await(45000, () -> (!requireLimit || limitNoticePresent()) && changedTranslation(initialTranslation));
        stage = "long-practice-pause";
        clickLabel("멈춤");
        await(5000, () -> !limitNoticePresent() && translationNode() == null);
        clickLabel("재개");
        stage = "long-practice-resume";
        await(45000, () -> (!requireLimit || limitNoticePresent()) && translationNode() != null);
        boolean observedLimit = limitNoticePresent();
        String longTranslation = translationNode().getText().toString();
        stage = "long-practice-log";
        String logs = processLog();
        require(!logs.contains(longTranslation) && !logs.contains(longFixturePrefix()));
        if (!requireLimit) {
            String facts = longSourceFacts();
            Bundle observation = new Bundle();
            observation.putString("stream", "Authored long reading source=" + source + ", limit=" + observedLimit + facts);
            sendStatus(2, observation);
        }
        stage = "basic-practice-restore";
        clickLabel("연습 내용 선택");
        clickLabel("기본 예문");
        longPractice = false;
        await(45000, () -> !limitNoticePresent() && changedTranslation(longTranslation));
    }

    @SuppressWarnings("deprecation")
    private Point displaySize() {
        Point size = new Point();
        getTargetContext().getSystemService(DisplayManager.class).getDisplay(0).getRealSize(size);
        return size;
    }

    private boolean readableOverlay() {
        layoutProbe = "missing-scroll";
        AccessibilityNodeInfo text = nativeBodyNode();
        AccessibilityNodeInfo body = text == null ? null : text.getParent();
        if (body == null || body.getParent() == null) return false;
        Rect viewport = new Rect();
        Rect panelBounds = new Rect();
        body.getBoundsInScreen(viewport);
        body.getParent().getBoundsInScreen(panelBounds);
        float density = getTargetContext().getResources().getDisplayMetrics().density;
        layoutProbe = "viewport=" + viewport.width() + "x" + viewport.height();
        if (viewport.height() < 72 * density || viewport.width() < 200 * density) return false;
        Point display = displaySize();
        if (display.x > display.y && panelBounds.width() > display.x * .67f) {
            layoutProbe += ",panel-too-wide=" + panelBounds.width();
            return false;
        }
        for (String control : new String[] {"멈춤", "접기", "종료", "powered by Google Translate"}) {
            AccessibilityNodeInfo node = findNode(item -> label(item).equals(control));
            if (node == null) { layoutProbe += ",missing-control=" + control; return false; }
            Rect bounds = new Rect();
            node.getBoundsInScreen(bounds);
            if (bounds.isEmpty() || !panelBounds.contains(bounds)) { layoutProbe += ",control-outside=" + control; return false; }
            if (!control.equals("powered by Google Translate") &&
                    (bounds.width() < 48 * density || bounds.height() < 48 * density)) {
                layoutProbe += ",control-small=" + control + ":" + bounds.width() + "x" + bounds.height(); return false;
            }
        }
        return true;
    }

    private void verifyReadingRotation() {
        stage = "rotation-save";
        originalDisplayRotation = getTargetContext().getSystemService(DisplayManager.class).getDisplay(0).getRotation();
        originalAutoRotationSetting = Settings.System.getString(getTargetContext().getContentResolver(), "accelerometer_rotation");
        originalUserRotationSetting = Settings.System.getString(getTargetContext().getContentResolver(), "user_rotation");
        require(originalAutoRotationSetting == null || originalAutoRotationSetting.matches("[01]"));
        require(originalUserRotationSetting == null || originalUserRotationSetting.matches("[0-3]"));
        originalAutoRotation = originalAutoRotationSetting == null ? 1 : Integer.parseInt(originalAutoRotationSetting);
        originalUserRotation = originalUserRotationSetting == null ? 0 : Integer.parseInt(originalUserRotationSetting);
        require(originalAutoRotation >= 0 && originalAutoRotation <= 1 && originalUserRotation >= 0 && originalUserRotation <= 3);
        Point initialSize = displaySize();
        boolean initiallyLandscape = initialSize.x > initialSize.y;
        rotationSettingsSaved = true;
        stage = "rotation-change";
        require(getUiAutomation().setRotation((originalDisplayRotation + 1) % 4));
        await(15000, () -> {
            Point size = displaySize();
            return (size.x > size.y) != initiallyLandscape && readableOverlay();
        });
        require(serviceRunning() && overlayPresent());
        stage = "rotation-controls";
        clickLabel("멈춤");
        await(5000, () -> findNode(node -> label(node).equals("재개")) != null && translationNode() == null);
        clickLabel("재개");
        stage = "rotation-restore";
        restoreRotation();
        await(45000, () -> translationNode() != null && readableOverlay());
        require(serviceRunning());
    }

    private void restoreRotation() {
        if (!rotationSettingsSaved) return;
        require(getUiAutomation().setRotation(originalDisplayRotation));
        require(getUiAutomation().setRotation(UiAutomation.ROTATION_UNFREEZE));
        shellOutput(originalUserRotationSetting == null ? "settings delete system user_rotation" :
                "settings put system user_rotation " + originalUserRotation);
        shellOutput(originalAutoRotationSetting == null ? "settings delete system accelerometer_rotation" :
                "settings put system accelerometer_rotation " + originalAutoRotation);
        require(java.util.Objects.equals(originalUserRotationSetting,
                Settings.System.getString(getTargetContext().getContentResolver(), "user_rotation")) &&
                java.util.Objects.equals(originalAutoRotationSetting,
                Settings.System.getString(getTargetContext().getContentResolver(), "accelerometer_rotation")));
        rotationSettingsSaved = false;
    }

    private String failureState() {
        try {
            boolean alternate = findNode(node -> "android.widget.TextView".contentEquals(
                    node.getClassName() == null ? "" : node.getClassName()) &&
                    (label(node).contains("라이브러리") || label(node).contains("도서실"))) != null;
            boolean startEnabled = findNode(node -> label(node).equals("화면 번역 시작") && node.isEnabled()) != null;
            boolean disclosure = findNode(node -> label(node).equals("화면 번역을 시작할까요?")) != null;
            AccessibilityNodeInfo body = nativeBodyNode();
            String bodyText = body == null ? "" : label(body);
            boolean han = bodyText.codePoints().anyMatch(point -> Character.UnicodeScript.of(point) == Character.UnicodeScript.HAN);
            boolean adventure = bodyText.contains("冒险") || bodyText.contains("冒險") || bodyText.contains("冒険");
            boolean scenicAlternative = bodyText.contains("风光") || bodyText.contains("風光") || bodyText.contains("观光") ||
                    bodyText.contains("觀光") || bodyText.contains("景致") || bodyText.contains("美景");
            return " Service=" + serviceRunning() + ", overlay=" + overlayPresent() +
                    ", alternateNoun=" + alternate + ", startEnabled=" + startEnabled +
                    ", disclosure=" + disclosure + ", body=" + (body != null) + ", han=" + han +
                    ", adventure=" + adventure + ", scenicAlternative=" + scenicAlternative +
                    ", bodyChars=" + bodyText.codePointCount(0, bodyText.length()) +
                    ", targetAnchor=" + (translationNode() != null) + ", limit=" + limitNoticePresent() +
                    ", display=" + displaySize().x + "x" + displaySize().y + ", layout=" + layoutProbe + ".";
        } catch (Throwable ignored) { return " Fixture state unavailable."; }
    }

    private AccessibilityNodeInfo nativeBodyNode() {
        // Flutter also exposes its page as a ScrollView. Require the native TextView child.
        return findNode(node -> PACKAGE.contentEquals(node.getPackageName() == null ? "" : node.getPackageName()) &&
                "android.widget.TextView".contentEquals(node.getClassName() == null ? "" : node.getClassName()) &&
                node.getParent() != null && "android.widget.ScrollView".contentEquals(node.getParent().getClassName()));
    }

    private String longSourceFacts() {
        // Metadata from the authored UI is not the SDK's recognized paragraph length.
        if (!longPractice || !practiceVerified || findNode(node -> label(node).equals(longFixtureTitle())) == null) return "";
        AccessibilityNodeInfo sourceNode = findNode(node -> label(node).contains(longFixturePrefix()));
        if (sourceNode == null) return " Authored source metadata unavailable.";
        Rect bounds = new Rect();
        sourceNode.getBoundsInScreen(bounds);
        String value = label(sourceNode);
        return " Authored UI source chars=" + value.codePointCount(0, value.length()) + ", bounds=" + bounds.toShortString() + ".";
    }
    private void savePracticeScreenshot() {
        // Never capture an arbitrary screen or a system dialog. Only our authored exercise.
        if (!practiceVerified) return;
        try {
            AccessibilityNodeInfo root = getUiAutomation().getRootInActiveWindow();
            if (root == null || !PACKAGE.contentEquals(root.getPackageName()) ||
                    visit(root, node -> label(node).contains(longPractice ? longFixtureTitle() : sourceFixture())) == null) return;
            Bitmap bitmap = getUiAutomation().takeScreenshot();
            if (bitmap == null) return;
            try {
                File directory = getTargetContext().getExternalFilesDir(null);
                if (directory == null) return;
                try (FileOutputStream output = new FileOutputStream(new File(directory, "qa-practice-" + source + ".png"))) {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
                }
            } finally { bitmap.recycle(); }
        } catch (Throwable ignored) {
            // A screenshot must never change the functional result or disable FLAG_SECURE.
        }
    }

    private String shellOutput(String command) {
        ParcelFileDescriptor descriptor = getUiAutomation().executeShellCommand(
                command);
        try (ParcelFileDescriptor.AutoCloseInputStream input =
                     new ParcelFileDescriptor.AutoCloseInputStream(descriptor);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
            return output.toString(StandardCharsets.UTF_8.name());
        } catch (java.io.IOException failure) {
            throw new AssertionError("Fixture shell transport failed");
        }
    }

    @SuppressWarnings("deprecation")
    private boolean serviceRunning() {
        ActivityManager manager = getTargetContext().getSystemService(ActivityManager.class);
        return manager.getRunningServices(20).stream().anyMatch(item -> item.service.getClassName().equals(SERVICE));
    }

    private boolean foregroundNotificationPresent() {
        return java.util.Arrays.stream(getTargetContext().getSystemService(NotificationManager.class)
                .getActiveNotifications()).anyMatch(item -> item.getId() == 6402);
    }

    private boolean overlayPresent() {
        return findNode(node -> "번역 카드 접기 또는 펼치기. 끌어서 이동"
                .contentEquals(node.getContentDescription() == null ? "" : node.getContentDescription())) != null;
    }

    private void wakeFixture() {
        shellOutput("input keyevent KEYCODE_WAKEUP");
        shellOutput("wm dismiss-keyguard");
    }

    private void verifyLifecycle() {
        stage = "live-service";
        require(serviceRunning());
        stage = "live-overlay";
        require(overlayPresent());
        stage = "live-projection";
        require(shellOutput("dumpsys media_projection").contains(PACKAGE));
        if (scenario.equals("revoke-overlay")) {
            require(Settings.canDrawOverlays(getTargetContext()));
            stage = "revoke-overlay";
            shellOutput("appops set " + PACKAGE + " SYSTEM_ALERT_WINDOW ignore");
            await(5000, () -> !Settings.canDrawOverlays(getTargetContext()));
        } else if (scenario.equals("screen-lock")) {
            stage = "screen-lock";
            shellOutput("input keyevent KEYCODE_SLEEP");
        } else {
            // Do not let the launcher/other recent tasks become OCR input after removal.
            stage = "pause-before-task-removal";
            clickLabel("멈춤");
            await(5000, () -> findNode(node -> label(node).equals("재개")) != null &&
                    findNode(node -> label(node).contains("경치")) == null);
            ActivityManager manager = getTargetContext().getSystemService(ActivityManager.class);
            require(!manager.getAppTasks().isEmpty());
            stage = "remove-task";
            runOnMainSync(() -> manager.getAppTasks().forEach(ActivityManager.AppTask::finishAndRemoveTask));
            await(12000, () -> manager.getAppTasks().isEmpty());
        }
        stage = "session-cleanup";
        await(12000, () -> !serviceRunning() && !foregroundNotificationPresent() && !overlayPresent());
        require(!shellOutput("dumpsys media_projection").contains(PACKAGE));
        if (scenario.equals("revoke-overlay")) {
            stage = "native-error-visible";
            shellOutput("input keyevent KEYCODE_BACK");
            String notice = "다른 앱 위에 표시 권한이 해제되어 번역을 종료했어요.";
            await(10000, () -> findNode(node -> label(node).equals(notice)) != null);
            stage = "native-error-dismiss";
            clickLabel("안내 닫기");
            await(5000, () -> findNode(node -> label(node).equals(notice)) == null);
            shellOutput("appops set " + PACKAGE + " SYSTEM_ALERT_WINDOW allow");
            await(5000, () -> Settings.canDrawOverlays(getTargetContext()));
        } else if (scenario.equals("screen-lock")) wakeFixture();
        stage = "no-automatic-restart";
        long deadline = SystemClock.uptimeMillis() + 4000;
        while (SystemClock.uptimeMillis() < deadline) {
            require(!serviceRunning() && !foregroundNotificationPresent() && !overlayPresent());
            SystemClock.sleep(150);
        }
        // Relaunch only after verifying cleanup; clearing the task must not cause it.
        stage = "fresh-home-start";
        Intent launch = getTargetContext().getPackageManager().getLaunchIntentForPackage(PACKAGE);
        require(launch != null);
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        // The previous home also has a start button. Wait for its replacement
        // before clicking, so CLEAR_TASK cannot discard a just-opened disclosure.
        startActivitySync(launch);
        waitForIdleSync();
        clickLabel("화면 번역 시작");
        stage = "fresh-disclosure";
        clickLabel("화면 공유 계속");
        stage = "fresh-os-consent";
        await(20000, () -> findNode(node -> {
            String value = label(node);
            return value.equals("Share screen") || value.equals("Start now") ||
                    value.equals("화면 공유") || value.equals("시작하기") || value.equals("지금 시작");
        }) != null);
        stage = "fresh-consent-idle";
        require(!serviceRunning());
        // Cancel the newly presented real OS dialog, without creating another session.
        shellOutput("input keyevent KEYCODE_BACK");
        stage = "fresh-consent-cancelled";
        require(!shellOutput("dumpsys media_projection").contains(PACKAGE));
    }

    private static String label(AccessibilityNodeInfo node) {
        CharSequence value = node.getText() != null ? node.getText() : node.getContentDescription();
        return value == null ? "" : value.toString();
    }

    private void clickLabel(String value) {
        await(20000, () -> {
            AccessibilityNodeInfo node = findNode(item -> item.isEnabled() && label(item).equals(value));
            return node != null && node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        });
    }

    private static void require(boolean condition) {
        if (!condition) throw new AssertionError("Fixture condition failed");
    }

    private void await(long timeout, BooleanSupplier condition) {
        long deadline = SystemClock.uptimeMillis() + timeout;
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition.getAsBoolean()) return;
            SystemClock.sleep(150);
        }
        throw new AssertionError("Timed out waiting for fixture UI");
    }

    private AccessibilityNodeInfo findNode(Predicate<AccessibilityNodeInfo> predicate) {
        AccessibilityNodeInfo found = visit(getUiAutomation().getRootInActiveWindow(), predicate);
        if (found != null) return found;
        for (AccessibilityWindowInfo window : getUiAutomation().getWindows()) {
            found = visit(window.getRoot(), predicate);
            if (found != null) return found;
        }
        return null;
    }

    private AccessibilityNodeInfo visit(AccessibilityNodeInfo node, Predicate<AccessibilityNodeInfo> predicate) {
        if (node == null) return null;
        if (predicate.test(node)) return node;
        for (int index = 0; index < node.getChildCount(); index++) {
            AccessibilityNodeInfo found = visit(node.getChild(index), predicate);
            if (found != null) return found;
        }
        return null;
    }
}
