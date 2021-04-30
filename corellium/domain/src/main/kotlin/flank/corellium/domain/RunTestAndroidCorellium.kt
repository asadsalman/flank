package flank.corellium.domain

import flank.corellium.api.AndroidApps
import flank.corellium.api.AndroidInstance
import flank.corellium.api.AndroidTestPlan
import flank.corellium.api.Authorization
import flank.corellium.api.CorelliumApi
import flank.corellium.api.TestApk
import flank.corellium.api.invoke
import flank.corellium.shard.Shard
import flank.corellium.shard.calculateShards
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking

interface RunTestAndroidCorellium {
    val api: CorelliumApi
    val config: Config

    data class Config(
        val credentials: Authorization.Credentials,
        val apks: List<Apk.App>,
        val maxShardsCount: Int,
    )

    sealed class Apk {
        abstract val path: String

        data class App(
            override val path: String,
            val tests: List<Test>,
        ) : Apk()

        data class Test(
            override val path: String
        ) : Apk()
    }
}

fun RunTestAndroidCorellium.invoke(): Unit = runBlocking {

    println("* Authorizing")
    api.authorize(config.credentials)

    println("* Calculating shards")
    val shards: List<List<Shard.App>> = config.apks
        .prepareDataForSharding(api.parseTestCases)
        .calculateShards(config.maxShardsCount)

    println("* Invoking devices")
    val associatedShards: AssociatedShards = api
        .invokeAndroidDevices(AndroidInstance.Config(shards.size))
        .associateShardsToInstances(shards)

    println("* Installing apks")
    val apps: List<AndroidApps> = associatedShards.prepareApkToInstall()
    api.installAndroidApps(apps)

    // If tests will be executed too fast just after the
    // app installed, the instrumentation will fail
    delay(10_000)

    println("* Executing tests")
    val testPlan = associatedShards.prepareTestPlan(api.parsePackageName)

    api.executeTest(testPlan).collect { line ->
        print(line)
    }
    println()
    println("* Finish")
}

private fun List<RunTestAndroidCorellium.Apk.App>.prepareDataForSharding(
    parseTestCases: TestApk.ParseTestCases
): List<Shard.App> =
    map { app ->
        Shard.App(
            name = app.path,
            tests = app.tests.map { test ->
                Shard.Test(
                    name = test.path,
                    cases = parseTestCases(test.path).map(Shard.Test::Case)
                )
            }
        )
    }

private fun List<String>.associateShardsToInstances(
    shards: List<List<Shard.App>>,
): AssociatedShards {
    require(shards.size <= size) { "Not enough instances, required ${shards.size} but was $size" }
    return mapIndexed { index, id -> id to shards[index] }.toMap()
}

private fun AssociatedShards.prepareApkToInstall(): List<AndroidApps> =
    map { (instanceId, list: List<Shard.App>) ->
        AndroidApps(
            instanceId = instanceId,
            paths = list.flatMap { app -> app.tests.map { test -> test.name } + app.name }
        )
    }

private fun AssociatedShards.prepareTestPlan(
    parsePackageName: TestApk.ParsePackageName
) =
    AndroidTestPlan.Config(
        instances = mapValues { (_, shards) ->
            shards.map { shard ->
                shard.tests.map { test ->

                    AndroidTestPlan.Shard(
                        packageName = shard.name,
                        testRunner = "",
                        testCases = shard.tests.map { test ->
                            test.name
                        }
                    )
                }
            }
        }
    )

private typealias AssociatedShards = Map<String, List<Shard.App>>
