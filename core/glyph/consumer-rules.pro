# Le SDK Nothing est atteint par réflexion côté système (binder, Messenger,
# noms de classes dans les meta-data du manifeste). R8 ne voit pas ces liens :
# sans ces règles, la release compile, s'installe, et la matrice reste noire.
-keep class com.nothing.ketchum.** { *; }
-dontwarn com.nothing.ketchum.**

-dontwarn com.nothing.thirdparty.**

# Les services de toy sont instanciés par leur nom, écrit dans le manifeste.
# AGP garde déjà les composants déclarés ; cette règle protège les sous-classes
# intermédiaires (le socle et ses `open`) contre l'inlining agressif.
-keep class * extends red.suns.haloglyph.core.glyph.GlyphMatrixService { *; }
