package com.mindreader.utils;

/**
 * Represents the various types of nodes within the Control Flow Graph.
 */
public enum NodeType {
    UNKNOWN,
    FILE_START,
    STATEMENT,
    FUNCTION_DEF,
    IF,
    ELSE_IF,
    ELSE,
    JOIN,
    CLASS,
    FUNCTION_START,
    METHOD
}