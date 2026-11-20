# ML Kit's transitive firebase-components 16.x discovers registrars by manifest
# name and no-argument constructor. AGP 9 strict R8 mode removes those constructors
# with the older consumer rules, leaving RemoteModelManager.getInstance() null.
# Keep only the reflective registration surface (Firebase upstream fix #6044).
-keep class * implements com.google.firebase.components.ComponentRegistrar { void <init>(); }
-keep,allowshrinking interface com.google.firebase.components.ComponentRegistrar

# JNI entry point is resolved by its declared class/method name.
-keep class com.example.livescreentranslation.privacy.NativeLogPrivacy { native <methods>; }
