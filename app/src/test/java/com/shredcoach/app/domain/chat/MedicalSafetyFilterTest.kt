package com.shredcoach.app.domain.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests sur [MedicalSafetyFilter.isMedicalCritical].
 *
 * Le filtre est volontairement keyword-based (déterministe, ~1µs total).
 * On veut ZÉRO faux négatif sur les keywords d'urgence vitale, quitte à
 * accepter des faux positifs (la nature même de "safety-first").
 */
class MedicalSafetyFilterTest {

    // ─── Vrais positifs FR ───────────────────────────────

    @Test fun `douleur thoracique détecté`() {
        assertTrue(MedicalSafetyFilter.isMedicalCritical("J'ai une douleur thoracique depuis ce matin"))
    }

    @Test fun `mal à la poitrine détecté`() {
        assertTrue(MedicalSafetyFilter.isMedicalCritical("J'ai mal à la poitrine après l'entraînement"))
    }

    @Test fun `essoufflement détecté`() {
        assertTrue(MedicalSafetyFilter.isMedicalCritical("Bizarre, j'ai un essoufflement même au repos"))
    }

    @Test fun `palpitations détecté`() {
        assertTrue(MedicalSafetyFilter.isMedicalCritical("Mes palpitations ne s'arrêtent pas"))
    }

    @Test fun `paralysie détecté`() {
        assertTrue(MedicalSafetyFilter.isMedicalCritical("J'ai une paralysie partielle du bras gauche"))
    }

    // ─── Vrais positifs EN ───────────────────────────────

    @Test fun `chest pain détecté`() {
        assertTrue(MedicalSafetyFilter.isMedicalCritical("I have chest pain that won't stop"))
    }

    @Test fun `shortness of breath détecté`() {
        assertTrue(MedicalSafetyFilter.isMedicalCritical("Sudden shortness of breath since yesterday"))
    }

    @Test fun `cant breathe variantes détecté`() {
        assertTrue(MedicalSafetyFilter.isMedicalCritical("I cant breathe properly anymore"))
        assertTrue(MedicalSafetyFilter.isMedicalCritical("I can't breathe well today"))
    }

    @Test fun `vomiting blood détecté`() {
        assertTrue(MedicalSafetyFilter.isMedicalCritical("I'm vomiting blood, should I worry?"))
    }

    @Test fun `slurred speech détecté`() {
        assertTrue(MedicalSafetyFilter.isMedicalCritical("My friend has slurred speech right now"))
    }

    // ─── Casse insensible ────────────────────────────────

    @Test fun `casse mixte ne masque pas`() {
        assertTrue(MedicalSafetyFilter.isMedicalCritical("CHEST PAIN since this morning"))
        assertTrue(MedicalSafetyFilter.isMedicalCritical("Douleur Thoracique aigüe"))
    }

    // ─── Vrais négatifs (questions coaching normales) ────

    @Test fun `question fitness normale pas critique`() {
        assertFalse(MedicalSafetyFilter.isMedicalCritical("Donne-moi un conseil pour mes ischios"))
    }

    @Test fun `entraînement normal pas critique`() {
        assertFalse(MedicalSafetyFilter.isMedicalCritical("J'ai fait 4x10 développé couché à 60kg"))
    }

    @Test fun `nutrition normale pas critique`() {
        assertFalse(MedicalSafetyFilter.isMedicalCritical("Combien de protéines par jour en sèche ?"))
    }

    @Test fun `vide pas critique`() {
        assertFalse(MedicalSafetyFilter.isMedicalCritical(""))
        assertFalse(MedicalSafetyFilter.isMedicalCritical("   "))
    }

    // ─── Faux positif documenté ──────────────────────────

    @Test fun `negation déclenche quand même - documenté`() {
        // Limitation connue : pas de NLU sémantique. Une négation est traitée
        // comme un positif. Acceptable car safety-first (false positive
        // déclenche juste un disclaimer, pas une refus).
        assertTrue(MedicalSafetyFilter.isMedicalCritical("Je n'ai PAS de douleur thoracique"))
    }
}
