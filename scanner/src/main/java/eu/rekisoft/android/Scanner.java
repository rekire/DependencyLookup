package eu.rekisoft.android;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

public class Scanner {
    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            System.out.println("Expecting args [outfile prefix] and [input directory]");
            return;
        }
        String prefix = args[0];
        File repo = new File(args[1]);

        PrintWriter versionConfig = new PrintWriter(prefix + "-versions.json");
        versionConfig.print("[\"");
        boolean first = true;
        List<String> versions = new ArrayList<>();
        versions.addAll(getVersionNumbers(repo));
        versions.sort(new VersionComparator());
        for (String versionFilter : versions) {
            PrintWriter out = new PrintWriter(prefix + "-" + versionFilter + ".csv");
            out.print(dumpVersion(repo, versionFilter));
            out.close();
            if (first) {
                first = false;
            } else {
                versionConfig.append("\",\"");
            }
            versionConfig.print(versionFilter);
        }
        versionConfig.print("\"]");
        versionConfig.close();
    }

    public static String dumpVersion(File repo, String filterVersion) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (File module : repo.listFiles()) {
            String moduleName = module.getPath().substring(module.getPath().lastIndexOf(File.separator) + 1);
            if (module.isDirectory()) {
                for (File version : module.listFiles()) {
                    String versionNumber = version.getPath().substring(version.getPath().lastIndexOf(File.separator) + 1);
                    if (version.isDirectory() && version.getPath().endsWith(filterVersion)) {
                        File aarFile = new File(version, moduleName + "-" + versionNumber + ".aar");
                        if (aarFile.exists()) {
                            try (ZipFile zipFile = new ZipFile(aarFile)) {
                                Enumeration aarEntries = zipFile.entries();
                                while (aarEntries.hasMoreElements()) {
                                    ZipEntry aarEntry = (ZipEntry) aarEntries.nextElement();
                                    String jarFileName = aarEntry.getName();
                                    if ("classes.jar".equals(jarFileName)) {
                                        InputStream jar = zipFile.getInputStream(aarEntry);
                                        dumpJar(sb, moduleName, jar);
                                    }
                                }
                            }
                        } else {
                            File jarFile = new File(version, moduleName + "-" + versionNumber + ".jar");
                            if(jarFile.exists()) {
                                InputStream jar = new FileInputStream(jarFile);
                                dumpJar(sb, moduleName, jar);
                            } else {
                                System.err.println("Could not find classes for module " + moduleName + ":" + versionNumber);
                            }
                        }
                    }
                }
            }
        }
        return sb.toString();
    }

    private static void dumpJar(StringBuilder sb, String moduleName, InputStream jar) throws IOException {
        try (ZipInputStream classesJar = new ZipInputStream(jar)) {
            ZipEntry jarEntry;
            while ((jarEntry = classesJar.getNextEntry()) != null) {
                String fileName = jarEntry.getName();
                if (fileName.endsWith(".class")) {
                    sb.append(moduleName).append(";").append(fileName.replace("/", ".").replace(".class", "")).append("\r\n");
                }
            }
        }
    }

    public static Set<String> getVersionNumbers(File repo) {
        Set<String> versions = new HashSet<>();
        for (File module : repo.listFiles()) {
            if (module.isDirectory()) {
                for (File version : module.listFiles()) {
                    String versionNumber = version.getPath().substring(version.getPath().lastIndexOf(File.separator) + 1);
                    if (!versionNumber.contains("maven") && !version.getPath().contains("test")) {
                        versions.add(versionNumber);
                    }
                }
            }
        }
        return versions;
    }

    // based on: http://stackoverflow.com/a/41200394/995926
    private static class VersionComparator implements Comparator<String> {
        @Override
        public int compare(String version1, String version2) {
            String[] arr1 = version1.split("\\.");
            String[] arr2 = version2.split("\\.");

            if (arr1.length < arr2.length)
                return -1;
            if (arr1.length > arr2.length)
                return 1;

            // same number of version "." dots
            for (int i = 0; i < arr1.length; i++) {
                try {
                    if (Integer.parseInt(arr1[i]) < Integer.parseInt(arr2[i]))
                        return -1;
                    if (Integer.parseInt(arr1[i]) > Integer.parseInt(arr2[i]))
                        return 1;
                } catch (NumberFormatException e) {
                    return arr1[i].compareToIgnoreCase(arr2[i]);
                }
            }
            // went through all version numbers and they are all the same
            return 0;
        }
    }
}