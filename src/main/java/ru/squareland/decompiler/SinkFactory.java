package ru.squareland.decompiler;

import org.benf.cfr.reader.api.OutputSinkFactory;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class SinkFactory implements OutputSinkFactory {
    private String decompile;

    @Override
    public List<SinkClass> getSupportedSinks(SinkType sinkType, Collection<SinkClass> collection) {
        return Arrays.asList(SinkClass.values());
    }

    @Override
    public <T> Sink<T> getSink(SinkType sinkType, SinkClass sinkClass) {
        if (sinkClass == SinkClass.DECOMPILED) {
            System.err.println("DECOMPILED " + sinkType);
        }
        switch(sinkType) {
            case JAVA:
                return this::setDecompilation;
            case EXCEPTION:
                return t -> System.err.println("CFR: " + t);
            case SUMMARY:
            case PROGRESS:
            default:
                return t -> {};
        }
    }

    private <T> void setDecompilation(T value) {
        decompile = value.toString();
        System.err.println("KEK " + value.getClass());
    }

    /**
     * @return Decompiled class content.
     */
    public String getDecompilation() {
        return decompile;
    }
}