package eu.rekisoft.android

import java.io.File
import java.io.InputStream
import java.io.PrintWriter
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

fun main(args: Array<String>) {
    if (args.size != 2) {
        println("Expecting args [outfile prefix] and [input directory]")
        return
    }
    val prefix = args[0]
    val repo = File(args[1])

    val versions = ArrayList<String>()
    versions.addAll(getVersionNumbers(repo))
    versions.sortWith(VersionComparator())

    val versionConfig = PrintWriter("$prefix-versions.json")
    versionConfig.print(versions.joinToString(separator = ",", prefix = "[", postfix = "]") { "\"$it\"" })
    versionConfig.close()

    for (version in versions) {
        val out = PrintWriter("$prefix-$version.csv")
        out.print(dumpVersion(repo, version))
        out.close()
    }
}

fun dumpVersion(repo: File, targetVersion: String): String {
    val sb = StringBuilder()

    repo.listFiles()
            .filter { it.isDirectory }
            .forEach { artifact ->
                val aarFile = File(artifact, "$targetVersion${File.separator}${artifact.lastPathSegment()}-$targetVersion.aar")

                if (aarFile.exists()) {
                    ZipFile(aarFile).use { aar ->
                        aar.entries()
                                .asSequence()
                                .filter { it.name == "classes.jar" }
                                .forEach { aarEntry ->
                                    dumpJar(sb, artifact.lastPathSegment(), aar.getInputStream(aarEntry));
                                }
                    }
                }
    }
    return sb.toString()
}

private fun dumpJar(sb: StringBuilder, moduleName: String, jar: InputStream) {
    ZipInputStream(jar).files()
            .filter { it.name.endsWith(".class") }
            .forEach { jarEntry ->
                sb.append(moduleName).append(";").append(jarEntry.name.replace("/", ".").replace(".class", "")).append("\r\n")
            }


    ZipInputStream(jar).use { classesJar ->
        var jarEntry: ZipEntry? = classesJar.nextEntry
        while (jarEntry != null) {
            val fileName = jarEntry.name
            if (fileName.endsWith(".class")) {
                sb.append(moduleName).append(";").append(fileName.replace("/", ".").replace(".class", "")).append("\r\n")
            }
            jarEntry = classesJar.nextEntry
        }
    }
}

fun File.lastPathSegment() = path.substring(path.lastIndexOf(File.separator) + 1)

fun ZipInputStream.files() = object : Iterable<ZipEntry> {
    override fun iterator() = object : Iterator<ZipEntry> {

        private lateinit var zipEntry: ZipEntry

        override fun hasNext(): Boolean {
            return this@files.nextEntry?.let { entry ->
                zipEntry = entry
                true
            } ?: false
        }

        override fun next() = zipEntry
    }
}

fun getVersionNumbers(repo: File) = repo.listFiles()
        .filter { it.isDirectory }
        .flatMap { directory ->

            directory.listFiles()
                    .filter { !it.path.contains("maven") && !it.path.contains("test") }
                    .map { it.lastPathSegment() }
        }
        .toHashSet()

// based on: http://stackoverflow.com/a/41200394/995926
private class VersionComparator : Comparator<String> {
    override fun compare(version1: String, version2: String): Int {
        val arr1 = version1.split("\\.".toRegex()).dropLastWhile({ it.isEmpty() }).toTypedArray()
        val arr2 = version2.split("\\.".toRegex()).dropLastWhile({ it.isEmpty() }).toTypedArray()

        if (arr1.size < arr2.size)
            return -1
        if (arr1.size > arr2.size)
            return 1

        // same number of version "." dots
        for (i in arr1.indices) {
            try {
                if (Integer.parseInt(arr1[i]) < Integer.parseInt(arr2[i]))
                    return -1
                if (Integer.parseInt(arr1[i]) > Integer.parseInt(arr2[i]))
                    return 1
            } catch (e: NumberFormatException) {
                return arr1[i].compareTo(arr2[i], ignoreCase = true)
            }

        }
        // went through all version numbers and they are all the same
        return 0
    }
}