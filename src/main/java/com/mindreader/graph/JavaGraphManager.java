package com.mindreader.graph;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.mindreader.utils.Node;
import com.mindreader.utils.NodeType;
import java.util.List;
import java.util.ArrayList;

public class JavaGraphManager {

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
                Node newNode = new Node(NodeType.STATEMENT, line);
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
}
