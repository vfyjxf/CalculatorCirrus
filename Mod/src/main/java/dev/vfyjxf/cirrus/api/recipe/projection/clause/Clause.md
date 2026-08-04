# Clause

Clause是一个修饰符+检测器+变换器的集合，他描述了某个配方/配方成分具有的特征属性，例如，对于GTM的消耗锤子耐久打板的配方，
我们认为锤子应该有一个Item2Damage Clause修饰，因此我们不会把这个配方和工具升级配方相混淆，还能拿到每次消耗的耐久

## 为什么不让构造RecipeProjection的RecipeSystem自己处理转换？

1. 对变换逻辑进行共享，标准化相同流程，RecipeSystem(和它的Extension)只需要处理如何标记的问题
2. 让变换变的可选，例如我们可以给配方标记GTM的工作电压Clause，这样我们可以让用户自己决定是否应用超频的Transformation

## RecipeProjection Clause V.S Content Clause

为什么我们需要RecipeProjection Clause，RecipeProjection Clause有何特殊之处？
为什么我们要在整体上区分，让api变的如此难看并且难以使用？

结论，RecipeProjection Clause变成Content Clause的子集

## Clause是否都有一个Clause Transformer ?

问题的本质是是否有只用来提供数据的Clause，我们为什么要使用Clause来提供单纯的数据而不是提供一套新的Api？
当前设想中的Clause:

1. 描述材料属性的：如耐久，充能，修理
2. 描述配方属性的：如最低电压，线圈温度

结论，Transformer应该和Clause区分开来，他们是有关联但是想对独立的关系，不是必须绑定在一起的。

## 关于Clause以及Transformer相关问题的讨论

1. 对于单边（只对输入/输出进行的）修饰的Transformer是否应该拥有对双边（输入和输出）的修改能力 (应该)
2. ContentTransformer是否只应该对RecipeContent进行递增变换,即输入只会更丰富，输出只会更丰富（当然不)
3. 对于配方本身的修饰该如何容纳进去，又如何表达为ContentTransformer进行变换呢 (应该对整体直接进行变换，从ViewA->ViewB )
4. 部分Clause之间应该是互斥的，我们需要定义一个Api去描述这种情况，来防止未定义行为。

基于上述讨论，我们得到如下新约定：
对于Content的Clause的Transform，我们默认为Replace，也就是说针对某个Content的Transform我们默认会把原有的移除，除非Transformer在increment中重新添加回来
对于RecipeProjection的Clause的Transform ，我们默认为Undo, 只有Transformer描述的改动才会被应用，不会有额外的默认行为
