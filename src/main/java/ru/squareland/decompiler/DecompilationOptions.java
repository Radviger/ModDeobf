package ru.squareland.decompiler;

import org.benf.cfr.reader.util.getopt.OptionDecoderParam;
import org.benf.cfr.reader.util.getopt.Options;
import org.benf.cfr.reader.util.getopt.OptionsImpl;
import org.benf.cfr.reader.util.getopt.PermittedOptionProvider;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class DecompilationOptions implements Options {
    private static final Method GET_FN;

    static {
        try {
            GET_FN = PermittedOptionProvider.ArgumentParam.class.getDeclaredMethod("getFn");
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
        if (!GET_FN.trySetAccessible()) {
            throw new IllegalStateException("Cannot get ArgumentParam.getFn handle");
        }
    }

    private final Map<String, String> opts = new HashMap<>();

    public DecompilationOptions(File versionFile) {
        opts.put(OptionsImpl.SHOW_CFR_VERSION.getName(), "false");
        opts.put(OptionsImpl.RENAME_ILLEGAL_IDENTS.getName(), "true");
        opts.put(OptionsImpl.OUTPUT_PATH.getName(), "decompile");
        if (versionFile.exists()) {
            opts.put(OptionsImpl.EXTRA_CLASS_PATH.getName(), versionFile.getAbsolutePath());
        }
        opts.put(OptionsImpl.CASE_INSENSITIVE_FS_RENAME.getName(), String.valueOf(System.getProperty("os.name").startsWith("Windows")));
    }


    @Override
    public <T> T getOption(PermittedOptionProvider.ArgumentParam<T, Void> option) {
        return getFn(option).invoke(opts.get(option.getName()), null, this);
    }

    @Override
    public <T, A> T getOption(PermittedOptionProvider.ArgumentParam<T, A> option, A arg) {
        return getFn(option).invoke(opts.get(option.getName()), arg, this);
    }

    @Override
    public boolean optionIsSet(PermittedOptionProvider.ArgumentParam<?, ?> option) {
        return opts.get(option.getName()) != null;
    }

    private <T, A> OptionDecoderParam<T, A> getFn(PermittedOptionProvider.ArgumentParam<T, A> option) {
        try {
            return (OptionDecoderParam<T, A>) GET_FN.invoke(option);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }
}
