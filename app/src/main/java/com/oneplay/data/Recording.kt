package com.oneplay.data

import android.net.Uri
import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
data class Recording(
    val uri: Uri,
    val title: String="",
    val duration: Int=0,
    val size: Long=0L,
    val modified: Long=0L,
    val isPending: Boolean = false
) : Parcelable