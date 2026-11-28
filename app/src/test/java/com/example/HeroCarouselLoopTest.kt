package com.example

import com.example.ui.components.HERO_INFINITE_VIRTUAL_PAGE_COUNT
import com.example.ui.components.heroActualIndex
import com.example.ui.components.heroShortestTargetPage
import com.example.ui.components.heroStartPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeroCarouselLoopTest {

  @Test
  fun heroStartPage_returnsZeroForSingleOrEmptyAndAlignedMidpointForMultiple() {
    assertEquals(0, heroStartPage(0))
    assertEquals(0, heroStartPage(1))

    for (count in listOf(2, 3, 5, 7, 11)) {
      val start = heroStartPage(count)
      assertTrue("Start page $start should leave plenty of headroom", start in count..(HERO_INFINITE_VIRTUAL_PAGE_COUNT - count))
      assertEquals("Start page must align to slide 0", 0, heroActualIndex(start, count))
    }
  }

  @Test
  fun heroActualIndex_wrapsBothForwardAndBackward() {
    assertEquals(0, heroActualIndex(5, 0))
    assertEquals(0, heroActualIndex(10, 1))

    val count = 5
    val start = heroStartPage(count)
    assertEquals(0, heroActualIndex(start, count))
    assertEquals(4, heroActualIndex(start + 4, count))
    assertEquals(0, heroActualIndex(start + 5, count))
    assertEquals(1, heroActualIndex(start + 6, count))
    assertEquals(4, heroActualIndex(start - 1, count))
    assertEquals(3, heroActualIndex(start - 2, count))
  }

  @Test
  fun heroShortestTargetPage_takesShortestPathAcrossWrapBoundaries() {
    val count = 5
    val total = HERO_INFINITE_VIRTUAL_PAGE_COUNT
    val start = heroStartPage(count)

    // From last slide (index 4) to first slide (index 0) should wrap forward (+1)
    assertEquals(start + 5, heroShortestTargetPage(start + 4, 0, count, total))

    // From first slide (index 0) to last slide (index 4) should wrap backward (-1)
    assertEquals(start - 1, heroShortestTargetPage(start, 4, count, total))

    // Normal adjacent hops
    assertEquals(start + 3, heroShortestTargetPage(start + 1, 3, count, total))
    assertEquals(start + 1, heroShortestTargetPage(start + 2, 1, count, total))

    // Boundary safety
    assertTrue(heroShortestTargetPage(total - 1, 0, count, total) in 0 until total)
    assertTrue(heroShortestTargetPage(0, 4, count, total) in 0 until total)
    assertEquals(0, heroShortestTargetPage(0, 0, 1, 1))
  }
}
