/*
 * AutoTV – aanpassingen bovenop AABrowser (GPL-3.0).
 *
 * - Per-site browser-identiteit: Netflix e.d. krijgen automatisch de desktop-identiteit
 *   (anders sturen ze door naar de Android-app).
 * - Eenvoudige adblocker op basis van host- en padlijsten.
 * - YouTube-advertenties overslaan via een script.
 */

package com.kododake.aabrowser.web

import android.net.Uri
import android.webkit.WebResourceResponse
import android.webkit.WebView
import com.kododake.aabrowser.data.BrowserPreferences
import com.kododake.aabrowser.model.UserAgentProfile
import java.io.ByteArrayInputStream

object AutoTvTweaks {

    /** Sites die alleen werken met een pc-browser-identiteit. */
    private val DESKTOP_HOSTS = listOf(
        "netflix.com",
        "primevideo.com",
        "amazon.com",
        "amazon.nl",
        "videoland.com",
        "disneyplus.com",
    )

    private val AD_HOSTS = listOf(
        "doubleclick.net",
        "googlesyndication.com",
        "googleadservices.com",
        "googletagservices.com",
        "google-analytics.com",
        "adservice.google.com",
        "adservice.google.nl",
        "pubads.g.doubleclick.net",
        "securepubads.g.doubleclick.net",
        "ads.youtube.com",
        "amazon-adsystem.com",
        "adnxs.com",
        "criteo.com",
        "criteo.net",
        "taboola.com",
        "outbrain.com",
        "scorecardresearch.com",
        "quantserve.com",
        "moatads.com",
        "adsrvr.org",
        "rubiconproject.com",
        "pubmatic.com",
        "openx.net",
        "casalemedia.com",
        "smartadserver.com",
        "teads.tv",
        "hotjar.com",
    )

    /** Pad-fragmenten die op YouTube voor advertenties/ad-tracking gebruikt worden. */
    private val YOUTUBE_AD_PATHS = listOf(
        "/pagead/",
        "/api/stats/ads",
        "/ptracking",
        "/get_midroll_info",
        "/youtubei/v1/log_event",
    )

    private fun hostMatches(host: String, domains: List<String>): Boolean =
        domains.any { host == it || host.endsWith(".$it") }

    fun wantsDesktop(url: String?): Boolean? {
        val host = url?.let { Uri.parse(it).host?.lowercase() } ?: return null
        return if (hostMatches(host, DESKTOP_HOSTS)) true else null
    }

    /**
     * Zorgt dat de WebView de juiste identiteit heeft voor [url].
     * @return true als de identiteit gewijzigd is (de pagina moet dan opnieuw geladen worden).
     */
    fun applyIdentityFor(view: WebView, url: String?): Boolean {
        val scheme = url?.let { Uri.parse(it).scheme?.lowercase() }
        if (scheme != "http" && scheme != "https") return false
        val context = view.context
        val forcedDesktop = wantsDesktop(url) == true
        val profile = if (forcedDesktop) UserAgentProfile.ANDROID_CHROME else BrowserPreferences.getUserAgentProfile(context)
        val desktop = forcedDesktop || BrowserPreferences.shouldUseDesktopMode(context)
        if (UserAgentManager.isBrowserIdentityApplied(view, profile, desktop)) return false
        UserAgentManager.applyBrowserIdentity(view, profile, desktop)
        return true
    }

    fun shouldBlock(uri: Uri?): Boolean {
        val host = uri?.host?.lowercase() ?: return false
        if (hostMatches(host, AD_HOSTS)) return true
        if (host == "youtube.com" || host.endsWith(".youtube.com")) {
            val path = uri.path ?: return false
            return YOUTUBE_AD_PATHS.any { path.startsWith(it) }
        }
        return false
    }

    fun emptyResponse(): WebResourceResponse =
        WebResourceResponse("text/plain", "utf-8", 204, "No Content", emptyMap(), ByteArrayInputStream(ByteArray(0)))

    val YOUTUBE_ORIGINS = setOf(
        "https://youtube.com",
        "https://www.youtube.com",
        "https://m.youtube.com",
    )

    val YOUTUBE_AD_SKIP_JS = """
        (function() {
            if (window.__autotvAdSkip) return;
            window.__autotvAdSkip = true;
            var mutedByUs = false;
            function tick() {
                try {
                    var player = document.querySelector('.ad-showing, .ad-interrupting');
                    var video = document.querySelector('video');
                    if (player && video) {
                        if (!video.muted) { video.muted = true; mutedByUs = true; }
                        if (isFinite(video.duration) && video.duration > 0) {
                            video.currentTime = video.duration;
                        }
                    } else if (video && mutedByUs) {
                        video.muted = false;
                        mutedByUs = false;
                    }
                    document.querySelectorAll(
                        '.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .ytp-skip-ad-button, .ytm-ad-skip-button, .ytp-ad-overlay-close-button'
                    ).forEach(function(b) { b.click(); });
                    document.querySelectorAll(
                        'ytm-promoted-sparkles-web-renderer, ytm-companion-ad-renderer, ad-slot-renderer, ' +
                        'ytd-ad-slot-renderer, ytm-promoted-video-renderer, ytd-banner-promo-renderer, ' +
                        'ytd-in-feed-ad-layout-renderer, ytm-statement-banner-renderer'
                    ).forEach(function(e) { e.remove(); });
                } catch (e) {}
            }
            setInterval(tick, 300);
        })();
    """.trimIndent()
}
