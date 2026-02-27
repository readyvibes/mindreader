package com.mindreader;


import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.mindreader.graph.JavaGraphManager;
import com.mindreader.utils.Node;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    private static final JavaGraphManager javaGraphManager = new JavaGraphManager();

    public static void main(String[] args) throws IOException {
        try {
            Path path = Paths.get("src/main/resources/Example.java");
            String content = Files.readString(path);

            // 1. Parse the code into a CompilationUnit
            CompilationUnit cu = StaticJavaParser.parse(content);

            // 2. Build the graph starting from the file level
            Node startNode = javaGraphManager.buildFileGraph(cu);

            // 3. Print the result to verify
            System.out.println("\n--- MindReader Graph Structure ---");
            printGraph(startNode, "");

        } catch (IOException e) {
            System.err.println("Error reading file: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void printGraph(Node node, String indent) {
        System.out.println(indent + "[L" + node.getLineNumber() + ": " + node.getNodeType() + "]");
        for (Node neighbor : node.getNeighbors()) {
            // Simple recursion to show the flow
            printGraph(neighbor, indent + "  -> ");
        }
    }
}
