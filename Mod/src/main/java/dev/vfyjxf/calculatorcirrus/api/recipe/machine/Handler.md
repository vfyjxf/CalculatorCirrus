# The Abstraction of Handler

Ok，让我们来讨论下如何抽象那些具有机器属性的事务，大部分都是一个方块或者一个方块能指代的事务，比如大部分多方块
但是对于一些特殊场景，
例如InworldCrafting的世界合成，我们无法找到一个具体的机器，
又比如沉浸工程的多方块，它无法获取一个真的多方块本体，在jei看到的是一个展示模型。
以这些场景为前提，我们需要在针对ItemStack能表示的机器用起来方便，抽象机器能表示。
同时又需要对用ItemStack表示的机器提供提取Constraint的能力。
Api整体上参考ContentResolver上设计，然后添加对来自不同类型的转换，例如...真的存在不是物品的机器，并且还能获取物品形式的吗

现在给出如下概念定义:
HandlerBase/HandlerSource:机器的物理形式，表现为工作台，熔炉， 爆炸，火焰燃烧， etc
HandlerRepresentation:机器的表现形式,大部分表现为物品/方块,jei展示
HandlerConstraint:机器对配方施加的约束，详见Resolution.md

明确我们的需求：
- 一个提供配方系统所有机器的api，例如jei，配方系统本身
- 一个从原始物理形式/配方系统自带的呈现表达转换到统一表达的转换系统
- 一个统一的HandlerRepresentation数据结构，我们并不关心原本的数据是如何，我们只需要能展示，能获取约束即可
- 一个通用的展示系统，以及一个可选实现的获取机器本身的东西