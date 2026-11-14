# The watch reads the same API as the phone, through the same shared modules, which carry no
# rules of their own. These mirror the phone app's rules for that part; without them a release
# build cannot read an API answer, and every lookup looks like "no connection".

# Reflection metadata used by Retrofit, Gson TypeToken and kotlinx.serialization.
# Runtime libraries supply their own consumer rules.
-keepattributes Signature,InnerClasses,EnclosingMethod
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,AnnotationDefault
-keepattributes SourceFile,LineNumberTable

# Retrofit constructs these interfaces through Proxy and reads their generic types.
-keep,allowoptimization interface cx.aswin.boxlore.core.network.BoxLoreApi { *; }
-keep,allowoptimization interface cx.aswin.boxlore.core.network.BoxLoreApiV2 { *; }
# Retrofit 2.9's consumer rules predate full-mode generic return-type protection.
-keep,allowoptimization,allowobfuscation interface retrofit2.Call
-keep,allowoptimization,allowobfuscation class retrofit2.Response
-keep,allowoptimization,allowobfuscation interface kotlin.coroutines.Continuation

# Gson 2.10's consumer rule allows TypeToken subclasses to shrink.
-keep,allowoptimization,allowobfuscation class com.google.gson.reflect.TypeToken
-keep,allowobfuscation class * extends com.google.gson.reflect.TypeToken

# JSON field names and constructors are wire contracts.
-keep,allowoptimization class cx.aswin.boxlore.core.model.** {
    <fields>;
    <init>(...);
}
-keep,allowoptimization class cx.aswin.boxlore.core.network.model.** {
    <fields>;
    <init>(...);
}
