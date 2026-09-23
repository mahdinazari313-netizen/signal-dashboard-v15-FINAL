package com.mt5dashboard;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.InputType;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.mt5dashboard.service.MonitoringForegroundService;
import com.mt5dashboard.ui.ActiveSignalsFragment;
import com.mt5dashboard.ui.AppSettings;
import com.mt5dashboard.ui.ScenarioBoardsFragment;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_POST_NOTIFICATIONS = 501;
    private static final String PREF_MONITORING_ENABLED = "monitoring_enabled";

    private AppSettings appSettings;
    private MenuItem toggleMonitoringMenuItem;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        appSettings = new AppSettings(this);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigation);
        bottomNav.setOnItemSelectedListener(item -> {
            Fragment fragment;
            if (item.getItemId() == R.id.nav_scenarios) {
                fragment = new ScenarioBoardsFragment();
            } else {
                fragment = new ActiveSignalsFragment();
            }
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragmentContainer, fragment)
                    .commit();
            return true;
        });

        if (savedInstanceState == null) {
            bottomNav.setSelectedItemId(R.id.nav_active_signals);
        }

        ensurePostNotificationPermission();
        startMonitoringService();
    }

    // ---------------- منوی سه‌نقطه ----------------

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.toolbar_menu, menu);
        toggleMonitoringMenuItem = menu.findItem(R.id.action_toggle_monitoring);
        updateToggleMonitoringTitle();
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            showDisplayDurationDialog();
            return true;
        } else if (id == R.id.action_permissions) {
            showPermissionsDialog();
            return true;
        } else if (id == R.id.action_toggle_monitoring) {
            toggleMonitoring();
            return true;
        } else if (id == R.id.action_about) {
            showAboutDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // ---------------- تنظیمات نمایش (Display Duration - فقط صفحه اول) ----------------

    private void showDisplayDurationDialog() {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setText(String.valueOf(appSettings.getDisplayDurationMinutes()));

        new AlertDialog.Builder(this)
                .setTitle(R.string.settings_display_duration_label)
                .setView(input)
                .setPositiveButton(R.string.settings_save, (dialog, which) -> {
                    String text = input.getText().toString().trim();
                    if (!text.isEmpty()) {
                        try {
                            int minutes = Integer.parseInt(text);
                            if (minutes > 0) {
                                appSettings.setDisplayDurationMinutes(minutes);
                            }
                        } catch (NumberFormatException ignored) {
                            // مقدار نامعتبر، تنظیمات قبلی حفظ می‌شود
                        }
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    // ---------------- مدیریت مجوزها ----------------

    private void showPermissionsDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.menu_permissions)
                .setMessage(getString(R.string.permission_notification_access_desc)
                        + "\n\n" + getString(R.string.permission_battery_desc))
                .setPositiveButton(R.string.permission_open_settings, (dialog, which) -> {
                    // ابتدا Notification Access
                    startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
                })
                .setNeutralButton(R.string.permission_battery_title, (dialog, which) -> {
                    requestIgnoreBatteryOptimizations();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void requestIgnoreBatteryOptimizations() {
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } else {
            Toast.makeText(this, "این برنامه از قبل از بهینه‌سازی باتری معاف است", Toast.LENGTH_SHORT).show();
        }
    }

    private void ensurePostNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{android.Manifest.permission.POST_NOTIFICATIONS},
                        REQUEST_POST_NOTIFICATIONS);
            }
        }
    }

    // ---------------- شروع/توقف سرویس مانیتورینگ ----------------

    private void startMonitoringService() {
        boolean enabled = getPreferences(MODE_PRIVATE).getBoolean(PREF_MONITORING_ENABLED, true);
        if (!enabled) return;
        Intent serviceIntent = new Intent(this, MonitoringForegroundService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    private void toggleMonitoring() {
        boolean currentlyEnabled = getPreferences(MODE_PRIVATE).getBoolean(PREF_MONITORING_ENABLED, true);
        boolean newState = !currentlyEnabled;
        getPreferences(MODE_PRIVATE).edit().putBoolean(PREF_MONITORING_ENABLED, newState).apply();

        if (newState) {
            startMonitoringService();
        } else {
            stopService(new Intent(this, MonitoringForegroundService.class));
        }
        updateToggleMonitoringTitle();
    }

    private void updateToggleMonitoringTitle() {
        if (toggleMonitoringMenuItem == null) return;
        boolean enabled = getPreferences(MODE_PRIVATE).getBoolean(PREF_MONITORING_ENABLED, true);
        toggleMonitoringMenuItem.setTitle(enabled
                ? R.string.menu_stop_monitoring
                : R.string.menu_start_monitoring);
    }

    // ---------------- درباره برنامه ----------------

    private void showAboutDialog() {
        String versionName;
        try {
            versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            versionName = "-";
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.menu_about)
                .setMessage(getString(R.string.about_description)
                        + "\n\n" + getString(R.string.about_version_label) + ": " + versionName)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }
}
