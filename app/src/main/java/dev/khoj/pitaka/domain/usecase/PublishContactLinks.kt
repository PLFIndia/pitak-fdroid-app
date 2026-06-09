package dev.khoj.pitaka.domain.usecase

import java.net.URLEncoder

/**
 * Pure (JVM-only, no Android) builder for the optional "library contact"
 * line rendered on the published page. Kept separate from
 * [PublishLibraryUseCase] so the GPS parse-or-fallback and link logic is
 * unit-testable without Android dependencies.
 *
 * All three fields are optional. When all are blank, [render] returns an
 * empty string and the viewer renders no contact line at all.
 *
 * Privacy note: unlike notes (stripped at publish per D10), these values are
 * PII the user has *deliberately* chosen to publish to a public page. The
 * Publish UI surfaces a "shown publicly" caution; this helper only formats.
 */
object PublishContactLinks {

    data class Contact(
        val location: String = "",
        val email: String = "",
        val phone: String = "",
    )

    /**
     * Builds the inner HTML for the contact line, or "" when nothing is set.
     * Each present field becomes one tappable `<a>` styled as `.contact-item`.
     * Values are HTML-escaped; hrefs are URL/attribute-safe.
     */
    fun render(contact: Contact, escape: (String) -> String): String {
        val items = buildList {
            locationHref(contact.location)?.let { href ->
                add(anchor(href, "📍 " + contact.location.trim(), escape))
            }
            emailHref(contact.email)?.let { href ->
                add(anchor(href, "✉ " + contact.email.trim(), escape))
            }
            phoneHref(contact.phone)?.let { href ->
                add(anchor(href, "☎ " + contact.phone.trim(), escape))
            }
        }
        if (items.isEmpty()) return ""
        return "<div class=\"contact\">" + items.joinToString("") + "</div>"
    }

    private fun anchor(href: String, label: String, escape: (String) -> String): String {
        // href is built from validated/encoded parts; escape it for the
        // attribute context anyway (defence in depth), and escape the label
        // for the text context.
        return "<a class=\"contact-item\" href=\"${escape(href)}\" " +
            "target=\"_blank\" rel=\"noopener noreferrer\">${escape(label)}</a>"
    }

    /**
     * GPS field, parse-or-fallback (decision C):
     *  - "lat, lng" with lat in [-90,90] and lng in [-180,180] → precise pin
     *    `https://www.google.com/maps?q=lat,lng`
     *  - anything else non-blank → search query
     *    `https://www.google.com/maps/search/?api=1&query=<encoded>`
     *  - blank → null (renders nothing)
     */
    fun locationHref(raw: String): String? {
        val v = raw.trim()
        if (v.isEmpty()) return null
        parseLatLng(v)?.let { (lat, lng) ->
            return "https://www.google.com/maps?q=$lat,$lng"
        }
        return "https://www.google.com/maps/search/?api=1&query=${urlEncode(v)}"
    }

    /** Parses "lat,lng" / "lat, lng" into a normalized pair, or null. */
    private fun parseLatLng(v: String): Pair<Double, Double>? {
        val parts = v.split(",").map { it.trim() }
        if (parts.size != 2) return null
        val lat = parts[0].toDoubleOrNull() ?: return null
        val lng = parts[1].toDoubleOrNull() ?: return null
        if (lat < -90.0 || lat > 90.0) return null
        if (lng < -180.0 || lng > 180.0) return null
        // Re-stringify so we emit a clean, canonical pair (drops stray spaces).
        return lat to lng
    }

    /** email → mailto:, or null when blank / obviously not an email. */
    fun emailHref(raw: String): String? {
        val v = raw.trim()
        if (v.isEmpty()) return null
        // Light check only (matches the app's forgiving style): one '@' with
        // non-empty local and domain parts, and a dot in the domain.
        val at = v.indexOf('@')
        if (at <= 0 || at == v.length - 1) return null
        if (v.indexOf('@', at + 1) != -1) return null
        val domain = v.substring(at + 1)
        if (!domain.contains('.') || domain.startsWith('.') || domain.endsWith('.')) return null
        if (v.any { it.isWhitespace() }) return null
        return "mailto:$v"
    }

    /** phone → tel:, or null when blank. Strips spaces/dashes/parens for the href. */
    fun phoneHref(raw: String): String? {
        val v = raw.trim()
        if (v.isEmpty()) return null
        val digits = v.filter { it.isDigit() || it == '+' }
        if (digits.isEmpty()) return null
        return "tel:$digits"
    }

    private fun urlEncode(s: String): String =
        URLEncoder.encode(s, "UTF-8").replace("+", "%20")
}
