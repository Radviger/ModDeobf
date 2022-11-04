package ru.squareland.mapper;

import org.objectweb.asm.tree.ClassNode;

import java.io.File;
import java.io.IOException;

public interface Mapper {
    boolean process(ClassNode node);

    void loadRelations(File file) throws IOException;
}
