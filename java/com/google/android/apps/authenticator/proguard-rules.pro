# Copyright 2019 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

-dontoptimize

-keep class com.google.android.apps.authenticator.** {*;}

-keepclassmembers class * {
    public protected static final % *;
}

-dontnote ** # Suppress warnings -- we know we're being overbroad

# The support library contains references to newer platform versions.
# Don't warn about those in case this app is linking against an older
# platform version.  We know about them, and they are safe.
-dontwarn android.support.**

-dontwarn com.google.android.gms.**

# This allows proguard to strip isLoggable() blocks containing only <=INFO log
# code from release builds.
-assumenosideeffects class android.util.Log {
  static *** i(...);
  static *** d(...);
  static *** v(...);
  static *** isLoggable(...);
}

# Allows proguard to make private and protected methods and fields public as
# part of optimization. This lets proguard inline trivial getter/setter methods.
-allowaccessmodification

# needlessly repeats com.google.android.apps.etc.
-repackageclasses ""

# release > source file
# The source file attribute must be present in order to print stack traces, but
# we rename it in order to avoid leaking the pre-obfuscation class name.
-renamesourcefileattribute PG

# The presence of both of these attributes causes dalvik and other jvms to print
# stack traces on uncaught exceptions, which is necessary to get useful crash
# reports.
-keepattributes SourceFile,LineNumberTable

# Preverification was introduced in Java 6 to enable faster classloading, but
# dex doesn't use the java .class format, so it has no benefit and can cause
# problems.
-dontpreverify

# Skipping analysis of some classes may make proguard strip something that's
# needed.
-dontskipnonpubliclibraryclasses

# Case-insensitive filesystems can't handle when a.class and A.class exist in
# the same directory.
-dontusemixedcaseclassnames

# This prevents the names of native methods from being obfuscated and prevents
# UnsatisfiedLinkErrors.
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# hackbod discourages the use of enums on android, but if you use them, they
# should work. Allow instantiation via reflection by keeping the values method.
-keepclassmembers enum * {
    public static **[] values();
}

# Parcel reflectively accesses this field.
-keepclassmembers class * implements android.os.Parcelable {
  public static *** CREATOR;
}

# These methods are needed to ensure that serialization behaves as expected when
# classes are obfuscated, shrunk, and/or optimized.
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# Don't warn about Guava. Any Guava-using app will fail the proguard stage without this dontwarn,
# and since Guava is so widely used, we include it here in the base.
-dontwarn com.google.common.**

# Don't warn about Error Prone annotations (e.g. @CompileTimeConstant)
-dontwarn com.google.errorprone.annotations.**

# android.app.Notification.setLatestEventInfo() was removed in MNC, but is still
# referenced (safely) by the NotificationCompat code.
-dontwarn android.app.Notification

# Keep all Javascript API methods
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Silence notes about dynamically referenced classes from AOSP support libraries.
-dontnote android.graphics.Insets

# AOSP support library:  ICU references to gender and plurals messages.
-dontnote libcore.icu.ICU
-keep class libcore.icu.ICU { *** get(...);}

# AOSP support library:  Handle classes that use reflection.
-dontnote android.support.v4.app.NotificationCompatJellybean

# Annotations are implemented as attributes, so we have to explicitly keep them.
# Catch all which encompasses attributes like RuntimeVisibleParameterAnnotations
# and RuntimeVisibleTypeAnnotations
-keepattributes RuntimeVisible*Annotation*

# Keep the annotations that proguard needs to process.
-keep class com.google.android.apps.common.proguard.UsedBy*

# Just because native code accesses members of a class, does not mean that the
# class itself needs to be annotated - only annotate classes that are
# referenced themselves in native code.
-keep @com.google.android.apps.common.proguard.UsedBy* class * {
  <init>();
}
-keepclassmembers class * {
    @com.google.android.apps.common.proguard.UsedBy* *;
}

# For design widgets from Android Support Library
-keep class android.support.design.widget.** { *; }
-keep interface android.support.design.widget.** { *; }
