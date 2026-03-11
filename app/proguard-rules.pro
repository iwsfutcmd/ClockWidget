# Keep ICU calendar classes used via reflection in android.icu
-keep class android.icu.util.** { *; }
-keep class android.icu.text.** { *; }

# Keep our enum names (used in SharedPreferences by name)
-keepnames class dev.benyates.clockwidget.CalendarSystem
-keepnames class dev.benyates.clockwidget.DisplayMode
