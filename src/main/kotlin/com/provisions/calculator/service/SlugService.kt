package com.provisions.calculator.service

import com.provisions.calculator.repository.TenantRepository
import org.springframework.stereotype.Service
import java.text.Normalizer

@Service
class SlugService(private val tenantRepository: TenantRepository) {

    fun slugify(displayName: String): String {
        val base = Normalizer.normalize(displayName, Normalizer.Form.NFD)
            .replace(Regex("[\\p{InCombiningDiacriticalMarks}]"), "")
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .ifEmpty { "user" }

        if (!tenantRepository.existsById(base)) return base

        repeat(10) {
            val suffix = (1000..9999).random().toString()
            val candidate = "$base-$suffix"
            if (!tenantRepository.existsById(candidate)) return candidate
        }

        return "$base-${System.currentTimeMillis()}"
    }
}
