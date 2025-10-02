package ru.squareland.decompiler;

import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.main.Fernflower;
import org.jetbrains.java.decompiler.main.decompiler.ConsoleFileSaver;
import org.jetbrains.java.decompiler.main.decompiler.DirectoryResultSaver;
import org.jetbrains.java.decompiler.main.decompiler.SingleFileSaver;
import org.jetbrains.java.decompiler.main.extern.IContextSource;
import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger;
import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger.Severity;
import org.jetbrains.java.decompiler.main.extern.IResultSaver;
import org.jetbrains.java.decompiler.util.InterpreterUtil;
import org.jetbrains.java.decompiler.util.ZipFileCache;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class ConsoleDecompiler implements IResultSaver, AutoCloseable {
    private final File root;
    private final Fernflower engine;
    private final Map<String, ZipOutputStream> mapArchiveStreams;
    private final Map<String, Set<String>> mapArchiveEntries;
    private final ZipFileCache openZips;

    public ConsoleDecompiler(File destination, Map<String, Object> options, IFernflowerLogger logger) {
        this(destination, options, logger, destination.isDirectory() ? ConsoleDecompiler.SaveType.LEGACY_CONSOLEDECOMPILER : ConsoleDecompiler.SaveType.FILE);
    }

    public ConsoleDecompiler(File destination, Map<String, Object> options, IFernflowerLogger logger, SaveType saveType) {
        this.mapArchiveStreams = new HashMap<>();
        this.mapArchiveEntries = new HashMap<>();
        this.openZips = new ZipFileCache();
        this.root = destination;
        this.engine = new Fernflower(saveType == SaveType.LEGACY_CONSOLEDECOMPILER ? this : saveType.getSaver().apply(destination), options, logger);
    }

    public void addSource(File source) {
        this.engine.addSource(source);
    }

    public void addLibrary(File library) {
        this.engine.addLibrary(library);
    }

    public void addLibrary(IContextSource source) {
        this.engine.addLibrary(source);
    }

    public void addWhitelist(String prefix) {
        this.engine.addWhitelist(prefix);
    }

    public void decompileContext() {
        try {
            this.engine.decompileContext();
        } finally {
            this.engine.clearContext();
        }

    }

    /** @deprecated */
    @Deprecated
    public byte[] getBytecode(String externalPath, String internalPath) throws IOException {
        if (internalPath == null) {
            File file = new File(externalPath);
            return InterpreterUtil.getBytes(file);
        } else {
            ZipFile archive = this.openZips.get(externalPath);
            ZipEntry entry = archive.getEntry(internalPath);
            if (entry == null) {
                throw new IOException("Entry not found: " + internalPath);
            } else {
                return InterpreterUtil.getBytes(archive, entry);
            }
        }
    }

    private String getAbsolutePath(String path) {
        return (new File(this.root, path)).getAbsolutePath();
    }

    public void saveFolder(String path) {
        File dir = new File(this.getAbsolutePath(path));
        if (!dir.mkdirs() && !dir.isDirectory()) {
            throw new RuntimeException("Cannot create directory " + String.valueOf(dir));
        }
    }

    public void copyFile(String source, String path, String entryName) {
        try {
            InterpreterUtil.copyFile(new File(source), new File(this.getAbsolutePath(path), entryName));
        } catch (IOException ex) {
            DecompilerContext.getLogger().writeMessage("Cannot copy " + source + " to " + entryName, ex);
        }

    }

    public void saveClassFile(String path, String qualifiedName, String entryName, String content, int[] mapping) {
        File file = new File(this.getAbsolutePath(path), entryName);
        if (content != null) {
            try (Writer out = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
                out.write(content);
            } catch (IOException ex) {
                DecompilerContext.getLogger().writeMessage("Cannot write class file " + String.valueOf(file), ex);
            }
        } else {
            DecompilerContext.getLogger().writeMessage("Attempted to write null class file to " + String.valueOf(file), Severity.WARN);
        }

    }

    public void createArchive(String path, String archiveName, Manifest manifest) {
        File file = new File(this.getAbsolutePath(path), archiveName);

        try {
            if (!file.createNewFile() && !file.isFile()) {
                throw new IOException("Cannot create file " + String.valueOf(file));
            }

            FileOutputStream fileStream = new FileOutputStream(file);
            ZipOutputStream zipStream = manifest != null ? new JarOutputStream(fileStream, manifest) : new ZipOutputStream(fileStream);
            this.mapArchiveStreams.put(file.getPath(), zipStream);
        } catch (IOException ex) {
            DecompilerContext.getLogger().writeMessage("Cannot create archive " + String.valueOf(file), ex);
        }

    }

    public void saveDirEntry(String path, String archiveName, String entryName) {
        if (entryName.lastIndexOf(47) != entryName.length() - 1) {
            entryName = entryName + "/";
        }

        this.saveClassEntry(path, archiveName, null, entryName, null);
    }

    public void copyEntry(String source, String path, String archiveName, String entryName) {
        String file = (new File(this.getAbsolutePath(path), archiveName)).getPath();
        if (this.checkEntry(entryName, file)) {
            try {
                ZipFile srcArchive = this.openZips.get(source);
                ZipEntry entry = srcArchive.getEntry(entryName);
                if (entry != null) {
                    try (InputStream in = srcArchive.getInputStream(entry)) {
                        ZipOutputStream out = this.mapArchiveStreams.get(file);
                        out.putNextEntry(new ZipEntry(entryName));
                        InterpreterUtil.copyStream(in, out);
                    }
                }
            } catch (IOException ex) {
                String message = "Cannot copy entry " + entryName + " from " + source + " to " + file;
                DecompilerContext.getLogger().writeMessage(message, ex);
            }

        }
    }

    public void saveClassEntry(String path, String archiveName, String qualifiedName, String entryName, String content) {
        this.saveClassEntry(path, archiveName, qualifiedName, entryName, content, null);
    }

    public void saveClassEntry(String path, String archiveName, String qualifiedName, String entryName, String content, int[] mapping) {
        String file = (new File(this.getAbsolutePath(path), archiveName)).getPath();
        if (this.checkEntry(entryName, file)) {
            try {
                ZipOutputStream out = this.mapArchiveStreams.get(file);
                ZipEntry entry = new ZipEntry(entryName);
                if (mapping != null && DecompilerContext.getOption("dump-code-lines")) {
                    entry.setExtra(this.getCodeLineData(mapping));
                }

                out.putNextEntry(entry);
                if (content != null) {
                    out.write(content.getBytes(StandardCharsets.UTF_8));
                }
            } catch (IOException ex) {
                String message = "Cannot write entry " + entryName + " to " + file;
                DecompilerContext.getLogger().writeMessage(message, ex);
            }

        }
    }

    private boolean checkEntry(String entryName, String file) {
        Set<String> set = this.mapArchiveEntries.computeIfAbsent(file, (k) -> new HashSet());
        boolean added = set.add(entryName);
        if (!added) {
            String message = "Zip entry " + entryName + " already exists in " + file;
            DecompilerContext.getLogger().writeMessage(message, Severity.WARN);
        }

        return added;
    }

    public void closeArchive(String path, String archiveName) {
        String file = (new File(this.getAbsolutePath(path), archiveName)).getPath();

        try {
            this.mapArchiveEntries.remove(file);
            ZipOutputStream removed = this.mapArchiveStreams.remove(file);
            if (removed != null) {
                removed.close();
            }
        } catch (IOException var5) {
            DecompilerContext.getLogger().writeMessage("Cannot close " + file, Severity.WARN);
        }

    }

    public void close() throws IOException {
        this.openZips.close();
    }

    public static String version() {
        String ver = ConsoleDecompiler.class.getPackage().getImplementationVersion();
        return ver == null ? "<UNK>" : ver;
    }

    public enum SaveType {
        LEGACY_CONSOLEDECOMPILER(null),
        FOLDER(DirectoryResultSaver::new),
        FILE(SingleFileSaver::new),
        CONSOLE(ConsoleFileSaver::new);

        private final Function<File, IResultSaver> saver;

        SaveType(Function<File, IResultSaver> saver) {
            this.saver = saver;
        }

        public Function<File, IResultSaver> getSaver() {
            return this.saver;
        }
    }
}
