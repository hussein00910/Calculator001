// Top-level build file: يعرّف الإضافات فقط دون تطبيقها (تُطبَّق داخل :app).
// ملاحظة: مع AGP 9 لا حاجة لإضافة org.jetbrains.kotlin.android (Kotlin مدمج في AGP).
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}
