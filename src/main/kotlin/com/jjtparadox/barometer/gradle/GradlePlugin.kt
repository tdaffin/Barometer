/*
 * This file is part of Barometer
 *
 * Copyright (c) 2018 jjtParadox
 *
 * Barometer is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * Barometer is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Barometer. If not, see <http://www.gnu.org/licenses/>.
 */
package com.jjtparadox.barometer.gradle

import com.jjtparadox.barometer.tester.TestMain
import net.minecraftforge.gradle.user.patcherUser.forge.ForgeExtension
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test

class BarometerPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("barometer", BarometerPluginExtension::class.java)
        val mcExt = project.extensions.getByName("minecraft") as ForgeExtension
        val testTask = project.tasks.getByName("test") as Test

        val barometerTask = project.task("barometerPrep")
        barometerTask.doLast {
            val workingDir = mcExt.runDir + "/" + extension.testServerDir

            testTask.setWorkingDir(workingDir)
            project.mkdir(testTask.workingDir)

            if (extension.acceptEula) {
                project.file("$workingDir/eula.txt").writeText("eula=true")
            }

            if (extension.exitWhenCompleted) {
                System.setProperty("barometer.exitWhenCompleted", true.toString())
            }

            // Create a new process to run barometer tests in
            println("Creating process for BarometerTester tests")
            var process = TestMain.launch()
            val exitValue = process.waitFor()
            if ( exitValue != 0 )
                throw GradleException("Test process exited with $exitValue")
        }
        testTask.dependsOn(barometerTask)

//        project.dependencies.add("compile", "com.jjtparadox.barometer:Barometer:${Barometer.VERSION}")

        project.dependencies.add("testCompile", project.tasks.getByName("makeStart").outputs.files)
    }
}

open class BarometerPluginExtension {
    var exitWhenCompleted = true
    var testServerDir: String = "test"
    var acceptEula = false

    fun acceptEula() {
        acceptEula = true
    }
}
