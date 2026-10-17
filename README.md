# Voce Italiana 🇮🇹🎙️

تطبيق موبايل بيحوّل أي ملف صوتي **إيطالي** لنص مكتوب بشكل احترافي.

- بيستخدم موديلات **OpenAI Whisper** المفتوحة والمجانية (عن طريق [Transformers.js](https://github.com/huggingface/transformers.js)).
- اللغة متثبتة على **الإيطالي** (`language: italian`, `task: transcribe`) — مفيش ترجمة، النص بيطلع إيطالي زي ما اتقال.
- المعالجة كلها **على الموبايل نفسه**؛ الملف مش بيترفع على أي سيرفر.
- أول مرة بس محتاج إنترنت عشان يحمّل الموديل، بعد كده بيشتغل أوفلاين.
- بيقبل mp3 / m4a / wav / ogg / opus (ريكوردات واتساب) / aac / flac / فيديو mp4.
- نسخ، مشاركة، تحميل TXT أو ملف ترجمة SRT بالتوقيتات.

## الموديلات

| الاختيار | الموديل | الحجم تقريباً | ملاحظات |
|---|---|---|---|
| Base | `onnx-community/whisper-base` | ~80MB | سريع، دقة متوسطة |
| Small (الافتراضي) | `onnx-community/whisper-small` | ~250MB | أحسن توازن للموبايل |
| Large v3 Turbo | `onnx-community/whisper-large-v3-turbo` | ~600MB+ | الأدق، محتاج موبايل قوي |

لو الموبايل/المتصفح بيدعم WebGPU بيستخدمه (أسرع بكتير)، غير كده بيشتغل على المعالج.

## التحميل

- **APK للأندرويد:** من صفحة [Releases](../../releases/tag/apk-latest) نزّل `VoceItaliana.apk`.
- **نسخة الويب (أي موبايل حتى آيفون):** GitHub Pages للريبو — افتحها من Chrome/Safari واختار "Add to Home screen".

الاتنين بيتبنوا أوتوماتيك بـ GitHub Actions مع كل push.

## التطوير

```bash
npm ci --ignore-scripts
npm run dev            # يفتح على http://localhost:5173
npm run build          # نسخة الويب في dist/
npm run android:sync   # بعد `npx cap add android` مرة واحدة، وبعدها افتح android/ في Android Studio
```

الكود:
- `src/worker.js` — تحميل الموديل وتشغيل Whisper في Web Worker (عشان الواجهة متهنجش).
- `src/main.js` — الواجهة: قراءة الملف وتحويله لـ 16kHz mono، عرض النتيجة، التصدير.
