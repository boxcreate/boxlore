# Reflection metadata used by Retrofit, Gson TypeToken and kotlinx.serialization.
# Runtime libraries supply their own consumer rules.
-keepattributes Signature,InnerClasses,EnclosingMethod
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,AnnotationDefault
-keepattributes SourceFile,LineNumberTable

# Retrofit constructs this interface through Proxy and reads its generic types.
-keep,allowoptimization interface cx.aswin.boxlore.core.network.BoxLoreApi { *; }
# Retrofit 2.9's consumer rules predate full-mode generic return-type protection.
# Keep the signature endpoints, not every class in Retrofit or Kotlin coroutines.
-keep,allowoptimization,allowobfuscation interface retrofit2.Call
-keep,allowoptimization,allowobfuscation class retrofit2.Response
-keep,allowoptimization,allowobfuscation interface kotlin.coroutines.Continuation

# Gson 2.10's consumer rule allows TypeToken subclasses to shrink. Preserve the
# concrete anonymous tokens and their generic superclass, not all Gson internals.
-keep,allowoptimization,allowobfuscation class com.google.gson.reflect.TypeToken
-keep,allowobfuscation class * extends com.google.gson.reflect.TypeToken

# JSON field names and constructors are storage/wire contracts. Ordinary methods
# can be optimized; repositories and unrelated core implementation code need no keep.
-keep,allowoptimization class cx.aswin.boxlore.core.model.** {
    <fields>;
    <init>(...);
}
-keep,allowoptimization class cx.aswin.boxlore.core.network.model.** {
    <fields>;
    <init>(...);
}
-keep,allowoptimization class cx.aswin.boxlore.core.catalog.backup.BoxLoreBackup { <fields>; <init>(...); }
-keep,allowoptimization class cx.aswin.boxlore.core.catalog.backup.GlobalPreferencesBackup { <fields>; <init>(...); }
-keep,allowoptimization class cx.aswin.boxlore.core.catalog.backup.DirectFeedOptInBackup { <fields>; <init>(...); }
-keep,allowoptimization class cx.aswin.boxlore.core.catalog.backup.SubscriptionFolderBackup { <fields>; <init>(...); }
-keep,allowoptimization class cx.aswin.boxlore.core.catalog.content.RecentSectionIntentRecord { <fields>; <init>(...); }
-keep,allowoptimization class cx.aswin.boxlore.core.database.PodcastEntity { <fields>; <init>(...); }
-keep,allowoptimization class cx.aswin.boxlore.core.database.ListeningHistoryEntity { <fields>; <init>(...); }
-keep,allowoptimization class cx.aswin.boxlore.core.ranking.AdaptiveRankingBackup { <fields>; <init>(...); }
-keep,allowoptimization class cx.aswin.boxlore.core.ranking.database.AdaptiveModelEntity { <fields>; <init>(...); }
-keep,allowoptimization class cx.aswin.boxlore.core.ranking.database.PreferenceFacetEntity { <fields>; <init>(...); }
-keep,allowoptimization class cx.aswin.boxlore.core.ranking.database.RankingExposureEntity { <fields>; <init>(...); }

# WorkManager persists names and constructs these reflectively. Keeping a class
# alone does not retain the public no-arg constructor in R8 full mode.
-keep,allowoptimization class * extends androidx.work.InputMerger { public <init>(); }
-keep,allowoptimization class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context,androidx.work.WorkerParameters);
}
# Permanent aliases for upgraded installations.
-keep,allowoptimization class cx.aswin.boxlore.core.data.service.** { public <init>(); }

# Manifest components, Room implementations, Cast/credentials providers, Firebase
# registrars and serializers have consumer/generated rules and artifact checks.

# Historical OEM splash-screen compatibility: prevent platform bridge inlining.
-dontwarn android.window.SplashScreenView
-keep class androidx.core.splashscreen.** { *; }

# Keep warnings/errors available for release diagnosis.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
