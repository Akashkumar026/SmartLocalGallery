package com.example.smartgallery.engine

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.smartgallery.data.db.MediaItem
import java.util.Locale

/**
 * Multi-signal intelligent document and image classifier.
 * Combines ML Kit Text Recognition (OCR) + ML Kit Image Labeling + ML Kit Barcode Scanning +
 * ML Kit Face Detection + Aspect Ratio & Path heuristics to categorize images into 12 practical collections.
 */
object SmartCategoryClassifier {

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
            icon = Icons.Default.Article
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

    private val BILLS_KEYWORDS = listOf(
        "invoice", "receipt", "total", "subtotal", "tax", "gst", "cgst", "sgst",
        "amount due", "paid", "bill", "rs.", "inr", "upi", "transaction", "order #",
        "bill no", "cashier", "balance", "net amount", "grand total", "payment",
        "account", "statement", "debit", "credit"
    )

    private val CERTIFICATE_KEYWORDS = listOf(
        "certificate", "this is to certify", "awarded to", "completion", "diploma",
        "degree", "university", "institute", "achievement", "authorized signatory",
        "seal", "presented to", "honour", "honor", "conferred", "merit", "certified"
    )

    private val BROCHURE_KEYWORDS = listOf(
        "offer", "discount", "admission", "brochure", "flyer", "register now",
        "contact us", "www.", "venue", "schedule", "% off", "special offer",
        "limited time", "enroll now", "webinar", "conference", "fest", "sale",
        "menu", "combos", "combo", "price"
    )

    private val DOCUMENT_ID_KEYWORDS = listOf(
        "passport", "driving licence", "driving license", "license", "aadhaar",
        "pan card", "identity card", "identity", "government of", "agreement",
        "declaration", "form", "application", "resume", "curriculum vitae",
        "affidavit", "republic of", "election commission", "voter id", "national id",
        "birth certificate", "marksheet", "transcript", "statement", "ministry of",
        "signature", "valid thru", "permanent account number"
    )

    private val NOTES_KEYWORDS = listOf(
        "note", "notes", "whiteboard", "blackboard", "handwriting", "bullet",
        "chapter", "lecture", "homework", "assignment", "summary", "formula",
        "theorem", "diagram", "exercise", "question", "answer", "syllabus"
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
     * Classifies a media item based on multi-signal analysis:
     * - File/Folder heuristics (screenshots, selfies, bucket source)
     * - Detected Barcodes / QR codes
     * - Face detection counts and frame area occupancy
     * - ML Kit Image Labeling (pets, food, vehicles, landscapes, cartoons)
     * - ML Kit Text Recognition (OCR keywords, structured text, dense document shapes)
     */
    fun classify(
        item: MediaItem,
        ocrText: String? = null,
        labels: List<Pair<String, Float>> = emptyList(),
        faceCount: Int = 0,
        faceAreaFraction: Float = 0f,
        hasBarcodes: Boolean = false
    ): String? {
        val lowerPath = item.absolutePath.lowercase(Locale.ROOT)
        val lowerBucket = item.bucketName.lowercase(Locale.ROOT)
        val lowerName = item.displayName.lowercase(Locale.ROOT)
        val text = (ocrText ?: item.ocrText)?.lowercase(Locale.ROOT)
        val width = item.width
        val height = item.height
        val aspectRatio = if (width > 0 && height > 0) height.toFloat() / width.toFloat() else 1.0f

        // 1. Screenshots check via directory path, filename, or screen aspect ratio with UI text
        if (lowerPath.contains("screenshot") || lowerBucket.contains("screenshot") || lowerName.contains("screenshot")) {
            return CATEGORY_SCREENSHOTS
        }
        if (aspectRatio in 1.7f..2.4f || aspectRatio in 0.41f..0.59f) {
            if (text != null && (text.contains("am") || text.contains("pm") || text.contains("wifi") ||
                        text.contains("battery") || text.contains("lte") || text.contains("5g") ||
                        text.contains("settings") || text.contains("search"))) {
                if (lowerBucket in SOCIAL_BUCKETS || lowerPath.contains("pictures") || lowerPath.contains("download")) {
                    return CATEGORY_SCREENSHOTS
                }
            }
        }

        // 2. Barcodes / QR code check
        if (hasBarcodes || lowerName.contains("qrcode") || lowerName.contains("barcode") || lowerBucket.contains("qr")) {
            return CATEGORY_QR_BARCODES
        }

        // 3. Selfies & Portraits check (1-2 faces taking up >15% of frame, or selfie path)
        if (faceCount in 1..2 && faceAreaFraction >= 0.15f) {
            return CATEGORY_SELFIES_PORTRAITS
        }
        if (lowerPath.contains("selfie") || lowerBucket.contains("selfie") || lowerName.contains("selfie")) {
            return CATEGORY_SELFIES_PORTRAITS
        }

        // 4. ML Kit Image Labeling (high-confidence visual scene tags)
        val highConfidenceLabels = labels.filter { it.second >= 0.65f }.map { it.first.lowercase(Locale.ROOT) }
        
        // 4a. Pets & Animals
        if (highConfidenceLabels.any { lbl -> PETS_LABELS.any { pet -> lbl.contains(pet) } }) {
            return CATEGORY_PETS_ANIMALS
        }

        // 4b. Food & Dining
        if (highConfidenceLabels.any { lbl -> FOOD_LABELS.any { food -> lbl.contains(food) } }) {
            return CATEGORY_FOOD_DINING
        }

        // 4c. Travel & Vehicles
        if (highConfidenceLabels.any { lbl -> TRAVEL_LABELS.any { travel -> lbl.contains(travel) } }) {
            return CATEGORY_TRAVEL_VEHICLES
        }

        // 5. Text / OCR analysis (Documents, Bills, Certificates, Brochures, Notes)
        if (!text.isNullOrBlank()) {
            // 5a. Certificates & Awards
            var certMatches = 0
            for (kw in CERTIFICATE_KEYWORDS) {
                if (text.contains(kw)) {
                    certMatches++
                    if (certMatches >= 2 || kw == "this is to certify" || kw == "certificate") {
                        return CATEGORY_CERTIFICATES
                    }
                }
            }

            // 5b. Bills & Receipts (Keywords match or tall receipt aspect ratio with pricing keywords)
            var billMatches = 0
            for (kw in BILLS_KEYWORDS) {
                if (text.contains(kw)) {
                    billMatches++
                }
            }
            if (billMatches >= 2 || (aspectRatio > 1.75f && (text.contains("total") || text.contains("amount") || text.contains("bill") || text.contains("paid") || text.contains("inr") || text.contains("rs.")))) {
                return CATEGORY_BILLS_RECEIPTS
            }

            // 5c. Documents & IDs
            var docMatches = 0
            for (kw in DOCUMENT_ID_KEYWORDS) {
                if (text.contains(kw)) {
                    docMatches++
                    if (kw == "aadhaar" || kw == "passport" || kw == "pan card" || kw == "driving license" || kw == "driving licence") {
                        return CATEGORY_DOCUMENTS_IDS
                    }
                }
            }
            if (docMatches >= 2) {
                return CATEGORY_DOCUMENTS_IDS
            }

            // 5d. Brochures & Flyers
            var flyerMatches = 0
            for (kw in BROCHURE_KEYWORDS) {
                if (text.contains(kw)) {
                    flyerMatches++
                }
            }
            if (flyerMatches >= 2) {
                return CATEGORY_BROCHURES_FLYERS
            }

            // 5e. Notes & Whiteboards
            var noteMatches = 0
            for (kw in NOTES_KEYWORDS) {
                if (text.contains(kw)) {
                    noteMatches++
                }
            }
            if (noteMatches >= 2 || lowerBucket.contains("note") || lowerName.contains("note")) {
                return CATEGORY_NOTES_WHITEBOARDS
            }

            // 5f. General Document fallback if dense structured text detected
            if (text.length > 120 && (aspectRatio in 1.2f..1.65f || aspectRatio in 0.60f..0.85f)) {
                return CATEGORY_DOCUMENTS_IDS
            }
        }

        // 6. Memes & Funny Social media
        if (highConfidenceLabels.any { lbl -> MEME_LABELS.any { meme -> lbl.contains(meme) } } ||
            (SOCIAL_BUCKETS.any { lowerBucket.contains(it) } && text != null && text.length in 5..80)) {
            return CATEGORY_MEMES_SOCIAL
        }

        // 7. Path heuristics fallback for receipts if aspect ratio matches
        if (aspectRatio > 2.0f && (lowerBucket.contains("bill") || lowerName.contains("bill") || lowerName.contains("receipt"))) {
            return CATEGORY_BILLS_RECEIPTS
        }

        return null
    }
}
