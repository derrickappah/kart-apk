package com.example

import com.example.domain.model.KartFormatters
import com.example.domain.model.Product
import com.example.domain.model.UserSubscription
import com.example.notifications.KartNotificationHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

  @Test
  fun formatCedis_formatsWholeAndDecimalPrices() {
    assertEquals("₵ 1,250", KartFormatters.formatCedis(1250.0))
    assertEquals("₵ 49.50", KartFormatters.formatCedis(49.5))
    assertEquals("₵ 0", KartFormatters.formatCedis(null))
  }

  @Test
  fun toSentenceCase_capitalizesFirstCharacterOnly() {
    assertEquals("Wireless earbuds", KartFormatters.toSentenceCase("  wireless earbuds "))
    assertEquals("", KartFormatters.toSentenceCase("   "))
  }

  @Test
  fun parseReviewComment_extractsEmbeddedTags() {
    val (comment, tags) = KartFormatters.parseReviewComment(
      "Super fast delivery! [Tags: Fast Delivery, Item As Described, Friendly Seller]"
    )
    assertEquals("Super fast delivery!", comment)
    assertEquals(listOf("Fast Delivery", "Item As Described", "Friendly Seller"), tags)
  }

  @Test
  fun extractMessageImageUrl_handlesTrustedUrlsAndRejectsUntrustedHosts() {
    val trustedUrl = "https://demo.supabase.co/storage/v1/object/sign/chat-media/pic.jpg"
    val bracketUrl = KartFormatters.extractMessageImageUrl("[IMAGE:$trustedUrl]")
    assertEquals(trustedUrl, bracketUrl)
    assertEquals("📷 Photo", KartFormatters.messageSnippet("[IMAGE:$trustedUrl]"))
    assertNull(KartFormatters.extractMessageImageUrl("[IMAGE:https://example.com/pic.jpg]"))
    assertNull(KartFormatters.extractMessageImageUrl("Meet me at Commonwealth Hall"))
  }

  @Test
  fun seededShuffle_isDeterministicForSameSeed() {
    val products = (1..6).map { idx ->
      Product(id = "prod-$idx", title = "Item $idx", price = idx * 10.0)
    }
    val run1 = KartFormatters.seededShuffle(products, seed = 42L)
    val run2 = KartFormatters.seededShuffle(products, seed = 42L)
    assertEquals(run1.map { it.id }, run2.map { it.id })
    assertEquals(products.size, run1.size)
    assertTrue(run1.map { it.id }.toSet() == products.map { it.id }.toSet())
  }

  @Test
  fun kartLucideIcons_parseAllPathsCleanly() {
    val icons = com.example.ui.components.KartLucideIcons.allIcons
    assertEquals(73, icons.size)
    assertTrue(icons.all { it.viewportWidth == 24f && it.viewportHeight == 24f })
  }

  @Test
  fun kartConditions_matchRepoOptionsAndLookup() {
    assertEquals(
      listOf("New", "Like New", "Good", "Fair", "Acceptable"),
      com.example.domain.model.KartConditions
    )
    assertEquals("New", com.example.domain.model.conditionOptionFor("Brand New")?.name)
    assertEquals("Acceptable", com.example.domain.model.conditionOptionFor("Used")?.name)
    assertEquals(
      "Flawless, barely used",
      com.example.domain.model.conditionOptionFor("Like New")?.description
    )
  }

  @Test
  fun sanitizeDecimalInput_enforcesSingleDotAndTwoDecimals() {
    assertEquals("123.45", KartFormatters.sanitizeDecimalInput("123.4567"))
    assertEquals("12.34", KartFormatters.sanitizeDecimalInput("12..3.4abc"))
    assertEquals("0.5", KartFormatters.sanitizeDecimalInput(".5"))
    assertEquals("", KartFormatters.sanitizeDecimalInput("-abc"))
  }

  @Test
  fun isTrustedMediaUrl_restrictsSchemeAndHosts() {
    assertTrue(KartFormatters.isTrustedMediaUrl("https://demo.supabase.co/storage/v1/object/sign/chat-media/a.jpg"))
    assertTrue(KartFormatters.isTrustedMediaUrl("https://www.kart.cx/images/item.webp"))
    assertTrue(KartFormatters.isTrustedMediaUrl("https://res.cloudinary.com/demo/image/upload/item.png"))
    assertFalse(KartFormatters.isTrustedMediaUrl("http://demo.supabase.co/storage/v1/object/public/a.jpg"))
    assertFalse(KartFormatters.isTrustedMediaUrl("https://evil.example.com/tracker.jpg"))
    assertFalse(KartFormatters.isTrustedMediaUrl("javascript:alert(1)"))
  }

  @Test
  fun extractMessageVoice_clampsDurationAndRejectsUntrustedHosts() {
    val trustedVoice = "[VOICE:https://demo.supabase.co/storage/v1/object/sign/chat-media/v.m4a|9999]"
    val parsedVoice = KartFormatters.extractMessageVoice(trustedVoice)
    assertNotNull(parsedVoice)
    assertEquals(600, parsedVoice!!.durationSeconds)
    assertNull(KartFormatters.extractMessageVoice("[VOICE:https://attacker.example/track.m4a|12]"))
  }

  @Test
  fun sanitizeSocialUrl_validatesHandlesAndBlocksForeignDomains() {
    assertEquals("https://instagram.com/kart_gh", KartFormatters.sanitizeSocialUrl("@kart_gh", "instagram"))
    assertEquals("https://www.instagram.com/kart_gh", KartFormatters.sanitizeSocialUrl("https://www.instagram.com/kart_gh", "instagram"))
    assertNull(KartFormatters.sanitizeSocialUrl("https://evil.example.com/kart_gh", "instagram"))
    assertNull(KartFormatters.sanitizeSocialUrl("http://instagram.com/kart_gh", "instagram"))
    assertEquals("https://snapchat.com/add/kart-user", KartFormatters.sanitizeSocialUrl("@kart-user", "snapchat"))
    assertNull(KartFormatters.sanitizeSocialUrl("https://phish.example/add/kart", "snapchat"))
    assertEquals("https://wa.me/233241234567", KartFormatters.sanitizeSocialUrl("+233 24 123 4567", "whatsapp"))
    assertNull(KartFormatters.sanitizeSocialUrl("12345", "whatsapp"))
  }

  @Test
  fun isActiveSubscription_validatesStatusAndExpiry() {
    val activeFuture = UserSubscription(id = "sub-1", status = "Active", endDate = "2099-12-31T23:59:59Z")
    val activeExpired = UserSubscription(id = "sub-2", status = "Active", endDate = "2020-01-01T00:00:00Z")
    val cancelledFuture = UserSubscription(id = "sub-3", status = "Cancelled", endDate = "2099-12-31T23:59:59Z")
    assertTrue(KartFormatters.isActiveSubscription(activeFuture))
    assertFalse(KartFormatters.isActiveSubscription(activeExpired))
    assertFalse(KartFormatters.isActiveSubscription(cancelledFuture))
    assertFalse(KartFormatters.isActiveSubscription(null))
  }

  @Test
  fun notificationDeepLink_allowsSafePathsAndBlocksSensitiveRoutes() {
    assertEquals(
      "https://www.kart.cx/orders/ord-123",
      KartNotificationHelper.sanitizeNotificationDeepLinkString("/orders/ord-123", KartNotificationHelper.CHANNEL_UPDATES)
    )
    assertEquals(
      "https://kart.cx/messages?id=c1",
      KartNotificationHelper.sanitizeNotificationDeepLinkString("https://kart.cx/messages?id=c1", KartNotificationHelper.CHANNEL_MESSAGES)
    )
    assertEquals(
      "https://www.kart.cx/notifications",
      KartNotificationHelper.sanitizeNotificationDeepLinkString("https://evil.example.com/phish", KartNotificationHelper.CHANNEL_UPDATES)
    )
    assertEquals(
      "https://www.kart.cx/notifications",
      KartNotificationHelper.sanitizeNotificationDeepLinkString("https://www.kart.cx@evil.example.com/phish", KartNotificationHelper.CHANNEL_UPDATES)
    )
    assertEquals(
      "https://www.kart.cx/messages",
      KartNotificationHelper.sanitizeNotificationDeepLinkString("https://www.kart.cx/auth-callback?refresh_token=abc", KartNotificationHelper.CHANNEL_MESSAGES)
    )
    assertEquals(
      "https://www.kart.cx/notifications",
      KartNotificationHelper.sanitizeNotificationDeepLinkString("https://www.kart.cx/checkout-success?reference=ref1", KartNotificationHelper.CHANNEL_UPDATES)
    )
  }

  @Test
  fun productAvailabilityFlags_handleStockAndStatus() {
    val available = Product(id = "p1", status = "Active", stockQuantity = 3)
    assertTrue(available.isActiveListing)
    assertFalse(available.isOutOfStock)

    val zeroStock = Product(id = "p2", status = "Active", stockQuantity = 0)
    assertTrue(zeroStock.isOutOfStock)
    assertTrue(zeroStock.isActiveListing)

    val soldStatus = Product(id = "p3", status = "Sold", stockQuantity = 2)
    assertFalse(soldStatus.isOutOfStock)
    assertFalse(soldStatus.isActiveListing)

    val draftStatus = Product(id = "p4", status = "Inactive", stockQuantity = 5)
    assertFalse(draftStatus.isOutOfStock)
    assertFalse(draftStatus.isActiveListing)
  }

  @Test
  fun parseBooleanSetting_handlesJsonTypesAndDefaults() {
    assertTrue(KartFormatters.parseBooleanSetting(null, default = true))
    assertFalse(KartFormatters.parseBooleanSetting(null, default = false))
    assertFalse(KartFormatters.parseBooleanSetting(false, default = true))
    assertTrue(KartFormatters.parseBooleanSetting(true, default = false))
    assertFalse(KartFormatters.parseBooleanSetting("false", default = true))
    assertFalse(KartFormatters.parseBooleanSetting("\"false\"", default = true))
    assertTrue(KartFormatters.parseBooleanSetting("true", default = false))
    assertFalse(KartFormatters.parseBooleanSetting(mapOf("enabled" to false), default = true))
    assertTrue(KartFormatters.parseBooleanSetting(mapOf("value" to "true"), default = false))
    assertFalse(KartFormatters.parseBooleanSetting(0, default = true))
    assertTrue(KartFormatters.parseBooleanSetting(1, default = false))
  }
}
