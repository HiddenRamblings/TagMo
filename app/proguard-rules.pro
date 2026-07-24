# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

-dontobfuscate
-optimizations !code/allocation/variable

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
-keepclassmembers class com.hiddenramblings.tagmo.fragment.WebsiteFragment {
   public *;
}

# Uncomment this to preserve the line number information for
# debugging stack traces.
-keepattributes *Annotation*,SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

-keep class com.google.android.material.R$drawable { *; }

# Wear OS drawer setup is invoked from BrowserActivity through reflection because the Wear
# dependency is only available to the wearos build type.
-keep class com.hiddenramblings.tagmo.WearableAdapter { *; }
-keepclassmembers class androidx.wear.widget.drawer.WearableNavigationDrawerView {
    public void setAdapter(androidx.wear.widget.drawer.WearableNavigationDrawerView$WearableNavigationDrawerAdapter);
    public void addOnItemSelectedListener(androidx.wear.widget.drawer.WearableNavigationDrawerView$OnItemSelectedListener);
}
-keepclassmembers class androidx.wear.widget.drawer.WearableActionDrawerView {
    public android.view.Menu getMenu();
    public void setOnMenuItemClickListener(android.view.MenuItem$OnMenuItemClickListener);
}
