package com.mindreader.utils;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a single point in the execution flow.
 * Each node tracks its type, source code line number, and its outgoing execution paths.
 */
public class Node {
    private NodeType type;
    private int lineNumber;
    private final List<Node> neighbors;

    // Default constructor initialized with UNKNOWN type
    public Node() {
        this.type = NodeType.UNKNOWN;
        this.lineNumber = 0;
        this.neighbors = new ArrayList<>();
    }

    public Node(NodeType type) {
        this.type = type;
        this.neighbors = new ArrayList<>();
    }

    // Convenience constructor for faster node creation in GraphManager
    public Node(NodeType type, int lineNumber) {
        this.type = type;
        this.lineNumber = lineNumber;
        this.neighbors = new ArrayList<>();
    }

    public void setNodeType(NodeType type) {
        this.type = type;
    }

    public NodeType getNodeType() {
        return type;
    }

    public void setLineNumber(int lineNumber) {
        this.lineNumber = lineNumber;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    /**
     * Adds an outgoing execution path to another node.
     * @param neighbor The next node in the control flow.
     */
    public void addNeighbor(Node neighbor) {
        if (neighbor != null) {
            this.neighbors.add(neighbor);
        }
    }

    public List<Node> getNeighbors() {
        return neighbors;
    }
}