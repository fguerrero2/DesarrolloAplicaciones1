# Reglas de R8 propias de la app. Room, Hilt, kotlinx.serialization y Navigation traen las suyas.

# SQLCipher llama a estas clases desde código nativo (JNI): si R8 las renombra o las quita,
# la base cifrada no se puede abrir en la versión release.
-keep class net.zetetic.database.** { *; }
-keepclassmembers class net.zetetic.database.** { native <methods>; }
