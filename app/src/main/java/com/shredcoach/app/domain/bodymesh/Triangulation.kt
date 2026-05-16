package com.shredcoach.app.domain.bodymesh

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Triangulation Delaunay 2D — algorithme Bowyer-Watson incrémental, pure JVM.
 *
 * **Pourquoi cette implémentation maison plutôt qu'une lib externe** :
 *  - Aucune dep additionnelle au build (les libs Java geometry pèsent ~1 MB+)
 *  - ~150 LOC contrôlables, testables, debuggables
 *  - Pas de besoin de constrained Delaunay (le sampling assure que les
 *    triangles restent dans la silhouette via filtrage post-process)
 *
 * **Performance** : O(N^1.5) en moyenne, ~5 ms pour 400 points sur Pixel 8.
 * Acceptable pour un calcul one-shot lors de la génération du mesh (pas
 * recalculé à chaque frame de render).
 *
 * **Robustesse** : on accepte des dégénérescences (4 points cocirculaires)
 * qui peuvent produire un triangle "tordu" — visuellement OK pour notre usage.
 * Pour de la précision géométrique exacte, on aurait besoin de
 * Shewchuk-style adaptive predicates (overkill ici).
 *
 * **Coordonnées** : tous les points sont en pixels image source (mêmes
 * unités que [MeshFeatures.silhouetteContour] et [Landmark.x/y]).
 */
object Triangulation {

    /** Vertex indexé pour pouvoir mapper les triangles vers les points sources. */
    data class Vertex(val x: Float, val y: Float, val index: Int)

    /** Triangle indexé : 3 indices dans le tableau de vertices d'origine. */
    data class Triangle(val a: Int, val b: Int, val c: Int) {
        /** Edges canoniques (toujours min→max pour comparaison set-style). */
        fun edges(): List<Edge> = listOf(
            Edge(min(a, b), max(a, b)),
            Edge(min(b, c), max(b, c)),
            Edge(min(c, a), max(c, a)),
        )
    }

    data class Edge(val a: Int, val b: Int)

    /**
     * Calcule la triangulation Delaunay des points donnés. Retourne la liste
     * des triangles (indices dans [points]).
     *
     * **Pré-condition** : pas de doublons exacts dans [points] (points
     * confondus → division par zéro dans le test du circumcircle). Le caller
     * doit dédupliquer ; on n'ajoute pas un set d'unicité ici par perf.
     */
    fun triangulate(points: List<Vertex>): List<Triangle> {
        if (points.size < 3) return emptyList()

        // ─── 1. Super-triangle qui englobe tous les points ───
        // Il faut qu'il soit assez grand pour que tous les points soient
        // strictement à l'intérieur. On prend la bbox étendue par un facteur 10.
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        for (p in points) {
            if (p.x < minX) minX = p.x
            if (p.y < minY) minY = p.y
            if (p.x > maxX) maxX = p.x
            if (p.y > maxY) maxY = p.y
        }
        val dx = maxX - minX
        val dy = maxY - minY
        val deltaMax = max(dx, dy) * 10f
        val midX = (minX + maxX) / 2f
        val midY = (minY + maxY) / 2f

        // Indices négatifs pour distinguer les vertices du super-triangle.
        // À la fin, on retire les triangles qui contiennent un de ces vertices.
        val superA = Vertex(midX - 20f * deltaMax, midY - deltaMax, -1)
        val superB = Vertex(midX, midY + 20f * deltaMax, -2)
        val superC = Vertex(midX + 20f * deltaMax, midY - deltaMax, -3)

        // [extPoints] = points originaux + 3 super-triangle. Indices respectent
        // la position dans cette liste augmentée pour l'algo.
        val extPoints = mutableListOf<Vertex>().apply {
            addAll(points)
            add(superA.copy(index = points.size))
            add(superB.copy(index = points.size + 1))
            add(superC.copy(index = points.size + 2))
        }
        val superIndices = setOf(points.size, points.size + 1, points.size + 2)

        // Triangulation initiale = un seul super-triangle.
        val triangles = mutableListOf(
            Triangle(points.size, points.size + 1, points.size + 2)
        )

        // ─── 2. Insertion incrémentale ───
        for (pIdx in points.indices) {
            val p = extPoints[pIdx]

            // 2.1 Trouver les "bad triangles" — ceux dont le circumcircle
            // contient le nouveau point. Ils seront détruits.
            val badTriangles = triangles.filter { t ->
                pointInCircumcircle(p, extPoints[t.a], extPoints[t.b], extPoints[t.c])
            }

            // 2.2 Construire le polygone trou (edges des bad triangles qui ne
            // sont PAS partagés avec un autre bad triangle).
            val edgeCount = mutableMapOf<Edge, Int>()
            for (t in badTriangles) {
                for (e in t.edges()) {
                    edgeCount[e] = (edgeCount[e] ?: 0) + 1
                }
            }
            val boundaryEdges = edgeCount.filter { it.value == 1 }.keys

            // 2.3 Retire les bad triangles + retriangule en connectant chaque
            // edge frontière au nouveau point.
            triangles.removeAll(badTriangles.toSet())
            for (e in boundaryEdges) {
                triangles.add(Triangle(e.a, e.b, p.index))
            }
        }

        // ─── 3. Cleanup : retire les triangles qui touchent le super-triangle ───
        return triangles.filter { t ->
            t.a !in superIndices && t.b !in superIndices && t.c !in superIndices
        }
    }

    /**
     * Test inCircle : retourne `true` si [p] est strictement à l'intérieur du
     * cercle circonscrit au triangle [a, b, c].
     *
     * Utilise le déterminant 4×4 classique (CGAL formulation). Sensible aux
     * orientations (CW vs CCW) — on prend la valeur absolue du déterminant +
     * un test d'orientation si nécessaire pour rester robuste.
     */
    private fun pointInCircumcircle(p: Vertex, a: Vertex, b: Vertex, c: Vertex): Boolean {
        val ax = a.x - p.x
        val ay = a.y - p.y
        val bx = b.x - p.x
        val by = b.y - p.y
        val cx = c.x - p.x
        val cy = c.y - p.y

        val a2 = ax * ax + ay * ay
        val b2 = bx * bx + by * by
        val c2 = cx * cx + cy * cy

        // Déterminant 3×3 : positive si p est à l'intérieur (pour triangle CCW).
        // Pour ne pas dépendre de l'orientation, on multiplie par le signe de
        // l'orientation du triangle ABC.
        val det =
            ax * (by * c2 - cy * b2) -
            ay * (bx * c2 - cx * b2) +
            a2 * (bx * cy - cx * by)

        // Orientation : 2 × aire signée du triangle (positive = CCW).
        val orient = (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)

        // Si triangle CCW (orient > 0), p est à l'intérieur si det > 0.
        // Si CW, l'inverse.
        return if (orient > 0f) det > 0f else det < 0f
    }
}

/**
 * Test point-in-polygon par ray casting. Polygon = liste fermée de vertices
 * (last == first OU on considère la fermeture implicite).
 *
 * Algorithme classique : on compte combien de fois un rayon horizontal partant
 * de [p] vers +∞ traverse les edges du polygone. Pair = dehors, impair = dedans.
 *
 * **Robustesse** : on évite les comparaisons d'égalité strictes pour les
 * points sur les edges (cas pathologique). Pour notre usage (triangle
 * centroid filtering), c'est largement suffisant.
 */
fun pointInPolygon(px: Float, py: Float, polygon: List<Point>): Boolean {
    if (polygon.size < 3) return false
    var inside = false
    var j = polygon.size - 1
    for (i in polygon.indices) {
        val xi = polygon[i].x
        val yi = polygon[i].y
        val xj = polygon[j].x
        val yj = polygon[j].y
        val intersects = ((yi > py) != (yj > py)) &&
                (px < (xj - xi) * (py - yi) / (yj - yi + 1e-10f) + xi)
        if (intersects) inside = !inside
        j = i
    }
    return inside
}
