package ru.squareland;

import org.benf.cfr.reader.Main;
import org.benf.cfr.reader.state.ClassFileSourceWrapper;
import org.benf.cfr.reader.state.DCCommonState;
import org.benf.cfr.reader.util.getopt.Options;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;
import ru.squareland.decompiler.ClassSource;
import ru.squareland.decompiler.DecompilationOptions;
import ru.squareland.decompiler.DumperFactory;
import ru.squareland.mapper.Mapper;
import ru.squareland.mapper.Notch2SrgMapper;
import ru.squareland.mapper.Srg2CsvMapper;

import java.io.*;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ModDeobf {
    public static void remap(List<Mapper> mappers, File inputFile, File outputFile, File versionFile) throws IOException {
        if (versionFile != null) {
            for (Mapper mapper : mappers) {
                mapper.loadRelations(versionFile);
            }
        }
        try (var output = new JarOutputStream(new FileOutputStream(outputFile))) {
            try (var input = new ZipFile(inputFile)) {
                Enumeration<? extends ZipEntry> entries = input.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    try (InputStream contents = input.getInputStream(entry)) {
                        if (!entry.getName().endsWith(".class")) {
                            output.putNextEntry(entry);
                            contents.transferTo(output);
                            continue;
                        }
                        var reader = new ClassReader(contents);
                        var node = new ClassNode();
                        reader.accept(node, ClassReader.EXPAND_FRAMES);

                        boolean mapped = false;

                        for (Mapper mapper : mappers) {
                            mapped |= mapper.process(node);
                        }

                        if (mapped) {
                            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
                            node.accept(writer);
                            System.err.println("Mapped class " + node.name);
                            ByteArrayInputStream bis = new ByteArrayInputStream(writer.toByteArray());
                            output.putNextEntry(new JarEntry(node.name + ".class"));
                            bis.transferTo(output);
                        } else {
                            output.putNextEntry(entry);
                            try (InputStream cs = input.getInputStream(entry)) {
                                cs.transferTo(output);
                            }
                        }
                    }
                }
            }
        }
    }

    public static void deobfVersionJar(String version) throws Exception {
        File versionFile = new File(version + ".jar");
        File deobfVersionFile = new File(version + ".deobf.jar");
        Notch2SrgMapper srg = new Notch2SrgMapper(version, "client");
        Srg2CsvMapper csv = new Srg2CsvMapper(version);

        remap(List.of(srg, csv), versionFile, deobfVersionFile, null);
    }

    public static void main(String[] programArgs) throws Exception {
        String version = "1.12.2";
        File versionFile = new File(version + ".jar");
        File deobfVersionFile = new File(version +  ".deobf.jar");

        if (!deobfVersionFile.exists()) {
            deobfVersionJar(version);
        }

        boolean decompile = true;

        File inputFile = new File("Biosphere-Mod-1.6.2.zip");
        File outputFile = new File("Biosphere-Mod-1.6.2.deobf.jar");

        Notch2SrgMapper srg = new Notch2SrgMapper(version, "client");
        Srg2CsvMapper csv = new Srg2CsvMapper(version);

        remap(List.of(srg, csv), inputFile, outputFile, versionFile);

        System.out.println("Remapping done");

        if (decompile) {
            File decompileDir = new File("decompile");
            Options options = new DecompilationOptions(deobfVersionFile);

            DCCommonState dcCommonState = new DCCommonState(options, new ClassFileSourceWrapper(new ClassSource(outputFile, deobfVersionFile)));
            DumperFactory dumperFactory = new DumperFactory(options, decompileDir);
            Main.doJar(dcCommonState, outputFile.toString(), dumperFactory);
        }
    }

}