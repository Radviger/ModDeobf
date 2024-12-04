package ru.squareland;

import org.benf.cfr.reader.Main;
import org.benf.cfr.reader.state.ClassFileSourceWrapper;
import org.benf.cfr.reader.state.DCCommonState;
import org.benf.cfr.reader.util.getopt.Options;
import ru.squareland.decompiler.ClassSource;
import ru.squareland.decompiler.DecompilationOptions;
import ru.squareland.decompiler.DumperFactory;
import ru.squareland.mapper.Notch2SrgMapper;
import ru.squareland.mapper.Srg2CsvMapper;

import java.io.File;
import java.io.FileReader;
import java.util.Objects;
import java.util.Properties;

public class ModDeobf {
    public static void main(String[] programArgs) throws Exception {
        Properties profile = new Properties();
        profile.load(new FileReader("profile.properties"));
        String version = Objects.requireNonNull(profile.getProperty("version"), "missing game version");
        File versionFile = new File(version + ".jar");
        File deobfVersionFile = new File(version +  ".deobf.jar");

        File inputFile = new File(Objects.requireNonNull(profile.getProperty("input"), "missing input file"));
        File outputFile = new File(Objects.requireNonNull(profile.getProperty("output"), "missing output file"));

        Notch2SrgMapper srg = new Notch2SrgMapper(version, Objects.requireNonNull(profile.getProperty("side"), "missing mapping side"));
        Srg2CsvMapper csv = new Srg2CsvMapper(version);
        Deobfuscator de = new Deobfuscator(srg, csv);

        if (!deobfVersionFile.exists() && versionFile.exists()) {
            de.remap(versionFile, deobfVersionFile);
        }  else {
            de.loadRelations(deobfVersionFile);
        }

        de.remap(inputFile, outputFile);

        System.out.println("Remapping done");

        boolean decompile = Boolean.parseBoolean(Objects.requireNonNull(profile.getProperty("decompile"), "missing decompile flag"));

        if (decompile) {
            File decompileDir = new File(Objects.requireNonNull(profile.getProperty("decompileDir"), "missing decompileDir"));
            Options options = new DecompilationOptions(deobfVersionFile);

            DCCommonState dcCommonState = new DCCommonState(options, new ClassFileSourceWrapper(new ClassSource(outputFile, deobfVersionFile)));
            DumperFactory dumperFactory = new DumperFactory(options, decompileDir);
            Main.doJar(dcCommonState, outputFile.toString(), dumperFactory);
        }
    }
}