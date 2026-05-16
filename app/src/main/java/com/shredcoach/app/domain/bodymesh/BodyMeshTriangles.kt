package com.shredcoach.app.domain.bodymesh

import kotlin.math.max
import kotlin.math.sqrt

/**
 * Pipeline de génération du mesh polygonal triangulé du corps.
 *
 * **Stratégie d'échantillonnage** (assemble un nuage de points "anatomiquement
 * cohérent" avant triangulation) :
 *  1. **Contour silhouette** (échantillonné via [contourSampleStride]) — la
 *     forme externe du corps.
 *  2. **Keypoints ML Kit** (filtrés par confidence) — les articulations
 *     anatomiques.
 *  3. **Bone subdivisions** ([boneSubdivisions] points interpolés le long de
 *     chaque connexion POSE_CONNECTIONS) — résolution sur les membres pour
 *     que les triangles "suivent" les bras/jambes.
 *  4. **Grille intérieure** (pas [interiorGridStep], filtrée par
 *     [pointInPolygon] sur le contour) — densifie le remplissage du tronc et
 *     évite les triangles géants à l'intérieur du corps.
 *
 * **Filtrage post-triangulation** : la triangulation Delaunay produit l'enveloppe
 * convexe du nuage. Les régions concaves du corps (entre les jambes, sous les
 * bras) reçoivent donc des triangles "qui passent à travers le vide" — on les
 * retire en testant si leur centroïde est dans la silhouette.
 *
 * **Densité visée** : ~250-450 points → ~500-900 triangles. Trade-off :
 *  - Trop peu : mesh anguleux, "robot des années 90".
 *  - Trop : Bowyer-Watson est O(N^1.5), >600 points = >50ms (lag perceptible).
 *  - Surtout : au-delà de ~1000 triangles, le mesh devient un blob noir à
 *    l'oeil, plus de structure visible.
 *
 * **z-coords (pour rendu 3D #17)** : les keypoints + bone subdivisions portent
 * leur z natif ML Kit. Le contour et la grille n'en ont pas — on les estime
 * par interpolation IDW (Inverse Distance Weighting) depuis les keypoints
 * environnants. Donne un effet "drapé" cohérent. Sur scans v1 où aucun
 * landmark n'a de z, tout reste à 0f → mesh 2D plat (rétro-compatibilité).
 */
object BodyMeshTriangles {

    /**
     * Triangle du body mesh. Coords en pixels image source (mêmes unités que
     * [MeshFeatures.silhouetteContour] et [Landmark.x]/[Landmark.y]).
     *
     * z relatif aux keypoints ML Kit : 0f ≈ plan des hanches, négatif = devant,
     * positif = derrière. Sur scans 2D, tous les z sont 0f.
     */
    data class BodyTriangle(
        val ax: Float, val ay: Float, val az: Float,
        val bx: Float, val by: Float, val bz: Float,
        val cx: Float, val cy: Float, val cz: Float,
    ) {
        /** Profondeur moyenne — utilisée pour z-sort en rendu 3D. */
        val avgZ: Float get() = (az + bz + cz) / 3f

        /** Centroïde 2D — utile pour shading depth-fade ou debug. */
        val centroidX: Float get() = (ax + bx + cx) / 3f
        val centroidY: Float get() = (ay + by + cy) / 3f
    }

    /** Confidence minimum pour qu'un keypoint participe au mesh. */
    private const val MIN_CONFIDENCE_FOR_MESH = 0.4f

    /**
     * Mesh volumétrique = 2 surfaces (front + back) inflatées par
     * distance-transform sur la silhouette. Donne un VRAI volume 3D : à
     * yaw=0 on voit la face, à yaw=90 le profil avec un effet "lens" vertical
     * (épaisseur max au centre du corps), à yaw=180 le dos.
     *
     * **Pourquoi cette approche** : ML Kit z est imprécise (~10-20% d'erreur)
     * et les landmarks d'une photo de face sont quasi-coplanaires (le corps
     * varie peu en profondeur dans le repère caméra). Résultat : un mesh basé
     * uniquement sur les z ML Kit est plat. Le distance-transform offre du
     * volume cohérent avec la silhouette détectée — anatomiquement plausible
     * sans modèle paramétrique (SMPL = 40MB asset, overkill).
     *
     * **Trade-off** : la profondeur est symétrique (front = -back), donc le
     * dos ressemble à la face miroir. Acceptable pour un body scan : l'utilisateur
     * a uploadé une photo de face, on ne peut pas inventer le dos. La rotation
     * révèle que c'est un volume cohérent, pas un modèle anatomique exact.
     */
    data class VolumetricBody(
        val frontTriangles: List<BodyTriangle>,
        val backTriangles: List<BodyTriangle>,
    ) {
        /** Concaténation pour z-sort + render unifié. */
        val all: List<BodyTriangle> get() = frontTriangles + backTriangles
    }

    /**
     * Construit un mesh volumétrique du corps via distance-transform sur la
     * silhouette. Pour chaque point intérieur, l'épaisseur (Z) est proportionnelle
     * à la distance au contour le plus proche : centre du tronc = épais,
     * bords = fin, exactement comme un corps réel.
     *
     * @param inflationFactor multiplicateur Z = distance × factor. 0.7-1.0 typique.
     *   Plus haut = corps plus "potelé". 0.85 par défaut donne un torse ~80%
     *   aussi épais que large = anatomiquement plausible.
     * @param contourSampleStride pas d'échantillonnage du contour (couture
     *   front/back).
     * @param interiorGridStep pas grille intérieure. Plus dense = mesh plus
     *   smooth quand on tourne.
     * @param boneSubdivisions points interpolés le long des bones (densifie
     *   les membres pour avoir du volume aux bras/jambes même fins).
     */
    fun buildVolumetric(
        features: MeshFeatures,
        inflationFactor: Float = 0.85f,
        contourSampleStride: Int = 2,
        interiorGridStep: Float = 50f,
        boneSubdivisions: Int = 2,
    ): VolumetricBody {
        val contour = features.silhouetteContour
        if (contour.size < 3) return VolumetricBody(emptyList(), emptyList())

        // Listes parallèles : (x, y) + (zFront, zBack). Le contour est à z=0
        // (couture où front meets back). Les points intérieurs ont
        // zFront = -d * inflation et zBack = +d * inflation (convention
        // ML Kit : z négatif = devant la caméra).
        val xs = mutableListOf<Float>()
        val ys = mutableListOf<Float>()
        val zFront = mutableListOf<Float>()
        val zBack = mutableListOf<Float>()

        // ─── 1a. Contour silhouette (z = 0, couture front/back) ───
        val stride = max(1, contourSampleStride)
        var i = 0
        while (i < contour.size) {
            xs += contour[i].x
            ys += contour[i].y
            zFront += 0f
            zBack += 0f
            i += stride
        }

        // ─── 1b. Bone subdivisions + endpoints ───
        // On inclut les endpoints (k=0 et k=N+1) pour garantir que les
        // articulations sont dans le mesh — sinon les bras peuvent paraître
        // détachés du tronc dans le mesh volumétrique.
        val byType = features.landmarks.associateBy { it.type }
        for ((from, to) in POSE_CONNECTIONS) {
            val a = byType[from.ordinal] ?: continue
            val b = byType[to.ordinal] ?: continue
            if (a.inFrameLikelihood < MIN_CONFIDENCE_FOR_MESH ||
                b.inFrameLikelihood < MIN_CONFIDENCE_FOR_MESH) continue
            for (k in 0..(boneSubdivisions + 1)) {
                val t = k.toFloat() / (boneSubdivisions + 1)
                val px = a.x + (b.x - a.x) * t
                val py = a.y + (b.y - a.y) * t
                // Skip si en dehors de la silhouette (rare : poignet hors champ
                // de la segmentation, contour qui coupe au-dessus du genou…).
                if (!pointInPolygon(px, py, contour)) continue
                val d = distanceToContour(px, py, contour)
                xs += px
                ys += py
                zFront += -d * inflationFactor
                zBack += d * inflationFactor
            }
        }

        // ─── 1c. Grille intérieure ───
        if (interiorGridStep > 0f) {
            var minX = Float.POSITIVE_INFINITY
            var minY = Float.POSITIVE_INFINITY
            var maxX = Float.NEGATIVE_INFINITY
            var maxY = Float.NEGATIVE_INFINITY
            for (p in contour) {
                if (p.x < minX) minX = p.x
                if (p.y < minY) minY = p.y
                if (p.x > maxX) maxX = p.x
                if (p.y > maxY) maxY = p.y
            }
            var gy = minY + interiorGridStep / 2f
            while (gy < maxY) {
                var gx = minX + interiorGridStep / 2f
                while (gx < maxX) {
                    if (pointInPolygon(gx, gy, contour)) {
                        val d = distanceToContour(gx, gy, contour)
                        xs += gx
                        ys += gy
                        zFront += -d * inflationFactor
                        zBack += d * inflationFactor
                    }
                    gx += interiorGridStep
                }
                gy += interiorGridStep
            }
        }

        // ─── 2. Triangulation Delaunay 2D (XY plan) ───
        // On triangule UNE FOIS la projection 2D — la même connectivité
        // s'applique aux deux surfaces front/back. Économise un Delaunay.
        val vertices = ArrayList<Triangulation.Vertex>(xs.size)
        for (k in xs.indices) {
            vertices += Triangulation.Vertex(xs[k], ys[k], k)
        }
        val raw = Triangulation.triangulate(vertices)

        // ─── 3. Filtrage centroïde-in-silhouette + génération front/back ───
        val front = ArrayList<BodyTriangle>(raw.size)
        val back = ArrayList<BodyTriangle>(raw.size)
        for (t in raw) {
            val ax = xs[t.a]; val ay = ys[t.a]
            val bx = xs[t.b]; val by = ys[t.b]
            val cx = xs[t.c]; val cy = ys[t.c]
            val ccx = (ax + bx + cx) / 3f
            val ccy = (ay + by + cy) / 3f
            if (!pointInPolygon(ccx, ccy, contour)) continue
            front += BodyTriangle(
                ax, ay, zFront[t.a],
                bx, by, zFront[t.b],
                cx, cy, zFront[t.c],
            )
            back += BodyTriangle(
                ax, ay, zBack[t.a],
                bx, by, zBack[t.b],
                cx, cy, zBack[t.c],
            )
        }
        return VolumetricBody(front, back)
    }

    /**
     * Distance euclidienne du point [(px, py)] au point le plus proche du
     * [contour]. Brute force O(N) — N est typiquement 150-300 points donc
     * ~3-6μs par appel. Pour 250 points intérieurs × 250 contour points
     * = 62k ops = ~5-10ms total. Acceptable pour un calcul one-shot.
     *
     * Note : on calcule distance au plus proche VERTEX du contour, pas au
     * plus proche SEGMENT. Le contour est dense (5-10px entre vertices),
     * donc l'erreur est bornée par la moitié du segment ≈ 3-5px = ok pour
     * le rendu visuel. Une distance-au-segment serait plus précise mais
     * coûte 4x plus cher.
     */
    private fun distanceToContour(px: Float, py: Float, contour: List<Point>): Float {
        var minD2 = Float.POSITIVE_INFINITY
        for (p in contour) {
            val dx = px - p.x
            val dy = py - p.y
            val d2 = dx * dx + dy * dy
            if (d2 < minD2) minD2 = d2
        }
        return sqrt(minD2)
    }

    /**
     * Construit le mesh polygonal du corps à partir des features extraites.
     *
     * @param contourSampleStride pas d'échantillonnage du contour (1 = tous les
     *  points, 2 = un sur deux). Le contour de [MeshFeatures] est déjà simplifié
     *  à ~150-300 points, donc 2-3 donne un bon équilibre densité/lisibilité.
     * @param interiorGridStep pas de la grille intérieure en pixels. 40-80 typique.
     *  Plus petit = plus dense = plus de triangles. Mettre 0f pour désactiver.
     * @param boneSubdivisions nombre de points interpolés le long de chaque bone
     *  (en plus des extrémités). 2 = découpe en 3 segments. Aide à ce que les
     *  triangles épousent les membres au lieu de couper en travers.
     */
    fun build(
        features: MeshFeatures,
        contourSampleStride: Int = 2,
        interiorGridStep: Float = 60f,
        boneSubdivisions: Int = 2,
    ): List<BodyTriangle> {
        val contour = features.silhouetteContour
        if (contour.size < 3) return emptyList()

        // Listes parallèles pour les coords + flag indiquant si le point est
        // un "vrai" keypoint (z natif ML Kit) ou un point estimé. Le flag sert
        // à skipper l'IDW sur les points dont le z est déjà fiable.
        val xs = mutableListOf<Float>()
        val ys = mutableListOf<Float>()
        val zs = mutableListOf<Float>()
        val zIsNative = mutableListOf<Boolean>()

        // ─── 1a. Contour silhouette (échantillonné) ───
        val stride = max(1, contourSampleStride)
        var i = 0
        while (i < contour.size) {
            xs += contour[i].x
            ys += contour[i].y
            zs += 0f
            zIsNative += false
            i += stride
        }

        // ─── 1b. Keypoints (filtre confidence) ───
        val keypoints = features.landmarks.filter {
            it.inFrameLikelihood >= MIN_CONFIDENCE_FOR_MESH
        }
        for (lm in keypoints) {
            xs += lm.x
            ys += lm.y
            zs += lm.z
            zIsNative += true
        }

        // ─── 1c. Bone subdivisions (interpolation linéaire le long des bones) ───
        // On ajoute des points intermédiaires sur chaque bone visible : ça
        // garantit que les triangles "suivent" les bras/jambes au lieu de
        // sauter de l'épaule au poignet en une arête géante.
        val byType = features.landmarks.associateBy { it.type }
        for ((from, to) in POSE_CONNECTIONS) {
            val a = byType[from.ordinal] ?: continue
            val b = byType[to.ordinal] ?: continue
            if (a.inFrameLikelihood < MIN_CONFIDENCE_FOR_MESH ||
                b.inFrameLikelihood < MIN_CONFIDENCE_FOR_MESH) continue
            for (k in 1..boneSubdivisions) {
                val t = k.toFloat() / (boneSubdivisions + 1)
                xs += a.x + (b.x - a.x) * t
                ys += a.y + (b.y - a.y) * t
                // z interpolé linéairement entre les 2 endpoints — fidèle car
                // un bone est un segment droit en 3D dans le repère ML Kit.
                zs += a.z + (b.z - a.z) * t
                zIsNative += true
            }
        }

        // ─── 1d. Grille intérieure (filtrée par pointInPolygon) ───
        // Pourquoi filtrer ICI plutôt que post-triangulation : on évite que
        // l'enveloppe convexe inclue ces points hors corps qui produiraient
        // des slivers à supprimer ensuite. Plus efficace.
        if (interiorGridStep > 0f) {
            var minX = Float.POSITIVE_INFINITY
            var minY = Float.POSITIVE_INFINITY
            var maxX = Float.NEGATIVE_INFINITY
            var maxY = Float.NEGATIVE_INFINITY
            for (p in contour) {
                if (p.x < minX) minX = p.x
                if (p.y < minY) minY = p.y
                if (p.x > maxX) maxX = p.x
                if (p.y > maxY) maxY = p.y
            }
            var gy = minY + interiorGridStep / 2f
            while (gy < maxY) {
                var gx = minX + interiorGridStep / 2f
                while (gx < maxX) {
                    if (pointInPolygon(gx, gy, contour)) {
                        xs += gx
                        ys += gy
                        zs += 0f
                        zIsNative += false
                    }
                    gx += interiorGridStep
                }
                gy += interiorGridStep
            }
        }

        // ─── 1.5. Estimation z par IDW pour les points non-keypoints ───
        // IDW (Inverse Distance Weighting) : pour chaque point sans z natif,
        // on pondère le z des keypoints proches par 1/d² et on moyenne.
        // Donne un effet de drapé doux : un point intérieur entre deux
        // hanches reçoit ~la profondeur moyenne des hanches.
        // Skippé si le scan n'est pas 3D — tous les keypoints ont z=0 →
        // IDW retourne 0 → équivalent à ne rien faire mais sans le coût.
        if (features.is3D && keypoints.isNotEmpty()) {
            for (idx in xs.indices) {
                if (zIsNative[idx]) continue
                var sumW = 0f
                var sumWZ = 0f
                for (lm in keypoints) {
                    val dx = xs[idx] - lm.x
                    val dy = ys[idx] - lm.y
                    // +1f évite la division par zéro si le point est confondu
                    // avec un keypoint (improbable mais pas impossible).
                    val d2 = dx * dx + dy * dy + 1f
                    val w = 1f / d2
                    sumW += w
                    sumWZ += w * lm.z
                }
                if (sumW > 0f) zs[idx] = sumWZ / sumW
            }
        }

        // ─── 2. Triangulation Delaunay ───
        val vertices = ArrayList<Triangulation.Vertex>(xs.size)
        for (k in xs.indices) {
            vertices += Triangulation.Vertex(xs[k], ys[k], k)
        }
        val raw = Triangulation.triangulate(vertices)

        // ─── 3. Filtrage : centroïde doit être dans la silhouette ───
        // Élimine les triangles qui spannent les régions concaves (entre les
        // jambes, sous les bras, autour de la tête). Sans ça, l'enveloppe
        // convexe Delaunay produirait un mesh "rectangulaire" qui inclurait
        // tout l'intérieur de la bbox du corps.
        val result = ArrayList<BodyTriangle>(raw.size)
        for (t in raw) {
            val ax = xs[t.a]; val ay = ys[t.a]; val az = zs[t.a]
            val bx = xs[t.b]; val by = ys[t.b]; val bz = zs[t.b]
            val cx = xs[t.c]; val cy = ys[t.c]; val cz = zs[t.c]
            val ccx = (ax + bx + cx) / 3f
            val ccy = (ay + by + cy) / 3f
            if (!pointInPolygon(ccx, ccy, contour)) continue
            result += BodyTriangle(ax, ay, az, bx, by, bz, cx, cy, cz)
        }
        return result
    }
}
