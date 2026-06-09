package dev.khoj.pitaka.domain.usecase

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PublishContactLinksTest {

    // Identity escape so we can assert on raw structure; the production caller
    // passes a real HTML escaper.
    private val noEscape: (String) -> String = { it }

    // --- GPS: parse-or-fallback (decision C) ---

    @Test
    fun location_precise_pin_for_valid_coords() {
        assertThat(PublishContactLinks.locationHref("12.9716, 77.5946"))
            .isEqualTo("https://www.google.com/maps?q=12.9716,77.5946")
    }

    @Test
    fun location_precise_pin_trims_and_handles_no_space() {
        assertThat(PublishContactLinks.locationHref("12.9716,77.5946"))
            .isEqualTo("https://www.google.com/maps?q=12.9716,77.5946")
    }

    @Test
    fun location_negative_coords_ok() {
        assertThat(PublishContactLinks.locationHref("-33.8688, 151.2093"))
            .isEqualTo("https://www.google.com/maps?q=-33.8688,151.2093")
    }

    @Test
    fun location_out_of_range_falls_back_to_search() {
        // lat 200 is out of range → treated as a search query, not a pin.
        assertThat(PublishContactLinks.locationHref("200, 77"))
            .isEqualTo("https://www.google.com/maps/search/?api=1&query=200%2C%2077")
    }

    @Test
    fun location_address_falls_back_to_search() {
        assertThat(PublishContactLinks.locationHref("MG Road, Bengaluru"))
            .isEqualTo("https://www.google.com/maps/search/?api=1&query=MG%20Road%2C%20Bengaluru")
    }

    @Test
    fun location_blank_is_null() {
        assertThat(PublishContactLinks.locationHref("   ")).isNull()
    }

    @Test
    fun location_three_parts_is_search_not_pin() {
        assertThat(PublishContactLinks.locationHref("1, 2, 3"))
            .isEqualTo("https://www.google.com/maps/search/?api=1&query=1%2C%202%2C%203")
    }

    // --- email ---

    @Test
    fun email_valid_becomes_mailto() {
        assertThat(PublishContactLinks.emailHref("lib@example.com"))
            .isEqualTo("mailto:lib@example.com")
    }

    @Test
    fun email_without_at_is_null() {
        assertThat(PublishContactLinks.emailHref("notanemail")).isNull()
    }

    @Test
    fun email_without_domain_dot_is_null() {
        assertThat(PublishContactLinks.emailHref("a@localhost")).isNull()
    }

    @Test
    fun email_with_two_ats_is_null() {
        assertThat(PublishContactLinks.emailHref("a@@b.com")).isNull()
    }

    @Test
    fun email_blank_is_null() {
        assertThat(PublishContactLinks.emailHref("")).isNull()
    }

    // --- phone ---

    @Test
    fun phone_strips_formatting_for_tel() {
        assertThat(PublishContactLinks.phoneHref("+91 98765 43210"))
            .isEqualTo("tel:+919876543210")
    }

    @Test
    fun phone_blank_is_null() {
        assertThat(PublishContactLinks.phoneHref("  ")).isNull()
    }

    @Test
    fun phone_no_digits_is_null() {
        assertThat(PublishContactLinks.phoneHref("call me")).isNull()
    }

    // --- render() composition ---

    @Test
    fun render_empty_when_all_blank() {
        val html = PublishContactLinks.render(PublishContactLinks.Contact(), noEscape)
        assertThat(html).isEmpty()
    }

    @Test
    fun render_only_set_fields_in_order_location_email_phone() {
        val html = PublishContactLinks.render(
            PublishContactLinks.Contact(
                location = "12.97, 77.59",
                email = "lib@example.com",
                phone = "+91 99999 88888",
            ),
            noEscape,
        )
        // Order: location, email, phone.
        val locIdx = html.indexOf("maps?q=")
        val mailIdx = html.indexOf("mailto:")
        val telIdx = html.indexOf("tel:")
        assertThat(locIdx).isGreaterThan(-1)
        assertThat(mailIdx).isGreaterThan(locIdx)
        assertThat(telIdx).isGreaterThan(mailIdx)
        // Each is a contact-item anchor opening in a new tab safely.
        assertThat(html).contains("class=\"contact-item\"")
        assertThat(html).contains("rel=\"noopener noreferrer\"")
    }

    @Test
    fun render_skips_invalid_email_but_keeps_phone() {
        val html = PublishContactLinks.render(
            PublishContactLinks.Contact(email = "bogus", phone = "12345"),
            noEscape,
        )
        assertThat(html).doesNotContain("mailto:")
        assertThat(html).contains("tel:12345")
    }

    @Test
    fun render_escapes_label_and_href() {
        // A search-query location containing an angle bracket must be escaped
        // by the provided escaper in both contexts.
        val html = PublishContactLinks.render(
            PublishContactLinks.Contact(location = "a<b"),
            escape = { it.replace("<", "&lt;") },
        )
        assertThat(html).doesNotContain("a<b")
        assertThat(html).contains("&lt;")
    }
}
