package com.mochame.support

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.runner.RunWith

///**
// * Map the ghost to the real JVM/Android JUnit runner type.
// */
//actual typealias PlatformRunner = Runner
//
///**
// * Map the bridge to the real RunWith annotation.
// */
//actual typealias MochaRunWith = RunWith


@RunWith(AndroidJUnit4::class) // Pre-configure the runner here!
actual annotation class MochaTestSuite