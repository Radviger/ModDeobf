package ru.squareland;

import org.jetbrains.java.decompiler.main.decompiler.CancelationManager;
import org.jetbrains.java.decompiler.main.decompiler.PrintStreamLogger;
import ru.squareland.decompiler.ConsoleDecompiler;
import ru.squareland.mapper.Notch2SrgMapper;
import ru.squareland.mapper.Srg2CsvMapper;

import java.io.File;
import java.io.FileReader;
import java.util.HashMap;
import java.util.Map;
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
            Map<String, Object> mapOptions = new HashMap<>();
            mapOptions.put("include-runtime", "current");
            mapOptions.put("decompile-generics", "");
            mapOptions.put("indent-string", "    ");

            PrintStreamLogger logger = new PrintStreamLogger(System.out);
            ConsoleDecompiler decompiler = new ConsoleDecompiler(decompileDir, mapOptions, logger, ConsoleDecompiler.SaveType.FOLDER);

            if (deobfVersionFile.exists()) {
                decompiler.addLibrary(deobfVersionFile);
            }

            decompiler.addSource(outputFile);

//            decompiler.addWhitelist(prefix);

            try {
                decompiler.decompileContext();
            } catch (CancelationManager.CanceledException var16) {
                System.out.println("Decompilation canceled");
            }
        }
    }
}