package dev.vfyjxf.cirrus.api.content.value;

public sealed interface ContentVariant permits ContentKind, ContentStack, ContentValue, //data
        ContentResource {}
