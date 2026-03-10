package com.nippon.simplysave.sdk.models

/**
 * Where model files are sourced. Only ModelStorageManager and ModelDeliveryManager use this.
 * - Lightweight: AAR_ASSETS (bundled) or LOCAL_PATH (from configurable base path).
 * - Heavier: REMOTE_DOWNLOAD — one base URL provided at init; all such models are hosted there.
 */
enum class AssetSource {
    /** Bundled in app assets (lightweight). */
    AAR_ASSETS,
    PLAY_FEATURE_DELIVERY,
    PLAY_ASSET_DELIVERY_ON_DEMAND,
    /** Lightweight: pick from local path (modelLocalBasePath / logicalName / fileName). */
    LOCAL_PATH,
    /** Heavier: download from single base URL (modelDownloadBaseUrl) on first use. */
    REMOTE_DOWNLOAD
}
