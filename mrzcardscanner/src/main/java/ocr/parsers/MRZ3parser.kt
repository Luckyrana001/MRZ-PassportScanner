package ocr.parsers

import ocr.utils.CheckSum

class MRZ3parser {
    private val line1Regex = Regex("(P[A-Z0-9<]{1})([A-Z]{3})([A-Z0-9<]{39})")
    private val line2Regex = Regex("([A-Z0-9<]{9})([0-9]{1})([A-Z]{3})([0-9]{6})([0-9]{1})([M|F|X|<]{1})([0-9]{6})([0-9]{1})([A-Z0-9<]{14})([0-9]{1})([0-9]{1})")

    fun checkCardValidity(cardString: String) {
        val rows = splitToRow(cardString)
        var error = rows.size == 2
        if (!line1Regex.matches(rows[0])) error = true
        if (!line2Regex.matches(rows[1])) error = true
        if (!CheckSum.check(getDocumentNumber(rows[2]), getPhoneNumberCheckDigit(rows[2]))) error = true
        @Suppress("UNUSED_VALUE")
        error = error
    }

    fun String.removePlaceHolders() = replace("<", "")
    fun getDocument(row1: String) = row1.first().toString().removePlaceHolders()
    fun getCountry(row1: String) = row1.slice(1..3)
    fun splitToRow(cardString: String): List<String> = cardString.split("\n")

    fun getNames(row1: String): HashMap<String, String> {
        val result = hashMapOf<String, String>()
        val sections = row1.slice(5..38).split("<").filter(String::isNotEmpty)
        if (sections.isEmpty()) return result
        if (sections.size >= 3) {
            result["firstName"] = sections[0]
            result["surname"] = sections[1]
            result["lastname"] = sections[2]
        } else {
            result["firstName"] = sections[0]
            result["lastname"] = sections[1]
        }
        return result
    }

    fun getDocumentNumber(row2: String): String {
        val result = row2.slice(0..8).removePlaceHolders()
        return result.slice(0..result.length - 2)
    }

    fun getPhoneNumberCheckDigit(row2: String) = row2.slice(0..8).removePlaceHolders().last().toString()
    fun getNationality(row2: String): String = ""
    fun getDateOfBirth(row2: String) = Unit
    fun getDateOfBirthCheckDigit(row2: String) = Unit
    fun getGender(row2: String): String = ""
}
