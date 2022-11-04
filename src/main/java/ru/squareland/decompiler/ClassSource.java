package ru.squareland.decompiler;

import org.benf.cfr.reader.api.ClassFileSource;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.Pair;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

public class ClassSource implements ClassFileSource {
    private final JarFile input;
    private final JarFile version;

    public ClassSource(File input, File version) throws IOException {
        this.input = new JarFile(input);
        this.version = version.exists() ? new JarFile(version) : null;
    }

    @Override
    public void informAnalysisRelativePathDetail(String usePath, String specPath) {

    }

    @Override
    public Collection<String> addJar(String jarPath) {
        System.err.println("Adding jar: " + jarPath);
        List<String> result = new ArrayList<>();
        addJarEntries(input, result);
        if (version != null) {
            addJarEntries(version, result);
        }
        return result;
    }

    private void addJarEntries(JarFile file, Collection<String> output) {
        Enumeration<JarEntry> entries = input.entries();
        while (entries.hasMoreElements()) {
            JarEntry e = entries.nextElement();
            output.add(e.getRealName());
        }
    }

    @Override
    public String getPossiblyRenamedPath(String path) {
        return path;
    }

    @Override
    public Pair<byte[], String> getClassFileContent(String inputPath) throws IOException {
        try (InputStream is = input.getInputStream(new ZipEntry(inputPath))) {
            if (is == null && version != null) {
                try (InputStream ls = version.getInputStream(new ZipEntry(inputPath))) {
                    if (ls != null) {
                        System.err.println("Found library class " + inputPath);
                    } else {
                        System.err.println("Class file not found " + inputPath);
                    }
                    return new Pair<>(ls.readAllBytes(), inputPath);
                }
            }
            return new Pair<>(is.readAllBytes(), inputPath);
        }
    }
}
