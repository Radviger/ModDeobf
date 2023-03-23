package ru.squareland.mapper;

import org.objectweb.asm.tree.*;
import ru.squareland.Deobfuscator;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Srg2CsvMapper extends Mapper {
    public static final Pattern ID_PATTERN = Pattern.compile("^[a-z]+_(\\d+)_\\w+");
    private static final Map<String, Integer> IDS = new HashMap<>();

    private final Map<Integer, String> methods = new HashMap<>();
    private final Map<Integer, String> fields = new HashMap<>();
    private final Map<Integer, List<String>> params = new HashMap<>();
    private Deobfuscator deobfuscator;

    public Srg2CsvMapper(String version) throws IOException {
        InputStream mis = Srg2CsvMapper.class.getResourceAsStream("/mappings/" + version + "/methods.csv");
        InputStream fis = Srg2CsvMapper.class.getResourceAsStream("/mappings/" + version + "/fields.csv");
        InputStream pis = Srg2CsvMapper.class.getResourceAsStream("/mappings/" + version + "/params.csv");
        try (BufferedReader mr = new BufferedReader(new InputStreamReader(mis))) {
            String line;
            while ((line = mr.readLine()) != null) {
                String[] parts = line.split(",", 3);
                if (parts.length >= 2) {
                    int id = idOf(parts[0]);
                    if (id >= 0) {
                        methods.put(id, parts[1]);
                    }
                }
            }
        }
        try (BufferedReader fr = new BufferedReader(new InputStreamReader(fis))) {
            String line;
            while ((line =fr.readLine()) != null) {
                String[] parts = line.split(",", 3);
                if (parts.length >= 2) {
                    int id = idOf(parts[0]);
                    if (id >= 0) {
                        fields.put(id, parts[1]);
                    }
                }
            }
        }
        /*if (pis != null) {
            try (BufferedReader pr = new BufferedReader(new InputStreamReader(pis))) {
                String line;
                while ((line = pr.readLine()) != null) {
                    String[] parts = line.split(",", 3);
                    if (parts.length >= 2) {
                        int id = idOf(parts[0]);
                        if (id >= 0) {
                            List<String> p = params.computeIfAbsent(id, i -> new ArrayList<>());
                            p.add(id, parts[1]);
                        }
                    }
                }
            }
        }*/
    }

    public static int idOf(String name) {
        return IDS.computeIfAbsent(name, n -> {
            Matcher matcher = ID_PATTERN.matcher(name);
            if (matcher.matches()) {
                String value = matcher.group(1);
                return Integer.parseInt(value);
            }
            return -1;
        });
    }

    @Override
    public boolean process(ClassNode node, HashSet<String> skip) {
        boolean mapped = false;
        for (FieldNode field : node.fields) {
            int id = idOf(field.name);
            if (id >= 0) {
                mapped = true;
                field.name = Objects.requireNonNull(fields.computeIfAbsent(id, i -> field.name));
            }
        }
        for (MethodNode method : node.methods) {
            int id = idOf(method.name);
            if (id >= 0) {
                mapped = true;
                method.name = Objects.requireNonNull(methods.computeIfAbsent(id, i -> method.name));
            }
            for (AbstractInsnNode insn : method.instructions) {
                if (insn instanceof FieldInsnNode fi) {
                    int fid = idOf(fi.name);
                    if (fid >= 0) {
                        mapped = true;
                        fi.name = Objects.requireNonNull(fields.computeIfAbsent(fid, i -> fi.name));
                    }
                } else if (insn instanceof MethodInsnNode mi) {
                    int mid = idOf(mi.name);
                    if (mid >= 0) {
                        mapped = true;
                        mi.name = Objects.requireNonNull(methods.computeIfAbsent(mid, i -> mi.name));
                    }
                }
            }
        }
        return mapped;
    }

    @Override
    public void loadRelations(File file) {}
}
