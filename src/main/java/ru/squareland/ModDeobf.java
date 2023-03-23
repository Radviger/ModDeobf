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

public class ModDeobf {
    public static void main(String[] programArgs) throws Exception {
        String version = "1.4.7";
        File versionFile = new File(version + ".jar");
        File deobfVersionFile = new File(version +  ".deobf.jar");

        Notch2SrgMapper srg = new Notch2SrgMapper(version, "packaged");
        Srg2CsvMapper csv = new Srg2CsvMapper(version);
        Deobfuscator de = new Deobfuscator(srg, csv);

        if (!deobfVersionFile.exists()) {
            de.remap(versionFile, deobfVersionFile);
        }  else {
            de.loadRelations(deobfVersionFile);
        }

        boolean decompile = false;

        File inputFile = new File("appeng-rv9-i.zip");
        File outputFile = new File("appeng-rv9-i.deobf.jar");

        de.remap(inputFile, outputFile);

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