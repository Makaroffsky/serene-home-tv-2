import com.sun.source.util.JavacTask;
import javax.tools.*;
import java.nio.file.*;
import java.util.*;

/** Parses all app Java files without loading Android SDK classes. */
public class JavaSyntaxCheck {
  public static void main(String[] args) throws Exception {
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
    StandardJavaFileManager fm = compiler.getStandardFileManager(diagnostics, null, null);
    List<java.io.File> files = new ArrayList<>();
    try(var stream = Files.walk(Path.of(args[0]))) {
      stream.filter(x -> x.toString().endsWith(".java")).forEach(p -> files.add(p.toFile()));
    }
    JavacTask task = (JavacTask) compiler.getTask(null, fm, diagnostics,
            List.of("-proc:none"), null, fm.getJavaFileObjectsFromFiles(files));
    task.parse();
    for(var diagnostic : diagnostics.getDiagnostics()) {
      if(diagnostic.getKind() == Diagnostic.Kind.ERROR) {
        System.err.println(diagnostic);
        System.exit(1);
      }
    }
    System.out.println("Parsed " + files.size() + " Java sources with no syntax errors");
  }
}
