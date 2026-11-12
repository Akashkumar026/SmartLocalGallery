package com.example.smartgallery.engine

import com.example.smartgallery.data.db.MediaItem
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SmartCategoryClassifierTest {

    private fun createItem(
        path: String = "/storage/emulated/0/DCIM/test.jpg",
        displayName: String = "test.jpg",
        bucketName: String = "DCIM",
        width: Int = 1080,
        height: Int = 1920,
        ocrText: String? = null
    ): MediaItem {
        return MediaItem(
            mediaStoreId = 1001L,
            displayName = displayName,
            absolutePath = path,
            mimeType = "image/jpeg",
            size = 1024L,
            dateAdded = System.currentTimeMillis() / 1000L,
            dateModified = System.currentTimeMillis() / 1000L,
            dateTaken = System.currentTimeMillis(),
            width = width,
            height = height,
            mediaType = "IMAGE",
            bucketName = bucketName,
            ocrText = ocrText
        )
    }

    @Test
    fun testIndianPiiMasking_aadhaar() {
        val sample = "Applicant Aadhaar Number: 2345 6789 0123 for verification."
        val (masked, hasSensitive) = SmartCategoryClassifier.sanitizePii(sample)
        assertThat(hasSensitive).isTrue()
        assertThat(masked).isNotNull()
        assertThat(masked).contains("XXXX-XXXX-0123")
        assertThat(masked).doesNotContain("2345 6789")
    }

    @Test
    fun testIndianPiiMasking_pan() {
        val sample = "Permanent Account Number: ABCDE1234F issued by Income Tax Dept."
        val (masked, hasSensitive) = SmartCategoryClassifier.sanitizePii(sample)
        assertThat(hasSensitive).isTrue()
        assertThat(masked).isNotNull()
        assertThat(masked).contains("XXXXX1234X")
        assertThat(masked).doesNotContain("ABCDE")
    }

    @Test
    fun testIndianPiiMasking_passport() {
        val sample = "Republic of India Passport No: Z1234567 valid until 2030."
        val (masked, hasSensitive) = SmartCategoryClassifier.sanitizePii(sample)
        assertThat(hasSensitive).isTrue()
        assertThat(masked).isNotNull()
        assertThat(masked).contains("X****567")
        assertThat(masked).doesNotContain("Z1234")
    }

    @Test
    fun testIndianPiiMasking_voterId() {
        val sample = "Election Commission of India EPIC NO: WXY1234567"
        val (masked, hasSensitive) = SmartCategoryClassifier.sanitizePii(sample)
        assertThat(hasSensitive).isTrue()
        assertThat(masked).isNotNull()
        assertThat(masked).contains("XXX****567")
        assertThat(masked).doesNotContain("WXY1234")
    }

    @Test
    fun testClassification_billsAndReceipts_english() {
        val item = createItem(
            path = "/storage/emulated/0/Download/order_summary.jpg",
            displayName = "order_summary.jpg",
            bucketName = "Download"
        )
        val ocr = "INVOICE #98765 Total Amount: Rs 1500 GST: 18% Paid via UPI"
        val result = SmartCategoryClassifier.classifyMultiSignal(
            item = item,
            ocrText = ocr
        )
        assertThat(result.primaryCategory).isEqualTo(SmartCategoryClassifier.CATEGORY_BILLS_RECEIPTS)
        assertThat(result.confidence).isAtLeast(0.6f)
    }

    @Test
    fun testClassification_billsAndReceipts_hindi() {
        val item = createItem(
            path = "/storage/emulated/0/Pictures/receipt.jpg",
            displayName = "receipt.jpg",
            bucketName = "Pictures"
        )
        val ocr = "दुकान की रसीद कुल भुगतान 500 रुपये चालान"
        val result = SmartCategoryClassifier.classifyMultiSignal(
            item = item,
            ocrText = ocr
        )
        assertThat(result.primaryCategory).isEqualTo(SmartCategoryClassifier.CATEGORY_BILLS_RECEIPTS)
    }

    @Test
    fun testClassification_certificates_hindi() {
        val item = createItem(
            path = "/storage/emulated/0/Documents/award.jpg",
            displayName = "award.jpg",
            bucketName = "Documents",
            width = 1920,
            height = 1080
        )
        val ocr = "प्रमाणपत्र दिया जाता है विश्वविद्यालय द्वारा उत्तीर्ण degree certificate"
        val result = SmartCategoryClassifier.classifyMultiSignal(
            item = item,
            ocrText = ocr
        )
        assertThat(result.primaryCategory).isEqualTo(SmartCategoryClassifier.CATEGORY_CERTIFICATES)
    }

    @Test
    fun testClassification_qrBarcodes() {
        val item = createItem(
            path = "/storage/emulated/0/DCIM/payment_qr.png",
            displayName = "payment_qr.png"
        )
        val result = SmartCategoryClassifier.classifyMultiSignal(
            item = item,
            hasBarcodes = true
        )
        assertThat(result.primaryCategory).isEqualTo(SmartCategoryClassifier.CATEGORY_QR_BARCODES)
    }

    @Test
    fun testClassification_screenshots() {
        val item = createItem(
            path = "/storage/emulated/0/Pictures/Screenshots/Screenshot_20261112.png",
            displayName = "Screenshot_20261112.png",
            bucketName = "Screenshots",
            width = 1080,
            height = 2400
        )
        val ocr = "10:45 AM LTE 85% Battery Settings"
        val result = SmartCategoryClassifier.classifyMultiSignal(
            item = item,
            ocrText = ocr
        )
        assertThat(result.primaryCategory).isEqualTo(SmartCategoryClassifier.CATEGORY_SCREENSHOTS)
    }

    @Test
    fun testClassification_selfiePortrait() {
        val item = createItem(
            path = "/storage/emulated/0/DCIM/Camera/IMG_selfie.jpg",
            displayName = "IMG_selfie.jpg",
            bucketName = "Camera",
            width = 1080,
            height = 1440
        )
        val result = SmartCategoryClassifier.classifyMultiSignal(
            item = item,
            faceCount = 1,
            faceAreaFraction = 0.28f
        )
        assertThat(result.primaryCategory).isEqualTo(SmartCategoryClassifier.CATEGORY_SELFIES_PORTRAITS)
    }

    @Test
    fun testClassification_foodDining() {
        val item = createItem(
            path = "/storage/emulated/0/Pictures/dinner.jpg",
            displayName = "dinner.jpg",
            bucketName = "Pictures"
        )
        val result = SmartCategoryClassifier.classifyMultiSignal(
            item = item,
            labels = listOf("Food" to 0.90f, "Dish" to 0.85f)
        )
        assertThat(result.primaryCategory).isEqualTo(SmartCategoryClassifier.CATEGORY_FOOD_DINING)
    }

    @Test
    fun testClassification_travelVehicles() {
        val item = createItem(
            path = "/storage/emulated/0/DCIM/Camera/mountain_trip.jpg",
            displayName = "mountain_trip.jpg",
            bucketName = "Camera"
        )
        val result = SmartCategoryClassifier.classifyMultiSignal(
            item = item,
            labels = listOf("Mountain" to 0.92f, "Vehicle" to 0.85f)
        )
        assertThat(result.primaryCategory).isEqualTo(SmartCategoryClassifier.CATEGORY_TRAVEL_VEHICLES)
    }

    @Test
    fun testClassification_petsAnimals() {
        val item = createItem(
            path = "/storage/emulated/0/DCIM/Camera/pet.jpg",
            displayName = "pet.jpg",
            bucketName = "Camera"
        )
        val result = SmartCategoryClassifier.classifyMultiSignal(
            item = item,
            labels = listOf("Dog" to 0.95f, "Puppy" to 0.89f)
        )
        assertThat(result.primaryCategory).isEqualTo(SmartCategoryClassifier.CATEGORY_PETS_ANIMALS)
    }
}
