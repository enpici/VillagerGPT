package io.github.enpici.villager.gpt.conversation.formatting

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NavigationFormatterTest {
    @Test
    fun `formats coordinates with rounded values`() {
        val formatted = NavigationFormatter.formatCoordinates(10.4, 63.6, -2.2)

        assertEquals("(10, 64, -2)", formatted)
    }

    @Test
    fun `describes diagonal direction`() {
        val direction = NavigationFormatter.describeDirection(0.0, 0.0, 8.0, -3.0)

        assertEquals("north-east", direction)
    }

    @Test
    fun `builds route hint using cardinal steps`() {
        val hint = NavigationFormatter.buildRouteHint(100.0, 50.0, 90.0, 62.0)

        assertEquals("Walk 10 blocks west then walk 12 blocks south.", hint)
    }
}
