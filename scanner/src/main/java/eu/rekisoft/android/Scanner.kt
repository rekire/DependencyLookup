package eu.rekisoft.android

import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.io.PrintWriter
import java.util.ArrayList
import java.util.Comparator
import java.util.Enumeration
import java.util.HashSet
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

object Scanner {
    fun main(args: Array<String>) {
        if (args.size != 2) {
            println("Expecting args [outfile prefix] and [input directory]")
            return
        }
        val prefix = args[0]
        val repo = File(args[1])

        val versionConfig = PrintWriter(prefix + "-versions.json")
        versionConfig.print("[\"")
        var first = true
        val versions = ArrayList<String>()
        versions.addAll(getVersionNumbers(repo))
        versions.sort(VersionComparator())
        for (versionFilter in versions) {
            val out = PrintWriter("$prefix-$versionFilter.csv")
            out.print(dumpVersion(repo, versionFilter))
            out.close()
            if (first) {
                first = false
            } else {
                versionConfig.append("\",\"")
            }
            versionConfig.print(versionFilter)
        }
        versionConfig.print("\"]")
        versionConfig.close()
    }

    @Throws(IOException::class)
    fun dumpVersion(repo: File, filterVersion: String): String {
        val sb = StringBuilder()
        for (module in repo.listFiles()!!) {
            val moduleName = module.path.substring(module.path.lastIndexOf(File.separator) + 1)
            if (module.isDirectory) {
                for (version in module.listFiles()!!) {
                    val versionNumber = version.path.substring(version.path.lastIndexOf(File.separator) + 1)
                    if (version.isDirectory && version.path.endsWith(filterVersion)) {
                        val aarFile = File(version, moduleName + "-" + versionNumber + ".aar")
                        if (aarFile.exists()) {
                            ZipFile(aarFile).use { zipFile ->
                                val aarEntries = zipFile.entries()
                                while (aarEntries.hasMoreElements()) {
                                    val aarEntry = aarEntries.nextElement()
                                    val jarFileName = aarEntry.name
                                    if ("classes.jar" == jarFileName) {
                                        val jar = zipFile.getInputStream(aarEntry)
                                        dumpJar(sb, moduleName, jar)
                                    }
                                }
                            }
                        } else {
                            val jarFile = File(version, moduleName + "-" + versionNumber + ".jar")
                            if (jarFile.exists()) {
                                val jar = FileInputStream(jarFile)
                                dumpJar(sb, moduleName, jar)
                            } else {
                                System.err.println("Could not find classes for module $moduleName:$versionNumber")
                            }
                        }
                    }
                }
            }
        }
        return sb.toString()
    }

    @Throws(IOException::class)
    private fun dumpJar(sb: StringBuilder, moduleName: String, jar: InputStream) {
        ZipInputStream(jar).use { classesJar ->
            var jarEntry: ZipEntry
            while ((jarEntry = classesJar.getNextEntry()) != null) {
                val fileName = jarEntry.name
                if (fileName.endsWith(".class")) {
                    sb.append(moduleName).append(";").append(fileName.replace("/", ".").replace(".class", "")).append("\r\n")
                }
            }
        }
    }

    fun getVersionNumbers(repo: File): Set<String> {
        val versions = HashSet<String>()
        for (module in repo.listFiles()!!) {
            if (module.isDirectory) {
                for (version in module.listFiles()!!) {
                    val versionNumber = version.path.substring(version.path.lastIndexOf(File.separator) + 1)
                    if (!versionNumber.contains("maven") && !version.path.contains("test")) {
                        versions.add(versionNumber)
                    }
                }
            }
        }
        return versions
    }

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
}