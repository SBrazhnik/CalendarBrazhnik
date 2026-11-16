package app.calstats

import app.calstats.work.Activity
import app.calstats.work.HealthMath
import app.calstats.work.HealthOps
import app.calstats.work.HealthProfile
import app.calstats.work.Sex
import app.calstats.work.WeightGoal
import app.calstats.work.Workspace
import app.calstats.health.FoodSearch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HealthTest {
    private val today = LocalDate.of(2026, 11, 16)

    @Test fun bmrMifflin() {
        // 80 кг, 180 см, 36 лет, мужчина: 10*80 + 6.25*180 - 5*36 + 5 = 1750
        val p = HealthProfile(Sex.MALE, 1990, 180, 80.0)
        assertEquals(1750.0, HealthMath.bmr(p, today), 0.01)
        val f = p.copy(sex = Sex.FEMALE)
        assertEquals(1584.0, HealthMath.bmr(f, today), 0.01)
    }

    @Test fun normsByGoal() {
        val p = HealthProfile(Sex.MALE, 1990, 180, 80.0, Activity.MODERATE, WeightGoal.KEEP)
        val keep = HealthMath.norms(p, today)
        assertEquals(1750 * 1.55, keep.kcal.toDouble(), 1.0)
        assertEquals(128, keep.protein)
        assertEquals(2400, keep.waterMl)
        val lose = HealthMath.norms(p.copy(goal = WeightGoal.LOSE), today)
        assertTrue(lose.kcal < keep.kcal)
        // Безопасный минимум
        val tiny = HealthMath.norms(HealthProfile(Sex.FEMALE, 1960, 150, 40.0, Activity.LOW, WeightGoal.LOSE), today)
        assertTrue(tiny.kcal >= 1200)
        // Сумма макросов ≈ калории
        assertEquals(keep.kcal.toDouble(), keep.protein * 4.0 + keep.fat * 9.0 + keep.carbs * 4.0, 15.0)
    }

    @Test fun waterAccumulates() {
        var w = Workspace()
        w = HealthOps.addWater(w, today, 250)
        w = HealthOps.addWater(w, today, 250)
        w = HealthOps.addWater(w, today, -250)
        w = HealthOps.addWater(w, today.minusDays(1), 500)
        assertEquals(250, HealthOps.water(w, today))
        assertEquals(500, HealthOps.water(w, today.minusDays(1)))
        assertEquals(0, HealthOps.water(HealthOps.addWater(w, today, -1000), today))
    }

    @Test fun parseOpenFoodFacts() {
        val json = """{"code":"4607001","product_name":"Kefir","product_name_ru":"Кефир 3,2%","brands":"Простоквашино, X",
            "nutriments":{"energy-kcal_100g":59,"proteins_100g":"2.9","fat_100g":3.2,"carbohydrates_100g":4}}"""
        val f = FoodSearch.parse(Json.parseToJsonElement(json).jsonObject)!!
        assertEquals("Кефир 3,2%, Простоквашино", f.name)
        assertEquals(59.0, f.kcal, 0.01)
        assertEquals(2.9, f.protein, 0.01)
        assertEquals("4607001", f.barcode)
    }
}
