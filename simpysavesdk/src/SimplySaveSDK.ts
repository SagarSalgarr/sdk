/**
 * Public SDK class for Simply Save Voice.
 * Singleton that wraps NativeModules.SimplySaveVoiceModule with typed methods.
 */

import { NativeModules, Platform } from 'react-native';
import type {
  SDKConfig,
  VoiceResult,
  Language,
  SDKError,
  SDKInitResult,
  ModelStorageInfo,
} from './types';
import {
  Language as LanguageEnum,
  SDK_NOT_INITIALIZED,
} from './types';

const MODULE_NAME = 'SimplySaveVoiceModule';

function configToMap(config: SDKConfig): Record<string, unknown> {
  return {
    targetLanguage: config.targetLanguage ?? LanguageEnum.HINDI,
    ttsEnabled: config.ttsEnabled ?? true,
    ttsEnabledForTextMode: config.ttsEnabledForTextMode ?? false,
    intentAcceptThreshold: config.intentAcceptThreshold ?? 0.85,
    intentRejectThreshold: config.intentRejectThreshold ?? 0.45,
    maxClarificationTurns: config.maxClarificationTurns ?? 3,
    storageQuotaMB: config.storageQuotaMB ?? 450,
    customVocabulary: config.customVocabulary ?? [],
    modelLocalBasePath: config.modelLocalBasePath ?? undefined,
    modelDownloadBaseUrl: config.modelDownloadBaseUrl ?? undefined,
    useContinuousRecording: config.useContinuousRecording ?? false,
    useHardcodedIntent: config.useHardcodedIntent ?? false,
    hardcodedIntent: config.hardcodedIntent ?? undefined,
  };
}

function mapToVoiceResult(map: Record<string, unknown>): VoiceResult {
  return {
    sessionId: String(map.sessionId ?? ''),
    turn: Number(map.turn ?? 0),
    intent: map.intent as VoiceResult['intent'],
    confidence: Number(map.confidence ?? 0),
    isComplete: Boolean(map.isComplete),
    slotsCollected: (map.slotsCollected as Record<string, string | number>) ?? {},
    slotsPending: Array.isArray(map.slotsPending) ? map.slotsPending as string[] : [],
    responseTextTarget: String(map.responseTextTarget ?? ''),
    responseTextEnglish: String(map.responseTextEnglish ?? ''),
    audioFilePath: map.audioFilePath != null ? String(map.audioFilePath) : null,
    audioSampleRate: Number(map.audioSampleRate ?? 22050),
    processingTimeMs: Number(map.processingTimeMs ?? 0),
    language: map.language as Language,
    requiresConfirmation: map.requiresConfirmation != null ? Boolean(map.requiresConfirmation) : undefined,
  };
}

export class SimplySaveSDK {
  private static instance: SimplySaveSDK | null = null;
  private readonly native = NativeModules[MODULE_NAME];

  private constructor() {}

  static getInstance(): SimplySaveSDK {
    if (SimplySaveSDK.instance == null) {
      SimplySaveSDK.instance = new SimplySaveSDK();
    }
    return SimplySaveSDK.instance;
  }

  private ensureNative(): void {
    if (!this.native) {
      const error: SDKError = {
        code: SDK_NOT_INITIALIZED,
        message: 'Simply Save Voice native module not found. Is the app running on Android and the package linked?',
        sessionId: null,
        recoverable: false,
      };
      throw error;
    }
  }

  /**
   * Initialize the SDK. Must be called before any other method.
   * Returns which models were loaded/downloaded so you can confirm model server reachability.
   */
  async initialize(licenseKey: string, config: SDKConfig = {}): Promise<SDKInitResult> {
    this.ensureNative();
    if (Platform.OS !== 'android') {
      return { readyModelNames: [] };
    }
    const configMap = configToMap(config);
    const result = await this.native.initialize(licenseKey, configMap);
    const readyModelNames = Array.isArray(result?.readyModelNames) ? result.readyModelNames as string[] : [];
    return { readyModelNames };
  }

  /**
   * Process a WAV file through the full voice pipeline.
   */
  async processVoice(
    audioFilePath: string,
    sessionId: string,
    language: Language
  ): Promise<VoiceResult> {
    this.ensureNative();
    const map = await this.native.processVoice(audioFilePath, sessionId, language);
    return mapToVoiceResult(map);
  }

  /**
   * Process typed text (skips audio steps).
   */
  async processText(
    text: string,
    sessionId: string,
    language: Language
  ): Promise<VoiceResult> {
    this.ensureNative();
    const map = await this.native.processText(text, sessionId, language);
    return mapToVoiceResult(map);
  }

  /**
   * Trigger download and preparation of a language's TTS model.
   */
  async prepareLanguage(language: Language): Promise<void> {
    this.ensureNative();
    return this.native.prepareLanguage(language);
  }

  /**
   * Change active language mid-session.
   */
  async setLanguage(language: Language): Promise<void> {
    this.ensureNative();
    return this.native.setLanguage(language);
  }

  /**
   * Check if a language's TTS model is on device and ready.
   */
  async isLanguageAvailable(language: Language): Promise<boolean> {
    this.ensureNative();
    return this.native.isLanguageAvailable(language);
  }

  /**
   * Get list of languages currently downloaded and ready.
   */
  async getSupportedLanguages(): Promise<Language[]> {
    this.ensureNative();
    const list = await this.native.getSupportedLanguages();
    return (list ?? []) as Language[];
  }

  /**
   * Get storage path and per-model sizes for downloaded models. Call after initialize.
   */
  async getModelStorageInfo(): Promise<ModelStorageInfo | null> {
    this.ensureNative();
    if (Platform.OS !== 'android') return null;
    const raw = await this.native.getModelStorageInfo();
    if (raw == null) return null;
    const models = Array.isArray(raw.models)
      ? (raw.models as Array<{ logicalName: string; sizeBytes: number; fileCount: number }>).map((m) => ({
          logicalName: m.logicalName,
          sizeBytes: Number(m.sizeBytes ?? 0),
          fileCount: Number(m.fileCount ?? 0),
        }))
      : [];
    return {
      storagePath: String(raw.storagePath ?? ''),
      totalSizeBytes: Number(raw.totalSizeBytes ?? 0),
      models,
    };
  }

  /**
   * Start internal microphone recording.
   */
  async startRecording(sessionId: string): Promise<void> {
    this.ensureNative();
    return this.native.startRecording(sessionId);
  }

  /**
   * Stop recording and process captured audio through the pipeline.
   */
  async stopRecordingAndProcess(language: Language): Promise<VoiceResult> {
    this.ensureNative();
    const map = await this.native.stopRecordingAndProcess(language);
    return mapToVoiceResult(map);
  }

  /**
   * Release all resources. Call when app backgrounds or user finishes voice interaction.
   */
  async destroy(): Promise<void> {
    if (!this.native) return Promise.resolve();
    return this.native.destroy();
  }
}
