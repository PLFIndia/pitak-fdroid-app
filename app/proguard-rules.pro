# =============================================================================
# Pitaka R8 / ProGuard rules (F-11)
#
# Previously this file was a single blanket `-keep class dev.khoj.pitaka.** { *; }`
# which disabled R8 entirely — every class, method, and field name shipped in
# plaintext. R8 now runs (isMinifyEnabled=true) and the keeps below are scoped
# to ONLY what genuinely breaks under shrinking/obfuscation:
#
#   1. Reflection-based Moshi DTOs + domain models (KotlinJsonAdapterFactory,
#      NOT @JsonClass codegen — see NetworkModule.provideMoshi). Reflective
#      Moshi maps JSON keys to *field names*; if R8 renames the fields,
#      deserialization silently yields nulls. Keep the serialized classes and
#      their members.
#   2. Room entities/DAOs — Room's generated code + schema rely on stable names.
#   3. SQLCipher (net.zetetic) + Argon2 (com.lambdapioneer.argon2kt) — JNI/native
#      bindings R8 cannot trace.
#
# Everything else in dev.khoj.pitaka.** (UI, use cases, repositories, view
# models) is now fair game for R8 to shrink and obfuscate — that is the win.
# =============================================================================

# --- 1. Moshi (reflection-based) ---------------------------------------------
# Keep Moshi's own runtime + the Kotlin reflection metadata it walks.
-keep class com.squareup.moshi.** { *; }
-keep interface com.squareup.moshi.** { *; }
-keep @com.squareup.moshi.JsonClass class * { *; }
-keepclassmembers class * {
    @com.squareup.moshi.Json <fields>;
}
# Keep all JSON-serialized model classes WITH their members. These are decoded
# reflectively, so field names must survive. Scoped to the packages that hold
# JSON DTOs + domain models actually (de)serialized by Moshi.
-keep class dev.khoj.pitaka.data.remote.** { *; }
-keep class dev.khoj.pitaka.domain.model.** { *; }
# Export/import + backup serialization surface. PitakaExport (data.export) is
# the top-level type the export, publish, and JSON-import paths (de)serialize;
# omitting it lets R8 obfuscate its field names so reflective Moshi falls back
# to LinkedHashTreeMap and the importer's cast to Book crashes (F-11 regression
# caught on-device 2026-06-01). Keep the whole export package + BackupManifest.
-keep class dev.khoj.pitaka.data.export.** { *; }
# World-facing publish payload (PublishExport / PublishBook). Serialized by the
# publish path with reflective Moshi exactly like PitakaExport; same F-11 risk
# if R8 renames its fields. Kept as its own package so the export round-trip
# type and the publish-only type stay independent (PitakaExport must never carry
# vault-derived fields; PublishBook carries coarse availability).
-keep class dev.khoj.pitaka.data.publish.PublishExport { *; }
-keep class dev.khoj.pitaka.data.publish.PublishBook { *; }
-keep class dev.khoj.pitaka.data.publish.PublishBook$* { *; }
# Incremental-publish manifest (bare field names, reflective Moshi). Same F-11
# risk if R8 renames its fields — the manifest would silently fail to parse and
# every publish would re-upload everything (a perf regression, not a crash, but
# still defeats the whole incremental design).
-keep class dev.khoj.pitaka.data.publish.PublishManifest { *; }
-keep class dev.khoj.pitaka.data.backup.BackupManifest { *; }
-keep class dev.khoj.pitaka.data.backup.BackupManifest$* { *; }

# Kotlin reflection metadata used by KotlinJsonAdapterFactory.
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-keep class kotlin.Metadata { *; }
-keep class kotlin.reflect.** { *; }
-keep class kotlin.jvm.internal.** { *; }
-dontwarn org.jetbrains.annotations.**

# --- 2. Room -----------------------------------------------------------------
# Room generates code against entities + DAOs; keep them and the column fields.
-keep class dev.khoj.pitaka.data.local.** { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep @androidx.room.Database class * { *; }
-keepclassmembers @androidx.room.Entity class * { *; }

# --- 3. SQLCipher + Argon2 (native/JNI) --------------------------------------
-keep class net.zetetic.** { *; }
-keep interface net.zetetic.** { *; }
-keep class com.lambdapioneer.argon2kt.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}

# --- Retrofit / OkHttp (their own shipped consumer rules cover most) ---------
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-keepattributes Exceptions
