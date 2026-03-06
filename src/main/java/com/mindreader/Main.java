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
    private static MindReader mindReader;

    public static void main(String[] args) throws IOException {
        mindReader = new MindReader("", false);
    }


}
