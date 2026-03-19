package com.mindreader.graph;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.mindreader.utils.Node;
import com.mindreader.utils.NodeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

class JavaGraphManagerTest {

    private JavaGraphManager graphManager;
    private JavaParser parser;

    @BeforeEach
    void setUp() {
        graphManager = new JavaGraphManager();
        parser = new JavaParser();
    }

    @Test
    void testBuildFileGraph_EmptyFile() {
        String code = "public class Empty {}";
        CompilationUnit cu = parser.parse(code).getResult().orElseThrow();

        Node[] result = graphManager.buildFileGraph(cu);

        assertNotNull(result);
        assertEquals(2, result.length);
        assertEquals(NodeType.FILE_START, result[0].getNodeType());
    }

    @Test
    void testBuildFileGraph_WithClass() {
        String code = """
            public class TestClass {
                private int x;
                
                public void method() {
                    System.out.println("test");
                }
            }
            """;
        CompilationUnit cu = parser.parse(code).getResult().orElseThrow();

        Node[] result = graphManager.buildFileGraph(cu);

        assertNotNull(result);
        assertEquals(2, result.length);
        Node fileStart = result[0];
        assertEquals(NodeType.FILE_START, fileStart.getNodeType());
        assertFalse(fileStart.getNeighbors().isEmpty());
    }

    @Test
    void testBuildFunctionGraph_EmptyMethod() {
        String code = """
            public class TestClass {
                public void emptyMethod() {}
            }
            """;
        CompilationUnit cu = parser.parse(code).getResult().orElseThrow();
        MethodDeclaration method = cu.findFirst(MethodDeclaration.class).orElseThrow();

        Node[] result = graphManager.buildFunctionGraph(method);

        assertNotNull(result);
        assertEquals(2, result.length);
        assertEquals(NodeType.FUNCTION_START, result[0].getNodeType());
        assertEquals(result[0], result[1]); // Empty method, start equals tail
    }

    @Test
    void testBuildFunctionGraph_SimpleStatements() {
        String code = """
            public class TestClass {
                public void simpleMethod() {
                    int x = 5;
                    System.out.println(x);
                    x++;
                }
            }
            """;
        CompilationUnit cu = parser.parse(code).getResult().orElseThrow();
        MethodDeclaration method = cu.findFirst(MethodDeclaration.class).orElseThrow();

        Node[] result = graphManager.buildFunctionGraph(method);

        assertNotNull(result);
        assertEquals(2, result.length);
        Node functionStart = result[0];
        assertEquals(NodeType.FUNCTION_START, functionStart.getNodeType());
        
        // Verify we have statements connected
        assertFalse(functionStart.getNeighbors().isEmpty());
        Node firstStatement = functionStart.getNeighbors().get(0);
        assertEquals(NodeType.STATEMENT, firstStatement.getNodeType());
    }

    @Test
    void testBuildFunctionGraph_WithIfStatement() {
        String code = """
            public class TestClass {
                public void methodWithIf(int x) {
                    if (x > 0) {
                        System.out.println("positive");
                    }
                }
            }
            """;
        CompilationUnit cu = parser.parse(code).getResult().orElseThrow();
        MethodDeclaration method = cu.findFirst(MethodDeclaration.class).orElseThrow();

        Node[] result = graphManager.buildFunctionGraph(method);

        assertNotNull(result);
        Node functionStart = result[0];
        assertEquals(NodeType.FUNCTION_START, functionStart.getNodeType());
        
        // Should have an IF node
        Node ifNode = functionStart.getNeighbors().get(0);
        assertEquals(NodeType.IF, ifNode.getNodeType());
        
        // IF node should have at least one neighbor (the then branch)
        assertFalse(ifNode.getNeighbors().isEmpty());
    }

    @Test
    void testHandleIfStructure_SimpleIf() {
        String code = """
            public class TestClass {
                public void test(int x) {
                    if (x > 0) {
                        x = 10;
                    }
                }
            }
            """;
        CompilationUnit cu = parser.parse(code).getResult().orElseThrow();
        MethodDeclaration method = cu.findFirst(MethodDeclaration.class).orElseThrow();

        Node[] result = graphManager.buildFunctionGraph(method);
        Node functionStart = result[0];
        Node tail = result[1];

        // Tail should be a JOIN node
        assertEquals(NodeType.JOIN, tail.getNodeType());
        
        // IF node should have 2 neighbors: then branch and join
        Node ifNode = functionStart.getNeighbors().get(0);
        assertEquals(NodeType.IF, ifNode.getNodeType());
        assertTrue(ifNode.getNeighbors().size() >= 1);
    }

    @Test
    void testHandleIfStructure_IfElse() {
        String code = """
            public class TestClass {
                public void test(int x) {
                    if (x > 0) {
                        x = 10;
                    } else {
                        x = -10;
                    }
                }
            }
            """;
        CompilationUnit cu = parser.parse(code).getResult().orElseThrow();
        MethodDeclaration method = cu.findFirst(MethodDeclaration.class).orElseThrow();

        Node[] result = graphManager.buildFunctionGraph(method);
        Node functionStart = result[0];
        Node tail = result[1];

        assertEquals(NodeType.JOIN, tail.getNodeType());
        
        // Function start should have IF node
        Node ifNode = functionStart.getNeighbors().get(0);
        assertEquals(NodeType.IF, ifNode.getNodeType());
        
        // Should be able to find ELSE node
        boolean hasElse = functionStart.getNeighbors().stream()
                .anyMatch(n -> n.getNodeType() == NodeType.ELSE);
        assertTrue(hasElse);
    }

    @Test
    void testHandleIfStructure_IfElseIfElse() {
        String code = """
            public class TestClass {
                public void test(int x) {
                    if (x > 0) {
                        x = 10;
                    } else if (x < 0) {
                        x = -10;
                    } else {
                        x = 0;
                    }
                }
            }
            """;
        CompilationUnit cu = parser.parse(code).getResult().orElseThrow();
        MethodDeclaration method = cu.findFirst(MethodDeclaration.class).orElseThrow();

        Node[] result = graphManager.buildFunctionGraph(method);
        Node functionStart = result[0];
        Node tail = result[1];

        assertEquals(NodeType.JOIN, tail.getNodeType());
        
        // Should find IF, ELSE_IF, and ELSE nodes
        boolean hasIf = functionStart.getNeighbors().stream()
                .anyMatch(n -> n.getNodeType() == NodeType.IF);
        boolean hasElseIf = functionStart.getNeighbors().stream()
                .anyMatch(n -> n.getNodeType() == NodeType.ELSE_IF);
        boolean hasElse = functionStart.getNeighbors().stream()
                .anyMatch(n -> n.getNodeType() == NodeType.ELSE);
        
        assertTrue(hasIf);
        assertTrue(hasElseIf);
        assertTrue(hasElse);
    }

    @Test
    void testProcessBlock_WithNestedClass() {
        String code = """
            public class Outer {
                public class Inner {
                    public void innerMethod() {
                        System.out.println("inner");
                    }
                }
            }
            """;
        CompilationUnit cu = parser.parse(code).getResult().orElseThrow();

        Node[] result = graphManager.buildFileGraph(cu);
        Node fileStart = result[0];

        // Should have a CLASS node
        boolean hasClass = hasNodeTypeInGraph(fileStart, NodeType.CLASS);
        assertTrue(hasClass);
    }

    @Test
    void testProcessBlock_WithMethodDeclaration() {
        String code = """
            public class TestClass {
                public void method1() {
                    System.out.println("m1");
                }
                
                public void method2() {
                    System.out.println("m2");
                }
            }
            """;
        CompilationUnit cu = parser.parse(code).getResult().orElseThrow();

        Node[] result = graphManager.buildFileGraph(cu);
        Node fileStart = result[0];

        // Should have FUNCTION_DEF nodes
        boolean hasFunctionDef = hasNodeTypeInGraph(fileStart, NodeType.FUNCTION_DEF);
        assertTrue(hasFunctionDef);
    }

    @Test
    void testLineNumbers_ArePreserved() {
        String code = """
            public class TestClass {
                public void test(int x) {
                    if (x > 0) {
                        x = 10;
                    }
                }
            }
            """;
        CompilationUnit cu = parser.parse(code).getResult().orElseThrow();
        MethodDeclaration method = cu.findFirst(MethodDeclaration.class).orElseThrow();

        Node[] result = graphManager.buildFunctionGraph(method);
        Node functionStart = result[0];

        // Function start should have a line number
        assertTrue(functionStart.getLineNumber() > 0);
        
        // IF node should have a line number
        if (!functionStart.getNeighbors().isEmpty()) {
            Node ifNode = functionStart.getNeighbors().get(0);
            assertTrue(ifNode.getLineNumber() > 0);
        }
    }

    @Test
    void testBuildFunctionGraph_ComplexMethod() {
        String code = """
            public class TestClass {
                public void complexMethod(int x, int y) {
                    int result = 0;
                    if (x > 0) {
                        result = x + y;
                    } else if (x < 0) {
                        result = x - y;
                    } else {
                        result = y;
                    }
                    System.out.println(result);
                    return;
                }
            }
            """;
        CompilationUnit cu = parser.parse(code).getResult().orElseThrow();
        MethodDeclaration method = cu.findFirst(MethodDeclaration.class).orElseThrow();

        Node[] result = graphManager.buildFunctionGraph(method);

        assertNotNull(result);
        assertEquals(2, result.length);
        Node functionStart = result[0];
        Node tail = result[1];
        
        assertEquals(NodeType.FUNCTION_START, functionStart.getNodeType());
        assertNotNull(tail);
        
        // Verify graph is properly connected
        assertFalse(functionStart.getNeighbors().isEmpty());
    }

    @Test
    void testGraphDiff() {
        // Version 1: if without else
        String codeV1 = """
            public class TestClass {
                public void f(boolean x) {
                    if (x) {
                        int y = 1;
                    }
                }
            }
            """;

        // Version 2: if with else
        String codeV2 = """
            public class TestClass {
                public void f(boolean x) {
                    if (x) {
                        int y = 1;
                    } else {
                        int z = 2;
                    }
                }
            }
            """;

        CompilationUnit cu1 = parser.parse(codeV1).getResult().orElseThrow();
        CompilationUnit cu2 = parser.parse(codeV2).getResult().orElseThrow();

        MethodDeclaration method1 = cu1.findFirst(MethodDeclaration.class).orElseThrow();
        MethodDeclaration method2 = cu2.findFirst(MethodDeclaration.class).orElseThrow();

        Node[] result1 = graphManager.buildFunctionGraph(method1);
        Node[] result2 = graphManager.buildFunctionGraph(method2);

        Node g1 = result1[0];
        Node g2 = result2[0];

        graphManager.displayAllPaths(g1);
        graphManager.displayAllPaths(g2);

        // This will output exactly one added path (the else branch)
        // and one removed path (the original if-only flow).
        // Note: diffGraphs prints to console, so this test verifies it runs without errors
        assertDoesNotThrow(() -> graphManager.diffGraphs(g1, g2));

        // Verify g2 has an ELSE node that g1 doesn't have
        boolean g1HasElse = hasNodeTypeInGraph(g1, NodeType.ELSE);
        boolean g2HasElse = hasNodeTypeInGraph(g2, NodeType.ELSE);



        assertFalse(g1HasElse, "Version 1 should not have ELSE node");
        assertTrue(g2HasElse, "Version 2 should have ELSE node");
    }

    // Helper method to check if a node type exists in the graph
    private boolean hasNodeTypeInGraph(Node start, NodeType type) {
        if (start.getNodeType() == type) {
            return true;
        }
        for (Node neighbor : start.getNeighbors()) {
            if (hasNodeTypeInGraph(neighbor, type)) {
                return true;
            }
        }
        return false;
    }

    // Helper method to count paths in graph
    private int countPaths(Node start) {
        java.util.Set<java.util.List<NodeType>> paths = new java.util.HashSet<>();
        countPathsRecursive(start, new java.util.ArrayList<>(), paths, new java.util.HashSet<>());
        return paths.size();
    }

    private void countPathsRecursive(Node current, java.util.List<NodeType> currentPath,
                                      java.util.Set<java.util.List<NodeType>> allPaths,
                                      java.util.Set<Node> visited) {
        if (visited.contains(current)) {
            return;
        }

        currentPath.add(current.getNodeType());

        if (current.getNeighbors().isEmpty()) {
            allPaths.add(new java.util.ArrayList<>(currentPath));
        } else {
            visited.add(current);
            for (Node neighbor : current.getNeighbors()) {
                countPathsRecursive(neighbor, currentPath, allPaths, visited);
            }
            visited.remove(current);
        }

        currentPath.remove(currentPath.size() - 1);
    }

    @Test
    void testMethodCallDetection() {
        String code = """
            public class Operation {
                public int operate(int x) {
                    int result = add(x, 5);
                    return result;
                }
            }
            """;

        CompilationUnit cu = parser.parse(code).getResult().orElseThrow();
        MethodDeclaration method = cu.findFirst(MethodDeclaration.class).orElseThrow();

        Node[] result = graphManager.buildFunctionGraph(method);
        Node functionStart = result[0];

        // Should find a METHOD_CALL node for "add"
        boolean hasMethodCallNode = hasNodeTypeInGraph(functionStart, NodeType.METHOD_CALL);
        assertTrue(hasMethodCallNode, "Graph should contain a METHOD_CALL node");

        // Verify the method call is for "add"
        Node methodCallNode = findNodeByType(functionStart, NodeType.METHOD_CALL);
        assertNotNull(methodCallNode);
        assertEquals("add", methodCallNode.getTargetMethodName());
    }

    @Test
    void testGraphConcatenation() {
        // Operation.java - operate() method that calls add()
        String operateCode = """
            public class Operation {
                public int operate(int x, int y) {
                    int result = add(x, y);
                    System.out.println("Done");
                    return result;
                }
            }
            """;

        // Add.java - add() method
        String addCode = """
            public class Add {
                public int add(int a, int b) {
                    int sum = a + b;
                    return sum;
                }
            }
            """;

        // Parse and build graphs
        CompilationUnit operateCu = parser.parse(operateCode).getResult().orElseThrow();
        CompilationUnit addCu = parser.parse(addCode).getResult().orElseThrow();

        MethodDeclaration operateMethod = operateCu.findFirst(MethodDeclaration.class).orElseThrow();
        MethodDeclaration addMethod = addCu.findFirst(MethodDeclaration.class).orElseThrow();

        Node[] operateGraph = graphManager.buildFunctionGraph(operateMethod);
        Node[] addGraph = graphManager.buildFunctionGraph(addMethod);

        Node operateStart = operateGraph[0];
        Node addStart = addGraph[0];
        Node addExit = addGraph[1];

        // NEW: Provide the source code content to the manager so it can retrieve lines
        graphManager.putFileContent("Operation.java", operateCode);
        graphManager.putFileContent("Add.java", addCode);

        // Set file paths for nodes
        setFilePathForGraph(operateStart, "Operation.java");
        setFilePathForGraph(addStart, "Add.java");

        // Concatenate the graphs
        boolean success = graphManager.concatenateGraphs(operateStart, "add", addStart, addExit);
        assertTrue(success);

        // Concatenate file content based on the merged graph
        String concatenatedContent = graphManager.concatenateFileContent(operateStart, addStart);

        assertNotNull(concatenatedContent);
        assertFalse(concatenatedContent.isEmpty());

        // The output will now follow the execution: operate -> add -> rest of operate
        System.out.println("\n=== Concatenated File Content ===");
        System.out.println(concatenatedContent);
    }

    // Helper method to find a node by type
    private Node findNodeByType(Node start, NodeType type) {
        if (start.getNodeType() == type) {
            return start;
        }
        for (Node neighbor : start.getNeighbors()) {
            Node found = findNodeByType(neighbor, type);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    // Helper method to collect all node types in a graph
    private List<NodeType> collectPathTypes(Node start) {
        List<NodeType> types = new ArrayList<>();
        Set<Node> visited = new HashSet<>();
        collectPathTypesRecursive(start, types, visited);
        return types;
    }

    private void collectPathTypesRecursive(Node current, List<NodeType> types, Set<Node> visited) {
        if (current == null || visited.contains(current)) {
            return;
        }
        visited.add(current);
        types.add(current.getNodeType());

        for (Node neighbor : current.getNeighbors()) {
            collectPathTypesRecursive(neighbor, types, visited);
        }
    }

    // Helper method to set file path for all nodes in a graph
    private void setFilePathForGraph(Node start, String filePath) {
        Set<Node> visited = new HashSet<>();
        setFilePathRecursive(start, filePath, visited);
    }

    private void setFilePathRecursive(Node current, String filePath, Set<Node> visited) {
        if (current == null || visited.contains(current)) {
            return;
        }
        visited.add(current);
        current.setTargetFilePath(filePath);

        for (Node neighbor : current.getNeighbors()) {
            setFilePathRecursive(neighbor, filePath, visited);
        }
    }
}
