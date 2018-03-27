package com.jjtparadox.barometer.gradle

import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class BarometerPluginTest {

    @Test
    fun barometerPrepTest() {
        val project = ProjectBuilder.builder().build()

        val minecraftPluginId = "net.minecraftforge.gradle.forge"
        project.getPluginManager().apply(minecraftPluginId)

        val pluginId = "com.jjtparadox.barometer"
        project.getPluginManager().apply(pluginId)

        assertTrue(project.getPluginManager().hasPlugin(pluginId))

        val tasks = project.getTasks()
        //tasks.forEach { println(it.name) }
        //assertNotNull(tasks.getByName("test"))

        val barometerTask = tasks.getByName("barometerPrep")

        assertNotNull(barometerTask)
        val actions = barometerTask.actions
        assertEquals("actions.size", 1, actions.size)
        actions.first().execute(null)
    }
}
