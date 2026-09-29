package ocr.utils

object CheckSum {
    private val factors = intArrayOf(7, 3, 1)

    fun check(testString: String, checkDigit: String): Boolean {
        val sum = testString.withIndex().sumOf { (index, character) ->
            val value = when (character) {
                in '0'..'9' -> character.digitToInt()
                in 'A'..'Z' -> character.code - 'A'.code + 10
                '<' -> 0
                else -> -1
            }
            value * factors[index % factors.size]
        }
        return sum % 10 == checkDigit.toInt()
    }
}
