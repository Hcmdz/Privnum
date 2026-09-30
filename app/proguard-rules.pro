# Strip debug/verbose/error logging in release builds (may carry phone numbers).
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
    public static int e(...);
    public static int w(...);
}

# kotlinx-serialization: keep serializable route classes used by Navigation 3.
-keep @kotlinx.serialization.Serializable class * { *; }
-keepattributes *Annotation*, InnerClasses, EnclosingMethod

# ez-vcard ships no consumer rules and builds its property and parameter tables
# reflectively: CaseClasses reads the public static fields of a class through
# Class.getFields() and MediaTypeCaseClasses instantiates MediaTypeParameter by
# name. Shrinking renames those members, so a vCard 2.1 file no longer matched
# any property in a release build, while 3.0 kept working. The failure was
# silent: parseVcf's catch turned it into "no contacts found". Scoped to the
# two packages that hold the tables so ez-vcard's unused Jackson layer, which
# references classes absent from this classpath, is not pulled in.
-keep class ezvcard.util.CaseClasses { *; }
-keep class ezvcard.util.SupportedVersionsHelper { *; }
-keepclassmembers class ezvcard.property.** { *; }
-keepclassmembers class ezvcard.parameter.** { *; }
