package ru.squareland.decompiler;

import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.state.TypeUsageInformation;
import org.benf.cfr.reader.util.getopt.Options;
import org.benf.cfr.reader.util.output.*;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

public class DumperFactory extends InternalDumperFactoryImpl {
    private final AtomicInteger truncCount;
    private final Options options;
    private final File outputDir;

    public DumperFactory(Options options, File outputDir) {
        super(options);
        this.options = options;
        this.outputDir = outputDir;
        truncCount = new AtomicInteger();
    }

    @Override
    public ProgressDumper getProgressDumper() {
        return new ProgressDumper() {
            @Override
            public void analysingType(JavaTypeInstance type) {
                System.err.println("Decompiling class " + type.getRawName());
            }

            @Override
            public void analysingPath(String path) {
            }
        };
    }

    @Override
    public Dumper getNewTopLevelDumper(JavaTypeInstance classType, SummaryDumper summaryDumper, TypeUsageInformation typeUsageInformation, IllegalIdentifierDump illegalIdentifierDump) {
        return new FileDumper(outputDir, StandardCharsets.UTF_8.name(), false, classType, summaryDumper, typeUsageInformation, options, truncCount, illegalIdentifierDump);
    }
}
