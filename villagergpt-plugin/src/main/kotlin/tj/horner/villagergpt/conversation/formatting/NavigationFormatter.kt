package tj.horner.villagergpt.conversation.formatting

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

object NavigationFormatter {
    fun formatCoordinates(x: Double, y: Double, z: Double): String {
        return "(${x.roundToInt()}, ${y.roundToInt()}, ${z.roundToInt()})"
    }

    fun describeDirection(fromX: Double, fromZ: Double, toX: Double, toZ: Double): String {
        val dx = toX - fromX
        val dz = toZ - fromZ
        if (abs(dx) < 0.5 && abs(dz) < 0.5) return "same position"

        val eastWest = when {
            dx > 0.5 -> "east"
            dx < -0.5 -> "west"
            else -> null
        }

        val northSouth = when {
            dz > 0.5 -> "south"
            dz < -0.5 -> "north"
            else -> null
        }

        return listOfNotNull(northSouth, eastWest).joinToString("-")
    }

    fun estimateDistance(fromX: Double, fromZ: Double, toX: Double, toZ: Double): Int {
        val dx = toX - fromX
        val dz = toZ - fromZ
        return sqrt(dx * dx + dz * dz).roundToInt()
    }

    fun buildRouteHint(fromX: Double, fromZ: Double, toX: Double, toZ: Double): String {
        val dx = (toX - fromX).roundToInt()
        val dz = (toZ - fromZ).roundToInt()

        if (abs(dx) <= 1 && abs(dz) <= 1) {
            return "You are already very close; stay nearby."
        }

        val steps = mutableListOf<String>()
        if (abs(dx) > 1) {
            val direction = if (dx > 0) "east" else "west"
            steps.add("walk ${abs(dx)} blocks $direction")
        }

        if (abs(dz) > 1) {
            val direction = if (dz > 0) "south" else "north"
            steps.add("then walk ${abs(dz)} blocks $direction")
        }

        return steps.joinToString(" ").replaceFirstChar { it.uppercase() } + "."
    }
}
