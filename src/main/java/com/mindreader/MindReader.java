package com.mindreader;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.mindreader.graph.JavaGraphManager;
import com.mindreader.utils.Node;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Stream;
import java.util.stream.Collectors;

public class MindReader {
    public static String directory;
    private static final JavaGraphManager javaGraphManager = new JavaGraphManager();
    public static boolean enableGit;
    private static final HashMap<String, HashMap<String, Node>> repoFileMethodData = new HashMap<>();
    private static final HashMap<String, Node> repoFileData = new HashMap<>();

    public MindReader(String directory, boolean enableGit) {
        directory = directory;
        enableGit = enableGit;
        getRepoFileData();
        getRepoFileMethodCalls();
    }

    private static CompilationUnit astParseFile(String filePath) {
        try {
            Path path = Paths.get(filePath);
            String content = Files.readString(path);

            // 1. Parse the code into a CompilationUnit
            CompilationUnit cu = StaticJavaParser.parse(content);
            return cu;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void getRepoFileMethodCalls() {
        try {
            List<Path> javaFiles = findJavaFiles();
            for (Path path : javaFiles) {
                String filePath = path.toString();
                CompilationUnit cu = astParseFile(filePath);

                if (cu == null) continue;

                // Initialize the inner map for this specific file
                repoFileMethodData.putIfAbsent(filePath, new HashMap<>());

                // The "ast.walk" equivalent: Visit every Method in the file
                cu.accept(new com.github.javaparser.ast.visitor.VoidVisitorAdapter<Void>() {
                    @Override
                    public void visit(com.github.javaparser.ast.body.MethodDeclaration n, Void arg) {
                        String methodName = n.getNameAsString();

                        // Build the graph for this specific method node
                        // Assuming JavaGraphManager has a method for individual declarations
                        Node[] methodGraph = javaGraphManager.buildFunctionGraph(n);

                        repoFileMethodData.get(filePath).put(methodName, methodGraph[0]);

                        super.visit(n, arg);
                    }
                }, null);
            }
        } catch (Exception e) {
            // Log error and continue like the Python 'continue' logic
            System.err.println("Error processing repo methods: " + e.getMessage());
        }
    }

    private static void getRepoFileData(){
        try {
            List<Path> javaFiles = findJavaFiles();

            for (Path path : javaFiles) {
                CompilationUnit cu = astParseFile(path.toString());
                Node[] graph = javaGraphManager.buildFileGraph(cu);
                repoFileData.put(path.toString(), graph[0]);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static List<Path> findJavaFiles() {
        Path start = Paths.get(directory);

        // Files.walk visits all files and directories recursively
        try (Stream<Path> stream = Files.walk(start)) {
            return stream
                    .filter(Files::isRegularFile) // Ignore directories
                    .filter(path -> {
                        String name = path.toString().toLowerCase();
                        return name.endsWith(".java") || name.endsWith(".enum");
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
