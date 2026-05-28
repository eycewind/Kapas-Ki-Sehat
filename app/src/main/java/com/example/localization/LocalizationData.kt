package com.example.localization

enum class AppLanguage {
    ENGLISH, URDU, PUNJABI, SARAIKI
}

object LocalizationData {
    val diagnosisTitles = mapOf(
        AppLanguage.ENGLISH to "Whitefly Infestation",
        AppLanguage.URDU to "کپاس کی سفید مکھی کا حملہ",
        AppLanguage.PUNJABI to "چٹی مکھی دا حملہ",
        AppLanguage.SARAIKI to "چٹی مکھی دا حملہ"
    )

    val confidenceMetrics = mapOf(
        AppLanguage.ENGLISH to "Severity - High Alert",
        AppLanguage.URDU to "سنگین نقصان کا خطرہ",
        AppLanguage.PUNJABI to "شدید نقصان دا خطرہ",
        AppLanguage.SARAIKI to "سنگین نقصان دا خطرہ"
    )

    val actionProtocols = mapOf(
        AppLanguage.ENGLISH to listOf(
            "Immediate chemical spray of Diafenthiuron (200ml/acre).",
            "Deploy yellow sticky traps (40/acre) to catch adult flies.",
            "Ensure a clean sprinkler flush to wash away sticky honeydew.",
            "Avoid chemical cocktails that destroy predatory ladybugs."
        ),
        AppLanguage.URDU to listOf(
            "ڈائی فینتھیورون (200 ملی لیٹر/ایکڑ) کا فوری سپرے کریں۔",
            "بالغ مکھیوں کو پکڑنے کے لیے پیلے رنگ کے چپکنے والے جال (40/ایکڑ) لگائیں۔",
            "چپچپا مادہ دھونے کے لیے صاف پانی کا چھڑکاؤ یقینی بنائیں۔",
            "ایسے کیمیائی مرکبات سے پرہیز کریں جو شکاری لیڈی بگز کو تباہ کر دیں۔"
        ),
        AppLanguage.PUNJABI to listOf(
            "ڈائی فینتھیورون (200 ملی لیٹر/ایکڑ) دا فوری سپرے کرو۔",
            "بالغ مکھیاں نوں پھڑن لئی پیلے رنگ دے چپکنے والے جال (40/ایکڑ) لاؤ۔",
            "چپچپا مادہ دھون لئی صاف پانی دا چھڑکاؤ یقینی بناؤ۔",
            "ایسے کیمیائی مرکبات توں بچو جیہڑے شکاری لیڈی بگز نوں تباہ کر دین۔"
        ),
        AppLanguage.SARAIKI to listOf(
            "ڈائی فینتھیورون (200 ملی لیٹر/ایکڑ) دا فوری سپرے کرو۔",
            "بالغ مکھیاں کوں پکڑنڑ کیتے پیلے رنگ دے چپکنے والے جال (40/ایکڑ) لاؤ۔",
            "چپچپا مادہ دھوونڑ کیتے صاف پانی دا چھڑکاؤ یقینی بناؤ۔",
            "ایجیئیں کیمیائی مرکبات توں بچو جیہڑے شکاری لیڈی بگز کوں تباہ کر ڈیندن۔"
        )
    )

    val greetings = mapOf(
        AppLanguage.ENGLISH to "As-Salamu Alaykum! Tap to begin scanning.",
        AppLanguage.URDU to "السلام علیکم! اسکین کرنے کے لیے ٹیپ کریں۔",
        AppLanguage.PUNJABI to "اسلام علیکم! سکین کرن لئی ٹیپ کرو۔",
        AppLanguage.SARAIKI to "اسلام علیکم! سکین کرنڑ کیتے ٹیپ کرو۔"
    )

    val criticalWhiteflyRiskTitle = mapOf(
        AppLanguage.ENGLISH to "CRITICAL WHITEFLY RISK",
        AppLanguage.URDU to "سفید مکھی کا شدید خطرہ",
        AppLanguage.PUNJABI to "چٹی مکھی دا شدید خطرہ",
        AppLanguage.SARAIKI to "چٹی مکھی دا شدید خطرہ"
    )

    val criticalWhiteflyRiskDesc = mapOf(
        AppLanguage.ENGLISH to "High risk of Whitefly expansion due to continuous dry heat wave.",
        AppLanguage.URDU to "سنگین خطرہ: مسلسل خشک گرمی کی وجہ سے سفید مکھی کے پھیلاؤ کا زیادہ خطرہ۔",
        AppLanguage.PUNJABI to "شدید خطرہ: مسلسل خشک گرمی دی وجہ توں چٹی مکھی دے پھیلاؤ دا زیادہ خطرہ۔",
        AppLanguage.SARAIKI to "شدید خطرہ: مسلسل خشک گرمی دی وجہ توں چٹی مکھی دے پھیلاؤ دا زیادہ خطرہ۔"
    )

    val scanCropMain = mapOf(
        AppLanguage.ENGLISH to "SCAN CROP",
        AppLanguage.URDU to "کپاس کا پتہ اسکین کریں",
        AppLanguage.PUNJABI to "کپاہ دا پتہ سکین کرو",
        AppLanguage.SARAIKI to "کپاہ دا پتہ سکین کرو"
    )
}

