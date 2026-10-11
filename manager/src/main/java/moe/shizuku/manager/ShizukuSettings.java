package moe.shizuku.manager;

import android.app.ActivityThread;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.os.Build;
import android.text.TextUtils;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;

import java.lang.annotation.Retention;
import java.util.Locale;

import moe.shizuku.manager.utils.EmptySharedPreferencesImpl;
import moe.shizuku.manager.utils.EnvironmentUtils;

import static java.lang.annotation.RetentionPolicy.SOURCE;

public class ShizukuSettings {

    public static final String NAME = "settings";
    public static final String NIGHT_MODE = "night_mode";
    public static final String LANGUAGE = "language";
    public static final String KEEP_START_ON_BOOT = "start_on_boot";
    public static final String ALLOW_ALL_APPS = "allow_all_apps";
    public static final String TCP_MODE = "tcp_mode";
    public static final String TCP_MODE_WARNING_SHOWN = "tcp_mode_warning_shown";

    /** The fixed local port used by TCP mode (same as "adb tcpip 5555"). */
    public static final int TCP_PORT = 5555;

    private static SharedPreferences sPreferences;

    public static SharedPreferences getPreferences() {
        return sPreferences;
    }

    @NonNull
    private static Context getSettingsStorageContext(@NonNull Context context) {
        Context storageContext;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            storageContext = context.createDeviceProtectedStorageContext();
        } else {
            storageContext = context;
        }

        storageContext = new ContextWrapper(storageContext) {
            @Override
            public SharedPreferences getSharedPreferences(String name, int mode) {
                try {
                    return super.getSharedPreferences(name, mode);
                } catch (IllegalStateException e) {
                    // SharedPreferences in credential encrypted storage are not available until after user is unlocked
                    return new EmptySharedPreferencesImpl();
                }
            }
        };

        return storageContext;
    }

    public static void initialize(Context context) {
        if (sPreferences == null) {
            sPreferences = getSettingsStorageContext(context)
                    .getSharedPreferences(NAME, Context.MODE_PRIVATE);
        }
    }

    @IntDef({
            LaunchMethod.UNKNOWN,
            LaunchMethod.ROOT,
            LaunchMethod.ADB,
    })
    @Retention(SOURCE)
    public @interface LaunchMethod {
        int UNKNOWN = -1;
        int ROOT = 0;
        int ADB = 1;
    }

    @LaunchMethod
    public static int getLastLaunchMode() {
        return getPreferences().getInt("mode", LaunchMethod.UNKNOWN);
    }

    public static void setLastLaunchMode(@LaunchMethod int method) {
        getPreferences().edit().putInt("mode", method).apply();
    }

    /**
     * When true, every app that asks for access is approved automatically,
     * without showing the confirmation dialog.
     */
    public static boolean isAllowAllApps() {
        return getPreferences().getBoolean(ALLOW_ALL_APPS, false);
    }

    public static void setAllowAllApps(boolean allow) {
        getPreferences().edit().putBoolean(ALLOW_ALL_APPS, allow).apply();
    }

    /**
     * When true, after Mitsuku is started over Wi-Fi (wireless debugging) it also asks
     * Android's debug service to listen on {@link #TCP_PORT}, so it can be restarted later
     * through 127.0.0.1 without Wi-Fi. Android forgets that port on every reboot.
     */
    public static boolean isTcpMode() {
        return getPreferences().getBoolean(TCP_MODE, false);
    }

    public static boolean isTcpModeWarningShown() {
        return getPreferences().getBoolean(TCP_MODE_WARNING_SHOWN, false);
    }

    public static void setTcpModeWarningShown() {
        getPreferences().edit().putBoolean(TCP_MODE_WARNING_SHOWN, true).apply();
    }

    @AppCompatDelegate.NightMode
    public static int getNightMode() {
        int defValue = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        if (EnvironmentUtils.isWatch(ActivityThread.currentActivityThread().getApplication())) {
            defValue = AppCompatDelegate.MODE_NIGHT_YES;
        }
        return getPreferences().getInt(NIGHT_MODE, defValue);
    }

    public static Locale getLocale() {
        String tag = getPreferences().getString(LANGUAGE, null);
        if (TextUtils.isEmpty(tag) || "SYSTEM".equals(tag)) {
            return Locale.getDefault();
        }
        return Locale.forLanguageTag(tag);
    }
}
