package com.msebenzi.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidatorsTest {

    @Test
    fun `isValidEmail accepts well-formed addresses`() {
        assertTrue(Validators.isValidEmail("thabo@example.com"))
        assertTrue(Validators.isValidEmail("j.doe@sub.example.co.za"))
    }

    @Test
    fun `isValidEmail rejects malformed addresses`() {
        assertFalse(Validators.isValidEmail(""))
        assertFalse(Validators.isValidEmail("not-an-email"))
        assertFalse(Validators.isValidEmail("missing@dot"))
        assertFalse(Validators.isValidEmail("@example.com"))
    }

    @Test
    fun `isValidPassword requires at least 6 characters`() {
        assertFalse(Validators.isValidPassword("12345"))
        assertTrue(Validators.isValidPassword("123456"))
    }

    @Test
    fun `areJobFieldsValid fails when any required field is blank`() {
        assertFalse(Validators.areJobFieldsValid("", "Gardening", "Cape Town", "Mow the lawn"))
        assertFalse(Validators.areJobFieldsValid("Mow lawn", "Gardening", "  ", "desc"))
        assertTrue(Validators.areJobFieldsValid("Mow lawn", "Gardening", "Cape Town", "desc"))
    }

    @Test
    fun `parseBudget rejects negative or non-numeric input`() {
        assertEquals(null, Validators.parseBudget("-5"))
        assertEquals(null, Validators.parseBudget("abc"))
        assertEquals(150.0, Validators.parseBudget("150"))
    }
}
