package ru.squareland.mapper;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import ru.squareland.ClassMapping;

import java.io.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class Notch2SrgMapper extends Mapper {
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
    public boolean process(ClassNode node, HashSet<String> skip) {
        String className = node.name;
        String superName = node.superName;
        if (skip.contains(className)) {
            return false;
        }
        skip.add(className);
        deobfuscator.etrace("Processing class " + className);
        deobfuscator.step++;
        ClassMapping clazz = findObfuscated(className);
        ClassMapping superClass = find(superName, skip);

        boolean mapped = false;

        if (clazz != null) {
            mapped = true;
            node.name = clazz.name;
        }
        if (superClass != null) {
            mapped = true;
            node.superName = superClass.name;
        } else {
            deobfuscator.etrace("Missing superclass " + node.superName);
        }

        List<ClassMapping> interfaces = new ArrayList<>();
        for (int i = 0; i < node.interfaces.size(); i++) {
            ClassMapping interfaceClass = find(node.interfaces.get(i), skip);
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
            classes.put(className, clazz);
        }

        for (FieldNode field : node.fields) {
            ClassMapping.Member mm = clazz.findField(field.name, "", true, false, deobfuscator::etrace);
            if (mm != null) {
                deobfuscator.etrace("Found field mapping for " + field.name + ": " + mm.name() + " (" + field.desc + ")");
                mapped = true;
                field.name = mm.name();
                if (!mm.desc().isEmpty()) {
                    deobfuscator.etrace("!WARN! updated field desc from " + field.desc + " to " + mm.desc());
                    field.desc = mm.desc();
                }
            }
            String d = descriptor(field.desc, skip);
            if (!d.equals(field.desc)) {
                mapped = true;
                field.desc = d;
            }
        }
        for (MethodNode method : node.methods) {
            deobfuscator.etrace("Mapping method " + method.name + method.desc);
            ClassMapping.Member mm = clazz.findMethod(method.name, method.desc, true, true, deobfuscator::etrace);
            if (mm != null) {
                mapped = true;
                method.name = mm.name();
                method.desc = mm.desc();
                deobfuscator.etrace("Renamed to " + method.name + method.desc);
            }
            {
                String d = fixMethodDesc(method.desc, skip);
                if (!method.desc.equals(d)) {
                    mapped = true;
                    method.desc = d;
                    deobfuscator.etrace("Fixed desc to " + method.desc);
                }
            }
            if (method.localVariables != null) {
                for (LocalVariableNode lv : method.localVariables) {
                    String d = descriptor(lv.desc, skip);
                    if (!d.equals(lv.desc)) {
                        mapped = true;
                        lv.desc = d;
                    }
                }
            }
        }
        for (MethodNode method : node.methods) {
            deobfuscator.etrace("Mapping method instructions in " + method.name + method.desc);
            for (AbstractInsnNode insn : method.instructions) {
                if (insn instanceof MethodInsnNode mi) {
                    ClassMapping cm = mi.owner.equals(clazz.name) ? clazz : find(mi.owner, skip);
                    if (cm != null) {
                        deobfuscator.etrace("Searching method " + mi.name + mi.desc + " in class " + cm.name + " (" + cm.obfuscatedName + ")");
                        ClassMapping.Member m = cm.findMethod(mi.name, mi.desc, true, true, deobfuscator::etrace);
                        if (m != null) {
                            mapped = true;
                            mi.name = m.name();
                            mi.desc = m.desc();
                            deobfuscator.etrace("Found " + mi.name + mi.desc);
                        }
                        if (!mi.owner.equals(cm.name)) {
                            mapped = true;
                            mi.owner = cm.name;
                        }
                    }
                    String d = fixMethodDesc(mi.desc, skip);
                    if (!d.equals(mi.desc)) {
                        mapped = true;
                        mi.desc = d;
                    }
                } else if (insn instanceof FieldInsnNode fi) {
                    deobfuscator.etrace("Was n=" + fi.name + " d=" + fi.desc + " o=" + fi.owner);
                    String d = descriptor(fi.desc, skip);
                    if (!d.equals(fi.desc)) {
                        mapped = true;
                        fi.desc = d;
                    }

                    ClassMapping cm = find(fi.owner, skip);

                    if (cm != null) {
                        mapped = true;
                        fi.owner = cm.name;
                        ClassMapping.Member m = cm.findField(fi.name, "", true, true, deobfuscator::etrace);
                        if (m != null) {
                            fi.name = m.name();
                            if (!m.desc().isEmpty()) {
                                fi.desc = m.desc();
                            }
                        }
                    }
                    deobfuscator.etrace("Now n=" + fi.name + " d=" + fi.desc + " o=" + fi.owner);
                } else if (insn instanceof TypeInsnNode ti) {
                    Type ty = Type.getObjectType(ti.desc);
                    ClassMapping cm = find(ty.getClassName(), skip);
                    if (cm != null) {
                        mapped = true;
                        ti.desc = Type.getType(descriptor(ti.desc, ty, skip)).getInternalName();
                    }
                } else if (insn instanceof LdcInsnNode li && li.cst instanceof Type ty) {
                    ClassMapping cm = find(ty.getClassName(), skip);
                    if (cm != null) {
                        mapped = true;
                        li.cst = Type.getType(descriptor(ty.getDescriptor(), ty, skip));
                    }
                }
            }
        }
        deobfuscator.step--;
        return mapped;
    }

    public void loadRelations(File file) throws IOException {
        try (ZipFile binary = new ZipFile(file)) {
            HashSet<String> skip = new HashSet<>(Set.of("java/lang/Object"));
            Enumeration<? extends ZipEntry> entries = binary.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (!entry.getName().endsWith(".class")) {
                    continue;
                }
                try (InputStream stream = binary.getInputStream(entry)) {
                    ClassReader reader = new ClassReader(stream);
                    ClassMapping clazz = find(reader.getClassName(), skip);
                    ClassMapping parent = find(reader.getSuperName(), skip);
                    String[] itfs = reader.getInterfaces();
                    if (clazz != null && parent != null) {
                        deobfuscator.etrace("class " + clazz.name + " extends " + parent.name);
                        clazz.parent = parent;
                    }
                    if (clazz != null && itfs.length > 0) {
                        List<ClassMapping> interfaces = new ArrayList<>();
                        for (String itf : itfs) {
                            ClassMapping i = find(itf, skip);
                            if (i != null) {
                                deobfuscator.etrace("class " + clazz.name + " implements " + i.name);
                                interfaces.add(i);
                            }
                        }
                        clazz.interfaces.addAll(interfaces);
                    }
                }
            }
        }
    }

    public ClassMapping findObfuscated(String name) {
        return obfuscatedClasses.get(name);
    }

    public ClassMapping findDeobfuscated(String name) {
        return classes.get(name);
    }

    public ClassMapping find(String name, HashSet<String> skip) {
        if (name.startsWith("java/")) {
            return null;
        }
        ClassMapping first = findObfuscated(name);
        if (first == null) {
            first = findDeobfuscated(name);
        }

        if (first == null && !skip.contains(name)) {
            deobfuscator.trace("Searching node for class: " + name);
            deobfuscator.step++;
            ClassNode node = deobfuscator.loadNode(name, skip);
            deobfuscator.step--;
            if (node != null) {
                deobfuscator.step++;
                process(node, skip);
                deobfuscator.step--;
                return findDeobfuscated(name);
            } else {
                deobfuscator.etrace("Missing node: " + name);
            }
        }
        return first;
    }

    private String descriptor(String desc, Type type, HashSet<String> skip) {
        if (type.getSort() == Type.ARRAY) {
            Type et = type.getElementType();
            return '[' + descriptor(desc, et, skip);
        } else if (type.getSort() == Type.OBJECT) {
            ClassMapping mapping = find(type.getInternalName(), skip);
            if (mapping != null) {
                return "L" + mapping.name + ";";
            }
        }
        return desc;
    }

    private String descriptor(String desc, HashSet<String> skip) {
        Type type = Type.getType(desc);
        return descriptor(desc, type, skip);
    }

    private String fixMethodDesc(String desc, HashSet<String> skip) {
        Type ret = Type.getReturnType(desc);
        Type[] args = Type.getArgumentTypes(desc);
        boolean modified = false;
        for (int i = 0; i < args.length; i++) {
            Type a = args[i];
            ClassMapping am = find(a.getInternalName(), skip);
            if (am != null) {
                modified = true;
                args[i] = Type.getType(descriptor(a.getDescriptor(), skip));
            }
        }
        ClassMapping rm = find(ret.getInternalName(), skip);
        if (rm != null) {
            modified = true;
            ret = Type.getType(descriptor(ret.getDescriptor(), skip));
        }
        if (modified) {
            return Type.getMethodDescriptor(ret, args);
        }
        return desc;
    }
}
