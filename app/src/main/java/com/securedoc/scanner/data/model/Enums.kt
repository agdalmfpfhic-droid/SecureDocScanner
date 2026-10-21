package com.securedoc.scanner.data.model

/**
 * Profils de scan. Les dimensions (mm) fixent le format de page du PDF ;
 * 0 = dimension variable, déduite du ratio de l'image.
 */
enum class ScanProfile(
    val label: String,
    val widthMm: Float,
    val heightMm: Float,
    val defaultFilter: PageFilter,
    val defaultCategory: String,
    val secureKind: SecureKind?,
    /** Nombre maximal de pages par scan (null = illimité). */
    val pageLimit: Int?,
) {
    A4("Document A4", 210f, 297f, PageFilter.COLOR, "Administratif", null, null),
    A5("Document A5", 148f, 210f, PageFilter.COLOR, "Administratif", null, null),
    RECEIPT("Reçu ou facture", 80f, 0f, PageFilter.RECEIPT, "Administratif", null, null),
    PASSPORT("Passeport", 125f, 88f, PageFilter.COLOR, "Identité", SecureKind.PASSPORT, 2),
    NATIONAL_ID("Carte nationale d'identité", 85.6f, 53.98f, PageFilter.COLOR, "Identité", SecureKind.NATIONAL_ID, 2),
    DRIVING_LICENSE("Permis de conduire", 85.6f, 53.98f, PageFilter.COLOR, "Identité", SecureKind.DRIVING_LICENSE, 2),
    BANK_CARD("Carte bancaire", 85.6f, 53.98f, PageFilter.COLOR, "Banque", SecureKind.BANK_CARD, 2),
    CONTRACT("Contrat", 210f, 297f, PageFilter.SHARP_TEXT, "Travail", null, null),
    HANDWRITTEN("Note manuscrite", 210f, 297f, PageFilter.GRAYSCALE, "Autres", null, null),
    BOOK("Livre ou manuel", 0f, 0f, PageFilter.SHARP_TEXT, "Autres", null, null);

    val formatText: String
        get() = when {
            widthMm == 0f -> "Format libre"
            heightMm == 0f -> "Largeur ${mm(widthMm)} mm"
            else -> "${mm(widthMm)} × ${mm(heightMm)} mm"
        }

    val pagesText: String get() = pageLimit?.let { "Recto / verso" } ?: "Pages illimitées"

    private fun mm(v: Float) = if (v % 1f == 0f) v.toInt().toString() else "%.1f".format(v).replace('.', ',')
}

enum class PageFilter(val label: String) {
    ORIGINAL("Original"),
    COLOR("Couleur"),
    GRAYSCALE("Niveaux de gris"),
    BLACK_WHITE("Noir et blanc"),
    SHARP_TEXT("Texte net"),
    RECEIPT("Mode reçu"),
}

enum class SecureKind(val label: String) {
    NATIONAL_ID("CIN"),
    PASSPORT("Passeport"),
    BANK_CARD("Carte bancaire"),
    DRIVING_LICENSE("Permis de conduire"),
    OTHER("Document sensible"),
}

enum class ExportFormat(val label: String, val mime: String, val extension: String) {
    PDF("PDF", "application/pdf", "pdf"),
    JPEG("JPEG", "image/jpeg", "jpg"),
    PNG("PNG", "image/png", "png"),
}

enum class ThemeMode(val label: String) { SYSTEM("Système"), LIGHT("Clair"), DARK("Sombre") }

enum class ImageQuality(val label: String, val jpeg: Int) {
    STANDARD("Standard", 75), HIGH("Élevée", 85), MAX("Maximale", 95)
}
