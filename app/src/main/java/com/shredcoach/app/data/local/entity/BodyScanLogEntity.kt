package com.shredcoach.app.data.local.entity

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Historique des scans corporels — UNE ligne par génération de mesh.
 *
 * **Pourquoi une table dédiée** :
 *  - L'utilisateur veut suivre son évolution (V-Taper, Posture, Body Fat)
 *    dans le temps. UserProfileEntity ne stocke QUE le dernier scan, donc
 *    sans historique on ne peut pas tracer de courbes de progression.
 *  - On stocke un **snapshot** des analytics au moment du scan (vs des refs
 *    vers MeshFeatures fichier qui peuvent être supprimés). Garantit qu'on
 *    peut tracer l'historique même si tous les fichiers JSON sont nettoyés.
 *  - [featuresPath] reste optionnel : si présent + fichier existant, on peut
 *    re-charger les keypoints pour ré-afficher le mesh complet ; sinon on
 *    affiche juste les analytics chiffrées.
 *
 * **Granularité storage** : ~50 octets DB par row + ~10 KB JSON sur disque
 * (features). Pour 100 scans (>2 ans à 1/sem) : 5 KB DB + 1 MB JSON. Acceptable.
 *
 * **Politique rétention** : pas de cleanup automatique en V1 — l'utilisateur
 * peut supprimer manuellement depuis l'historique. Si la croissance devient
 * problématique on ajoutera un worker de purge (>2 ans / >100 scans).
 *
 * **Index `capturedAtMs`** : tous les queries sont `ORDER BY capturedAtMs
 * DESC`, soit pour le Dashboard (timeline graph) soit pour le screen
 * historique. L'index évite les sorts complets sur grosses tables.
 */
@Entity(
    tableName = "body_scan_logs",
    indices = [Index("capturedAtMs")],
)
@Immutable
data class BodyScanLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Epoch ms du scan — `MeshFeatures.capturedAtMs` au moment de la génération. */
    val capturedAtMs: Long,
    /**
     * Path absolu du `mesh_<ts>.json` dans `filesDir/body_scans/`. Null si
     * fichier supprimé (cleanup manuel ou system). L'historique reste
     * exploitable pour les analytics chiffrées via les colonnes ci-dessous.
     */
    val featuresPath: String? = null,
    /** Path absolu de la photo originale. Idem null = fichier disparu. */
    val photoPath: String? = null,
    // ─── Snapshot analytics au moment du scan ───
    val postureScore: Int,
    val vTaperRatio: Float,
    val shoulderTiltDeg: Float,
    val hipTiltDeg: Float,
    val shoulderAsymmetryPct: Float,
    val hipAsymmetryPct: Float,
    // ─── Snapshot mesures profil au moment du scan (pour comparer le contexte) ───
    val heightCm: Int,
    val weightKg: Double,
    val bodyFatPercent: Double,
)
