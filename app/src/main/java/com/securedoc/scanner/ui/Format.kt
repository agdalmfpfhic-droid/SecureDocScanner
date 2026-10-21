package com.securedoc.scanner.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatDate(millis: Long): String = SimpleDateFormat("d MMM yyyy", Locale.FRANCE).format(Date(millis))

fun pagesLabel(count: Int) = if (count <= 1) "$count page" else "$count pages"
