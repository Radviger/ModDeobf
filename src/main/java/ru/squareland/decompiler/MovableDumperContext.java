package ru.squareland.decompiler;

public class MovableDumperContext {
    BlockCommentState inBlockComment = BlockCommentState.Not;
    boolean atStart = true;
    boolean pendingCR = false;
    boolean classDeclaration = false;
    int indent;
    int outputCount = 0;
    int currentLine = 1; // lines are 1 based.  Sigh.
}

