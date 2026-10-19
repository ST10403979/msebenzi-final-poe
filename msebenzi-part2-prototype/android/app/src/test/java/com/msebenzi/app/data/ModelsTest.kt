package com.msebenzi.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ModelsTest {

    @Test
    fun `JobDto maps people and photo into the Room entity`() {
        val dto = JobDto(
            id = 4, title = "Fix tap", description = "Leaking", category = "plumbing",
            location = "Mthatha", budget = 200.0, status = "accepted", postedById = 1,
            acceptedById = 2, photoMediaId = 11,
            postedBy = PersonRef(1, "Thandi"), acceptedBy = PersonRef(2, "Sipho")
        )

        val job = dto.toJob()

        assertEquals("Thandi", job.postedByName)
        assertEquals("Sipho", job.acceptedByName)
        assertEquals(11, job.photoMediaId)
        assertFalse(job.isPendingSync)
    }

    @Test
    fun `JobDto without people or photo still maps`() {
        val job = JobDto(1, "A", "B", "cleaning", "C", 10.0, "open", 3).toJob()
        assertNull(job.postedByName)
        assertNull(job.photoMediaId)
    }

    @Test
    fun `service catalogue offers eight unique services`() {
        val keys = ServiceCatalog.all.map { it.key }
        assertEquals(8, keys.size)
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun `service lookup is case and whitespace insensitive`() {
        assertEquals("gardening", ServiceCatalog.find(" Gardening ")?.key)
        assertNull(ServiceCatalog.find("astronaut"))
        assertNull(ServiceCatalog.find(null))
    }

    @Test
    fun `unknown categories still get a picture`() {
        assertNotNull(ServiceCatalog.imageFor("something-else"))
        assertEquals(ServiceCatalog.find("cleaning")!!.imageRes, ServiceCatalog.imageFor("CLEANING"))
    }
}
