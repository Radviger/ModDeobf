package ru.squareland.mapper;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import ru.squareland.ClassMapping;

import java.io.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class Notch2SrgMapper implements Mapper {
    private final Map<String, String> packages = new HashMap<>();
    private final Map<String, ClassMapping> obfuscatedClasses = new HashMap<>();
    private final Map<String, ClassMapping> classes = new HashMap<>();

    public Notch2SrgMapper(String version, String side) throws IOException {
        this(Notch2SrgMapper.class.getResourceAsStream("/mappings/" + version + "/" + side + ".srg"));
    }

    public Notch2SrgMapper(InputStream stream) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.length() > 4) {
                    String operand = line.substring(0, 4);
                    String value = line.substring(4);
                    switch (operand) {
                        case "PK: " -> {
                            String[] parts = value.split(" ", 2);
                            packages.put(parts[0], parts[1]);
                        }
                        case "CL: " -> {
                            String[] parts = value.split(" ", 2);
                            String obfuscatedName = parts[0];
                            String name = parts[1];
                            ClassMapping mapping = new ClassMapping(obfuscatedName, name);
                            obfuscatedClasses.put(obfuscatedName, mapping);
                            classes.put(name, mapping);
                        }
                        case "FD: " -> {
                            String[] parts = value.split(" ", 2);
                            String obfPath = parts[0];
                            String deobfPath = parts[1];
                            String obfuscatedClassName = obfPath.substring(0, obfPath.lastIndexOf('/'));
                            String obfuscatedName = obfPath.substring(obfPath.lastIndexOf('/') + 1);
                            String className = deobfPath.substring(0, deobfPath.lastIndexOf('/'));
                            String name = deobfPath.substring(deobfPath.lastIndexOf('/') + 1);
                            System.out.println("Found field named " + obfuscatedName + " (" + name + ") in class " + obfuscatedClassName + " (" + className + ")");

                            ClassMapping mapping = obfuscatedClasses.get(obfuscatedClassName);
                            mapping.fields.put(obfuscatedName, "", name, "");
                        }
                        case "MD: " -> {
                            String[] parts = value.split(" ", 4);
                            String obfPath = parts[0];
                            String obfSignature = parts[1];
                            String path = parts[2];
                            String signature = parts[3];
                            String obfuscatedClassName = obfPath.substring(0, obfPath.lastIndexOf('/'));
                            String obfName = obfPath.substring(obfPath.lastIndexOf('/') + 1);
                            String className = path.substring(0, path.lastIndexOf('/'));
                            String name = path.substring(path.lastIndexOf('/') + 1);
                            System.out.println("Found method named " + obfName + " (" + name + ") in class " + obfuscatedClassName + " (" + className + ")");

                            ClassMapping mapping = obfuscatedClasses.get(obfuscatedClassName);
                            mapping.methods.put(obfName, obfSignature, name, signature);
                        }
                    }
                }
            }
        }
    }
    @Override
    public boolean process(ClassNode node) {
        String className = node.name;
        String superName = node.superName;
        ClassMapping clazz = find(className);
        ClassMapping superClass = find(superName);

        boolean mapped = false;

        if (clazz != null) {
            mapped = true;
            node.name = clazz.name;
        }
        if (superClass != null) {
            mapped = true;
            node.superName = superClass.name;
        }
        List<ClassMapping> interfaces = new ArrayList<>();
        for (int i = 0; i < node.interfaces.size(); i++) {
            ClassMapping interfaceClass = find(node.interfaces.get(i));
            if (interfaceClass != null) {
                mapped = true;
                interfaces.add(interfaceClass);
                node.interfaces.set(i, interfaceClass.name);
            }
        }

        if (clazz == null) {
            clazz = new ClassMapping(className, className);
            clazz.parent = superClass;
            clazz.interfaces.addAll(interfaces);
        }

        for (FieldNode field : node.fields) {
            ClassMapping.Member mm = clazz.findField(field.name, "", true, false);
            if (mm != null) {
                System.err.println("Found field mapping for " + field.name + ": " + mm.name() + " (" + field.desc + ")");
                mapped = true;
                field.name = mm.name();
                if (!mm.desc().isEmpty()) {
                    System.err.println("!WARN! updated field desc from " + field.desc + " to " + mm.desc());
                    field.desc = mm.desc();
                }
            }
            String d = descriptor(field.desc);
            if (!d.equals(field.desc)) {
                mapped = true;
                field.desc = d;
            }
        }
        for (MethodNode method : node.methods) {
            ClassMapping.Member mm = clazz.findMethod(method.name, method.desc, true, true);
            if (mm != null) {
                mapped = true;
                method.name = mm.name();
                method.desc = mm.desc();
            }
            {
                String d = fixMethodDesc(method.desc);
                if (!method.desc.equals(d)) {
                    mapped = true;
                    method.desc = d;
                }
            }
            if (method.localVariables != null) {
                for (LocalVariableNode lv : method.localVariables) {
                    String d = descriptor(lv.desc);
                    if (!d.equals(lv.desc)) {
                        mapped = true;
                        lv.desc = d;
                    }
                }
            }
            for (AbstractInsnNode insn : method.instructions) {
                if (insn instanceof MethodInsnNode mi) {
                    ClassMapping cm = mi.owner.equals(clazz.name) ? clazz : find(mi.owner);
                    if (cm != null) {
                        ClassMapping.Member m = cm.findMethod(mi.name, mi.desc, true, true);
                        if (m != null) {
                            mapped = true;
                            mi.name = m.name();
                            mi.desc = m.desc();
                        }
                        if (!mi.owner.equals(cm.name)) {
                            mapped = true;
                            mi.owner = cm.name;
                        }
                    }
                    String d = fixMethodDesc(mi.desc);
                    if (!d.equals(mi.desc)) {
                        mapped = true;
                        mi.desc = d;
                    }
                } else if (insn instanceof FieldInsnNode fi) {
                    System.err.println("Was n=" + fi.name + " d=" + fi.desc + " o=" + fi.owner);
                    String d = descriptor(fi.desc);
                    if (!d.equals(fi.desc)) {
                        mapped = true;
                        fi.desc = d;
                    }

                    ClassMapping cm = find(fi.owner);

                    if (cm != null) {
                        mapped = true;
                        fi.owner = cm.name;
                        ClassMapping.Member m = cm.findField(fi.name, "", true, true);
                        if (m != null) {
                            fi.name = m.name();
                            if (!m.desc().isEmpty()) {
                                fi.desc = m.desc();
                            }
                        }
                    }
                    System.err.println("Now n=" + fi.name + " d=" + fi.desc + " o=" + fi.owner);
                } else if (insn instanceof TypeInsnNode ti) {
                    ClassMapping cm = find(ti.desc);
                    if (cm != null) {
                        mapped = true;
                        ti.desc = cm.name;
                    }
                }
            }
        }
        return mapped;
    }

    public void loadRelations(File file) throws IOException {
        try (ZipFile binary = new ZipFile(file)) {
            Enumeration<? extends ZipEntry> entries = binary.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (!entry.getName().endsWith(".class")) {
                    continue;
                }
                try (InputStream stream = binary.getInputStream(entry)) {
                    ClassReader reader = new ClassReader(stream);
                    ClassMapping clazz = find(reader.getClassName());
                    ClassMapping parent = find(reader.getSuperName());
                    String[] itfs = reader.getInterfaces();
                    if (clazz != null && parent != null) {
                        System.err.println("class " + clazz.name + " extends " + parent.name);
                        clazz.parent = parent;
                    }
                    if (clazz != null && itfs.length > 0) {
                        List<ClassMapping> interfaces = new ArrayList<>();
                        for (String itf : itfs) {
                            ClassMapping i = find(itf);
                            if (i != null) {
                                System.err.println("class " + clazz.name + " implements " + i.name);
                                interfaces.add(i);
                            }
                        }
                        clazz.interfaces.addAll(interfaces);
                    }
                }
            }
        }
    }

    public ClassMapping find(String name) {
        return obfuscatedClasses.get(name);
    }

    private String descriptor(String desc) {
        Type type = Type.getType(desc);
        if (type.getSort() == Type.ARRAY) {
            Type et = type.getElementType();
            System.err.println("ARRAY TYPE DESC: " + desc + " array of " + et.getInternalName());
            if (et.getSort() == Type.OBJECT) {
                ClassMapping mapping = find(et.getInternalName());
                if (mapping != null) {
                    System.err.println("Mapped to " + "[L" + mapping.name + ";");
                    return "[L" + mapping.name + ";";
                }
            }
        } else if (type.getSort() == Type.OBJECT) {
            ClassMapping mapping = find(type.getInternalName());
            if (mapping != null) {
                return "L" + mapping.name + ";";
            }
        }
        return desc;
    }

    private String fixMethodDesc(String desc) {
        Type ret = Type.getReturnType(desc);
        Type[] args = Type.getArgumentTypes(desc);
        boolean modified = false;
        for (int i = 0; i < args.length; i++) {
            Type a = args[i];
            if (a.getSort() == Type.OBJECT) {
                ClassMapping am = find(a.getInternalName());
                if (am != null) {
                    modified = true;
                    args[i] = Type.getObjectType(am.name);
                }
            }
        }
        if (ret.getSort() == Type.OBJECT) {
            ClassMapping rm = find(ret.getInternalName());
            if (rm != null) {
                modified = true;
                ret = Type.getObjectType(rm.name);
            }
        }
        if (modified) {
            return Type.getMethodDescriptor(ret, args);
        }
        return desc;
    }
}
