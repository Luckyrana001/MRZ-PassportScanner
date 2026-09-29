package com.android.mrzcardreader.cardconnectors
import com.android.mrzcardreader.camera.MRZResponse
import com.android.mrzcardreader.camera.models.IdData
import ocr.MrzReader
import java.util.Locale


object CardConnector {
    val mrzReader = MrzReader()
    private const val MAX_RECENT_LINES = 24
    private const val MAX_RECENT_REFERENCE_LINES = 120
    private const val MIN_LIVE_PASSPORT_OBSERVATIONS = 5
    private const val MAX_CONSENSUS_OBSERVATIONS = 9
    private val recentLines = ArrayDeque<String>()
    private val recentReferenceLines = ArrayDeque<String>()

    fun onLinesCaptured(
        lines: List<String>,
        mrzResponse: MRZResponse,
        referenceLines: List<String> = lines,
    ) {
        val passportLines = synchronized(recentLines) {
            lines.forEach { line ->
                recentLines.addLast(line)
            }
            while (recentLines.size > MAX_RECENT_LINES) {
                recentLines.removeFirst()
            }
            recentLines.toList()
        }
        val passportReferenceLines = synchronized(recentReferenceLines) {
            referenceLines.forEach { line -> recentReferenceLines.addLast(line) }
            while (recentReferenceLines.size > MAX_RECENT_REFERENCE_LINES) {
                recentReferenceLines.removeFirst()
            }
            recentReferenceLines.toList()
        }

        parsePassport(
            passportLines,
            MIN_LIVE_PASSPORT_OBSERVATIONS,
            passportReferenceLines,
        )?.let {
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
                firstName = readCard.firstName,
                middleName = readCard.secondName,
                lastName = readCard.lastName,
                gender = readCard.gender,
                documentNo = readCard.documentNumber,
                dateOfBirth = formatMrzDate(readCard.dateOfBirth),
                idNo = readCard.id,
                nationality = readCard.country,
                rawMrz = mrzString,
                dateOfExpiry = formatMrzDate(readCard.dateOfExpiry),
                placeOfIssue = extractPlaceOfIssue(referenceLines),
            )
            mrzResponse.cardResponse(card)
        }


    }

    fun clear(){
        synchronized(recentLines) {
            recentLines.clear()
        }
        synchronized(recentReferenceLines) {
            recentReferenceLines.clear()
        }
    }

    private fun cleanMRZ(details_: String): String {
        var details = details_.replace(" ", "")
        details = details.replace("«", "<")
        details = details.replace("e", "<")
        details = details.replace("€", "<")
        return details
    }

    fun parsePassport(lines: List<String>): IdData? = parsePassport(lines, 1, lines)

    private fun parsePassport(
        lines: List<String>,
        minimumRowOneObservations: Int,
        referenceLines: List<String>,
    ): IdData? {
        val normalizedLines = lines.map(::normalizeMrzLine)
        val rowOneCandidates = normalizedLines
            .asSequence()
            .map(::normalizePassportFirstLine)
            .filter(::isPassportRowOneCandidate)
            .map { it.padEnd(44, '<').take(44) }
            .toList()
        val rowOne = consensusPassportRowOne(rowOneCandidates, minimumRowOneObservations)
            ?: return null

        val rowTwo = normalizedLines
            .asReversed()
            .asSequence()
            .filter { it.length >= 40 && it.any(Char::isDigit) }
            .map(::normalizePassportRowTwo)
            .firstOrNull(::isPassportRowTwo)
            ?: return null

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

        val issuingCountry = rowOne.substring(2, 5)
        val parsedFirstName: String
        val parsedMiddleName: String
        if (issuingCountry == "SGP" && givenNames.size > 1) {
            parsedFirstName = givenNames.last()
            parsedMiddleName = givenNames.dropLast(1).joinToString(" ")
        } else {
            parsedFirstName = givenNames.first()
            parsedMiddleName = givenNames.drop(1).joinToString(" ")
        }
        val firstName = correctTrailingFillerMisread(parsedFirstName, referenceLines)
        val middleName = parsedMiddleName
            .split(' ')
            .joinToString(" ") { correctTrailingFillerMisread(it, referenceLines) }
        val correctedLastName = correctTrailingFillerMisread(lastName, referenceLines)

        return IdData(
            firstName = firstName,
            middleName = middleName,
            lastName = correctedLastName,
            gender = resolveGender(rowTwo[20], referenceLines),
            documentNo = rowTwo.substring(0, 9).trimEnd('<'),
            dateOfBirth = formatMrzDate(rowTwo.substring(13, 19)),
            idNo = rowTwo.substring(28, 43).trimEnd('<'),
            nationality = rowTwo.substring(10, 13).trimEnd('<'),
            rawMrz = "$rowOne\n$rowTwo",
            dateOfExpiry = formatMrzDate(rowTwo.substring(21, 27)),
            placeOfIssue = extractPlaceOfIssue(referenceLines),
        )
    }

    private fun extractPlaceOfIssue(lines: List<String>): String {
        val labelPattern = Regex(
            "(?:PLACE\\s+OF\\s+ISSUE|PLACE\\s+OF\\s+ISSUANCE|ISSUING\\s+PLACE)\\s*[:/-]?\\s*(.*)$",
            RegexOption.IGNORE_CASE,
        )
        val cleanedLines = lines.map { line -> line.trim().replace(Regex("\\s+"), " ") }

        cleanedLines.forEachIndexed { index, line ->
            val labelMatch = labelPattern.find(line) ?: return@forEachIndexed
            cleanPlaceOfIssueCandidate(labelMatch.groupValues[1])?.let { return it }

            for (candidateIndex in (index + 1)..minOf(index + 3, cleanedLines.lastIndex)) {
                cleanPlaceOfIssueCandidate(cleanedLines[candidateIndex])?.let { return it }
            }
        }
        return ""
    }

    private fun cleanPlaceOfIssueCandidate(value: String): String? {
        val candidate = value
            .trim(' ', ':', '-', '/', '|')
            .replace(Regex("\\s+"), " ")
        if (candidate.length !in 2..48 || candidate.contains('<')) return null
        if (candidate.any(Char::isDigit)) return null
        if (candidate.count(Char::isLetter) < 2) return null

        val uppercaseCandidate = candidate.uppercase(Locale.US)
        val labelWords = listOf(
            "PLACE OF ISSUE",
            "PLACE OF ISSUANCE",
            "ISSUING PLACE",
            "DATE OF ISSUE",
            "DATE OF EXPIRY",
            "DATE OF BIRTH",
            "PASSPORT",
            "NATIONALITY",
            "AUTHORITY",
            "GENDER",
            "SEX",
        )
        if (labelWords.any(uppercaseCandidate::contains)) return null
        return candidate
    }

    private fun correctTrailingFillerMisread(
        name: String,
        referenceLines: List<String>,
    ): String {
        if (!name.endsWith('K')) return name
        for (suffixLength in 2 downTo 1) {
            if (name.length <= suffixLength || !name.endsWith("K".repeat(suffixLength))) continue
            val candidate = name.dropLast(suffixLength)
            if (hasPrintedNameEvidence(candidate, referenceLines)) return candidate
        }
        return name
    }

    private fun hasPrintedNameEvidence(name: String, lines: List<String>): Boolean = lines.any { line ->
        if (line.contains('<') || line.contains('«')) return@any false
        val words = line.uppercase(Locale.US)
            .replace(Regex("[^A-Z]+"), " ")
            .trim()
            .split(Regex("\\s+"))
        name in words || words.joinToString("").endsWith(name)
    }

    private fun resolveGender(mrzGender: Char, referenceLines: List<String>): String {
        if (mrzGender == 'M' || mrzGender == 'F' || mrzGender == 'X') {
            return mrzGender.toString()
        }

        val normalizedLines = referenceLines.map { line ->
            line.uppercase(Locale.US)
                .replace(Regex("[^A-Z]+"), " ")
                .trim()
        }
        normalizedLines.forEach { line ->
            val words = line.split(Regex("\\s+")).filter(String::isNotEmpty)
            val hasGenderLabel = words.any { word ->
                word == "SEX" || word == "GENDER" || word == "KYN"
            }
            if (hasGenderLabel) {
                words.lastOrNull { it == "M" || it == "F" || it == "X" }?.let { return it }
            }
        }
        return normalizedLines.firstOrNull { it == "M" || it == "F" || it == "X" }.orEmpty()
    }

    private fun isPassportRowOneCandidate(row: String): Boolean =
        row.length >= 30 &&
            row.startsWith("P<") &&
            row.substring(2, 5).all { it.isLetter() || it == '<' } &&
            row.substring(2, 5).any(Char::isLetter) &&
            row.count { it == '<' } >= 2

    private fun consensusPassportRowOne(
        candidates: List<String>,
        minimumObservations: Int,
    ): String? {
        val compatibleCandidates = candidates
            .takeLast(MAX_CONSENSUS_OBSERVATIONS)
            .groupBy { it.take(5) }
            .values
            .maxByOrNull(List<String>::size)
            .orEmpty()
        if (compatibleCandidates.size < minimumObservations) return null
        if (compatibleCandidates.size == 1) return compatibleCandidates.first()

        return buildString(44) {
            repeat(44) { index ->
                val votes = compatibleCandidates.groupingBy { it[index] }.eachCount()
                val mostVotes = votes.maxOf { it.value }
                val winners = votes.filterValues { it == mostVotes }.keys
                append(
                    when {
                        '<' in winners -> '<'
                        else -> compatibleCandidates.asReversed()
                            .first { it[index] in winners }[index]
                    }
                )
            }
        }
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
        return String(row)
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
        if (!row.matches(Regex("[A-Z0-9<]{9}[0-9][A-Z<]{3}[0-9]{6}[0-9][MF<][0-9]{6}[0-9][A-Z0-9<]{15}[0-9]"))) {
            return false
        }
        val validPrimaryCheckDigits = listOf(
            checkDigit(row.substring(0, 9)) == row[9],
            checkDigit(row.substring(13, 19)) == row[19],
            checkDigit(row.substring(21, 27)) == row[27],
        ).count { it }
        return validPrimaryCheckDigits >= 1
    }

    private fun formatMrzDate(value: String): String {
        if (!value.matches(Regex("[0-9]{6}"))) return value
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
