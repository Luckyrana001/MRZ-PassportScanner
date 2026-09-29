package com.android.mrzcardreader.cardconnectors
import com.android.mrzcardreader.camera.MRZResponse
import com.android.mrzcardreader.camera.models.IdData
import ocr.MrzReader
import java.util.Locale


object CardConnector {
    val mrzReader = MrzReader()
    private const val MAX_RECENT_LINES = 24
    private val recentLines = ArrayDeque<String>()

    fun onLinesCaptured(lines: List<String>, mrzResponse: MRZResponse) {
        val passportLines = synchronized(recentLines) {
            lines.forEach { line ->
                recentLines.addLast(line)
            }
            while (recentLines.size > MAX_RECENT_LINES) {
                recentLines.removeFirst()
            }
            recentLines.toList()
        }

        parsePassport(passportLines)?.let {
            clear()
            mrzResponse.cardResponse(it)
            return
        }

        val mrzString = cleanMRZ(lines.joinToString("\n"))

        val cardType = mrzReader.getCardType(mrzString)

        val isValidCard = mrzReader.isValidCard(cardType, mrzString)

        if (!isValidCard) {

            return
        }else{
            val readCard = mrzReader.readDocument(cardType, mrzString)

            val card = IdData(
                readCard.firstName,
                readCard.secondName,
                readCard.lastName,
                readCard.gender,
                readCard.documentNumber,
                readCard.dateOfBirth,
                readCard.id,
                readCard.country,
                mrzString,
            )
            mrzResponse.cardResponse(card)
        }


    }

    fun clear(){
        synchronized(recentLines) {
            recentLines.clear()
        }
    }

    private fun cleanMRZ(details_: String): String {
        var details = details_.replace(" ", "")
        details = details.replace("«", "<")
        details = details.replace("e", "<")
        details = details.replace("€", "<")
        return details
    }

    fun parsePassport(lines: List<String>): IdData? {
        val normalizedLines = lines.map(::normalizeMrzLine)
        val firstLine = normalizedLines
            .asSequence()
            .map(::normalizePassportFirstLine)
            .firstOrNull { it.startsWith("P<") && it.length >= 30 }
            ?: return null

        val firstIndex = normalizedLines.indexOfFirst {
            normalizePassportFirstLine(it) == firstLine
        }
        val secondLine = normalizedLines
            .drop(firstIndex + 1)
            .firstOrNull { it.length >= 40 && it.any(Char::isDigit) && it.contains("<") }
            ?: return null

        val rowOne = firstLine.padEnd(44, '<').take(44)
        val rowTwo = normalizePassportRowTwo(secondLine)
        if (!isPassportRowTwo(rowTwo)) return null

        val nameArea = rowOne.substring(5)
        val doubleSeparator = nameArea.indexOf("<<")
        val hasGivenNamesAfterSeparator = doubleSeparator >= 0 &&
            nameArea.substring(doubleSeparator + 2).any(Char::isLetter)
        val nameTokens = nameArea.split('<').filter(String::isNotBlank)
        val lastName: String
        val givenNames: List<String>
        if (hasGivenNamesAfterSeparator) {
            lastName = nameArea.substring(0, doubleSeparator).replace('<', ' ').trim()
            givenNames = nameArea.substring(doubleSeparator + 2)
                .split('<')
                .filter(String::isNotBlank)
        } else {
            lastName = nameTokens.firstOrNull().orEmpty()
            givenNames = nameTokens.drop(1)
        }
        if (lastName.isEmpty() || givenNames.isEmpty()) return null

        val firstName = givenNames.first()
        val middleName = givenNames.drop(1).joinToString(" ")

        return IdData(
            firstName = firstName,
            middleName = middleName,
            lastName = lastName,
            gender = rowTwo[20].toString(),
            documentNo = rowTwo.substring(0, 9).trimEnd('<'),
            dateOfBirth = formatMrzDate(rowTwo.substring(13, 19)),
            idNo = rowTwo.substring(28, 43).trimEnd('<'),
            nationality = rowTwo.substring(10, 13),
            rawMrz = "$rowOne\n$rowTwo"
        )
    }

    private fun normalizeMrzLine(value: String): String = value
        .uppercase(Locale.US)
        .replace('«', '<')
        .replace('€', '<')
        .replace(" ", "")
        .filter { it.isLetterOrDigit() || it == '<' }

    private fun normalizePassportFirstLine(value: String): String {
        if (value.startsWith("P") && value.length > 2 && value[1] != '<') {
            return "P<" + value.substring(2)
        }
        return value
    }

    private fun normalizePassportRowTwo(value: String): String {
        val row = value.padEnd(44, '<').take(44).toCharArray()
        val numericPositions = (13..19) + (21..27) + listOf(9, 42, 43)
        numericPositions.forEach { index ->
            row[index] = when (row[index]) {
                'O', 'Q', 'D' -> '0'
                'I', 'L' -> '1'
                'Z' -> '2'
                'S' -> '5'
                'B' -> '8'
                else -> row[index]
            }
        }
        (10..12).forEach { index ->
            row[index] = when (row[index]) {
                '0' -> 'O'
                '1' -> 'I'
                '5' -> 'S'
                '8' -> 'B'
                else -> row[index]
            }
        }
        correctDocumentNumber(row)
        return String(row)
    }

    private fun correctDocumentNumber(row: CharArray) {
        val field = String(row, 0, 9)
        val expectedCheckDigit = row[9]
        val corrected = ambiguousVariants(field)
            .filter { candidate -> checkDigit(candidate) == expectedCheckDigit }
            .maxByOrNull { candidate -> candidate.count(Char::isDigit) }
            ?: return
        corrected.forEachIndexed { index, character -> row[index] = character }
    }

    private fun ambiguousVariants(value: String): Sequence<String> = sequence {
        val variants = ArrayList<String>()

        fun build(index: Int, current: StringBuilder) {
            if (index == value.length) {
                variants.add(current.toString())
                return
            }
            val character = value[index]
            val alternatives = when (character) {
                'O' -> charArrayOf('O', '0')
                '0' -> charArrayOf('0', 'O')
                'I', 'L' -> charArrayOf(character, '1')
                '1' -> charArrayOf('1', 'I')
                'Z' -> charArrayOf('Z', '2')
                '2' -> charArrayOf('2', 'Z')
                'S' -> charArrayOf('S', '5')
                '5' -> charArrayOf('5', 'S')
                'B' -> charArrayOf('B', '8')
                '8' -> charArrayOf('8', 'B')
                'G' -> charArrayOf('G', '6')
                '6' -> charArrayOf('6', 'G')
                else -> charArrayOf(character)
            }
            alternatives.forEach { alternative ->
                current.append(alternative)
                build(index + 1, current)
                current.deleteCharAt(current.lastIndex)
            }
        }

        build(0, StringBuilder())
        yieldAll(variants)
    }

    private fun checkDigit(value: String): Char {
        val weights = intArrayOf(7, 3, 1)
        val sum = value.mapIndexed { index, character ->
            val characterValue = when (character) {
                in '0'..'9' -> character - '0'
                in 'A'..'Z' -> character - 'A' + 10
                else -> 0
            }
            characterValue * weights[index % weights.size]
        }.sum()
        return '0' + (sum % 10)
    }

    private fun isPassportRowTwo(row: String): Boolean {
        if (!row.matches(Regex("[A-Z0-9<]{9}[0-9][A-Z]{3}[0-9]{6}[0-9][MF<][0-9]{6}[0-9][A-Z0-9<]{15}[0-9]"))) {
            return false
        }
        return true
    }

    private fun formatMrzDate(value: String): String {
        val year = value.substring(0, 2).toInt()
        val fullYear = if (year >= 50) 1900 + year else 2000 + year
        return "%02d/%02d/%04d".format(
            Locale.US,
            value.substring(4, 6).toInt(),
            value.substring(2, 4).toInt(),
            fullYear
        )
    }
}
