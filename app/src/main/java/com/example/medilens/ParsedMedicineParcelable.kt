package com.example.medilens

import android.os.Parcel
import android.os.Parcelable

/**
 * Parcelable wrapper for ParsedMedicine so it can be passed between Activities via Intent.
 * PrescriptionScanActivity → PrescriptionConfirmActivity
 */
data class ParsedMedicineParcelable(
    val medicineName:       String,
    val dose:               String,
    val form:               String,
    val timesPerDay:        Int,
    val scheduleTimes:      List<String>,
    val duration:           String,
    val instructions:       String,
    val quantity:           Int,
    val confidence:         Float,
    val verificationStatus: String,    // VerificationStatus.name
    val validationFlag:     String     // ValidationFlag.name
) : Parcelable {

    constructor(parcel: Parcel) : this(
        medicineName       = parcel.readString() ?: "",
        dose               = parcel.readString() ?: "",
        form               = parcel.readString() ?: "",
        timesPerDay        = parcel.readInt(),
        scheduleTimes      = parcel.createStringArrayList() ?: emptyList(),
        duration           = parcel.readString() ?: "",
        instructions       = parcel.readString() ?: "",
        quantity           = parcel.readInt(),
        confidence         = parcel.readFloat(),
        verificationStatus = parcel.readString() ?: VerificationStatus.ENROLLMENT_PENDING.name,
        validationFlag     = parcel.readString() ?: ValidationFlag.AMBER.name
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(medicineName)
        parcel.writeString(dose)
        parcel.writeString(form)
        parcel.writeInt(timesPerDay)
        parcel.writeStringList(scheduleTimes)
        parcel.writeString(duration)
        parcel.writeString(instructions)
        parcel.writeInt(quantity)
        parcel.writeFloat(confidence)
        parcel.writeString(verificationStatus)
        parcel.writeString(validationFlag)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<ParsedMedicineParcelable> {
        override fun createFromParcel(parcel: Parcel) = ParsedMedicineParcelable(parcel)
        override fun newArray(size: Int) = arrayOfNulls<ParsedMedicineParcelable>(size)
    }

    /** Convert back to domain object */
    fun toParsedMedicine() = ParsedMedicine(
        medicineName       = medicineName,
        dose               = dose,
        form               = form,
        timesPerDay        = timesPerDay,
        scheduleTimes      = scheduleTimes,
        duration           = duration,
        instructions       = instructions,
        quantity           = quantity,
        confidence         = confidence,
        verificationStatus = VerificationStatus.valueOf(verificationStatus),
        validationFlag     = ValidationFlag.valueOf(validationFlag)
    )
}

/** Extension on ParsedMedicine to create Parcelable */
fun ParsedMedicine.toParcelable() = ParsedMedicineParcelable(
    medicineName       = medicineName,
    dose               = dose,
    form               = form,
    timesPerDay        = timesPerDay,
    scheduleTimes      = scheduleTimes,
    duration           = duration,
    instructions       = instructions,
    quantity           = quantity,
    confidence         = confidence,
    verificationStatus = verificationStatus.name,
    validationFlag     = validationFlag.name
)
