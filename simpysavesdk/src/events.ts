/**
 * Typed event subscriptions for Simply Save Voice SDK.
 * Wraps React Native NativeEventEmitter behind typed event names and payloads.
 */

import { NativeEventEmitter, NativeModules, EmitterSubscription } from 'react-native';
import type { Language, LanguageDownloadProgress } from './types';

const EVENT_LANGUAGE_DOWNLOAD_PROGRESS = 'SDK_LANGUAGE_DOWNLOAD_PROGRESS';
const EVENT_AUDIO_LEVEL = 'SDK_AUDIO_LEVEL';
const EVENT_SDK_READY = 'SDK_READY';
const EVENT_INIT_PROGRESS = 'SDK_INIT_PROGRESS';

function getEventEmitter(): NativeEventEmitter | null {
  const module = NativeModules.SimplySaveVoiceModule;
  if (!module) return null;
  return new NativeEventEmitter(module);
}

/**
 * Subscribe to language model download progress.
 * Returned subscription must be removed in component cleanup to avoid leaks.
 */
export function onLanguageDownloadProgress(
  callback: (progress: LanguageDownloadProgress) => void
): EmitterSubscription | null {
  const emitter = getEventEmitter();
  if (!emitter) return null;
  return emitter.addListener(EVENT_LANGUAGE_DOWNLOAD_PROGRESS, callback);
}

/**
 * Subscribe to real-time microphone audio level during recording.
 * Used for waveform visualization.
 */
export function onAudioLevel(callback: (level: number) => void): EmitterSubscription | null {
  const emitter = getEventEmitter();
  if (!emitter) return null;
  return emitter.addListener(EVENT_AUDIO_LEVEL, callback);
}

/**
 * Fires once when SDK initialization completes.
 * Alternative to initialize() Promise for event-driven patterns.
 */
export function onSDKReady(callback: (languages: Language[]) => void): EmitterSubscription | null {
  const emitter = getEventEmitter();
  if (!emitter) return null;
  return emitter.addListener(EVENT_SDK_READY, callback);
}

/**
 * Fires during SDK initialization with progress messages (e.g. "Downloading whisper_tiny_v1...", "Resuming encoder_int8.onnx (80 MB)...").
 * Use to show live init logs in the UI.
 */
export function onInitProgress(callback: (message: string) => void): EmitterSubscription | null {
  const emitter = getEventEmitter();
  if (!emitter) return null;
  return emitter.addListener(EVENT_INIT_PROGRESS, (payload: { message?: string }) => {
    callback(payload?.message ?? '');
  });
}
