package com.local.bulksms.importdata

import com.local.bulksms.model.RawTable
import java.io.InputStream

fun interface TableImporter {
    fun import(input: InputStream): RawTable
}
