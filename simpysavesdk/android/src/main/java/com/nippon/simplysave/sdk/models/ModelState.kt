package com.nippon.simplysave.sdk.models

/**
 * Runtime state of a model. Lifecycle manager only; pipeline never sees this.
 */
enum class ModelState {
    UNLOADED,
    LOADING,
    WARMING_UP,
    READY,
    UNLOADING,
    ERROR
}
