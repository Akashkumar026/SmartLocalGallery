package com.example.smartgallery.engine

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.smartgallery.data.db.MediaItem
import java.util.Locale
import java.util.regex.Pattern

/**
 * Multi-signal intelligent document and image classifier (v2).
 * Combines ML Kit Text Recognition (OCR) + ML Kit Image Labeling + ML Kit Barcode Scanning +
 * ML Kit Face Detection + Aspect Ratio & Path heuristics to compute normalized confidence scores
 * across 12 practical collections.
 *
 * Implements strict Indian PII masking (Aadhaar Act & IT Act compliance) to scrub
 * sensitive national ID numbers before persistence.
 */
object SmartCategoryClassifier {

    const val CLASSIFIER_VERSION = 2

    const val CATEGORY_DOCUMENTS_IDS = "DOCUMENTS_IDS"
    const val CATEGORY_BILLS_RECEIPTS = "BILLS_RECEIPTS"
    const val CATEGORY_CERTIFICATES = "CERTIFICATES"
    const val CATEGORY_BROCHURES_FLYERS = "BROCHURES_FLYERS"
    const val CATEGORY_NOTES_WHITEBOARDS = "NOTES_WHITEBOARDS"
    const val CATEGORY_QR_BARCODES = "QR_BARCODES"
    const val CATEGORY_SCREENSHOTS = "SCREENSHOTS"
    const val CATEGORY_SELFIES_PORTRAITS = "SELFIES_PORTRAITS"
    const val CATEGORY_PETS_ANIMALS = "PETS_ANIMALS"
    const val CATEGORY_FOOD_DINING = "FOOD_DINING"
    const val CATEGORY_TRAVEL_VEHICLES = "TRAVEL_VEHICLES"
    const val CATEGORY_MEMES_SOCIAL = "MEMES_SOCIAL"

    data class CategoryMeta(
        val id: String,
        val title: String,
        val description: String,
        val icon: ImageVector
    )

    data class ClassificationResult(
        val primaryCategory: String?,
        val confidence: Float,
        val categoryScores: Map<String, Float>,
        val classifierVersion: Int = CLASSIFIER_VERSION,
        val hasSensitiveId: Boolean = false,
        val sanitizedOcrText: String? = null
    )

    val ALL_CATEGORIES = listOf(
        CategoryMeta(
            id = CATEGORY_DOCUMENTS_IDS,
            title = "Documents & IDs",
            description = "Passports, IDs, agreements, resumes & forms",
            icon = Icons.Default.Description
        ),
        CategoryMeta(
            id = CATEGORY_BILLS_RECEIPTS,
            title = "Bills & Receipts",
            description = "Invoices, payment receipts, taxes & orders",
            icon = Icons.Default.Receipt
        ),
        CategoryMeta(
            id = CATEGORY_CERTIFICATES,
            title = "Certificates",
            description = "Diplomas, degrees, awards & achievements",
            icon = Icons.Default.Star
        ),
        CategoryMeta(
            id = CATEGORY_BROCHURES_FLYERS,
            title = "Brochures & Flyers",
            description = "Offers, discounts, menus & event schedules",
            icon = Icons.AutoMirrored.Filled.Article
        ),
        CategoryMeta(
            id = CATEGORY_NOTES_WHITEBOARDS,
            title = "Notes & Whiteboards",
            description = "Handwritten notes, whiteboards & study materials",
            icon = Icons.Default.EditNote
        ),
        CategoryMeta(
            id = CATEGORY_QR_BARCODES,
            title = "QR & Barcodes",
            description = "Scannable codes, tickets & boarding passes",
            icon = Icons.Default.QrCode
        ),
        CategoryMeta(
            id = CATEGORY_SCREENSHOTS,
            title = "Screenshots",
            description = "Phone screen captures & UI clippings",
            icon = Icons.Default.PhotoLibrary
        ),
        CategoryMeta(
            id = CATEGORY_SELFIES_PORTRAITS,
            title = "Selfies & Portraits",
            description = "Close-up portraits & front camera captures",
            icon = Icons.Default.Face
        ),
        CategoryMeta(
            id = CATEGORY_PETS_ANIMALS,
            title = "Pets & Animals",
            description = "Dogs, cats, birds, wildlife & domestic pets",
            icon = Icons.Default.Pets
        ),
        CategoryMeta(
            id = CATEGORY_FOOD_DINING,
            title = "Food & Dining",
            description = "Dishes, meals, beverages & restaurant photos",
            icon = Icons.Default.Restaurant
        ),
        CategoryMeta(
            id = CATEGORY_TRAVEL_VEHICLES,
            title = "Travel & Vehicles",
            description = "Cars, flights, landscapes, mountains & beaches",
            icon = Icons.Default.Flight
        ),
        CategoryMeta(
            id = CATEGORY_MEMES_SOCIAL,
            title = "Memes & Social",
            description = "Humorous comics, cartoons & saved social media",
            icon = Icons.Default.EmojiEmotions
        )
    )

    // ─── PII Masking Patterns (Aadhaar, PAN, Passport, Voter ID) ────────────
    private val AADHAAR_PATTERN = Pattern.compile("\\b([2-9]\\d{3})[\\s-]?(\\d{4})[\\s-]?(\\d{4})\\b")
    private val PAN_PATTERN = Pattern.compile("\\b([A-Z]{5})(\\d{4})([A-Z])\\b", Pattern.CASE_INSENSITIVE)
    private val PASSPORT_PATTERN = Pattern.compile("\\b([A-Za-z])(\\d{7})\\b")
    private val VOTER_ID_PATTERN = Pattern.compile("\\b([A-Z]{3})(\\d{7})\\b", Pattern.CASE_INSENSITIVE)

    /**
     * Masks sensitive Indian identity numbers from raw OCR text to ensure zero PII leakage.
     * Returns sanitized text and a boolean flag indicating if sensitive IDs were present.
     */
    fun sanitizePii(rawText: String?): Pair<String?, Boolean> {
        if (rawText.isNullOrBlank()) return Pair(null, false)
        var hasSensitiveId = false
        var text = rawText

        // Mask Aadhaar: e.g. "2345 6789 0123" -> "XXXX-XXXX-0123"
        val aadhaarMatcher = AADHAAR_PATTERN.matcher(text)
        if (aadhaarMatcher.find()) {
            hasSensitiveId = true
            text = aadhaarMatcher.replaceAll("XXXX-XXXX-$3")
        }

        // Mask PAN: e.g. "ABCDE1234F" -> "XXXXX1234X"
        val panMatcher = PAN_PATTERN.matcher(text)
        if (panMatcher.find()) {
            hasSensitiveId = true
            text = panMatcher.replaceAll("XXXXX$2X")
        }

        // Mask Passport: e.g. "Z1234567" -> "X****567"
        val passportMatcher = PASSPORT_PATTERN.matcher(text)
        if (passportMatcher.find()) {
            hasSensitiveId = true
            val sb = StringBuffer()
            val m = PASSPORT_PATTERN.matcher(text)
            while (m.find()) {
                val digits = m.group(2) ?: ""
                val last3 = if (digits.length >= 3) digits.takeLast(3) else digits
                m.appendReplacement(sb, "X****$last3")
            }
            m.appendTail(sb)
            text = sb.toString()
        }

        // Mask Voter ID: e.g. "ABC1234567" -> "XXX****567"
        val voterMatcher = VOTER_ID_PATTERN.matcher(text)
        if (voterMatcher.find()) {
            hasSensitiveId = true
            val sb = StringBuffer()
            val m = VOTER_ID_PATTERN.matcher(text)
            while (m.find()) {
                val digits = m.group(2) ?: ""
                val last3 = if (digits.length >= 3) digits.takeLast(3) else digits
                m.appendReplacement(sb, "XXX****$last3")
            }
            m.appendTail(sb)
            text = sb.toString()
        }

        return Pair(text, hasSensitiveId)
    }

    // ─── Multilingual OCR Dictionaries (English + Hindi) ─────────────────────
    private val BILLS_KEYWORDS = listOf(
        "invoice", "receipt", "total", "subtotal", "tax", "gst", "cgst", "sgst",
        "amount due", "paid", "bill", "rs.", "inr", "upi", "transaction", "order #",
        "bill no", "cashier", "balance", "net amount", "grand total", "payment",
        "account", "statement", "debit", "credit", "merchant", "tax invoice",
        "रसीद", "बिल", "कुल", "भुगतान", "बकाया", "कर", "जीएसटी", "राशि", "चालान", "खाता",
        "rasid", "bhugtan", "chalan"
    )

    private val CERTIFICATE_KEYWORDS = listOf(
        "certificate", "this is to certify", "awarded to", "completion", "diploma",
        "degree", "university", "institute", "achievement", "authorized signatory",
        "seal", "presented to", "honour", "honor", "conferred", "merit", "certified",
        "प्रमाणपत्र", "प्रमाण पत्र", "डिप्लोमा", "उपाधि", "विश्वविद्यालय", "प्रमाणित किया जाता है",
        "सम्मानित", "सर्टिफिकेट", "praman patra", "vishwavidyalaya"
    )

    private val BROCHURE_KEYWORDS = listOf(
        "offer", "discount", "admission", "brochure", "flyer", "register now",
        "contact us", "www.", "venue", "schedule", "% off", "special offer",
        "limited time", "enroll now", "webinar", "conference", "fest", "sale",
        "menu", "combos", "combo", "price", "pamphlet",
        "छूट", "ऑफ़र", "प्रवेश", "पंजीकरण", "विशेष छूट", "समारोह", "विजिट करें",
        "ofer", "chhoot", "panjikaran"
    )

    private val DOCUMENT_ID_KEYWORDS = listOf(
        "passport", "driving licence", "driving license", "license", "aadhaar",
        "pan card", "identity card", "identity", "government of", "agreement",
        "declaration", "form", "application", "resume", "curriculum vitae",
        "affidavit", "republic of", "election commission", "voter id", "national id",
        "birth certificate", "marksheet", "transcript", "statement", "ministry of",
        "signature", "valid thru", "permanent account number",
        "पहचान पत्र", "आधार", "आयकर", "पैन", "भारत सरकार", "पासपोर्ट", "मतदाता",
        "शपथ पत्र", "फॉर्म", "आवेदन", "हस्ताक्षर", "aadhaar", "pehchan patra", "pan card"
    )

    private val NOTES_KEYWORDS = listOf(
        "note", "notes", "whiteboard", "blackboard", "handwriting", "bullet",
        "chapter", "lecture", "homework", "assignment", "summary", "formula",
        "theorem", "diagram", "exercise", "question", "answer", "syllabus",
        "नोट्स", "अध्याय", "पाठ", "प्रश्न", "उत्तर", "गृहकार्य", "सूत्र", "परीक्षा"
    )

    private val PETS_LABELS = setOf(
        "dog", "cat", "pet", "animal", "bird", "wildlife", "carnivore", "canine",
        "feline", "kitten", "puppy", "mammal", "reptile", "horse", "fauna"
    )

    private val FOOD_LABELS = setOf(
        "food", "dish", "cuisine", "meal", "beverage", "coffee", "dessert",
        "restaurant", "tableware", "recipe", "snack", "bread", "cake",
        "pizza", "burger", "drink", "ingredient", "produce", "fruit"
    )

    private val TRAVEL_LABELS = setOf(
        "car", "automobile", "aircraft", "airplane", "train", "beach", "mountain",
        "nature", "landscape", "sky", "sea", "ocean", "vehicle", "travel",
        "vacation", "road", "watercraft", "boat", "cloud", "sunset", "horizon"
    )

    private val MEME_LABELS = setOf(
        "comic", "cartoon", "illustration", "animated cartoon", "fiction", "humor"
    )

    private val SOCIAL_BUCKETS = listOf(
        "whatsapp", "telegram", "reddit", "instagram", "facebook", "twitter", "x", "download"
    )

    /**
     * Executes multi-signal classification, accumulating normalized confidence scores
     * across all 12 categories.
     */
    fun classifyMultiSignal(
        item: MediaItem,
        ocrText: String? = null,
        labels: List<Pair<String, Float>> = emptyList(),
        faceCount: Int = 0,
        faceAreaFraction: Float = 0f,
        hasBarcodes: Boolean = false
    ): ClassificationResult {
        val (sanitizedText, hasSensitiveId) = sanitizePii(ocrText ?: item.ocrText)
        val text = sanitizedText?.lowercase(Locale.ROOT)

        val lowerPath = item.absolutePath.lowercase(Locale.ROOT)
        val lowerBucket = item.bucketName.lowercase(Locale.ROOT)
        val lowerName = item.displayName.lowercase(Locale.ROOT)
        val width = item.width
        val height = item.height
        val aspectRatio = if (width > 0 && height > 0) height.toFloat() / width.toFloat() else 1.0f

        val scores = mutableMapOf<String, Float>()
        ALL_CATEGORIES.forEach { scores[it.id] = 0f }

        // 1. SCREENSHOTS: Path or screen aspect ratio with UI status bar signals
        if (lowerPath.contains("screenshot") || lowerBucket.contains("screenshot") || lowerName.contains("screenshot")) {
            scores[CATEGORY_SCREENSHOTS] = 0.95f
        } else if (aspectRatio in 1.7f..2.4f || aspectRatio in 0.41f..0.59f) {
            if (text != null && (text.contains("am") || text.contains("pm") || text.contains("wifi") ||
                        text.contains("battery") || text.contains("lte") || text.contains("5g") ||
                        text.contains("settings") || text.contains("search"))) {
                scores[CATEGORY_SCREENSHOTS] = 0.85f
            }
        }

        // 2. QR & BARCODES: Barcode scanner hits or code naming
        if (hasBarcodes) {
            scores[CATEGORY_QR_BARCODES] = 0.95f
        } else if (lowerName.contains("qrcode") || lowerName.contains("barcode") || lowerBucket.contains("qr")) {
            scores[CATEGORY_QR_BARCODES] = 0.80f
        }

        // 3. SELFIES & PORTRAITS: 1-2 faces occupying >=15% frame area, or selfie naming
        if (faceCount in 1..2 && faceAreaFraction >= 0.15f) {
            scores[CATEGORY_SELFIES_PORTRAITS] = (0.60f + faceAreaFraction.coerceAtMost(0.35f)).coerceAtMost(1.0f)
        } else if (lowerPath.contains("selfie") || lowerBucket.contains("selfie") || lowerName.contains("selfie")) {
            scores[CATEGORY_SELFIES_PORTRAITS] = 0.85f
        }

        // 4. ML Kit Image Labeling (Pets, Food, Travel, Memes)
        val highConfidenceLabels = labels.filter { it.second >= 0.55f }

        var petScore = 0f
        var foodScore = 0f
        var travelScore = 0f
        var memeScore = 0f

        for ((lbl, conf) in highConfidenceLabels) {
            val l = lbl.lowercase(Locale.ROOT)
            if (PETS_LABELS.any { l.contains(it) }) petScore += conf * 0.5f
            if (FOOD_LABELS.any { l.contains(it) }) foodScore += conf * 0.5f
            if (TRAVEL_LABELS.any { l.contains(it) }) travelScore += conf * 0.5f
            if (MEME_LABELS.any { l.contains(it) }) memeScore += conf * 0.5f
        }
        scores[CATEGORY_PETS_ANIMALS] = petScore.coerceAtMost(0.95f)
        scores[CATEGORY_FOOD_DINING] = foodScore.coerceAtMost(0.95f)
        scores[CATEGORY_TRAVEL_VEHICLES] = travelScore.coerceAtMost(0.95f)

        // 5. OCR Text Keyword Matching (Certificates, Bills, IDs, Brochures, Notes)
        if (!text.isNullOrBlank()) {
            // Certificates
            var certHits = 0
            for (kw in CERTIFICATE_KEYWORDS) {
                if (text.contains(kw)) certHits++
            }
            if (certHits > 0) {
                scores[CATEGORY_CERTIFICATES] = (0.35f + (certHits * 0.25f)).coerceAtMost(0.95f)
            }

            // Bills & Receipts
            var billHits = 0
            for (kw in BILLS_KEYWORDS) {
                if (text.contains(kw)) billHits++
            }
            var bScore = billHits * 0.22f
            if (aspectRatio > 1.75f && (text.contains("total") || text.contains("amount") || text.contains("paid") || text.contains("rs.") || text.contains("inr"))) {
                bScore += 0.40f
            }
            if (bScore > 0f) {
                scores[CATEGORY_BILLS_RECEIPTS] = bScore.coerceAtMost(0.95f)
            }

            // Documents & IDs
            var docHits = 0
            for (kw in DOCUMENT_ID_KEYWORDS) {
                if (text.contains(kw)) docHits++
            }
            var dScore = docHits * 0.25f
            if (hasSensitiveId) {
                dScore += 0.70f // Very strong signal if Aadhaar/PAN/Passport was detected
            }
            if (text.length > 120 && (aspectRatio in 1.2f..1.65f || aspectRatio in 0.60f..0.85f)) {
                dScore += 0.35f
            }
            if (dScore > 0f) {
                scores[CATEGORY_DOCUMENTS_IDS] = dScore.coerceAtMost(0.95f)
            }

            // Brochures & Flyers
            var flyerHits = 0
            for (kw in BROCHURE_KEYWORDS) {
                if (text.contains(kw)) flyerHits++
            }
            if (flyerHits > 0) {
                scores[CATEGORY_BROCHURES_FLYERS] = (flyerHits * 0.30f).coerceAtMost(0.90f)
            }

            // Notes & Whiteboards
            var noteHits = 0
            for (kw in NOTES_KEYWORDS) {
                if (text.contains(kw)) noteHits++
            }
            var nScore = noteHits * 0.30f
            if (lowerBucket.contains("note") || lowerName.contains("note")) nScore += 0.35f
            if (nScore > 0f) {
                scores[CATEGORY_NOTES_WHITEBOARDS] = nScore.coerceAtMost(0.90f)
            }
        }

        // 6. Memes & Social Media
        if (memeScore > 0f || (SOCIAL_BUCKETS.any { lowerBucket.contains(it) } && text != null && text.length in 5..80)) {
            scores[CATEGORY_MEMES_SOCIAL] = maxOf(memeScore, 0.70f).coerceAtMost(0.90f)
        }

        // Path fallback for receipts
        if (aspectRatio > 2.0f && (lowerBucket.contains("bill") || lowerName.contains("receipt"))) {
            scores[CATEGORY_BILLS_RECEIPTS] = maxOf(scores[CATEGORY_BILLS_RECEIPTS] ?: 0f, 0.75f)
        }

        // Determine primary winning category with minimum confidence threshold 0.35
        val best = scores.maxByOrNull { it.value }
        val winningCategory = if (best != null && best.value >= 0.35f) best.key else null
        val winningConfidence = best?.value ?: 0f

        return ClassificationResult(
            primaryCategory = winningCategory,
            confidence = winningConfidence,
            categoryScores = scores,
            classifierVersion = CLASSIFIER_VERSION,
            hasSensitiveId = hasSensitiveId,
            sanitizedOcrText = sanitizedText
        )
    }

    /**
     * Backwards compatibility helper returning single winning category.
     */
    fun classify(
        item: MediaItem,
        ocrText: String? = null,
        labels: List<Pair<String, Float>> = emptyList(),
        faceCount: Int = 0,
        faceAreaFraction: Float = 0f,
        hasBarcodes: Boolean = false
    ): String? {
        return classifyMultiSignal(
            item = item,
            ocrText = ocrText,
            labels = labels,
            faceCount = faceCount,
            faceAreaFraction = faceAreaFraction,
            hasBarcodes = hasBarcodes
        ).primaryCategory
    }
}
