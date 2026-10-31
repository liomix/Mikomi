# libzhuque.so 通过 JNI 按固定包名/类名查找方法，release 构建必须保留。
-keep class com.yy.myuko.component.ktor.addressing.ZhuqueSessionNativeLib { *; }
