package dev.mattramotar.atom.ksp

import kotlin.test.Test
import kotlin.test.assertEquals

class CodegenContractTest {

    @Test
    fun `atom container type points to runtime di package`() {
        assertEquals("dev.mattramotar.atom.runtime.di", RTypes.AtomContainer.packageName)
        assertEquals("AtomContainer", RTypes.AtomContainer.simpleName)
    }
}
