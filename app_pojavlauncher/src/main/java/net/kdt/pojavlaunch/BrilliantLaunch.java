package net.kdt.pojavlaunch;

import android.app.Activity;
import android.content.Intent;

import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.tasks.AsyncAssetManager;
import net.kdt.pojavlaunch.tasks.AsyncMinecraftDownloader;
import net.kdt.pojavlaunch.tasks.MinecraftDownloader;
import net.kdt.pojavlaunch.value.MinecraftAccount;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

public final class BrilliantLaunch {
    public static final String CONTROLS = "brilliant-v4.json";
    public static final String CUSTOM_CONTROLS = "my-controls.json";

    public static java.io.File controlsFile() {
        return new java.io.File(Tools.CTRLMAP_PATH, CONTROLS);
    }

    public static final String PROFILE_KEY = java.util.UUID.nameUUIDFromBytes("brilliant".getBytes()).toString();

    public interface Callback {
        void onStarted();
        void onFailed(Throwable error);
    }

    private BrilliantLaunch() {}

    private static final String[][] CONTROL_LABELS = {
            {"Jump", "跳躍", "跳跃"},
            {"Offhand", "副手", "副手"},
            {"Menu", "選單", "菜单"},
            {"Crouch", "潛行", "潜行"},
            {"Right\\nClick", "右鍵", "右键"},
            {"Middle\\nClick", "中鍵", "中键"},
            {"Inventory", "物品欄", "物品栏"},
            {"Sprint", "疾跑", "疾跑"},
            {"Hide\\nControls", "隱藏\\n按鍵", "隐藏\\n按键"},
            {"Pause", "暫停", "暂停"},
            {"Exit\\nMenu", "離開\\n選單", "退出\\n菜单"},
            {"Third\\nPerson", "第三\\n人稱", "第三\\n人称"},
            {"Debug\\nMenu", "除錯\\n畫面", "调试\\n界面"},
            {"Mouse\\nToggle", "切換\\n滑鼠", "切换\\n鼠标"},
            {"Chat", "聊天", "聊天"},
            {"Keyboard", "鍵盤", "键盘"},
            {"HUD\\nToggle", "切換\\n介面", "切换\\n界面"},
            {"Drop\\nItem", "丟棄\\n物品", "丢弃\\n物品"},
            {"Player\\nList", "玩家\\n列表", "玩家\\n列表"},
            {"More", "更多", "更多"},
    };

    public static void launch(Activity activity, String versionId, String gameDir, String username, String uuid,
                              String accessToken, String xuid, int memoryMb, String language, String jvmArgs, String join, Callback callback) {
        new Thread(() -> {
            try {
                prepare(activity, versionId, gameDir, username, uuid, accessToken, xuid, memoryMb, language, jvmArgs);
                new MinecraftDownloader().start(activity, null, versionId, new AsyncMinecraftDownloader.DoneListener() {
                    @Override
                    public void onDownloadDone() {
                        Intent intent = new Intent(activity, MainActivity.class);
                        intent.putExtra(MainActivity.INTENT_MINECRAFT_VERSION, versionId);
                        intent.putExtra(MainActivity.INTENT_QUICK_JOIN, join);
                        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                        activity.runOnUiThread(() -> activity.startActivity(intent));
                        callback.onStarted();
                    }

                    @Override
                    public void onDownloadFailed(Throwable throwable) {
                        callback.onFailed(throwable);
                    }
                });
            } catch (Throwable e) {
                callback.onFailed(e);
            }
        }, "BrilliantLaunch").start();
    }

    private static void prepare(Activity activity, String versionId, String gameDir, String username, String uuid,
                                String accessToken, String xuid, int memoryMb, String language, String jvmArgs) throws Exception {
        if (!Tools.checkStorageRoot(activity)) throw new IllegalStateException("Game storage is not writable");
        Tools.initStorageConstants(activity);
        LauncherPreferences.loadPreferences(activity);
        AsyncAssetManager.unpackComponentsNow(activity);
        writeControls(activity, language);
        Tools.copyAssetFile(activity, "resolv.conf", Tools.DIR_DATA, false);

        MinecraftAccount account = new MinecraftAccount();
        account.username = username;
        account.profileId = uuid;
        account.accessToken = accessToken == null || accessToken.isEmpty() ? "0" : accessToken;
        account.isMicrosoft = !"0".equals(account.accessToken);
        account.xuid = xuid;
        account.save();
        PojavProfile.setCurrentProfile(activity, username);

        LauncherProfiles.load();
        MinecraftProfile profile = LauncherProfiles.mainProfileJson.profiles.get(PROFILE_KEY);
        if (profile == null) profile = new MinecraftProfile();
        profile.name = "Brilliant";
        profile.lastVersionId = versionId;
        profile.gameDir = Tools.LAUNCHERPROFILES_RTPREFIX + gameDir;
        profile.javaArgs = jvmArgs;
        LauncherProfiles.mainProfileJson.profiles.put(PROFILE_KEY, profile);
        LauncherProfiles.write();

        android.content.SharedPreferences.Editor prefs = LauncherPreferences.DEFAULT_PREF.edit()
                .putString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, PROFILE_KEY)
                .putBoolean("force_english", "en".equals(language));
        int deviceRam = Tools.getTotalDeviceMemory(activity);
        int allocation = memoryMb > 0 ? memoryMb : deviceRam >= 7000 ? 3072 : deviceRam >= 5500 ? 2560 : 0;
        if (allocation > 0 && (memoryMb > 0 || !LauncherPreferences.DEFAULT_PREF.contains("allocation"))) {
            prefs.putInt("allocation", allocation);
            LauncherPreferences.PREF_RAM_ALLOCATION = allocation;
        }
        if (!LauncherPreferences.DEFAULT_PREF.contains("bigCoreAffinity")) prefs.putBoolean("bigCoreAffinity", true);
        prefs.commit();

        JMinecraftVersionList.Version info = readVersion(versionId);
        if (info.javaVersion == null && info.inheritsFrom != null) info = readVersion(info.inheritsFrom);
        if (!NewJREUtil.installNewJreIfNeeded(activity, info)) throw new IllegalStateException("Java runtime install failed");
    }

    private static void writeControls(Activity activity, String language) throws Exception {
        String original;
        try (java.io.InputStream in = activity.getAssets().open("brilliant_controls.json")) {
            original = Tools.read(in);
        }
        String[] variants = new String[3];
        for (int column = 0; column < 3; column++) {
            String json = original;
            for (String[] label : CONTROL_LABELS) {
                json = json.replace("\"name\":\"" + label[0] + "\"", "\"name\":\"" + label[column] + "\"");
            }
            variants[column] = json;
        }
        java.io.File target = controlsFile();
        new java.io.File(Tools.CTRLMAP_PATH, "brilliant-v3.json").delete();
        String chosen = LauncherPreferences.DEFAULT_PREF.getString("defaultCtrl", Tools.CTRLDEF_FILE);
        java.io.File moved = new java.io.File(Tools.CTRLMAP_PATH, new java.io.File(chosen).getName());
        if (!new java.io.File(chosen).isFile() && moved.isFile() && !moved.getName().startsWith("brilliant")) {
            chosen = moved.getAbsolutePath();
            LauncherPreferences.DEFAULT_PREF.edit().putString("defaultCtrl", chosen).commit();
            LauncherPreferences.PREF_DEFAULTCTRL_PATH = chosen;
        }
        if (chosen.equals(Tools.CTRLDEF_FILE) || new java.io.File(chosen).getName().startsWith("brilliant") || !new java.io.File(chosen).isFile()) {
            LauncherPreferences.DEFAULT_PREF.edit().putString("defaultCtrl", target.getAbsolutePath()).commit();
            LauncherPreferences.PREF_DEFAULTCTRL_PATH = target.getAbsolutePath();
        }
        if (target.exists()) {
            String current = Tools.read(target);
            if (current.contains("mControlDataList") && !java.util.Arrays.asList(variants).contains(current)) return;
        }
        Tools.write(target.getAbsolutePath(), variants["zh-CN".equals(language) ? 2 : "en".equals(language) ? 0 : 1]);
    }

    private static JMinecraftVersionList.Version readVersion(String id) throws Exception {
        return Tools.GLOBAL_GSON.fromJson(Tools.read(Tools.DIR_HOME_VERSION + "/" + id + "/" + id + ".json"), JMinecraftVersionList.Version.class);
    }
}
