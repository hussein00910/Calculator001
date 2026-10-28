# الحاسبة (Calculator)

تطبيق حاسبة أندرويد بـ Kotlin وJetpack Compose وHilt، بمعمارية MVVM.

## التشغيل
1. افتح المجلد في Android Studio (الإصدار المتوافق مع AGP 9.4.0). إن لم يوجد Gradle wrapper فاستخدم Gradle 9.6.0 المدمج أو نفّذ `gradle wrapper --gradle-version 9.6.0` من الجذر.
2. JDK 17 على الأقل.
3. انتظر مزامنة Gradle ثم شغّل الوحدة `app` على جهاز أو محاكي (minSdk 26).
4. الاختبارات: `./gradlew test`

## البنية
```
app/src/main/java/com/example/calculator/
├── CalculatorApplication.kt, MainActivity.kt
├── di/CalculatorModule.kt
├── core/ui/theme/Theme.kt
└── feature/calculator/
    ├── domain/        Operation, ExpressionEvaluator, NumberFormatting
    ├── data/          ShuntingYardEvaluator
    └── presentation/  UiState, Action, Reducer, ViewModel, Screen
```

القرارات الافتراضية وقواعد الإدخال في `docs/pre-start-checklist.md`.

## حالة التحقق
- **تم التحقق**: منطق المقيّم والـReducer واختباراتهما (29 اختبارًا) ترجمتُها بمترجم Kotlin 2.4.21 وشغّلتها خارج أندرويد، ونجحت كلها (باستبدال `@Inject` و`JUnit` بنُسخ مبسطة).
- **لم يُتحقق منه**: بناء المشروع كاملًا بـ Gradle/AGP، تشغيل الواجهة، توافق Hilt/KSP مع Kotlin 2.4.21، توافق إصدار Android Studio، وظهور الرمز ⌫ على الأجهزة. لا أعلم إن كانت ستظهر تحذيرات أو أخطاء عند أول مزامنة.
- ملفات `gradlew` وwrapper jar غير موجودة (تعذّر توليدها لأن services.gradle.org محجوب في بيئة العمل)؛ الموجود هو `gradle-wrapper.properties` فقط.
