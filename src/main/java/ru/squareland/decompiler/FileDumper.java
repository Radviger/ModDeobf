package ru.squareland.decompiler;

import org.benf.cfr.reader.bytecode.analysis.loc.HasByteCodeLoc;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.Pair;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.QuotingUtils;
import org.benf.cfr.reader.bytecode.analysis.types.ClassNameUtils;
import org.benf.cfr.reader.bytecode.analysis.types.JavaRefTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.MethodPrototype;
import org.benf.cfr.reader.entities.Method;
import org.benf.cfr.reader.mapping.NullMapping;
import org.benf.cfr.reader.mapping.ObfuscationMapping;
import org.benf.cfr.reader.state.TypeUsageInformation;
import org.benf.cfr.reader.util.getopt.Options;
import org.benf.cfr.reader.util.getopt.OptionsImpl;
import org.benf.cfr.reader.util.output.*;

import java.io.*;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class FileDumper implements Dumper {
    private final File dir;
    private final String encoding;
    private final boolean clobber;
    private final JavaTypeInstance type;
    private final SummaryDumper summaryDumper;
    private final String path;
    private final BufferedWriter writer;
    private final AtomicInteger truncCount;

    private final TypeUsageInformation typeUsageInformation;
    protected final Options options;
    protected final IllegalIdentifierDump illegalIdentifierDump;
    protected static final String STANDARD_INDENT = "    ";
    private final MovableDumperContext context = new MovableDumperContext();
    private final boolean convertUTF;
    protected final Set<JavaTypeInstance> emitted = new HashSet<>();

    private static final int MAX_FILE_LEN_MINUS_EXT = 249;
    private static final int TRUNC_PREFIX_LEN = 150;

    private String mkFilename(File dir, Pair<String, String> names, SummaryDumper summaryDumper) {
        String packageName = names.getFirst();
        String className = names.getSecond();
        if (className.length() > MAX_FILE_LEN_MINUS_EXT) {
            /*
             * Have to try to find a replacement name.
             */
            className = className.substring(0, TRUNC_PREFIX_LEN) + "_cfr_" + truncCount.getAndIncrement();
            summaryDumper.notify("Class name " + names.getSecond() + " was shortened to " + className + " due to filesystem limitations.");
        }

        return dir + File.separator + packageName.replace(".", File.separator) + ((packageName.length() == 0) ? "" : File.separator) + className + ".java";
    }

    public FileDumper(File dir, String encoding, boolean clobber, JavaTypeInstance type, SummaryDumper summaryDumper, TypeUsageInformation typeUsageInformation, Options options, AtomicInteger truncCount, IllegalIdentifierDump illegalIdentifierDump) {
        this.options = options;
        this.convertUTF = options.getOption(OptionsImpl.HIDE_UTF8);
        this.typeUsageInformation = typeUsageInformation;
        this.illegalIdentifierDump = illegalIdentifierDump;
        this.truncCount = truncCount;
        this.dir = dir;
        this.encoding = encoding;
        this.clobber = clobber;
        this.type = type;
        this.summaryDumper = summaryDumper;
        String fileName = mkFilename(dir, ClassNameUtils.getPackageAndClassNames(type), summaryDumper);
        try {
            File file = new File(fileName);
            File parent = file.getParentFile();
            if (!parent.exists() && !parent.mkdirs()) {
                throw new IllegalStateException("Couldn't create dir: " + parent);
            }
            if (file.exists() && !clobber) {
                throw new RuntimeException("File already exists, and option '" + OptionsImpl.CLOBBER_FILES.getName() + "' not set");
            }
            path = fileName;

            if (encoding != null) {
                try {
                    writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), encoding));
                } catch (UnsupportedEncodingException e) {
                    throw new UnsupportedOperationException("Specified encoding '" + encoding + "' is not supported");
                }
            } else {
                writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file)));
            }
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() {
        try {
            writer.close();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    protected void write(String s) {
        if (context.inBlockComment != BlockCommentState.Not) {
            return;
        }
        try {
            writer.write(s);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    String getFileName() {
        return path;
    }

    @Override
    public void addSummaryError(Method method, String s) {
        summaryDumper.notifyError(type, method, s);
    }

    @Override
    public Dumper withTypeUsageInformation(TypeUsageInformation innerclassTypeUsageInformation) {
        return new TypeOverridingDumper(this, innerclassTypeUsageInformation);
    }

    @Override
    public BufferedOutputStream getAdditionalOutputStream(String description) {
        String fileName = mkFilename(dir, ClassNameUtils.getPackageAndClassNames(type), summaryDumper);
        fileName = fileName + "." + description;
        try {
            File file = new File(fileName);
            File parent = file.getParentFile();
            if (!parent.exists() && !parent.mkdirs()) {
                throw new IllegalStateException("Couldn't create dir: " + parent);
            }
            if (file.exists() && !clobber) {
                throw new RuntimeException("File already exists, and option '" + OptionsImpl.CLOBBER_FILES.getName() + "' not set");
            }
            return new BufferedOutputStream(new FileOutputStream(file));
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }

    }

    @Override
    public TypeUsageInformation getTypeUsageInformation() {
        return typeUsageInformation;
    }

    @Override
    public ObfuscationMapping getObfuscationMapping() {
        return NullMapping.INSTANCE;
    }

    @Override
    public Dumper label(String s, boolean inline) {
        processPendingCR();
        if (inline) {
            doIndent();
            write(s + ": ");
        } else {
            write(s + ":");
            newln();
        }
        return this;
    }

    @Override
    public Dumper identifier(String s, Object ref, boolean defines) {
        return print(illegalIdentifierDump.getLegalIdentifierFor(s));
    }

    @Override
    public Dumper methodName(String s, MethodPrototype p, boolean special, boolean defines) {
        return identifier(s, null, defines);
    }

    @Override
    public Dumper packageName(JavaRefTypeInstance t) {
        String s = t.getPackageName();
        if (!s.isEmpty()) {
            keyword("package ").print(s).endCodeln().newln();
        }
        return this;
    }

    @Override
    public Dumper print(String s) {
        processPendingCR();
        doIndent();
        boolean doNewLn = false;
        if (s.endsWith("\n")) { // this should never happen.
            s = s.substring(0, s.length() - 1);
            doNewLn = true;
        }
        if (convertUTF) s = QuotingUtils.enquoteUTF(s);
        write(s);
        context.atStart = false;
        if (doNewLn) {
            newln();
        }
        context.outputCount++;
        return this;
    }

    @Override
    public Dumper print(char c) {
        return print("" + c);
    }

    @Override
    public Dumper keyword(String s) {
        if (s.equals("class ")) {
            context.classDeclaration = true;
        }
        print(s);
        return this;
    }

    @Override
    public Dumper operator(String s) {
        print(s);
        return this;
    }

    @Override
    public Dumper separator(String s) {
        print(s);
        return this;
    }

    @Override
    public Dumper literal(String s, Object o) {
        print(s);
        return this;
    }

    @Override
    public Dumper newln() {
        if (context.classDeclaration) {
            write(" ");
            return this;
        }
        if (context.pendingCR) {
            write("\n");
            context.currentLine++;
            if (context.atStart && context.inBlockComment != BlockCommentState.Not) {
                doIndent();
            }
        }
        context.pendingCR = true;
        context.atStart = true;
        context.outputCount++;
        return this;
    }

    @Override
    public Dumper endCodeln() {
        write(";");
        context.pendingCR = true;
        context.atStart = true;
        context.outputCount++;
        return this;
    }

    private void doIndent() {
        if (!context.atStart) return;
        for (int x = 0; x < context.indent; ++x) write(STANDARD_INDENT);
        context.atStart = false;
    }

    private void processPendingCR() {
        if (context.pendingCR) {
            write("\n");
            context.atStart = true;
            context.pendingCR = false;
            context.currentLine++;
        }
    }

    @Override
    public Dumper explicitIndent() {
        print(STANDARD_INDENT);
        return this;
    }

    @Override
    public void indent(int diff) {
        context.indent += diff;
    }

    @Override
    public Dumper fieldName(String name, JavaTypeInstance owner, boolean hiddenDeclaration, boolean isStatic, boolean defines) {
        identifier(name, null, defines);
        return this;
    }

    @Override
    public Dumper dump(JavaTypeInstance javaTypeInstance, TypeContext typeContext) {
        javaTypeInstance.dumpInto(this, typeUsageInformation, typeContext);
        return this;
    }

    @Override
    public Dumper dump(Dumpable d) {
        if (d == null) {
            return keyword("null");
        }
        return d.dump(this);
    }

    @Override
    public boolean canEmitClass(JavaTypeInstance type) {
        return emitted.add(type);
    }

    @Override
    public int getOutputCount() {
        return context.outputCount;
    }

    @Override
    public int getCurrentLine() {
        int res = context.currentLine;
        if (context.pendingCR) res++;
        return res;
    }


    @Override
    public Dumper beginBlockComment(boolean inline) {
        if (context.inBlockComment != BlockCommentState.Not) {
            throw new IllegalStateException("Attempt to nest block comments.");
        }
        context.inBlockComment = inline ? BlockCommentState.InLine : BlockCommentState.In;
        return this;
    }

    @Override
    public Dumper endBlockComment() {
        if (context.inBlockComment == BlockCommentState.Not) {
            throw new IllegalStateException("Attempt to end block comment when not in one.");
        }
        context.inBlockComment = BlockCommentState.Not;
        return this;
    }

    @Override
    public Dumper comment(String s) {
        return this;
    }

    @Override
    public void enqueuePendingCarriageReturn() {
        context.pendingCR = true;
    }

    @Override
    public Dumper dump(JavaTypeInstance javaTypeInstance) {
        return dump(javaTypeInstance, TypeContext.None);
    }

    @Override
    public Dumper removePendingCarriageReturn() {
        context.pendingCR = false;
        context.atStart = false;
        context.classDeclaration = false;
        return this;
    }

    @Override
    public int getIndentLevel() {
        return context.indent;
    }

    @Override
    public void informBytecodeLoc(HasByteCodeLoc loc) {
    }
}
