package flank.corellium.api

/**
 * Structured representation of the parsed test apk file.
 * @property packageName Parsed apk package name.

 */
data class TestApk(
    val packageName: String,
    val testCases: List<String>
) {


    interface Parse : (LocalPath) -> TestApk

    /**
     * @return The full list of test methods from parsed apk.
     */
    interface ParseTestCases: (LocalPath) -> TestCases

    /**
     * @return The package name for given apk.
     */
    interface ParsePackageName: (LocalPath) -> PackageName
}

/**
 * Local path to the test apk file.
 */
private typealias LocalPath = String

/**
 * The full package name for example: "example.full.package.name"
 */
private typealias PackageName = String

/**
 * List of test method names, where test method name is matching format: "package.name.ClassName#testCaseName"
 */
private typealias TestCases = List<String>
