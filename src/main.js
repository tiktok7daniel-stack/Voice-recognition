import { Capacitor } from '@capacitor/core';
import { Share } from '@capacitor/share';

const $ = (id) => document.getElementById(id);
// The Android WebView can't download blobs or use the Web Share API,
// so inside the app we hand text to the native share sheet instead.
const isNative = Capacitor.isNativePlatform();

const worker = new Worker(new URL('./worker.js', import.meta.url), { type: 'module' });

let audioData = null;
let fileName = 'trascrizione';
let lastChunks = [];
let lastText = '';
let busy = false;
let model = 'small';
let showTs = false;

const MODEL_HINTS = {
  base: 'حوالي 80MB — سريع ومناسب للموبايلات الضعيفة',
  small: 'حوالي 250MB — أحسن توازن بين الدقة والسرعة',
  turbo: 'حوالي 600MB+ — الأدق، محتاج موبايل قوي ووقت أطول',
};

// Status colors carry fixed meaning: amber = waiting, ember = working, mint = done, crimson = error.
const STATES = {
  wait: { color: 'var(--amber)', icon: 'hourglass_top', pulse: false },
  work: { color: 'var(--ember)', icon: 'graphic_eq', pulse: true },
  ok: { color: 'var(--mint)', icon: 'check_circle', pulse: false },
  error: { color: 'var(--crimson)', icon: 'error', pulse: false },
};

// Whisper expects 16 kHz mono PCM.
async function decodeTo16kMono(file) {
  const buf = await file.arrayBuffer();
  const ctx = new (window.AudioContext || window.webkitAudioContext)({ sampleRate: 16000 });
  try {
    const decoded = await ctx.decodeAudioData(buf);
    if (decoded.numberOfChannels === 1) return decoded.getChannelData(0);
    const out = new Float32Array(decoded.length);
    for (let c = 0; c < decoded.numberOfChannels; c++) {
      const ch = decoded.getChannelData(c);
      for (let i = 0; i < ch.length; i++) out[i] += ch[i];
    }
    for (let i = 0; i < out.length; i++) out[i] /= decoded.numberOfChannels;
    return out;
  } finally {
    ctx.close();
  }
}

function setStatus(state, title, text, pct = null) {
  const s = STATES[state];
  $('statusCard').hidden = false;
  $('statusIcon').style.setProperty('--c', s.color);
  $('statusIcon').classList.toggle('pulse', s.pulse);
  $('statusSymbol').textContent = s.icon;
  $('statusTitle').textContent = title;
  $('statusText').textContent = text;
  const fill = $('barFill');
  fill.classList.toggle('indeterminate', pct === null);
  fill.style.width = pct === null ? '' : `${Math.max(2, Math.min(100, pct))}%`;
}

function fmtTime(s, sep = ',') {
  if (s == null || Number.isNaN(s)) s = 0;
  const h = Math.floor(s / 3600);
  const m = Math.floor((s % 3600) / 60);
  const sec = Math.floor(s % 60);
  const ms = Math.round((s - Math.floor(s)) * 1000);
  const p = (n, l = 2) => String(n).padStart(l, '0');
  return `${p(h)}:${p(m)}:${p(sec)}${sep}${p(ms, 3)}`;
}

function cleanText(t) {
  return t.replace(/\s+/g, ' ').trim();
}

function renderResult() {
  const el = $('result');
  if (showTs && lastChunks.length) {
    el.innerText = lastChunks
      .map((c) => `[${fmtTime(c.timestamp[0], '.').slice(3, 8)}] ${cleanText(c.text)}`)
      .join('\n');
  } else {
    // One paragraph per chunk reads naturally for long recordings.
    el.innerText = lastChunks.length
      ? lastChunks.map((c) => cleanText(c.text)).filter(Boolean).join('\n')
      : cleanText(lastText);
  }
}

function toSrt() {
  return lastChunks
    .map((c, i) => {
      const [a, b] = c.timestamp;
      return `${i + 1}\n${fmtTime(a)} --> ${fmtTime(b ?? a + 2)}\n${cleanText(c.text)}\n`;
    })
    .join('\n');
}

async function shareText(text, title = fileName) {
  if (isNative) return Share.share({ title, text, dialogTitle: title });
  if (navigator.share) {
    try {
      await navigator.share({ title, text });
    } catch {}
    return;
  }
  await navigator.clipboard.writeText(text);
}

function download(content, ext) {
  if (isNative) return shareText(content, `${fileName}.${ext}`);
  const blob = new Blob([content], { type: 'text/plain;charset=utf-8' });
  const a = document.createElement('a');
  a.href = URL.createObjectURL(blob);
  a.download = `${fileName}.${ext}`;
  a.click();
  setTimeout(() => URL.revokeObjectURL(a.href), 1000);
}

$('file').addEventListener('change', async (e) => {
  const file = e.target.files?.[0];
  if (!file) return;
  fileName = file.name.replace(/\.[^.]+$/, '') || 'trascrizione';
  $('fileLabel').textContent = file.name;
  $('player').src = URL.createObjectURL(file);
  $('player').hidden = false;
  $('go').disabled = true;
  setStatus('work', 'بقرا الملف', file.name);
  try {
    audioData = await decodeTo16kMono(file);
    const mins = (audioData.length / 16000 / 60).toFixed(1);
    $('fileIcon').style.setProperty('--c', 'var(--mint)');
    setStatus('ok', 'الملف جاهز', `المدة ${mins} دقيقة — دوس "ابدأ استخراج الكلام"`, 100);
    $('go').disabled = false;
  } catch (err) {
    audioData = null;
    $('fileIcon').style.setProperty('--c', 'var(--crimson)');
    setStatus('error', 'مش قادر أقرا الملف', 'جرّب صيغة تانية زي mp3 أو wav أو m4a', 0);
  }
});

$('go').addEventListener('click', () => {
  if (!audioData || busy) return;
  busy = true;
  $('go').disabled = true;
  setModelsDisabled(true);
  $('resultCard').hidden = true;
  setStatus('wait', 'بجهّز الموديل', 'أول مرة بس بيتحمّل من النت، بعد كده بيشتغل أوفلاين');
  // Copy so the original buffer stays usable for a re-run with another model.
  worker.postMessage({ type: 'transcribe', model, audio: audioData.slice() });
});

worker.addEventListener('message', ({ data }) => {
  switch (data.type) {
    case 'progress': {
      const p = data.data;
      if (p.status === 'progress_total') {
        const mb = (n) => (n / 1024 / 1024).toFixed(0);
        setStatus('wait', 'بحمّل الموديل', `${mb(p.loaded)} / ${mb(p.total)} MB`, p.progress);
      }
      break;
    }
    case 'ready': {
      const where = data.device === 'webgpu' ? 'كارت الشاشة (WebGPU)' : 'المعالج (CPU)';
      setStatus('work', 'بستخرج الكلام الإيطالي', `شغال على ${where} — ممكن ياخد شوية وقت`);
      break;
    }
    case 'done':
      lastText = data.text;
      lastChunks = data.chunks;
      renderResult();
      $('resultCard').hidden = false;
      setStatus('ok', 'خلصنا', 'تقدر تعدّل النص أو تنسخه أو تشاركه', 100);
      finish();
      break;
    case 'error':
      if (/fetch|network/i.test(data.message)) {
        setStatus('error', 'مش قادر أحمّل الموديل', 'أول مرة لازم تكون متصل بالإنترنت', 0);
      } else {
        setStatus('error', 'حصل خطأ', data.message, 0);
      }
      finish();
      break;
  }
});

function finish() {
  busy = false;
  $('go').disabled = !audioData;
  setModelsDisabled(false);
}

function setModelsDisabled(disabled) {
  document.querySelectorAll('#models .chip').forEach((c) => (c.disabled = disabled));
}

function selectModel(key) {
  model = key;
  document.querySelectorAll('#models .chip').forEach((c) => {
    const on = c.dataset.model === key;
    c.classList.toggle('selected', on);
    c.setAttribute('aria-checked', on);
  });
  $('modelHint').textContent = MODEL_HINTS[key];
}

$('models').addEventListener('click', (e) => {
  const chip = e.target.closest('.chip');
  if (chip && !chip.disabled) selectModel(chip.dataset.model);
});
selectModel(model);

$('showTs').addEventListener('click', () => {
  showTs = !showTs;
  $('showTs').setAttribute('aria-pressed', showTs);
  renderResult();
});

$('copy').addEventListener('click', async () => {
  await navigator.clipboard.writeText($('result').innerText);
  const label = $('copy').lastElementChild;
  label.textContent = 'اتنسخ';
  setTimeout(() => (label.textContent = 'نسخ'), 1500);
});

$('share').addEventListener('click', () => shareText($('result').innerText));

$('txt').addEventListener('click', () => download($('result').innerText, 'txt'));
$('srt').addEventListener('click', () => download(toSrt(), 'srt'));
