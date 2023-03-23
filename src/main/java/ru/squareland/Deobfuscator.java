package ru.squareland;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;
import ru.squareland.mapper.Mapper;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class Deobfuscator {
    private final Mapper[] mappers;
    private final Map<String, byte[]> bytecode = new HashMap<>();
    private final Map<String, byte[]> produced = new HashMap<>();
    private final Map<String, ClassNode> processed = new ConcurrentHashMap<>();
    public int step = 0;

    public void trace(String line) {
        String b = "\t".repeat(Math.max(0, step)) + line;
        System.out.println(b);
    }

    public void etrace(String line) {
        String b = "\t".repeat(Math.max(0, step)) + line;
        System.out.println(b);
    }

    public Deobfuscator(Mapper... mappers) {
        this.mappers = mappers;
        for (Mapper mapper : mappers) {
            mapper.setDeobfuscator(this);
        }
    }

    public void loadRelations(File library) throws IOException {
        for (Mapper mapper : mappers) {
            mapper.loadRelations(library);
        }
    }

    public ClassNode loadNode(String name, HashSet<String> skip) {
        ClassNode p = this.processed.get(name);
        if (p != null) {
            return p;
        }
        byte[] cached = findLoaded(name);
        ClassNode node = cached != null ? process(cached, skip) : null;
        if (node != null) {
            trace("Processed node: " + node.name);
            this.processed.put(name, node);
        }
        return node;
    }

    public byte[] findLoaded(String name) {
        return bytecode.get(name);
    }

    public void remap(File inputFile, File outputFile) throws IOException {
        this.bytecode.clear();
        this.produced.clear();

        try (var output = new JarOutputStream(new FileOutputStream(outputFile))) {
            try (var input = new ZipFile(inputFile)) {
                Enumeration<? extends ZipEntry> entries = input.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    try (InputStream contents = input.getInputStream(entry)) {
                        String name = entry.getName();
                        if (!name.endsWith(".class")) {
                            output.putNextEntry(entry);
                            contents.transferTo(output);
                            continue;
                        }
                        trace("Loaded file " + name);
                        bytecode.put(name.substring(0, name.length() - 6), contents.readAllBytes());
                    }
                }
            }
            HashSet<String> skip = new HashSet<>(Set.of("java/lang/Object"));
            for (Map.Entry<String, byte[]> e : bytecode.entrySet()) {
                ClassNode node = process(e.getValue(), skip);
                if (!produced.containsKey(node.name)) {
                    trace("Processed node2: " + node.name);
                    processed.put(node.name, node);
                }
            }
            for (Map.Entry<String, byte[]> e : produced.entrySet()) {
                String name = e.getKey();
                byte[] bytes = e.getValue();
                JarEntry entry = new JarEntry(name);
                output.putNextEntry(entry);
                try (InputStream cs = new ByteArrayInputStream(bytes)) {
                    cs.transferTo(output);
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
        }
    }

    ClassNode process(byte[] code, HashSet<String> skip) {
        var reader = new ClassReader(code);
        var node = new ClassNode();
        reader.accept(node, ClassReader.EXPAND_FRAMES);
        String fileName = node.name + ".class";
        if (produced.containsKey(fileName) || skip.contains(node.name)) {
            etrace("Already processed node: " + fileName);
            // Already processed
            return node;
        }

        boolean mapped = false;

        for (Mapper mapper : mappers) {
            mapped |= mapper.process(node, skip);
        }

        byte[] output;
        if (mapped) {
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(writer);
            etrace("Mapped class " + node.name);
            output = writer.toByteArray();
        } else {
            output = code;
        }
        produced.put(fileName, output);
        return node;
    }
}
