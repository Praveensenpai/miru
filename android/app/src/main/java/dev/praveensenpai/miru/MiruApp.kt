package dev.praveensenpai.miru

import android.app.Application

/**
 * Application entrypoint.
 *
 * Kept intentionally minimal for v1. The network module is a lazy singleton, so
 * no manual wiring is required here yet; this exists so Hilt or manual DI can be
 * introduced without renaming the manifest entry.
 */
class MiruApp : Application()