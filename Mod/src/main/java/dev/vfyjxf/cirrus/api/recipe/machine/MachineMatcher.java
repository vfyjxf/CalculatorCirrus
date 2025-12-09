package dev.vfyjxf.cirrus.api.recipe.machine;

import dev.vfyjxf.cloudlib.api.util.Namespace;

//判断给定机器是否在特定上下文等价，例如某些GT机器支持并行，而大部分不支持，他们不附加额外约束但作为配方上下文影响配方运行结果
//TODO:是否应该抽象为CalculationContextMatcher
public interface MachineMatcher {

    Namespace id();

    boolean matches(MachineEntry entry1, MachineEntry entry2);

}
