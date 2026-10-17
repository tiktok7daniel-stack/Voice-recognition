import { pipeline, env } from '@huggingface/transformers';

// Models are always fetched from the Hugging Face Hub and cached by the browser,
// so after the first download the app works offline.
env.allowLocalModels = false;
env.useBrowserCache = true;

// Free, open Whisper checkpoints converted to ONNX. All are multilingual and
// we force Italian at inference time.
const MODELS = {
  base: 'onnx-community/whisper-base',
  small: 'onnx-community/whisper-small',
  turbo: 'onnx-community/whisper-large-v3-turbo',
};

let transcriber = null;
let loadedKey = null;

async function hasWebGPU() {
  try {
    if (!('gpu' in navigator)) return false;
    return !!(await navigator.gpu.requestAdapter());
  } catch {
    return false;
  }
}

function dtypeFor(device) {
  // WebGPU: fp16 encoder keeps accuracy, q4 decoder keeps memory low.
  // WASM (CPU): 8-bit quantisation is the best speed/quality trade-off.
  return device === 'webgpu'
    ? { encoder_model: 'fp16', decoder_model_merged: 'q4' }
    : { encoder_model: 'q8', decoder_model_merged: 'q8' };
}

async function load(modelKey) {
  const device = (await hasWebGPU()) ? 'webgpu' : 'wasm';
  const key = `${modelKey}:${device}`;
  if (transcriber && loadedKey === key) return device;

  if (transcriber) {
    await transcriber.dispose?.();
    transcriber = null;
  }

  const progress_callback = (p) => self.postMessage({ type: 'progress', data: p });
  try {
    transcriber = await pipeline('automatic-speech-recognition', MODELS[modelKey], {
      device,
      dtype: dtypeFor(device),
      progress_callback,
    });
    loadedKey = key;
    return device;
  } catch (err) {
    if (device !== 'webgpu') throw err;
    // Some phones expose WebGPU but fail on it; fall back to CPU.
    transcriber = await pipeline('automatic-speech-recognition', MODELS[modelKey], {
      device: 'wasm',
      dtype: dtypeFor('wasm'),
      progress_callback,
    });
    loadedKey = `${modelKey}:wasm`;
    return 'wasm';
  }
}

self.addEventListener('message', async ({ data }) => {
  if (data.type !== 'transcribe') return;
  try {
    const device = await load(data.model);
    self.postMessage({ type: 'ready', device });

    const output = await transcriber(data.audio, {
      language: 'italian',
      task: 'transcribe',
      chunk_length_s: 30,
      stride_length_s: 5,
      return_timestamps: true,
    });

    self.postMessage({ type: 'done', text: output.text, chunks: output.chunks ?? [] });
  } catch (err) {
    self.postMessage({ type: 'error', message: err?.message ?? String(err) });
  }
});
