package com.example

import com.example.domain.model.KartFormatters
import com.example.domain.model.Product
import org.junit.Assert.assertEquals
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
  fun extractMessageImageUrl_handlesBracketAndDirectUrls() {
    val bracketUrl = KartFormatters.extractMessageImageUrl("[IMAGE:https://example.com/pic.jpg]")
    assertEquals("https://example.com/pic.jpg", bracketUrl)
    assertEquals("📷 Photo", KartFormatters.messageSnippet("[IMAGE:https://example.com/pic.jpg]"))
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
    assertEquals(71, icons.size)
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
}
