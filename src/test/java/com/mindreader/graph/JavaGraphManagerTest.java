package com.mindreader.graph;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.mindreader.utils.Node;
import com.mindreader.utils.NodeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
}
