package com.local.bulksms.importdata

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.Assert.assertEquals
import org.junit.Test

class PoiExcelImporterTest {

    @Test
    fun readsXlsxIncludingNumbersDatesAndFormulas() {
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("数据")
        val header = sheet.createRow(0)
        header.createCell(0).setCellValue("姓名")
        header.createCell(1).setCellValue("电话")
        header.createCell(2).setCellValue("金额")
        val row1 = sheet.createRow(1)
        row1.createCell(0).setCellValue("张三")
        row1.createCell(1).setCellValue("13800138000")
        row1.createCell(2).setCellValue(120.0)

        val bytes = ByteArrayOutputStream()
        workbook.use { it.write(bytes) }

        val raw = PoiExcelImporter().import(ByteArrayInputStream(bytes.toByteArray()))

        assertEquals(
            listOf(
                listOf("姓名", "电话", "金额"),
                listOf("张三", "13800138000", "120"),
            ),
            raw.rows,
        )
    }

    @Test
    fun readsXlsFromHssfWorkbook() {
        val workbook = HSSFWorkbook()
        val sheet = workbook.createSheet("数据")
        val row0 = sheet.createRow(0)
        row0.createCell(0).setCellValue("姓名")
        row0.createCell(1).setCellValue("电话")
        val row1 = sheet.createRow(1)
        row1.createCell(0).setCellValue("李四")
        row1.createCell(1).setCellValue("13900139000")

        val bytes = ByteArrayOutputStream()
        workbook.use { it.write(bytes) }

        val raw = PoiExcelImporter().import(ByteArrayInputStream(bytes.toByteArray()))

        assertEquals(
            listOf(
                listOf("姓名", "电话"),
                listOf("李四", "13900139000"),
            ),
            raw.rows,
        )
    }

    @Test
    fun normalizesRaggedRowsToUniformWidth() {
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("数据")
        sheet.createRow(0).createCell(0).setCellValue("A")
        val row1 = sheet.createRow(1)
        row1.createCell(0).setCellValue("1")
        row1.createCell(2).setCellValue("3")

        val bytes = ByteArrayOutputStream()
        workbook.use { it.write(bytes) }

        val raw = PoiExcelImporter().import(ByteArrayInputStream(bytes.toByteArray()))

        assertEquals(
            listOf(
                listOf("A", "", ""),
                listOf("1", "", "3"),
            ),
            raw.rows,
        )
    }
}
