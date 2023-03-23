package ru.squareland;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class ClassMapping {
    public final String obfuscatedName;
    public final String name;
    public final Methods methods = new Methods(this);
    public final Fields fields = new Fields(this);
    public ClassMapping parent;
    public List<ClassMapping> interfaces = new ArrayList<>();

    public ClassMapping(String obfuscatedName, String name) {
        this.obfuscatedName = obfuscatedName;
        this.name = name;
    }

    public Member findField(String name, String signature, boolean obfuscated, boolean searchParent, Consumer<String> logger) {
        Member field = fields.find(name, signature, obfuscated);
        if (field == null && searchParent) {
            logger.accept("skip to super field `" + name + ": " + signature + "` search: super = " + (parent == null ? null : parent.name));
            if (parent != null) {
                Member mm = parent.findField(name, signature, obfuscated, true, logger);
                if (mm != null) {
                    return mm;
                }
            }
        }
        return field;
    }

    public Member findMethod(String name, String signature, boolean obfuscated, boolean searchParent, Consumer<String> logger) {
        boolean init = name.equals("<init>");
        Member method = methods.find(name, signature, obfuscated);
        if (method == null && searchParent) {
            logger.accept("skip to super method `" + name + ": " + signature + "` search: super = " + (parent == null ? null : parent.name));
            if (parent != null) {
                Member mm = parent.findMethod(name, signature, obfuscated, !init, logger);
                if (mm != null) {
                    return mm;
                }
            }
            if (!init) {
                for (ClassMapping itf : interfaces) {
                    logger.accept("interface = " + itf.name + " (" + itf.obfuscatedName + ")");
                    Member mm = itf.findMethod(name, signature, obfuscated, true, logger);
                    if (mm != null) {
                        return mm;
                    }
                }
            }
        }
        return method;
    }

    public static class Fields {
        public final ClassMapping container;
        public final Map<String, Member> obfuscatedFields = new HashMap<>();
        public final Map<String, Member> fields = new HashMap<>();

        public Fields(ClassMapping container) {
            this.container = container;
        }

        public Member find(String name, String signature, boolean obfuscated) {
            return (obfuscated ? obfuscatedFields : fields).get(name + signature);
        }

        public void put(String obfuscatedName, String obfuscatedSignature, String name, String signature) {
            Member mapping = new Member(obfuscatedName, obfuscatedSignature, name, signature);
            obfuscatedFields.put(obfuscatedName + obfuscatedSignature, mapping);
            fields.put(name + signature, mapping);
        }
    }

    public static class Methods {
        public final ClassMapping container;
        public final Map<String, Member> obfuscatedMethods = new HashMap<>();
        public final Map<String, Member> methods = new HashMap<>();

        public Methods(ClassMapping container) {
            this.container = container;
        }

        public Member find(String name, String signature, boolean obfuscated) {
            return (obfuscated ? obfuscatedMethods : methods).get(name + signature);
        }

        public void put(String obfuscatedName, String obfuscatedSignature, String name, String signature) {
            Member mapping = new Member(obfuscatedName, obfuscatedSignature, name, signature);
            obfuscatedMethods.put(obfuscatedName + obfuscatedSignature, mapping);
            methods.put(name + signature, mapping);
        }
    }

    public record Member(
        String obfuscatedName,
        String obfuscatedDesc,
        String name,
        String desc
    ) {}
}
