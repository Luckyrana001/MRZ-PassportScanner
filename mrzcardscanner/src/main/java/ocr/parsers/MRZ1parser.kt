package ocr.parsers

import ocr.Card
import ocr.utils.CheckSum
import ocr.utils.Constants
import ocr.utils.TextProcessor

class MRZ1parser {
    private val line1Regex = Regex("([A|C|I][A-Z0-9<]{1})([A-Z]{3})([A-Z0-9<]{9})([0-9]{1})([A-Z\\d<]{15})")
    private val line2Regex = Regex("([0-9]{6})([0-9]{1})([M|F|X|<]{1})([0-9]{6})([0-9]{1})([A-Z0-9<]{3})([A-Z0-9<]{11})([0-9]{1})")
    private val line3Regex = Regex("([A-Z0-9<]{30})")
    private val genderRegex = Regex("([M|F|X|<]{1})")

    private var rowOne = ""
    private var rowTwo = ""
    private var rowThree = ""
    private val contentHashMap = hashMapOf<String, String>()
    private val typeCountry = arrayListOf<String>()
    private val rowOneFillers = arrayListOf<String>()
    private val rowTwoFillers = arrayListOf<String>()
    private val partOne = arrayListOf<String>()
    private val partTwo = arrayListOf<String>()
    private val partThree = arrayListOf<String>()

    fun main() = checkCardValidity()

    fun checkCardValidity() {
        val row1 = "IDKYA7029804442<<3631<<<<<4715"
        val row2 = "9609266M2106308<B034963670R<<6"
        val row3 = "ERIKSSON<<ANNA<MARIA<<<<<<<<<<"
        println(line2Regex.matches(row2))
        println(isValidData(row1, row2, row3))
    }

    fun isValid(cardString: String): Boolean {
        if (validateIncomingCardData(cardString)) {
            val rows = splitToRow(cardString)
            rowOne = rows[0]
            rowTwo = rows[1]
            rowThree = rows[2]
            return true
        }
        constructRowOne(cardString)
        constructRowTwo(cardString)
        constructRowThree(cardString)
        return validate()
    }

    fun validateIncomingCardData(cardString: String): Boolean {
        val rows = splitToRow(cardString)
        return rows.size >= 3 && isValidData(rows[0], rows[1], rows[2])
    }

    fun isValidData(row1: String, row2: String, row3: String): Boolean {
        var error = !line1Regex.matches(row1) ||
            !line2Regex.matches(row2) ||
            !line3Regex.matches(row3)

        if (!CheckSum.check(getDateOfBirth(row2), getDateOfBirthCheckDigit(row2))) error = true
        if (!CheckSum.check(getDocumentSerial(row1), getDocumentSerialCheckDigit(row1))) error = true
        if (!CheckSum.check(row2.slice(0..28), row2CheckDigit(row2))) error = true
        return !error
    }

    private fun String.removePlaceHolders() = replace("<", "")

    private fun getDocumentSerial(row1: String): String {
        val result = row1.slice(5..15).removePlaceHolders()
        return result.slice(0..result.length - 2)
    }

    private fun getDocumentSerialCheckDigit(row1: String): String =
        row1.slice(5..15).removePlaceHolders().last().toString()

    private fun getCountry(row1: String) = row1.slice(2..4)
    private fun getDateOfBirth(row2: String) = row2.slice(0..5)
    private fun getDateOfBirthCheckDigit(row2: String) = row2[6].toString()
    private fun getDateOfIssue(row2: String) = row2.slice(8..13)
    private fun getDateOfIssueCheckDigit(row2: String) = row2[14].toString()
    private fun getGender(row2: String) = row2[7].toString()

    private fun getID(row2: String): String =
        row2.slice(16..28).removePlaceHolders().replace(Regex("([A-Z])"), "")

    private fun getNames(row3: String): HashMap<String, String> {
        val result = hashMapOf<String, String>()
        val sections = row3.split("<").filter(String::isNotEmpty)
        if (sections.isEmpty()) return result
        if (sections.size >= 3) {
            result[Constants.FIRST_NAME] = sections[0]
            result[Constants.MIDDLE_NAME] = sections[1]
            result[Constants.LAST_NAME] = sections[2]
        } else {
            result[Constants.FIRST_NAME] = sections[0]
            result[Constants.LAST_NAME] = sections[1]
        }
        return result
    }

    fun readCard(cardString: String): Card {
        if (rowOne.isEmpty() || rowTwo.isEmpty() || rowThree.isEmpty()) {
            val rows = splitToRow(cardString)
            require(rows.size >= 3) { "MRZ1 data must contain three rows" }
            rowOne = rows[0]
            rowTwo = rows[1]
            rowThree = rows[2]
        }
        val names = getNames(rowThree)
        return Card(
            names[Constants.FIRST_NAME].orEmpty(),
            names[Constants.MIDDLE_NAME].orEmpty(),
            names[Constants.LAST_NAME].orEmpty(),
            getGender(rowTwo),
            getDocumentSerial(rowOne),
            getID(rowTwo),
            getCountry(rowOne),
            getDateOfBirth(rowTwo),
            getDateOfIssue(rowTwo),
        ).also { clear() }
    }

    fun splitToRow(cardString: String): List<String> = cardString.split("\n")

    private fun row2CheckDigit(row2: String) = row2.last().toString()

    fun constructRowOne(cardString: String) {
        val rows = splitToRow(cardString)
        if (rows.size < 3) return

        delete(typeCountry)
        typeCountry.add(rows[0].slice(0..4))
        if (!contentHashMap.containsKey(Constants.DOC_NUM) &&
            CheckSum.check(getDocumentSerial(rows[0]), getDocumentSerialCheckDigit(rows[0]))
        ) {
            contentHashMap[Constants.DOC_NUM] = getDocumentSerial(rows[0])
            contentHashMap[Constants.DOC_NUM_CHECK] = getDocumentSerialCheckDigit(rows[0])
        }
        if (rows[0].length < 30) return

        delete(rowOneFillers)
        rowOneFillers.add(rows[0].slice(15..29))
        if (!contentHashMap.containsKey(Constants.DOC_NUM)) return
        rowOne = TextProcessor.getMostAppearing(typeCountry) +
            contentHashMap[Constants.DOC_NUM] +
            contentHashMap[Constants.DOC_NUM_CHECK] +
            TextProcessor.getMostAppearing(rowOneFillers)
    }

    private fun String.removeSpace() = replace(" ", "").replace("\t", "")

    fun constructRowTwo(cardString: String) {
        val rows = splitToRow(cardString)
        if (rows.size < 3) return
        val currentRow2 = rows[1].removeSpace()
        if (currentRow2.length < 7) return

        val dateOfBirth = getDateOfBirth(currentRow2)
        val dateOfBirthCheckDigit = getDateOfBirthCheckDigit(currentRow2)
        if (CheckSum.check(dateOfBirth, dateOfBirthCheckDigit)) {
            contentHashMap[Constants.DOB] = dateOfBirth
            contentHashMap[Constants.DOB_CHECK] = dateOfBirthCheckDigit
        }
        val gender = getGender(currentRow2)
        if (genderRegex.matches(gender)) contentHashMap[Constants.GENDER] = gender

        val dateOfIssue = getDateOfIssue(currentRow2)
        val dateOfIssueCheckDigit = getDateOfIssueCheckDigit(currentRow2)
        if (CheckSum.check(dateOfIssue, dateOfIssueCheckDigit)) {
            contentHashMap[Constants.DATE_OF_ISSUE] = dateOfIssue
            contentHashMap[Constants.DATE_OF_ISSUE_CHECH] = dateOfIssueCheckDigit
        }
        if (currentRow2.length < 30) return
        delete(rowTwoFillers)
        rowTwoFillers.add(currentRow2.slice(15..29))
        concatRowData()
    }

    private fun concatRowData() {
        if (!contentHashMap.containsKey(Constants.DOB) ||
            !contentHashMap.containsKey(Constants.DATE_OF_ISSUE) ||
            !contentHashMap.containsKey(Constants.GENDER)
        ) return

        rowTwo = contentHashMap[Constants.DOB] +
            contentHashMap[Constants.DOB_CHECK] +
            contentHashMap[Constants.GENDER] +
            contentHashMap[Constants.DATE_OF_ISSUE] +
            contentHashMap[Constants.DATE_OF_ISSUE_CHECH] +
            TextProcessor.getMostAppearing(rowTwoFillers)
    }

    fun constructRowThree(cardString: String) {
        val rows = splitToRow(cardString)
        if (rows.size < 3) return
        val currentRowThree = rows[2].removeSpace()
        if (currentRowThree.length < 30) return
        val parts = currentRowThree.split("<").filter(String::isNotEmpty)

        when (parts.size) {
            1 -> {
                delete(partOne)
                partOne.add(parts[0])
            }
            2 -> {
                delete(partOne)
                delete(partTwo)
                partOne.add(parts[0])
                partThree.add(parts[1])
            }
            3 -> {
                delete(partOne)
                delete(partTwo)
                delete(partThree)
                partOne.add(parts[0])
                partTwo.add(parts[1])
                partThree.add(parts[2])
            }
        }

        rowThree = buildString {
            TextProcessor.getMostAppearing(partOne).takeIf(String::isNotEmpty)?.let {
                append(it).append(Constants.FILLER)
            }
            TextProcessor.getMostAppearing(partTwo).takeIf(String::isNotEmpty)?.let {
                append(it).append(Constants.FILLER)
            }
            append(TextProcessor.getMostAppearing(partThree))
        }.padEnd(30, '<')
    }

    fun validate(): Boolean = isValidData(rowOne, rowTwo, rowThree)

    private fun delete(arrayList: ArrayList<String>) {
        if (arrayList.size > 19) arrayList.removeAt(0)
    }

    fun clear() {
        rowOne = ""
        rowTwo = ""
        rowThree = ""
        partOne.clear()
        partTwo.clear()
        partThree.clear()
        rowOneFillers.clear()
        rowTwoFillers.clear()
        typeCountry.clear()
        contentHashMap.clear()
    }
}
