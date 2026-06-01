"""FluxDSL — parser, validator, converters, and CLI."""

from .lexer import Token, TokenKind, Lexer, LexerError
from .parser import Parser, ParserError, parse
from .main import main
from .validator import validate
from .json_ast import (
    Root, Item, Pair, Include, Schema,
    Value, Str, Num, Bool, Null, Obj, Lst,
    ObjEntry, ObjPair, ObjInclude, ObjSchema,
)
from .flux_mapper import to_json, from_json, to_dto, from_dto

__all__ = [
    "Token", "TokenKind", "Lexer", "LexerError",
    "Parser", "ParserError", "parse",
    "main", "validate",
    "Root", "Item", "Pair", "Include", "Schema",
    "Value", "Str", "Num", "Bool", "Null", "Obj", "Lst",
    "ObjEntry", "ObjPair", "ObjInclude", "ObjSchema",
    "to_json", "from_json", "to_dto", "from_dto",
]
