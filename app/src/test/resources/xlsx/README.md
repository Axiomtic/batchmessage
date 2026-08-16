# XLSX 测试夹具

`XlsxImporterTest` 使用 `ZipOutputStream` 在内存中生成最小 OOXML 包，覆盖工作簿、关系、共享字符串、样式和工作表 XML。这里不提交二进制 `.xlsx` 样本，避免测试资产携带不可见内容或绕过导入安全限制。
