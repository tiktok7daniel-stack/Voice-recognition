# Voce Italiana 🇮🇹🎙️

تطبيق أندرويد (APK) بيحوّل أي ملف صوتي **إيطالي** لنص مكتوب بدقة عالية — **على الموبايل نفسه ومن غير نت** بعد أول تحميل للموديل.

## التحميل

👉 [**VoceItaliana.apk**](../../releases/download/apk-latest/VoceItaliana.apk) (من صفحة [Releases](../../releases/tag/apk-latest))
بيتبني أوتوماتيك بـ GitHub Actions مع كل push.

## المميزات

- **محرّك native** مبني على [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) (k2-fsa، مفتوح المصدر) — أسرع وأدق بكتير من أي حل بيشتغل في المتصفح.
- **4 موديلات مجانية مفتوحة المصدر** تختار منها:

  | الموديل | الحجم | ليه تختاره |
  |---|---|---|
  | **Parakeet TDT v3** (NVIDIA) — الافتراضي | 670 MB | دقيق جداً، سريع على المعالج، بيحط علامات ترقيم |
  | **Whisper Large v3 Turbo** (OpenAI) | 1.04 GB | دقة عالية جداً، متثبت على الإيطالي |
  | **Cohere Transcribe** (2B) | ≈ 2.1 GB | الأدق في المقارنات المنشورة — محتاج موبايل قوي (8GB رام) |
  | **Whisper Small** (OpenAI) | 375 MB | للموبايلات الضعيفة |

- **كشف الكلام (Silero VAD):** بيقطّع الصوت لجمل ويتجاهل السكوت والموسيقى، وده بيمنع "الهلوسة" (جمل وهمية بيطلعها Whisper على السكوت) وبيخلّي الملفات الطويلة تشتغل بذاكرة ثابتة.
- **أي صيغة:** mp3, m4a, aac, wav, flac, ogg, **opus (فويسات واتساب)**, amr, وكمان الصوت من فيديو mp4.
- **مشاركة مباشرة:** من واتساب أو مدير الملفات → Share → Voce Italiana.
- النص بيظهر **أول بأول** وإنت مستني، وبعد ما يخلص تقدر **تعدّله**.
- **نسخ، مشاركة، حفظ TXT، وملف ترجمة SRT** بالتوقيتات، وعرض بالتوقيتات `[mm:ss]`.
- تحميل الموديلات **بيكمّل من مكان ما وقف** لو النت قطع.
- واجهة **MANHWA (زجاج ونيون)** بـ Jetpack Compose.

## إزاي بيشتغل

```
ملف صوتي ─► MediaCodec (فك أي صيغة) ─► 16kHz mono ─► Silero VAD (تقطيع جمل)
          ─► sherpa-onnx OfflineRecognizer (Parakeet / Whisper / Cohere) ─► نص + توقيتات
```

| الملف | فيه إيه |
|---|---|
| `app/src/main/java/.../asr/Models.kt` | كتالوج الموديلات وروابطها وإعداداتها (الإيطالي متثبت) |
| `asr/ModelDownloader.kt` | تحميل الموديلات مع الاستكمال |
| `asr/AudioDecoder.kt` | فك الصوت وتحويله لـ 16kHz بشكل متدفق |
| `asr/Transcriber.kt` | VAD + التعرف على الكلام |
| `ui/Glass.kt`, `ui/ManhwaKit.kt` | نظام تصميم MANHWA |
| `ui/MainScreen.kt` | الشاشة |

## البناء محلياً

محتاج Android SDK و JDK 17:

```bash
mkdir -p app/libs app/src/main/assets
curl -L -o app/libs/sherpa-onnx.aar https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.8/sherpa-onnx-1.13.8.aar
curl -L -o app/src/main/assets/silero_vad.onnx https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx
./gradlew assembleRelease
```

> مفتاح التوقيع `app/voce-release.jks` موجود في الريبو عشان كل نسخة جديدة تتثبت فوق القديمة. غيّره قبل النشر على Google Play.
