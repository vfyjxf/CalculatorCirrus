# Recipe Content Redesign

## Resolution

OK,让我们思考一下配方之间的关系，以及配方材料本质是什么
具体来说，我们需要在给定的N个配方的集合中，得到对配方Y实例的最优(或者说至少一条)可行的路径
路径这里指的是自顶向下的分解路径.

那么问题具体在哪里，似乎查找一个路径很简单，比如我们直接对着输入反向查找所有配方的输出，就像jei那样，快速且高效，
但是实际上这是有问题的，因为jei的查找是宽松的，忽略部分特征的，因为玩家物品栏之中的物品不可能完全满足，所以jei需要进行宽松的查找匹配，
即IIngredientHelper的Object getUid(V ingredient, UidContext context),此处UidContext为Recipe.

那么我们面对的问题是 需要根据情况精确匹配，甚至是忽略掉content本身进行匹配，比如假定存在ComponentIngredient，要求给定ItemStack必须要有X
Component即可作为输入，
那么实际上该ItemStack的约束为`stack.isEmpty() && stack.getComponentCount().get(X) != null`.
我们实际上需要模拟Ingredient所做的，它是一个黑箱，一个`Predicate<Ingredient>`，

我们实际上要将对Ingredient进行区分,将特征鲜明的，易于判断的Ingredient/Content使用高效简洁的实现，对于复杂的则退化成线性匹配。
基于一个假设，我们可以在不增加太多复杂度的前提下支持这种配方匹配。
假设：Ingredient拥有两种Value的组合或Neoforge的ICustomIngredient，没有其他情况，任意复杂度的Ingredient只能是ICustomIngredient
因此，我们可以对Value和ICustomIngredient进行分类，前者可以进行高速匹配，后者则需要线性匹配。
同时，配方本身和Ingredient本身并不一定携带有意义的信息，还需要RecipeSystem那边特殊处理

那么我们如何将这套系统推广到所有ItemStack类似物上？
或者说，如何泛化的描述CustomIngredient这个类型？

设计原则:Recipe和RecipeContent只是数据，常规意义不包含匹配逻辑，匹配逻辑抽象为RecipeConstraint
RecipeSystem:RecipeDataProvider
Recipe:RecipeDataHandle
RecipeContent:RecipeData
RecipeConstraint:RecipeMatcher

好吧经过简单尝试，我发现上述设计并不合理，我们需要的并不是如此粗的颗粒度，至少我们需要共享大部分细微颗粒度的匹配，
比如对于InputContent大部分子类型的匹配逻辑我们都是共享的，只有少部分配方才需要完全覆盖匹配逻辑，而我们有需要针对最常见的匹配逻辑，即简单的关联映射进行快速建模匹配
我们需要的究竟是什么，为什么我们需要将约束抽象化？

让我们回到最初的目的，我们该如何兼容类似ICustomIngredient的东西，以及把ICustomIngredient抽象化
基于分析自GTM和原版配方系统的实现，注意到以下特征：

- 大部分配方的匹配使用一个默认实现，例如GTM的配方在非并行情况下大部分都是用RecipeHelper#matchContents进行匹配，在此处，装配线是例外，因为它额外要求了输入顺序
- 存在会覆盖默认配方匹配逻辑的机器，例如GTM的装配线，Neoforge的DataComponentIngredient输入，
- 大部分默认的匹配逻辑都是简单的Map映射关系，例如minecraft默认的Ingredient只考虑了Item，其他一概不考虑，GTM则是直接使用了Ingredient作为校验器本身。
- 存在不提供有效Ingredient和ResultItem的配方（具体表现为没有Ingredient或者Ingredient为empty)
- 大部分的匹配逻辑实际上是在配方的Ingredient上的，即配方不存在针对单个Ingredient的额外匹配约束，但GTM的RecipeCondition是在配方本身上的

基于上述原则，我们需要设计一套默认匹配器，同时支持自定义override。
我们规定目前设计目标为:
- 对于通用实现要保持快速查询
- 要能对输入进行灵活匹配，支持复杂的逻辑表达式
- 匹配逻辑的实现要支持override

通用匹配规则作为fallback实现，我们默认使用类似Map一类的东西，表示为output -> Recipe的Map形式，让input迭代反向查询

现在给出如下api定义:

- 配方实际上是(id,rawRecipe,Inputs(data and constraint),Outputs(data and simple constraint),constraintOfRecipe,constraintOfMachines)的元组
- 输入其实是ingredient constraint 的集合和人类可读的描述descriptor.
- 输出是输出实际上也是某种集合，但是由于在当前场景下，我们的计算器必需要确定性，因此输出表示为单个content constraint + data 和人类可读的描述
- 配方约束表示为配方对材料施加的额外约束，例如材料比如符合一定的条件，或者存在某些条件，例如GTM的装配线,但实际情况是更复杂的，比如类似祛魔台的配方等，它可以表述为Ingredient上的约束，也可以表述为配方上对材料的约束，或者两者的组合 
- 机器是一类抽象的映射工具，机器并不一定是实体方块，可以是任意环境过程，
我们称为Handler，配方只有和机器结合才有意义，但是大部分情况下，我们假定一个类别的配方都服从同一套机器实现，
少有在原本配方定义的属性上拓展和重新定义的行为，比如GT的电动熔炉并不会让熔炉增产等
 

## Constraint,Except and Actual

我们定义了何为约束，但是我们又该如何设计它的Api，可能看起来很简单，对于输入，仅仅只需要一个`boolean satisfy(Input except ??? actual)`,
对于输出，呃我们真的需要对输出进行约束吗，只需要把它转换为合法地可作为输入的数据即可
问题难点在于actual的类型应该是什么，按照要求，它应该是所有被定义为Content的内容，但是我们目前没有吧value统一到content内，那么应该将Value统一到Content内，我们先前已经明确，Content是游戏内实际用户可以得到的东西
