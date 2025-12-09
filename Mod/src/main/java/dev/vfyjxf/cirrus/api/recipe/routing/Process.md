# Calculator Workflow

## 1. Recipe Source Process

- 由用户选取RecipeSystem，从RecipeSystem当中获取配方，该选择界面应该表现为一个带复选框的列表，该流程称为配方源筛选
- 筛选过配方源后，由用户从全局/system specific的配置界面配置RecipeFilter，过滤掉上下文中无意义的配方，例如各种电弧炉回收配方，流体原件灌装配方等
- 完成配方筛选过后，进入到RecipeGraph的组装,配方图的组装主要为构建各种节点之间的材料联系，大部分都是output -> node with
  specific input，[详见Resolution](../Resolution.md)

### RecipeFilter

目标：在特定的计算器上下文筛选出需要的配方，主要为筛选掉大部分无意义配方，例如电弧炉回收，铁砧锻造

设计：
分为Tagged RecipeSystem与RecipeSystem Entry Filter实现，前者对RecipeSystem进行分类，后者对特定System的Recipe进行分类
分歧：是否要把RecipeSystem改成使用Minecraft Registry系统注册？这样自带Tag能力,已经是否要支持全局过滤器，运行过滤器能够对任意System进行过滤？

## 2.PathBuilder

目标：在特定的计算器上下文中填充RecipeGraph，使给定配方集合的配方构建起来Input/Output links

## Fork:Graph ReTransform/Update

出于优化目的，我们需要在计算上下文变动时（RecipeContent/RecipeView#facets/TransformPipeline打开的Transformer变动）
更新RecipeGraph,由于并不是大部分配方都会发生变动，因此我们选择复用大部分节点，只更新部分节点。

## 3.PathFinder

目标：在特定的计算器上下文中寻找路径，并返回结果

## 4.Calculation

目标：在特定的计算器上下文中进行计算，并返回结果