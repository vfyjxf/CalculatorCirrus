package dev.vfyjxf.cirrus.api.content

import dev.vfyjxf.cirrus.api.content.render.StackRenderer
import dev.vfyjxf.cirrus.api.content.value.ContentStack
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component

extension [T, S](stack: ContentStack[T, S]) {

  inline def render(graphics: GuiGraphics, width: Int, height: Int, delta: Float)(using renderer: StackRenderer[T, S]): Unit = {
    renderer.render(graphics, stack, width, height, delta)
  }

  inline def decorate(graphics: GuiGraphics, width: Int, height: Int, delta: Float)(using renderer: StackRenderer[T, S]): Unit = {
    renderer.decorate(graphics, stack, width, height, delta)
  }

  inline def renderStack(graphics: GuiGraphics, width: Int, height: Int, delta: Float): Unit = {
    given StackRenderer[T, S] = StackRenderer.getStackRender(stack.`type`())

    render(graphics, width, height, delta)
  }

  inline def decorateStack(graphics: GuiGraphics, width: Int, height: Int, delta: Float): Unit = {
    given StackRenderer[T, S] = StackRenderer.getStackRender(stack.`type`())

    decorate(graphics, width, height, delta)
  }

  inline def name(using renderer: StackRenderer[T, S]): Component = {
    renderer.getName(stack.value)
  }
}
