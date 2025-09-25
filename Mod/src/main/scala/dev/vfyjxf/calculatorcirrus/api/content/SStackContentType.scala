package dev.vfyjxf.calculatorcirrus.api.content

import net.minecraft.world.item.{Item, ItemStack}

trait SStackContentType[Stack] {

  type Value
  val tagType: Option[STagType {type Value; type Stack}] = scala.None

}

object ItemTagType extends STagType {
  override type Value = Item
  override type Stack = ItemStack

  override def makeStack(value: Item): ItemStack = ItemStack(value, 1)
}

class ItemStackSStackType extends SStackContentType[ItemStack] {

  override type Value = Item
  override val tagType: Option[STagType {type Value; type Stack}] = Some(ItemTagType)
}

trait STagType {
  type Value
  type Stack

  def makeStack(value: Value): Stack

}