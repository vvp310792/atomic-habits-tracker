# Room
-keep class androidx.room.** { *; }
-keep @androidx.room.Entity class * { *; }

# Keep data classes used for JSON serialization to Google Sheets
-keep class com.atomichabits.tracker.data.** { *; }
-keep class com.atomichabits.tracker.sheets.** { *; }
