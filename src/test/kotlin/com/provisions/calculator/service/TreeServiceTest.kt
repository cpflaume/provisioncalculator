package com.provisions.calculator.service

import com.provisions.calculator.api.request.TreeNodeRequest
import com.provisions.calculator.repository.TreeNodeRepository
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.web.server.ResponseStatusException

class TreeServiceTest {

    private val treeService = TreeService(treeNodeRepository = mockk<TreeNodeRepository>())

    @Test
    fun `valid tree passes validation`() {
        val nodes = listOf(
            TreeNodeRequest("A", null),
            TreeNodeRequest("B", "A"),
            TreeNodeRequest("C", "A")
        )
        assertDoesNotThrow { treeService.validate(nodes) }
    }

    @Test
    fun `empty tree is rejected`() {
        val ex = assertThrows<ResponseStatusException> {
            treeService.validate(emptyList())
        }
        assertTrue(ex.reason!!.contains("at least one node"))
    }

    @Test
    fun `multiple roots are rejected`() {
        val nodes = listOf(
            TreeNodeRequest("A", null),
            TreeNodeRequest("B", null)
        )
        val ex = assertThrows<ResponseStatusException> {
            treeService.validate(nodes)
        }
        assertTrue(ex.reason!!.contains("exactly one root"))
    }

    @Test
    fun `duplicate customer IDs are rejected`() {
        val nodes = listOf(
            TreeNodeRequest("A", null),
            TreeNodeRequest("A", null)
        )
        val ex = assertThrows<ResponseStatusException> {
            treeService.validate(nodes)
        }
        assertTrue(ex.reason!!.contains("Duplicate"))
    }

    @Test
    fun `missing parent reference is rejected`() {
        val nodes = listOf(
            TreeNodeRequest("A", null),
            TreeNodeRequest("B", "MISSING")
        )
        val ex = assertThrows<ResponseStatusException> {
            treeService.validate(nodes)
        }
        assertTrue(ex.reason!!.contains("not found"))
    }
}
