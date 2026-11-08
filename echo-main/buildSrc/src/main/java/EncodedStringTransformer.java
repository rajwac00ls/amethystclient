import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.ArrayCreationExpr;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.type.ArrayType;
import com.github.javaparser.ast.type.PrimitiveType;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Encoded String Transformer
 * <p>
 * Rewrites Concat.of("literal") calls into Concat.ofEncoded(new byte[]{...}, seed)
 * so the plaintext never lands in the compiled jar (self-contained, no backend).
 * <p>
 * Encoding matches Concat.Encoded's XOR key stream:
 * keyAt(i) = seed, then i times (k * 1103515245 + 12345).
 * Seed is derived from the string hash so builds stay reproducible.
 * <p>
 * Only encodes strings whose chars are all &lt;= 0xFF (byte-safe round-trip).
 * Skips auth/string/mixin packages and calls with dynamic arguments.
 *
 * @author Echo
 */
public class EncodedStringTransformer {

    private static final int LCG_MULT = 1103515245;
    private static final int LCG_ADD = 12345;

    public static void transform(String sourceDir) throws IOException {
        Path sourcePath = Paths.get(sourceDir);
        System.out.println("[EncodedStringTransformer] Scanning " + sourceDir + " ...");

        int transformedFiles = 0;
        int transformedCalls = 0;

        List<Path> files;
        try (Stream<Path> paths = Files.walk(sourcePath)) {
            files = paths
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".java"))
                .sorted()
                .toList();
        }

        for (Path file : files) {
            int calls = transformFile(file);
            if (calls > 0) {
                transformedFiles++;
                transformedCalls += calls;
            }
        }

        System.out.println("[EncodedStringTransformer] Encoded " + transformedCalls + " Concat.of() calls across " + transformedFiles + " files");
    }

    private static int transformFile(Path filePath) {
        if (shouldSkip(filePath)) return 0;

        try {
            String content = Files.readString(filePath);

            JavaParser parser = new JavaParser();
            parser.getParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17);

            ParseResult<CompilationUnit> result = parser.parse(content);
            if (!result.isSuccessful() || result.getResult().isEmpty()) return 0;

            CompilationUnit cu = result.getResult().get();
            int modified = 0;

            List<MethodCallExpr> calls = new ArrayList<>(cu.findAll(MethodCallExpr.class));
            for (MethodCallExpr call : calls) {
                if (!isConcatOfCall(call)) continue;

                String value = extractLiteralValue(call);
                if (value == null || value.isEmpty()) continue;
                if (!isEncodable(value)) continue;

                call.replace(createEncodedCall(value));
                modified++;
            }

            if (modified > 0) {
                try (FileWriter writer = new FileWriter(filePath.toFile())) {
                    writer.write(cu.toString());
                }
                System.out.println("[EncodedStringTransformer] Encoded " + filePath.getFileName());
            }

            return modified;
        } catch (Exception e) {
            System.err.println("[EncodedStringTransformer] Error transforming " + filePath + ": " + e.getMessage());
            return 0;
        }
    }

    private static boolean shouldSkip(Path filePath) {
        String pathStr = filePath.toString().replace('\\', '/');
        return pathStr.contains("/auth/")
            || pathStr.contains("/utils/strings/")
            || pathStr.contains("/mixin/");
    }

    private static boolean isConcatOfCall(MethodCallExpr call) {
        if (!call.getNameAsString().equals("of")) return false;
        return call.getScope()
            .map(scope -> scope.toString().equals("Concat"))
            .orElse(false);
    }

    /**
     * Concatenates the call's string literals, or null if any argument is dynamic.
     */
    private static String extractLiteralValue(MethodCallExpr call) {
        if (call.getArguments().isEmpty()) return null;

        StringBuilder sb = new StringBuilder();
        for (Expression arg : call.getArguments()) {
            if (!(arg instanceof StringLiteralExpr strLit)) return null;
            sb.append(strLit.getValue());
        }
        return sb.toString();
    }

    /**
     * Encoded uses a byte[] and decrypts with (data[i] & 0xFF) ^ keyByte,
     * so chars above 0xFF would lose their high bits. Only byte-safe strings round-trip.
     */
    private static boolean isEncodable(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) > 0xFF) return false;
        }
        return true;
    }

    private static MethodCallExpr createEncodedCall(String value) {
        int seed = value.hashCode();
        char[] chars = value.toCharArray();

        NodeList<Expression> bytes = new NodeList<>();
        for (int i = 0; i < chars.length; i++) {
            int keyByte = keyAt(seed, i) & 0xFF;
            int encrypted = chars[i] ^ keyByte;
            bytes.add(new CastExpr(
                new PrimitiveType(PrimitiveType.Primitive.BYTE),
                new IntegerLiteralExpr(String.valueOf(encrypted))
            ));
        }

        ArrayCreationExpr array = new ArrayCreationExpr(
            new ArrayType(new PrimitiveType(PrimitiveType.Primitive.BYTE)),
            NodeList.nodeList(),
            new ArrayInitializerExpr(bytes)
        );

        return new MethodCallExpr(
            new NameExpr("Concat"),
            "ofEncoded",
            new NodeList<>(array, new IntegerLiteralExpr(String.valueOf(seed)))
        );
    }

    /**
     * Mirrors Concat.Encoded.keyAt(int, int) exactly (int overflow included).
     */
    private static int keyAt(int seed, int index) {
        int k = seed;
        for (int i = 0; i < index; i++) k = k * LCG_MULT + LCG_ADD;
        return k;
    }
}
