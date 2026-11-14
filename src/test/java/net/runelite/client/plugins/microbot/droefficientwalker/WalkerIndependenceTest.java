package net.runelite.client.plugins.microbot.droefficientwalker;

import java.io.InputStream;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import static org.junit.jupiter.api.Assertions.*;

public class WalkerIndependenceTest
{
    // On the Hub the walker reuses these client value types and helpers instead of shipping copies.
    // It must still never route or execute travel through the client's own walker.
    private static final java.util.Set<String> SHARED_CLIENT_TYPES = java.util.Set.of(
        "Rs2TransportLoadout", "Rs2RoutePolicy", "Rs2TransportItemRequirement", "Rs2MiniMap",
        "Rs2RouteTermination", "Rs2TransportType", "Rs2TransportExecutor", "Rs2TerminalTravelMode",
        "Rs2RouteRequest", "WebWalkLog");
    private static final java.util.regex.Pattern MAIN_WALKER_CLASS =
        java.util.regex.Pattern.compile("net/runelite/client/plugins/microbot/util/walker/(\\w+)");

    private static void assertIndependent(byte[] bytes, String source)
    {
        List<String> dependencies = new ArrayList<>();
        // ASM 9.0 can inspect these instructions, but its version gate predates Java 17.
        // Only the analysis copy is adjusted; the JVM test below loads the compiled classes unchanged.
        byte[] analysis = bytes.clone();
        int major = ((analysis[6] & 255) << 8) | (analysis[7] & 255);
        assertTrue(major <= 61, "Unexpected compiled bytecode version " + major);
        if (major == 61) analysis[7] = 60;
        new ClassReader(analysis).accept(new ClassVisitor(Opcodes.ASM9)
        {
            private void check(String name)
            {
                if (name == null) return;
                java.util.regex.Matcher walker = MAIN_WALKER_CLASS.matcher(name);
                while (walker.find())
                    if (!SHARED_CLIENT_TYPES.contains(walker.group(1))) dependencies.add(name);
            }
            @Override public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value)
            { check(descriptor); return null; }
            @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions)
            {
                check(descriptor);
                return new MethodVisitor(Opcodes.ASM9)
                {
                    @Override public void visitMethodInsn(int op, String owner, String method, String desc, boolean iface)
                    { check(owner); check(desc); }
                    @Override public void visitFieldInsn(int op, String owner, String field, String desc)
                    { check(owner); check(desc); }
                    @Override public void visitTypeInsn(int op, String type) { check(type); }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        assertTrue(dependencies.isEmpty(), source + " still depends on the main walker: " + dependencies);
    }

    @Test public void compiledEfficientWalkerDoesNotCallMainWalker() throws Exception
    {
        Path packageDir = Paths.get(EfficientWalker.class.getResource("EfficientWalker.class").toURI()).getParent();
        try (java.util.stream.Stream<Path> files = Files.walk(packageDir))
        {
            for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".class"))::iterator)
                assertIndependent(Files.readAllBytes(file), file.toString());
        }
    }

    @Test public void preparedEngineLoadsWithStockDependencies() throws Exception {
        ClassLoader parent = EfficientWalker.class.getClassLoader();
        Class<?> loaderClass = Class.forName(EfficientWalker.class.getName() + "$NavigationEngineClassLoader");
        java.lang.reflect.Constructor<?> constructor = loaderClass.getDeclaredConstructor(ClassLoader.class);
        constructor.setAccessible(true);
        ClassLoader loader = (ClassLoader) constructor.newInstance(parent);
        Set<String> names = new TreeSet<>();
        // Inspect every compiled engine class.
        String packageResource = WalkerTestReflection.SOURCE_PACKAGE.replace('.', '/') + "/WalkerEngine.class";
        java.net.URL resource = parent.getResource(packageResource);
        assertNotNull(resource);
        Path packageDir = Paths.get(resource.toURI()).getParent();
        try (java.util.stream.Stream<Path> files = Files.walk(packageDir)) {
            for (Path file : (Iterable<Path>) files.filter(path -> path.toString().endsWith(".class"))::iterator) {
                String relative = packageDir.relativize(file).toString().replace(java.io.File.separatorChar, '.');
                names.add(WalkerTestReflection.SOURCE_PACKAGE + "."
                    + relative.substring(0, relative.length() - ".class".length()));
            }
        }
        for (String name : names) {
            Class<?> engine = Class.forName(name, false, loader);
            assertSame(loader, engine.getClassLoader(), "Each selected engine class must remain isolated from the parent: " + name);
            engine.getDeclaredMethods();
            String compiledResource = name.replace('.', '/') + ".class";
            try (InputStream input = parent.getResourceAsStream(compiledResource)) {
                assertNotNull(input, "Ordinary compiled source class missing: " + name);
                assertIndependent(input.readAllBytes(), name);
            }
        }
    }

    @Test public void selectedSourceEngineUsesPrivateMinimap() throws Exception
    {
        ClassLoader parent = EfficientWalker.class.getClassLoader();
        String engineResource = WalkerTestReflection.SOURCE_PACKAGE.replace('.', '/') + "/WalkerEngine.class";
        byte[] engine;
        try (InputStream input = parent.getResourceAsStream(engineResource)) {
            assertNotNull(input);
            engine = input.readAllBytes();
        }
        assertIndependent(engine, "selected compiled source engine");
        byte[] analysis = engine.clone();
        if ((analysis[7] & 255) == 61) analysis[7] = 60;
        boolean[] privateDispatch = {false};
        new ClassReader(analysis).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                       String signature, String[] exceptions) {
                if ((access & Opcodes.ACC_PRIVATE) == 0
                    || !descriptor.equals("(Lnet/runelite/api/Point;)V")) return null;
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override public void visitMethodInsn(int opcode, String owner, String method,
                                                          String desc, boolean iface) {
                        if (owner.equals("net/runelite/client/plugins/microbot/droefficientwalker/WalkerMouseControl")
                            && method.equals("click")
                            && desc.equals("(Lnet/runelite/api/Client;Lnet/runelite/api/Point;)V")) {
                            privateDispatch[0] = true;
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        assertTrue(privateDispatch[0], "Selected minimap dispatch must use the walker-owned input/profile controller");
    }
}
