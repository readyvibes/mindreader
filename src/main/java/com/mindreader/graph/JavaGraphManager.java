package com.mindreader.graph;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
//import com.github.javaparser.ast.stmt.ExpressionStmt;
//import com.github.javaparser.ast.body.FieldDeclaration;
import com.mindreader.utils.Node;
import com.mindreader.utils.NodeType;

import java.util.*;
import java.util.stream.Collectors;

public class JavaGraphManager {

    private final Map<String, List<String>> fileContentCache = new HashMap<>();

    public void putFileContent(String filePath, String content) {
        if (content != null) {
            fileContentCache.put(filePath, Arrays.asList(content.split("\\r?\\n")));
        }
    }

    public Node[] buildFileGraph(CompilationUnit compilationUnit) {
        Node fileStartNode = new Node(NodeType.FILE_START);
        Node tail = fileStartNode;

        tail = processBlock(compilationUnit.getChildNodes(), tail);

        return new Node[] {fileStartNode, tail};
    }

    public Node[] buildFunctionGraph(MethodDeclaration methodDeclaration) {
        int line = methodDeclaration.getBegin().map(p -> p.line).orElse(0);
        Node functionStartNode = new Node(NodeType.FUNCTION_START, line);

        Node tail = functionStartNode;

        // Get the statements from the method body and process them
        if (methodDeclaration.getBody().isPresent()) {
            List<Statement> statements = methodDeclaration.getBody().get().getStatements();

            // Create a generic Node list to pass to processBlock
            List<com.github.javaparser.ast.Node> genericNodes = new ArrayList<>(statements);
            tail = processBlock(genericNodes, tail);
        }

        return new Node[]{functionStartNode, tail};
    }

    public Node processBlock(List<com.github.javaparser.ast.Node> nodes, Node currentTail) {
        for (com.github.javaparser.ast.Node node : nodes) {
            int line = node.getBegin().map(p -> p.line).orElse(0);
            if (node instanceof IfStmt ifStmt) {
                currentTail = handleIfStructure(ifStmt, currentTail);
            } else if (node instanceof MethodDeclaration method) {
                Node defNode = new Node(NodeType.FUNCTION_DEF, line);
                currentTail.addNeighbor(defNode);

                Node[] result = buildFunctionGraph(method);
                Node innerStart = result[0];
                Node innerExit = result[1];

                defNode.addNeighbor(innerStart);

                currentTail = innerExit;
            } else if (node instanceof ClassOrInterfaceDeclaration classDecl) {
                Node classNode = new Node(NodeType.CLASS, line);
                currentTail.addNeighbor(classNode);
                currentTail = processBlock(new ArrayList<>(classDecl.getMembers()), classNode);
            } else {
                // Check if this statement contains a method call
                Node newNode = detectMethodCall(node, line);
                currentTail.addNeighbor(newNode);
                currentTail = newNode;
            }
        }
        return currentTail;
    }

    private Node handleIfStructure(IfStmt ifStmt, Node branchParent) {
        int line = ifStmt.getBegin().map(p -> p.line).orElse(0);
        int endLine = ifStmt.getEnd().map(p -> p.line).orElse(line);

        Node joinNode = new Node(NodeType.JOIN, endLine);

        Node ifNode = new Node(NodeType.IF, line);
        branchParent.addNeighbor(ifNode);

        Node ifBodyTail = processBlock(getStatementAsList(ifStmt.getThenStmt()), ifNode);
        ifBodyTail.addNeighbor(joinNode);

        IfStmt currIf = ifStmt;
        while (currIf.getElseStmt().isPresent()) {
            Statement elseStmt = currIf.getElseStmt().get();

            if (elseStmt instanceof IfStmt elifStmt) {
                Node elifNode = new Node(NodeType.ELSE_IF, elifStmt.getBegin().map(p -> p.line).orElse(0));

                branchParent.addNeighbor(elifNode);

                Node elifBodyTail = processBlock(getStatementAsList(elifStmt.getThenStmt()), elifNode);
                elifBodyTail.addNeighbor(joinNode);

                currIf = elifStmt;
            } else {
                Node elseNode = new Node(NodeType.ELSE, elseStmt.getBegin().map(p -> p.line).orElse(0));
                branchParent.addNeighbor(elseNode);

                Node elseBodyTail = processBlock(getStatementAsList(elseStmt), elseNode);
                elseBodyTail.addNeighbor(joinNode);

                currIf = null;
                break;
            }
        }

        if (currIf != null && currIf.getElseStmt().isEmpty()) {
            branchParent.addNeighbor(joinNode);
        }

        return joinNode;
    }

    private List<com.github.javaparser.ast.Node> getStatementAsList(Statement stmt) {
        if (stmt.isBlockStmt()) {
            return new ArrayList<>(stmt.asBlockStmt().getStatements());
        }
        List<com.github.javaparser.ast.Node> list = new ArrayList<>();
        list.add(stmt);
        return list;
    }

    /**
     * Explores the graph to find and display all unique execution sequences.
     * Equivalent to Python's display_all_paths.
     */
    public void displayAllPaths(Node startNode) {
        List<List<Node>> allPaths = new ArrayList<>();
        findPathsRecursive(startNode, new ArrayList<>(), allPaths);

        _printFormattedPaths(allPaths);
    }

    private void findPathsRecursive(Node currentNode, List<Node> currentPath, List<List<Node>> allPaths) {
        List<Node> newPath = new ArrayList<>(currentPath);
        newPath.add(currentNode);

        List<Node> neighbors = currentNode.getNeighbors();

        // If no neighbors, we've reached the end of an execution string
        if (neighbors == null || neighbors.isEmpty()) {
            allPaths.add(newPath);
            return;
        }

        for (Node neighbor : neighbors) {
            // Basic cycle detection: if neighbor is already in path, mark as loop and stop
            if (currentPath.contains(neighbor)) {
                List<Node> loopPath = new ArrayList<>(newPath);
                loopPath.add(neighbor);
                allPaths.add(loopPath);
                continue;
            }
            findPathsRecursive(neighbor, newPath, allPaths);
        }
    }

    private void _printFormattedPaths(List<List<Node>> paths) {
        System.out.println("\n--- Project Mindreader: Execution Paths Found (" + paths.size() + ") ---");
        for (int i = 0; i < paths.size(); i++) {
            System.out.println("\nPath " + (i + 1) + ":");
            String pathStr = paths.get(i).stream()
                    .map(node -> "[L" + node.getLineNumber() + ": " + node.getNodeType().name() + "]")
                    .collect(Collectors.joining(" -> "));
            System.out.println("  " + pathStr);
        }
    }

    public Set<String> getPathSignatures(Node startNode) {
        List<String> allPaths = new ArrayList<>();
        findSignaturesRecursive(startNode, new ArrayList<>(), allPaths);
        return new HashSet<>(allPaths);
    }

    private void findSignaturesRecursive(Node currentNode, List<String> currentPath, List<String> allPaths) {
        String nodeSig = "L" + currentNode.getLineNumber() + ":" + currentNode.getNodeType().name();
        List<String> newPath = new ArrayList<>(currentPath);
        newPath.add(nodeSig);

        List<Node> neighbors = currentNode.getNeighbors();
        if (neighbors == null || neighbors.isEmpty()) {
            allPaths.add(String.join(" -> ", newPath));
            return;
        }

        for (Node neighbor : neighbors) {
            String neighborSig = "L" + neighbor.getLineNumber() + ":" + neighbor.getNodeType().name();
            if (currentPath.contains(neighborSig)) {
                List<String> loopPath = new ArrayList<>(newPath);
                loopPath.add("LOOP_DETECTED");
                allPaths.add(String.join(" -> ", loopPath));
                continue;
            }
            findSignaturesRecursive(neighbor, newPath, allPaths);
        }
    }

    public void diffGraphs(Node oldStartNode, Node newStartNode) {
        Set<String> oldPaths = getPathSignatures(oldStartNode);
        Set<String> newPaths = getPathSignatures(newStartNode);

        // Added: In new but not in old
        Set<String> added = new HashSet<>(newPaths);
        added.removeAll(oldPaths);

        // Removed: In old but not in new
        Set<String> removed = new HashSet<>(oldPaths);
        removed.removeAll(newPaths);

        System.out.println("\n--- Project Mindreader: Logic Diff ---");

        if (added.isEmpty() && removed.isEmpty()) {
            System.out.println("No changes in execution logic detected.");
            return;
        }

        if (!removed.isEmpty()) {
            System.out.println("\n[REMOVED PATHS]: " + removed.size());
            removed.forEach(path -> System.out.println("  - " + path));
        }

        if (!added.isEmpty()) {
            System.out.println("\n[ADDED PATHS]: " + added.size());
            added.forEach(path -> System.out.println("  + " + path));
        }
    }

    /**
     * Detects if a statement contains a method call and creates appropriate node type.
     * Returns a METHOD_CALL node if a method call is found, otherwise a STATEMENT node.
     */
    private Node detectMethodCall(com.github.javaparser.ast.Node astNode, int line) {
        final boolean[] hasMethodCall = {false};
        final String[] methodCallName = {null};

        astNode.accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(MethodCallExpr methodCall, Void arg) {
                super.visit(methodCall, arg);
                if (!hasMethodCall[0]) {  // Only capture the first method call
                    hasMethodCall[0] = true;
                    methodCallName[0] = methodCall.getNameAsString();
                }
            }
        }, null);

        if (hasMethodCall[0]) {
            Node methodCallNode = new Node(NodeType.METHOD_CALL, line);
            methodCallNode.setTargetMethodName(methodCallName[0]);
            return methodCallNode;
        } else {
            return new Node(NodeType.STATEMENT, line);
        }
    }

    /**
     * Concatenates two method graphs by splicing the callee graph into the caller graph
     * at the method call site.
     *
     * @param callerStart The start node of the caller method graph
     * @param targetMethodName The name of the method being called
     * @param calleeStart The start node of the callee method graph
     * @param calleeExit The exit node of the callee method graph
     * @return true if concatenation was successful, false if method call not found
     */
    public boolean concatenateGraphs(Node callerStart, String targetMethodName,
                                    Node calleeStart, Node calleeExit) {
        // Find the METHOD_CALL node in the caller graph
        Node methodCallNode = findMethodCallNode(callerStart, targetMethodName, new HashSet<>());

        if (methodCallNode == null) {
            return false;  // Method call not found
        }

        // Get what comes after the method call (if anything)
        List<Node> afterCallNodes = new ArrayList<>(methodCallNode.getNeighbors());

        // Clear the method call node's neighbors
        methodCallNode.getNeighbors().clear();

        // Connect method call node to the callee's start
        methodCallNode.addNeighbor(calleeStart);

        // Connect callee's exit to what originally came after the call
        for (Node afterNode : afterCallNodes) {
            calleeExit.addNeighbor(afterNode);
        }

        return true;
    }

    /**
     * Recursively searches for a METHOD_CALL node with the specified target method name.
     */
    private Node findMethodCallNode(Node current, String targetMethodName, Set<Node> visited) {
        if (current == null || visited.contains(current)) {
            return null;
        }
        visited.add(current);

        // Check if this is the method call we're looking for
        if (current.getNodeType() == NodeType.METHOD_CALL &&
            targetMethodName.equals(current.getTargetMethodName())) {
            return current;
        }

        // Recursively search neighbors
        for (Node neighbor : current.getNeighbors()) {
            Node found = findMethodCallNode(neighbor, targetMethodName, visited);
            if (found != null) {
                return found;
            }
        }

        return null;
    }

    /**
     * Concatenates file content from two graphs by reading their file paths and line numbers.
     * Walks through both graphs and retrieves the actual source code lines.
     *
     * @param graph1Start The start node of the first graph
     * @param graph2Start The start node of the second graph
     * @return Concatenated source code from both graphs
     */
    public String concatenateFileContent(Node graph1Start, Node graph2Start) {
        StringBuilder concatenated = new StringBuilder();
        List<Node> executionPath = new ArrayList<>();
        Set<Node> visited = new HashSet<>();

        // Follow the execution flow
        collectExecutionPath(graph1Start, executionPath, visited);

        // Track file contexts: stores (filePath, internalIndentLevel)
        Deque<FileContext> contextStack = new ArrayDeque<>();
        String currentFile = null;
        int currentInternalIndent = 0;

        for (Node node : executionPath) {
            String nodeFile = node.getTargetFilePath();
            if (nodeFile == null) continue;

            // Handle File Transitions (Inlining/Returning)
            if (currentFile != null && !nodeFile.equals(currentFile)) {
                boolean isReturning = false;

                // Check if we are returning to a file already in our call stack
                while (!contextStack.isEmpty()) {
                    if (contextStack.peek().filePath.equals(nodeFile)) {
                        FileContext restored = contextStack.pop();
                        currentInternalIndent = restored.savedIndent;
                        isReturning = true;
                        break;
                    }
                    contextStack.pop(); // Discard intermediate contexts if necessary
                }

                if (!isReturning) {
                    // Entering a new file/method call: save current context and start fresh internal indent
                    contextStack.push(new FileContext(currentFile, currentInternalIndent));
                    currentInternalIndent = 0;
                }
            }
            currentFile = nodeFile;

            String sourceLine = readSourceLine(nodeFile, node.getLineNumber());
            if (sourceLine != null && !sourceLine.trim().isEmpty()) {

                // 1. Decrement indent BEFORE printing if we are exiting a block
                if (node.getNodeType() == NodeType.JOIN) {
                    currentInternalIndent = Math.max(0, currentInternalIndent - 1);
                }

                // 2. Calculate Total Indent: Call Stack Depth + Internal File Indent
                int totalIndent = contextStack.size() + currentInternalIndent;

                concatenated.append("    ".repeat(totalIndent))
                        .append(sourceLine.trim())
                        .append("\n");

                // 3. Increment indent AFTER printing if we are entering a block or function
                if (node.getNodeType() == NodeType.FUNCTION_START ||
                        node.getNodeType() == NodeType.CLASS ||
                        node.getNodeType() == NodeType.IF) {
                    currentInternalIndent++;
                }
            }
        }

        return concatenated.toString();
    }

    /**
     * Collects the first execution path through a graph.
     */
    private void collectExecutionPath(Node current, List<Node> path, Set<Node> visited) {
        if (current == null || visited.contains(current)) return;
        visited.add(current);
        path.add(current);

        // Follow the primary execution path (first neighbor)
        if (!current.getNeighbors().isEmpty()) {
            collectExecutionPath(current.getNeighbors().get(0), path, visited);
        }
    }

    /**
     * Adds a node to the file-line map for organizing nodes by file and line.
     */
    private void addNodeToFileLineMap(Node node, java.util.Map<String, java.util.Map<Integer, Node>> fileLineMap) {
        String filePath = node.getTargetFilePath();
        int lineNumber = node.getLineNumber();

        if (filePath == null || lineNumber <= 0) {
            return;
        }

        fileLineMap.putIfAbsent(filePath, new java.util.HashMap<>());
        fileLineMap.get(filePath).put(lineNumber, node);
    }

    /**
     * Reads a specific line from a source file.
     * Returns null if the file cannot be read or line doesn't exist.
     */
    private String readSourceLine(String filePath, int lineNumber) {
        // Check cache first (for memory-based testing)
        if (fileContentCache.containsKey(filePath)) {
            List<String> lines = fileContentCache.get(filePath);
            return (lineNumber > 0 && lineNumber <= lines.size()) ? lines.get(lineNumber - 1) : null;
        }

        // Fallback to disk
        try {
            java.nio.file.Path path = java.nio.file.Paths.get(filePath);
            if (!java.nio.file.Files.exists(path)) return null;
            List<String> lines = java.nio.file.Files.readAllLines(path);
            return (lineNumber > 0 && lineNumber <= lines.size()) ? lines.get(lineNumber - 1) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static class FileContext {
        final String filePath;
        final int savedIndent;

        FileContext(String filePath, int savedIndent) {
            this.filePath = filePath;
            this.savedIndent = savedIndent;
        }
    }
}
