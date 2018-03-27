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
package com.jjtparadox.barometer.tester

import GradleStartServer
import com.google.common.util.concurrent.ListenableFutureTask
import com.jjtparadox.barometer.Barometer
import io.github.lukehutch.fastclasspathscanner.FastClasspathScanner
import net.minecraft.launchwrapper.Launch
import net.minecraft.launchwrapper.LaunchClassLoader
import net.minecraftforge.fml.common.launcher.FMLServerTweaker
import org.junit.runner.Description
import org.junit.runner.JUnitCore
import org.junit.runner.RunWith
import org.junit.runner.notification.RunListener
import org.junit.runner.notification.RunNotifier
import org.junit.runners.BlockJUnit4ClassRunner
import org.junit.runners.model.FrameworkMethod
import org.junit.runners.model.InitializationError
import java.io.File
import java.util.ArrayList
import java.util.Queue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.FutureTask
import kotlin.reflect.jvm.jvmName

class BarometerTester(klass: Class<*>) : BlockJUnit4ClassRunner(load(klass)) {
    @Suppress("UNCHECKED_CAST")
    companion object {

        var runningTests = false

        lateinit var proxy: BarometerProxy

        private fun load(klass: Class<*>): Class<*> {
            if ( !runningTests )
                return klass

            proxy = BarometerProxy()
            proxy.start()

            // Return test class that's been loaded by the same classloader as Minecraft
            try {
                return Class.forName(klass.name, true, Launch.classLoader)
            } catch (e: ClassNotFoundException) {
                throw InitializationError(e)
            }
        }
    }

    override fun run(notifier: RunNotifier?) {
        super.run(notifier)
        if ( runningTests )
            proxy.run()
    }

    override fun runChild(method: FrameworkMethod?, notifier: RunNotifier?) {
        if ( !runningTests )
            return
        val task = ListenableFutureTask.create {
            super.runChild(method, notifier)
        }
        proxy.addTask(task)
        task.get()
    }
}

class BarometerProxy {
    companion object {
        var started = false
        var testCount = -1

        // References to Barometer's mod class & fields
        lateinit var barometer: Class<*>
        lateinit var taskQueue: Queue<FutureTask<*>>
        lateinit var finishedLatch: CountDownLatch
    }

    fun start() {
        // If game not started, start it and grab the Barometer mod class
        if (!started) {
            started = true

            // Normally, LaunchWrapper sets the thread's context class loader to the LaunchClassLoader.
            // However, that causes issue as soon as tests are run in the normal class loader in the same thread.
            // Simply resetting it seems to fix various issues with Mockito.

            val thread = Thread.currentThread()
            val contextClassLoader = thread.contextClassLoader

            GradleStartTestServer().launch(arrayOf("--noCoreSearch", "nogui"))

            thread.contextClassLoader = contextClassLoader

            barometer = Class.forName(Barometer::class.qualifiedName, true, Launch.classLoader)
            taskQueue = barometer.getField("futureTaskQueue")[null] as Queue<FutureTask<*>>
            finishedLatch = barometer.getField("finishedLatch")[null] as CountDownLatch
        }
    }

    fun run() {
        if (testCount == -1) {
            try {
                // Use FastClasspathScanner to find the number of Barometer tests being run
                testCount = 0
                // Only scan directories for .class files
                val scanner = FastClasspathScanner("-jar:")
                scanner.matchClassesWithAnnotation(RunWith::class.java, {
                    val value = it.getAnnotation(RunWith::class.java).value
                    if (value == BarometerTester::class)
                        testCount++
                }
                )
                scanner.scan()
            } catch (e: Exception) {
                System.err.println("Could not get testCount:")
                e.printStackTrace(System.err)
            }
        }
        testCount--
        if (testCount <= 0) {
            barometer.getField("testing").setBoolean(null, false)
            finishedLatch.await()
        }
    }

    fun addTask(task: FutureTask<*>) {
        synchronized(taskQueue) {
            taskQueue.add(task)
        }
    }
}

class GradleStartTestServer : GradleStartServer() {
    public override fun launch(args: Array<String>) {
        super.launch(args)
    }

    override fun getTweakClass(): String? {
        return TestTweaker::class.qualifiedName
    }
}

class TestTweaker : FMLServerTweaker() {

    override fun injectIntoClassLoader(classLoader: LaunchClassLoader) {
        classLoader.addTransformerExclusion("com.jjtparadox.barometer.experimental.env.")

        classLoader.addClassLoaderExclusion("junit.")
        classLoader.addClassLoaderExclusion("org.junit.")
        classLoader.addClassLoaderExclusion("org.hamcrest.")

        classLoader.addClassLoaderExclusion("org.mockito.")
        classLoader.addClassLoaderExclusion("net.bytebuddy.")
        classLoader.addClassLoaderExclusion("org.objenesis.")

        classLoader.addClassLoaderExclusion("org.easymock.")
        classLoader.addClassLoaderExclusion("cglib.")
        classLoader.addClassLoaderExclusion("org.testng.")

        classLoader.addClassLoaderExclusion("org.powermock.")
        classLoader.addClassLoaderExclusion("org.javassist.")
        classLoader.addClassLoaderExclusion("com.thoughtworks.xstream")

        super.injectIntoClassLoader(classLoader)
    }
}

object TestMain {

    @JvmStatic
    fun launch(args: Array<String>? = null) : Process {
        val argumentList = object : ArrayList<String>() {
            init {
                add(File(System.getProperty("java.home"), "bin/java").path)
                add("-cp")
                add(System.getProperty("java.class.path"))
                add(TestMain::class.jvmName)
                if ( args != null )
                    for (arg in args)
                        add(arg)
            }
        }
        println("Launching test process with args:")
        argumentList.forEach({ println("\t$it") })
        val pb = ProcessBuilder(argumentList)
        pb.inheritIO()
        return pb.start()
    }

    @Throws(Exception::class)
    @JvmStatic
    fun main(args: Array<String>) {
        runTests()
    }

    private fun runTests() {
        val testClasses = findTests().toTypedArray()
        println("Running test classes:")
        testClasses.forEach({ println("\t$it") })
        val jUnitCore = JUnitCore()
        jUnitCore.addListener(TestRunListener())
        BarometerTester.runningTests = true
        val result = jUnitCore.run(*testClasses)
        println("Test run: ${result.runCount}, Failed: ${result.failureCount}")
    }

    private fun findTests(): List<Class<*>> {
        val classes = ArrayList<Class<*>>()
        try {
            // Use FastClasspathScanner to find the Barometer tests being run
            // Only scan directories for .class files
            val scanner = FastClasspathScanner("-jar:")
            scanner.matchClassesWithAnnotation(RunWith::class.java, {
                val annotation = it.getAnnotation(RunWith::class.java)
                if (annotation.value == BarometerTester::class)
                    classes.add(it)
            })
            scanner.scan()
        } catch (e: Exception) {
            System.err.println("Could not get tests to run with BarometerTester:")
            e.printStackTrace(System.err)
        }
        return classes
    }
}

class TestRunListener : RunListener() {
    @Throws(Exception::class)
    override fun testStarted(description: Description) {
        println("Test started: ${description.className}#${description.methodName}")
    }
}
