# Library-specific rules are supplied by AndroidX, Room, Retrofit, Hilt, Coil,
# and Kotlin Serialization. Retrofit needs generic signatures and annotations
# to reconstruct service method declarations after R8 optimization.
-keepattributes Signature,InnerClasses,EnclosingMethod
-keepattributes RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations,AnnotationDefault
