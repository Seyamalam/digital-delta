# ONNX Runtime's Java classes and native method names are a JNI boundary.
-keep class ai.onnxruntime.** { *; }

# GeneratedMessageLite resolves schema fields by their generated names at runtime.
-keepclassmembers class ** extends com.google.protobuf.GeneratedMessageLite {
    <fields>;
}
