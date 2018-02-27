package com.spleefleague.jshell;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jdk.jshell.Diag;
import jdk.jshell.EvalException;
import jdk.jshell.ExpressionSnippet;
import jdk.jshell.JShell;
import jdk.jshell.MethodSnippet;
import jdk.jshell.Snippet;
import jdk.jshell.SnippetEvent;
import jdk.jshell.SourceCodeAnalysis.CompletionInfo;
import jdk.jshell.TypeDeclSnippet;
import jdk.jshell.VarSnippet;

/**
 *
 * @author balsfull
 */
class JShellEvaluator {
    
    private final JShell shell;
    private final Pattern linebreakPattern = Pattern.compile("\\R");
    private final String lineRegex = System.getProperty("line.seperator") + '\t';
    private StringBuilder buffer = new StringBuilder();
    
    public JShellEvaluator() {
        shell = JShell.builder()
                .executionEngine(new SpigotExecutionControlProvider(), null)
                .build();
        File file = new File("plugins");
        File[] plugins = file.listFiles(f -> f.isFile() && f.getName().toLowerCase().endsWith(".jar"));
        for(File plugin : plugins) {
            System.out.println("Adding " + plugin);
            shell.addToClasspath(plugin.getPath());
        }
    }
    
    public void close() {
        shell.close();
    }
    
    public boolean isHoldingIncompleteScript() {
        return buffer.length() > 0;
    }
    
    private List<SnippetEvent> evalAll(String script) {
        if(isHoldingIncompleteScript()) {
            script = buffer.toString() + script;
        }
        while (true) {
            CompletionInfo completionInfo = shell.sourceCodeAnalysis().analyzeCompletion(script);
            if (!completionInfo.completeness().isComplete()) {
                buffer.append(script);
                return Collections.emptyList();
            }
            buffer = new StringBuilder();
            List<SnippetEvent> result = shell.eval(completionInfo.source());
            script = completionInfo.remaining();
            if (script.isEmpty()) {
                return result;
            }
        }
    }

    public String eval(String script) {
        List<SnippetEvent> events = evalAll(script);
        for (SnippetEvent event : events) {
            if (event.exception() != null) {
                StringWriter writer = new StringWriter();
                try {
                    throw event.exception();
                } catch (EvalException e) {
                    convertJShellException(e).printStackTrace(new PrintWriter(writer));
                } catch (Exception e) {
                    e.printStackTrace(new PrintWriter(writer));
                }
                return writer.getBuffer().toString().replace(lineRegex, "\n|        ");
            }
//
            if (event.status() == Snippet.Status.VALID) {
                Snippet snippet = event.snippet();
                if(snippet instanceof VarSnippet) {
                    VarSnippet vs = (VarSnippet)snippet;
                    return vs.name() + " ==> " + shell.varValue(vs);
                }
                else if(snippet instanceof MethodSnippet) {
                    MethodSnippet ms = (MethodSnippet)snippet;
                    return "|  created method " + ms.name() + "()";
                }
                else if(snippet instanceof TypeDeclSnippet) {
                    TypeDeclSnippet tds = (TypeDeclSnippet)snippet;
                    return "|  created class " + tds.name();
                }
                else if(snippet instanceof ExpressionSnippet) {
                    return "";
                }
            }
            
            if (event.status() == Snippet.Status.REJECTED) {
                Snippet snippet = event.snippet();
                Diag diag = shell.diagnostics(snippet).findAny().get();
                List<String> display = new ArrayList<>();
                displayDiagnostics(snippet.source(), diag, display);
                StringBuilder sb = new StringBuilder().append("|  Error:");
                display.forEach(s -> sb.append("\n|  ").append(s));
                return sb.toString();
            }
        }
        return null;
    }
    
    private Exception convertJShellException(EvalException original) {
        try {
            Class<?> exceptionClass = Class.forName(original.getExceptionClassName());
            if (Exception.class.isAssignableFrom(exceptionClass)) {
                try {
                    // Try message and cause.
                    Constructor<?> constructor = exceptionClass.getConstructor(String.class, Throwable.class);
                    Exception exception = (Exception)constructor.newInstance(original.getMessage(), original.getCause());
                    exception.setStackTrace(original.getStackTrace());
                    return exception;
                } catch (ReflectiveOperationException e2) {}

                try {
                    // Try message only.
                    Constructor<?> constructor = exceptionClass.getConstructor(String.class);
                    Exception exception = (Exception)constructor.newInstance(original.getMessage());
                    exception.setStackTrace(original.getStackTrace());
                    return exception;
                } catch (ReflectiveOperationException e2) {
                }

                try {
                    // Try cause only.
                    Constructor<?> constructor = exceptionClass.getConstructor(Throwable.class);
                    Exception exception = (Exception)constructor.newInstance(original.getCause());
                    exception.setStackTrace(original.getStackTrace());
                    return exception;
                } catch (ReflectiveOperationException e2) {
                }

                try {
                    Constructor<?> constructor = exceptionClass.getConstructor();
                    Exception exception = (Exception)constructor.newInstance();
                    exception.setStackTrace(original.getStackTrace());
                    return exception;
                } catch (ReflectiveOperationException e2) {
                }

            }
        } catch (ReflectiveOperationException e2) {
        }

        return original;
    }
    
    private void displayDiagnostics(String source, Diag diag, List<String> toDisplay) {
        
        for (String line : diag.getMessage(null).split("\\r?\\n")) {
            if(line == null || line.isEmpty()) continue;
            if (!line.trim().startsWith("location:")) {
                toDisplay.add(line);
            }
        }

        int pstart = (int)diag.getStartPosition();
        int pend = (int)diag.getEndPosition();
        Matcher m = linebreakPattern.matcher(source);
        int pstartl = 0;
        int pendl = -2;
        while (m.find(pstartl)) {
            pendl = m.start();
            if (pendl >= pstart) {
                break;
            } else {
                pstartl = m.end();
            }
        }
        if (pendl < pstart) {
            pendl = source.length();
        }
        toDisplay.add(source.substring(pstartl, pendl));

        StringBuilder sb = new StringBuilder();
        int start = pstart - pstartl;
        char[] space = new char[start];
        Arrays.fill(space, ' ');
        sb.append(space);
        sb.append('^');
        boolean multiline = pend > pendl;
        int end = -pstartl - 1;
        if(multiline) {
            end += pendl;
        }
        else {
            end += pend;
        }
        if (end > start) {
            char[] dash = new char[end - start + 1];
            Arrays.fill(dash, '-');
            sb.append(dash);
            if (multiline) {
                sb.append("-...");
            } else {
                sb.append('^');
            }
        }
        toDisplay.add(sb.toString());
    }
}
