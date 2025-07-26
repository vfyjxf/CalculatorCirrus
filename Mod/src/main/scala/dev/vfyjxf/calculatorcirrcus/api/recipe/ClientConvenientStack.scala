package dev.vfyjxf.calculatorcirrcus.api.recipe

import dev.vfyjxf.calculatorcirrus.api.content.`type`.ContentRenderer
import dev.vfyjxf.calculatorcirrus.api.content.value.ContentStack
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component

extension [T](stack: ContentStack[T])(using render: ContentRenderer[T]) {

    inline def render(graphics: GuiGraphics, width: Int, height: Int, mouseX: Int, mouseY: Int, delta: Float): Unit = {
        render.render(stack.value(), graphics, width, height, mouseX, mouseY, delta)
    }

    inline def render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float): Unit = {
        render.render(stack.value(), graphics, mouseX, mouseY, delta)
    }

    inline def decorate(graphics: GuiGraphics, width: Int, height: Int, mouseX: Int, mouseY: Int): Unit = {
        render.decorate(stack, graphics, width, height, mouseX, mouseY)
    }

    inline def name: Component = {
        render.getName(stack.value())
    }
}

extension [T](stack: ContentStack[T]) {

    inline def renderStack(graphics: GuiGraphics, width: Int, height: Int, mouseX: Int, mouseY: Int, delta: Float): Unit = {
        given ContentRenderer[T] = ContentRenderer.getRenderer(stack.contentType())

        stack.render(graphics, width, height, mouseX, mouseY, delta)
    }

    inline def renderStack(graphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float): Unit = {
        given ContentRenderer[T] = ContentRenderer.getRenderer(stack.contentType())

        stack.render(graphics, mouseX, mouseY, delta)
    }

    inline def decorateStack(graphics: GuiGraphics, width: Int, height: Int, mouseX: Int, mouseY: Int): Unit = {
        given ContentRenderer[T] = ContentRenderer.getRenderer(stack.contentType())

        stack.decorate(graphics, width, height, mouseX, mouseY)
    }

}


