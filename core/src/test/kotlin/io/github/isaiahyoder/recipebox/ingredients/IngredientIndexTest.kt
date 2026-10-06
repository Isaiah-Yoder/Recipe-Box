package io.github.isaiahyoder.recipebox.ingredients

import io.github.isaiahyoder.recipebox.model.RecipeLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IngredientIndexTest {
    @Test fun readsNamesAmountsAndGroups() {
        val index = IngredientIndex.read(
            listOf(
                RecipeLine("For the sauce", isHeader = true),
                RecipeLine("2 cups chopped tomatoes"),
                RecipeLine("1 (15 ounce) can black beans, drained"),
                RecipeLine("salt to taste"),
                RecipeLine("3 scallions, sliced"),
            )
        )
        assertEquals(listOf(1, 2, 3, 4), index.map { it.position })
        assertEquals(listOf("For the sauce"), index.map { it.group }.distinct())

        val tomatoes = index[0].reading
        assertEquals("tomato", tomatoes.name.key)
        assertEquals(Unit.CUP, tomatoes.unit)
        assertEquals(96.0, tomatoes.baseAmount!!, 0.0)

        val beans = index[1].reading
        assertEquals("black bean", beans.name.key)
        assertEquals(Unit.CAN, beans.unit)
        assertEquals("15 ounce", beans.size)
        assertNull("Counts have no base amount", beans.baseAmount)

        assertNull(index[2].reading.quantity)
        assertEquals("salt", index[2].reading.name.key)
        assertEquals("green onion", index[3].reading.name.key)
    }

    @Test fun aRangeKeepsBothEnds() {
        val reading = IngredientIndex.read(listOf(RecipeLine("2 to 3 pounds chicken thighs"))).single().reading
        assertEquals(Quantity(2.0, 3.0), reading.quantity)
        assertEquals(48.0, reading.baseAmount!!, 0.0)
    }
}
