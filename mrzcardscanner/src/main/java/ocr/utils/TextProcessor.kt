package ocr.utils

object TextProcessor {
    fun getMostAppearing(stringArray: ArrayList<String>): String =
        stringArray.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key.orEmpty()
}
