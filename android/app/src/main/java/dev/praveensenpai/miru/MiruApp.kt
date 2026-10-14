package dev.praveensenpai.miru

import android.app.Application

/**
 * Application entrypoint.
 *
 * Miru is serverless, so there is no backend to configure at startup: the
 * extractor, SponsorBlock, and Return YouTube Dislike clients are lazy
 * singletons that initialise on first use. This class exists so Hilt or manual
 * DI can be introduced later without renaming the manifest entry.
 */
class MiruApp : Application()