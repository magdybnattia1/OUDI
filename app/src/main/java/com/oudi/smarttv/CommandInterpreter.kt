package com.oudi.smarttv

object CommandInterpreter {
    fun describe(raw: String): String {
        val text = raw.lowercase(LocaleHolder.arabic).replace("أ", "ا").replace("إ", "ا").replace("آ", "ا")
        return when {
            text.contains("يوتيوب") || text.contains("youtube") ->
                "فهمت: فتح YouTube. في الخطوة التالية هنربط الأمر بالتلفزيون."
            text.contains("نتفلكس") || text.contains("نتفليكس") || text.contains("netflix") ->
                "فهمت: فتح Netflix. في الخطوة التالية هنربط الأمر بالتلفزيون."
            text.contains("هوم") || text.contains("الرئيسية") || text.contains("الرئيسيه") ->
                "فهمت: الرجوع للشاشة الرئيسية. التحكم الشبكي لسه هيتوصل."
            text.contains("المجد") ->
                "فهمت: طلب قناة المجد. هنحتاج نعرف موديل التلفزيون وطريقة استقبال القنوات لربطها."
            text.contains("ارفع الصوت") || text.contains("علي الصوت") ->
                "فهمت: رفع الصوت. أمر التحكم جاهز للربط."
            text.contains("وطي الصوت") || text.contains("خفض الصوت") ->
                "فهمت: خفض الصوت. أمر التحكم جاهز للربط."
            else -> "سمعت: «$raw» — لسه ما ضفناش الأمر ده."
        }
    }

    private object LocaleHolder {
        val arabic = java.util.Locale("ar", "EG")
    }
}
