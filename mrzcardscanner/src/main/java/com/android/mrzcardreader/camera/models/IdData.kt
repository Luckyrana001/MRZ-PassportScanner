package com.android.mrzcardreader.camera.models

import java.io.Serializable

data class IdData(
    var firstName: String,
    var middleName: String,
    var lastName: String,
    var gender:String,
    var documentNo:String,
    var dateOfBirth:String,
    var idNo: String,
    var nationality:String,
    var rawMrz: String = "",
    var mrzImagePath: String = "",
    var dateOfExpiry: String = "",
    var faceImagePath: String = "",
    var placeOfIssue: String = "",
):Serializable
