package ocr

import ocr.parsers.MRZ1parser

class MrzReader {
    val parser1 = MRZ1parser()

    fun getCardType(cardString: String): CardType.MRZType = CardType().getMRZType(cardString)

    fun isValidCard(mrzType: CardType.MRZType, cardString: String): Boolean =
        mrzType == CardType.MRZType.MRZ1 && parser1.isValid(cardString)

    fun readDocument(mrzType: CardType.MRZType, cardString: String): Card =
        if (mrzType == CardType.MRZType.MRZ1) {
            parser1.readCard(cardString)
        } else {
            Card("", "", "", "", "", "", "", "")
        }
}
