/**
 * Public type definitions for Simply Save Voice SDK.
 * These types describe the contract between the APK team and the SDK.
 */

export enum Language {
  HINDI = 'HINDI',
  BENGALI = 'BENGALI',
  GUJARATI = 'GUJARATI',
  MARATHI = 'MARATHI',
  ODIA = 'ODIA',
  ASSAMESE = 'ASSAMESE',
  BHOJPURI = 'BHOJPURI',
  MAITHILI = 'MAITHILI',
  ENGLISH = 'ENGLISH',
}

export enum Intent {
  CREATE_GULLAK_SIP = 'CREATE_GULLAK_SIP',
  INVEST_LUMPSUM = 'INVEST_LUMPSUM',
  CHECK_BALANCE = 'CHECK_BALANCE',
  NOMINEE = 'NOMINEE',
  NOTIFICATION = 'NOTIFICATION',
  ECS_MANDATE = 'ECS_MANDATE',
  BRIGHT_BHAVISHYA = 'BRIGHT_BHAVISHYA',
  FALLBACK = 'FALLBACK',
}

export enum InputMode {
  VOICE = 'VOICE',
  TEXT = 'TEXT',
}

export interface SDKConfig {
  targetLanguage?: Language;
  ttsEnabled?: boolean;
  ttsEnabledForTextMode?: boolean;
  intentAcceptThreshold?: number;
  intentRejectThreshold?: number;
  maxClarificationTurns?: number;
  storageQuotaMB?: number;
  customVocabulary?: string[];
  /** Base path for LOCAL_PATH models (e.g. file:///data/...). */
  modelLocalBasePath?: string | null;
  /** Base URL for REMOTE_DOWNLOAD models (e.g. http://YOUR_IP:8765/release). */
  modelDownloadBaseUrl?: string | null;
  /** When true, recording runs until user stops (no VAD); use for "press Stop when done". */
  useContinuousRecording?: boolean;
  /** When true, intent is not classified; [hardcodedIntent] is used so STT/translation can be tested without an intent model. Set to false when you add an intent classification model. */
  useHardcodedIntent?: boolean;
  /** Intent to use when [useHardcodedIntent] is true. */
  hardcodedIntent?: Intent;
}

export interface VoiceResult {
  sessionId: string;
  turn: number;
  intent: Intent;
  confidence: number;
  isComplete: boolean;
  slotsCollected: Record<string, string | number>;
  slotsPending: string[];
  responseTextTarget: string;
  responseTextEnglish: string;
  audioFilePath: string | null;
  audioSampleRate: number;
  processingTimeMs: number;
  language: Language;
  requiresConfirmation?: boolean;
}

export interface SDKError {
  code: string;
  message: string;
  sessionId: string | null;
  recoverable: boolean;
}

/** Result of successful SDK initialization. */
export interface SDKInitResult {
  /** Logical names of models that were loaded/downloaded (e.g. whisper_tiny_v1, indictrans2_en_indic_v1). */
  readyModelNames: string[];
}

/** Per-model storage info (size, file count). */
export interface ModelInfo {
  logicalName: string;
  sizeBytes: number;
  fileCount: number;
}

/** Downloaded/cached model storage location and sizes. Call after initialize. */
export interface ModelStorageInfo {
  storagePath: string;
  totalSizeBytes: number;
  models: ModelInfo[];
}

export interface LanguageDownloadProgress {
  language: Language;
  bytesDownloaded: number;
  totalBytes: number;
  percentage: number;
  status: 'PENDING' | 'DOWNLOADING' | 'PROCESSING' | 'COMPLETE' | 'FAILED';
}

/** Machine-readable error codes */
export const SDK_NOT_INITIALIZED = 'SDK_NOT_INITIALIZED';
export const INVALID_AUDIO_FORMAT = 'INVALID_AUDIO_FORMAT';
export const MODEL_NOT_AVAILABLE = 'MODEL_NOT_AVAILABLE';
export const LANGUAGE_NOT_DOWNLOADED = 'LANGUAGE_NOT_DOWNLOADED';
export const INFERENCE_FAILED = 'INFERENCE_FAILED';
export const UNAUTHORIZED_CALLER = 'UNAUTHORIZED_CALLER';
export const STORAGE_QUOTA_EXCEEDED = 'STORAGE_QUOTA_EXCEEDED';
export const NETWORK_REQUIRED_FOR_LANGUAGE = 'NETWORK_REQUIRED_FOR_LANGUAGE';
export const MAX_CLARIFICATION_EXCEEDED = 'MAX_CLARIFICATION_EXCEEDED';
export const MODEL_CORRUPTED = 'MODEL_CORRUPTED';
