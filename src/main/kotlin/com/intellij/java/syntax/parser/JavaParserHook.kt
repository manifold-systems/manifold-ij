package com.intellij.java.syntax.parser

import com.intellij.platform.syntax.SyntaxElementType
import java.util.function.Consumer

interface JavaParserHook {
    fun updateConverter( tuple_expression: SyntaxElementType, tuple_value_expression: SyntaxElementType )
}