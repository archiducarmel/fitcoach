package com.shredcoach.app.data.seed

import com.shredcoach.app.data.local.entity.FoodEntity

object FoodSeedData {
    fun getAllFoods(): List<FoodEntity> = listOf(
        // ═══ PROTÉINES ═══
        FoodEntity(name = "Blanc de poulet", category = "Protéines", caloriesPer100g = 165.0, proteinsPer100g = 31.0, carbsPer100g = 0.0, fatsPer100g = 3.6, defaultPortionGrams = 150, portionLabel = "1 filet"),
        FoodEntity(name = "Escalope de dinde", category = "Protéines", caloriesPer100g = 135.0, proteinsPer100g = 30.0, carbsPer100g = 0.0, fatsPer100g = 1.5, defaultPortionGrams = 150, portionLabel = "1 escalope"),
        FoodEntity(name = "Steak haché 5%", category = "Protéines", caloriesPer100g = 136.0, proteinsPer100g = 21.0, carbsPer100g = 0.0, fatsPer100g = 5.0, defaultPortionGrams = 125, portionLabel = "1 steak"),
        FoodEntity(name = "Saumon", category = "Protéines", caloriesPer100g = 208.0, proteinsPer100g = 20.0, carbsPer100g = 0.0, fatsPer100g = 13.0, defaultPortionGrams = 150, portionLabel = "1 pavé"),
        FoodEntity(name = "Thon en boîte (naturel)", category = "Protéines", caloriesPer100g = 116.0, proteinsPer100g = 26.0, carbsPer100g = 0.0, fatsPer100g = 1.0, defaultPortionGrams = 140, portionLabel = "1 boîte"),
        FoodEntity(name = "Œufs entiers", category = "Protéines", caloriesPer100g = 155.0, proteinsPer100g = 13.0, carbsPer100g = 1.1, fatsPer100g = 11.0, defaultPortionGrams = 60, portionLabel = "1 œuf"),
        FoodEntity(name = "Blancs d'œufs", category = "Protéines", caloriesPer100g = 52.0, proteinsPer100g = 11.0, carbsPer100g = 0.7, fatsPer100g = 0.2, defaultPortionGrams = 100, portionLabel = "3 blancs"),
        FoodEntity(name = "Crevettes", category = "Protéines", caloriesPer100g = 99.0, proteinsPer100g = 24.0, carbsPer100g = 0.2, fatsPer100g = 0.3, defaultPortionGrams = 150, portionLabel = "1 portion"),
        FoodEntity(name = "Tofu ferme", category = "Protéines", caloriesPer100g = 144.0, proteinsPer100g = 17.0, carbsPer100g = 3.0, fatsPer100g = 8.0, defaultPortionGrams = 150, portionLabel = "1 bloc"),
        FoodEntity(name = "Whey protéine", category = "Protéines", caloriesPer100g = 380.0, proteinsPer100g = 80.0, carbsPer100g = 6.0, fatsPer100g = 3.0, defaultPortionGrams = 30, portionLabel = "1 scoop"),
        FoodEntity(name = "Caséine", category = "Protéines", caloriesPer100g = 360.0, proteinsPer100g = 75.0, carbsPer100g = 8.0, fatsPer100g = 2.0, defaultPortionGrams = 30, portionLabel = "1 scoop"),

        // ═══ GLUCIDES ═══
        FoodEntity(name = "Riz blanc cuit", category = "Glucides", caloriesPer100g = 130.0, proteinsPer100g = 2.7, carbsPer100g = 28.0, fatsPer100g = 0.3, defaultPortionGrams = 200, portionLabel = "1 portion"),
        FoodEntity(name = "Riz complet cuit", category = "Glucides", caloriesPer100g = 123.0, proteinsPer100g = 2.7, carbsPer100g = 26.0, fatsPer100g = 0.9, fiberPer100g = 1.8, defaultPortionGrams = 200, portionLabel = "1 portion"),
        FoodEntity(name = "Pâtes cuites", category = "Glucides", caloriesPer100g = 131.0, proteinsPer100g = 5.0, carbsPer100g = 25.0, fatsPer100g = 1.1, defaultPortionGrams = 200, portionLabel = "1 portion"),
        FoodEntity(name = "Pâtes complètes cuites", category = "Glucides", caloriesPer100g = 124.0, proteinsPer100g = 5.3, carbsPer100g = 23.0, fatsPer100g = 1.0, fiberPer100g = 3.0, defaultPortionGrams = 200, portionLabel = "1 portion"),
        FoodEntity(name = "Patate douce cuite", category = "Glucides", caloriesPer100g = 90.0, proteinsPer100g = 2.0, carbsPer100g = 21.0, fatsPer100g = 0.1, fiberPer100g = 3.0, defaultPortionGrams = 200, portionLabel = "1 patate"),
        FoodEntity(name = "Pomme de terre cuite", category = "Glucides", caloriesPer100g = 87.0, proteinsPer100g = 1.9, carbsPer100g = 20.0, fatsPer100g = 0.1, defaultPortionGrams = 200, portionLabel = "1 pomme de terre"),
        FoodEntity(name = "Pain complet", category = "Glucides", caloriesPer100g = 247.0, proteinsPer100g = 13.0, carbsPer100g = 41.0, fatsPer100g = 3.4, fiberPer100g = 7.0, defaultPortionGrams = 40, portionLabel = "1 tranche"),
        FoodEntity(name = "Flocons d'avoine", category = "Glucides", caloriesPer100g = 379.0, proteinsPer100g = 13.0, carbsPer100g = 67.0, fatsPer100g = 7.0, fiberPer100g = 10.0, defaultPortionGrams = 60, portionLabel = "6 c. à soupe"),
        FoodEntity(name = "Quinoa cuit", category = "Glucides", caloriesPer100g = 120.0, proteinsPer100g = 4.4, carbsPer100g = 21.0, fatsPer100g = 1.9, fiberPer100g = 2.8, defaultPortionGrams = 200, portionLabel = "1 portion"),
        FoodEntity(name = "Semoule cuite", category = "Glucides", caloriesPer100g = 112.0, proteinsPer100g = 3.8, carbsPer100g = 23.0, fatsPer100g = 0.2, defaultPortionGrams = 200, portionLabel = "1 portion"),

        // ═══ LIPIDES / HUILES ═══
        FoodEntity(name = "Huile d'olive", category = "Lipides", caloriesPer100g = 884.0, proteinsPer100g = 0.0, carbsPer100g = 0.0, fatsPer100g = 100.0, defaultPortionGrams = 10, portionLabel = "1 c. à soupe"),
        FoodEntity(name = "Beurre de cacahuète", category = "Lipides", caloriesPer100g = 588.0, proteinsPer100g = 25.0, carbsPer100g = 20.0, fatsPer100g = 50.0, fiberPer100g = 6.0, defaultPortionGrams = 15, portionLabel = "1 c. à soupe"),
        FoodEntity(name = "Amandes", category = "Lipides", caloriesPer100g = 579.0, proteinsPer100g = 21.0, carbsPer100g = 22.0, fatsPer100g = 50.0, fiberPer100g = 12.0, defaultPortionGrams = 30, portionLabel = "1 poignée"),
        FoodEntity(name = "Noix", category = "Lipides", caloriesPer100g = 654.0, proteinsPer100g = 15.0, carbsPer100g = 14.0, fatsPer100g = 65.0, defaultPortionGrams = 30, portionLabel = "1 poignée"),
        FoodEntity(name = "Avocat", category = "Lipides", caloriesPer100g = 160.0, proteinsPer100g = 2.0, carbsPer100g = 9.0, fatsPer100g = 15.0, fiberPer100g = 7.0, defaultPortionGrams = 80, portionLabel = "1/2 avocat"),
        FoodEntity(name = "Graines de chia", category = "Lipides", caloriesPer100g = 486.0, proteinsPer100g = 17.0, carbsPer100g = 42.0, fatsPer100g = 31.0, fiberPer100g = 34.0, defaultPortionGrams = 15, portionLabel = "1 c. à soupe"),

        // ═══ LÉGUMES ═══
        FoodEntity(name = "Brocoli", category = "Légumes", caloriesPer100g = 34.0, proteinsPer100g = 2.8, carbsPer100g = 7.0, fatsPer100g = 0.4, fiberPer100g = 2.6, defaultPortionGrams = 150, portionLabel = "1 portion"),
        FoodEntity(name = "Épinards", category = "Légumes", caloriesPer100g = 23.0, proteinsPer100g = 2.9, carbsPer100g = 3.6, fatsPer100g = 0.4, fiberPer100g = 2.2, defaultPortionGrams = 100, portionLabel = "1 portion"),
        FoodEntity(name = "Haricots verts", category = "Légumes", caloriesPer100g = 31.0, proteinsPer100g = 1.8, carbsPer100g = 7.0, fatsPer100g = 0.1, fiberPer100g = 3.4, defaultPortionGrams = 150, portionLabel = "1 portion"),
        FoodEntity(name = "Tomates", category = "Légumes", caloriesPer100g = 18.0, proteinsPer100g = 0.9, carbsPer100g = 3.9, fatsPer100g = 0.2, defaultPortionGrams = 150, portionLabel = "1 tomate"),
        FoodEntity(name = "Concombre", category = "Légumes", caloriesPer100g = 15.0, proteinsPer100g = 0.7, carbsPer100g = 3.6, fatsPer100g = 0.1, defaultPortionGrams = 150, portionLabel = "1/2 concombre"),
        FoodEntity(name = "Courgettes", category = "Légumes", caloriesPer100g = 17.0, proteinsPer100g = 1.2, carbsPer100g = 3.1, fatsPer100g = 0.3, defaultPortionGrams = 200, portionLabel = "1 courgette"),
        FoodEntity(name = "Carottes", category = "Légumes", caloriesPer100g = 41.0, proteinsPer100g = 0.9, carbsPer100g = 10.0, fatsPer100g = 0.2, fiberPer100g = 2.8, defaultPortionGrams = 100, portionLabel = "2 carottes"),
        FoodEntity(name = "Champignons", category = "Légumes", caloriesPer100g = 22.0, proteinsPer100g = 3.1, carbsPer100g = 3.3, fatsPer100g = 0.3, defaultPortionGrams = 100, portionLabel = "1 portion"),

        // ═══ FRUITS ═══
        FoodEntity(name = "Banane", category = "Fruits", caloriesPer100g = 89.0, proteinsPer100g = 1.1, carbsPer100g = 23.0, fatsPer100g = 0.3, fiberPer100g = 2.6, defaultPortionGrams = 120, portionLabel = "1 banane"),
        FoodEntity(name = "Pomme", category = "Fruits", caloriesPer100g = 52.0, proteinsPer100g = 0.3, carbsPer100g = 14.0, fatsPer100g = 0.2, fiberPer100g = 2.4, defaultPortionGrams = 180, portionLabel = "1 pomme"),
        FoodEntity(name = "Myrtilles", category = "Fruits", caloriesPer100g = 57.0, proteinsPer100g = 0.7, carbsPer100g = 14.0, fatsPer100g = 0.3, fiberPer100g = 2.4, defaultPortionGrams = 100, portionLabel = "1 portion"),
        FoodEntity(name = "Fraises", category = "Fruits", caloriesPer100g = 32.0, proteinsPer100g = 0.7, carbsPer100g = 7.7, fatsPer100g = 0.3, fiberPer100g = 2.0, defaultPortionGrams = 150, portionLabel = "1 barquette"),
        FoodEntity(name = "Orange", category = "Fruits", caloriesPer100g = 47.0, proteinsPer100g = 0.9, carbsPer100g = 12.0, fatsPer100g = 0.1, fiberPer100g = 2.4, defaultPortionGrams = 200, portionLabel = "1 orange"),
        FoodEntity(name = "Dattes", category = "Fruits", caloriesPer100g = 277.0, proteinsPer100g = 1.8, carbsPer100g = 75.0, fatsPer100g = 0.2, fiberPer100g = 7.0, defaultPortionGrams = 25, portionLabel = "3 dattes"),

        // ═══ LAITIERS ═══
        FoodEntity(name = "Fromage blanc 0%", category = "Laitiers", caloriesPer100g = 48.0, proteinsPer100g = 8.0, carbsPer100g = 4.0, fatsPer100g = 0.2, defaultPortionGrams = 200, portionLabel = "1 pot"),
        FoodEntity(name = "Yaourt grec 0%", category = "Laitiers", caloriesPer100g = 59.0, proteinsPer100g = 10.0, carbsPer100g = 3.6, fatsPer100g = 0.4, defaultPortionGrams = 170, portionLabel = "1 pot"),
        FoodEntity(name = "Skyr", category = "Laitiers", caloriesPer100g = 63.0, proteinsPer100g = 11.0, carbsPer100g = 4.0, fatsPer100g = 0.2, defaultPortionGrams = 150, portionLabel = "1 pot"),
        FoodEntity(name = "Lait demi-écrémé", category = "Laitiers", caloriesPer100g = 46.0, proteinsPer100g = 3.2, carbsPer100g = 4.8, fatsPer100g = 1.5, defaultPortionGrams = 250, portionLabel = "1 verre"),
        FoodEntity(name = "Cottage cheese", category = "Laitiers", caloriesPer100g = 98.0, proteinsPer100g = 11.0, carbsPer100g = 3.4, fatsPer100g = 4.3, defaultPortionGrams = 150, portionLabel = "1 portion"),
        FoodEntity(name = "Mozzarella light", category = "Laitiers", caloriesPer100g = 226.0, proteinsPer100g = 25.0, carbsPer100g = 2.0, fatsPer100g = 13.0, defaultPortionGrams = 30, portionLabel = "1 tranche"),

        // ═══ LÉGUMINEUSES ═══
        FoodEntity(name = "Lentilles cuites", category = "Légumineuses", caloriesPer100g = 116.0, proteinsPer100g = 9.0, carbsPer100g = 20.0, fatsPer100g = 0.4, fiberPer100g = 8.0, defaultPortionGrams = 200, portionLabel = "1 portion"),
        FoodEntity(name = "Pois chiches cuits", category = "Légumineuses", caloriesPer100g = 164.0, proteinsPer100g = 9.0, carbsPer100g = 27.0, fatsPer100g = 2.6, fiberPer100g = 8.0, defaultPortionGrams = 200, portionLabel = "1 portion"),
        FoodEntity(name = "Haricots rouges cuits", category = "Légumineuses", caloriesPer100g = 127.0, proteinsPer100g = 9.0, carbsPer100g = 23.0, fatsPer100g = 0.5, fiberPer100g = 7.0, defaultPortionGrams = 200, portionLabel = "1 portion"),

        // ═══ SNACKS SAINS ═══
        FoodEntity(name = "Galette de riz", category = "Snacks", caloriesPer100g = 387.0, proteinsPer100g = 7.0, carbsPer100g = 85.0, fatsPer100g = 2.8, defaultPortionGrams = 10, portionLabel = "1 galette"),
        FoodEntity(name = "Barre protéinée", category = "Snacks", caloriesPer100g = 350.0, proteinsPer100g = 30.0, carbsPer100g = 35.0, fatsPer100g = 10.0, defaultPortionGrams = 60, portionLabel = "1 barre"),
        FoodEntity(name = "Chocolat noir 85%", category = "Snacks", caloriesPer100g = 580.0, proteinsPer100g = 11.0, carbsPer100g = 20.0, fatsPer100g = 46.0, defaultPortionGrams = 20, portionLabel = "2 carrés"),
        FoodEntity(name = "Miel", category = "Snacks", caloriesPer100g = 304.0, proteinsPer100g = 0.3, carbsPer100g = 82.0, fatsPer100g = 0.0, defaultPortionGrams = 15, portionLabel = "1 c. à soupe"),

        // ═══ BOISSONS ═══
        FoodEntity(name = "Eau", category = "Boissons", caloriesPer100g = 0.0, proteinsPer100g = 0.0, carbsPer100g = 0.0, fatsPer100g = 0.0, defaultPortionGrams = 500, portionLabel = "1 bouteille"),
        FoodEntity(name = "Café noir", category = "Boissons", caloriesPer100g = 2.0, proteinsPer100g = 0.3, carbsPer100g = 0.0, fatsPer100g = 0.0, defaultPortionGrams = 200, portionLabel = "1 tasse"),
        FoodEntity(name = "Thé vert", category = "Boissons", caloriesPer100g = 1.0, proteinsPer100g = 0.0, carbsPer100g = 0.0, fatsPer100g = 0.0, defaultPortionGrams = 250, portionLabel = "1 tasse"),
        FoodEntity(name = "Lait d'amande", category = "Boissons", caloriesPer100g = 13.0, proteinsPer100g = 0.4, carbsPer100g = 0.3, fatsPer100g = 1.1, defaultPortionGrams = 250, portionLabel = "1 verre")
    )
}
