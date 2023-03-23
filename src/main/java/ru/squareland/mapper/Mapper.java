package ru.squareland.mapper;

import org.objectweb.asm.tree.ClassNode;
import ru.squareland.Deobfuscator;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;

public abstract class Mapper {
    protected Deobfuscator deobfuscator;

    public abstract boolean process(ClassNode node, HashSet<String> skip);

    public abstract void loadRelations(File file) throws IOException;

    public void setDeobfuscator(Deobfuscator deobfuscator) {
        this.deobfuscator = deobfuscator;
    }
}
