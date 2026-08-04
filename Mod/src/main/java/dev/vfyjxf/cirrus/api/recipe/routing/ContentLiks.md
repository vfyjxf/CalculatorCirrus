# Links Between Recipe Content

下面我们来讨论如何描述配方之间的链接关系

简单来讲，我们希望表示(recipe,output) -> (recipe,input)这类关系
但是由于配方的灵活性导致了output是确定的(
在当前设计下是确定的，因为我们要求配方必须是确定的，通过不确定的FacetTransform得到当前情况下确定的结果)，
input是不确定的，复合的，但是link又是确定的，因为我们通常描述的都是具体指向，
接下来分类讨论

- EmptyRecipeContent：跳过
- StackContent：确定的，可以直接描述为固定link
- ValueContent：确定的，可以直接描述为固定的链接
- TypedStackListContent/StackListContent/TagStackContent：不确定的，
  但是我们描述的link都是他们实际上符合条件的一个值的link，例如tag:woods,我们实际上绑定的可能是一个橡木的配方，
  而不是一个完全等价于Tag的输出，因为输出被我们规范到唯一确定值了,这些SetLike Content设计上不被当做单个元素的集合对待，因此不可能和Output等价
- ComplexContent：实际上某种无限Stream，因此我们规定一个原则，配方的输入必然来自配方的输出，因此给定配方集S
  ，我们使用最粗暴的匹配从所有配方的输出中匹配ComplexContent为输入的情况，成立时link构建