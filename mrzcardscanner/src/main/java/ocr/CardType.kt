package ocr

class CardType {
    fun splitDataToRow(cardDetails: String): List<String> = cardDetails.split("\n")

    fun getMRZType(details: String): MRZType {
        val rows = splitDataToRow(details)
        return when {
            rows.first().startsWith("P") -> MRZType.MRZ3
            rows.size == 3 -> MRZType.MRZ1
            else -> MRZType.MRZ2
        }
    }

    sealed class MRZType {
        object MRZ1 : MRZType()
        object MRZ2 : MRZType()
        object MRZ3 : MRZType()
    }
}
